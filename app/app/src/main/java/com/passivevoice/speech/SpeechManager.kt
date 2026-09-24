package com.passivevoice.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.passivevoice.settings.AudioStream
import com.passivevoice.settings.SpeechQueueMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log10
import kotlin.math.roundToInt

/** Thin wrapper around Android's standard [TextToSpeech] — the PRD explicitly calls for the platform TTS, not a bundled engine. */
class SpeechManager(private val context: Context) {
  private var tts: TextToSpeech? = null
  private val ready = AtomicBoolean(false)
  private val utteranceCounter = AtomicInteger()

  /** Utterances queued or currently playing, so incoming speech can be weighed against them. */
  private val pending = ConcurrentHashMap<String, SpeechPriority>()

  @Volatile private var stream = AudioStream.ACCESSIBILITY
  @Volatile private var volume = 1f

  // TTS audio is tagged with this session so an effect can be attached to our speech only.
  private val audioSessionId = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
  private var loudnessEnhancer: LoudnessEnhancer? = null

  fun initialize(enginePackage: String?, onReady: () -> Unit = {}) {
    tts?.shutdown()
    ready.set(false)
    pending.clear()
    tts =
      TextToSpeech(
        context,
        { status ->
          if (status == TextToSpeech.SUCCESS) {
            tts?.setAudioAttributes(attributesFor(stream))
            tts?.setOnUtteranceProgressListener(progressListener)
            ready.set(true)
            onReady()
          } else {
            Log.w(TAG, "TextToSpeech init failed (status=$status, engine=$enginePackage)")
          }
        },
        enginePackage,
      )
  }

  fun setVoiceParams(rate: Float, pitch: Float) {
    tts?.setSpeechRate(rate)
    tts?.setPitch(pitch)
  }

  /** [volume] is relative to the chosen stream: 1.0 is its full level, and above that speech is boosted. */
  fun setOutput(stream: AudioStream, volume: Float) {
    if (stream != this.stream) {
      this.stream = stream
      if (ready.get()) tts?.setAudioAttributes(attributesFor(stream))
    }
    this.volume = volume
    applyBoost(volume)
  }

  private fun applyBoost(volume: Float) {
    val gainMillibels = if (volume > 1f) (2000 * log10(volume)).roundToInt() else 0
    try {
      if (gainMillibels == 0) {
        loudnessEnhancer?.enabled = false
        return
      }
      val enhancer = loudnessEnhancer ?: LoudnessEnhancer(audioSessionId).also { loudnessEnhancer = it }
      enhancer.setTargetGain(gainMillibels)
      enhancer.enabled = true
    } catch (e: RuntimeException) {
      // Some devices don't offer the effect; speech still plays at full (unboosted) volume.
      Log.w(TAG, "Loudness boost unavailable", e)
    }
  }

  /** Returns what happened to [text], or null when no engine is ready to speak. */
  fun speak(text: String, priority: SpeechPriority, queueMode: SpeechQueueMode): SpeechDecision? {
    val engine = tts ?: return null
    if (!ready.get()) return null

    val speaking = pending.values.minByOrNull { it.ordinal }
    val decision = SpeechArbiter.decide(priority, speaking, queueMode)
    if (decision == SpeechDecision.DROP) return decision

    val androidQueueMode =
      if (decision == SpeechDecision.INTERRUPT) {
        pending.clear()
        TextToSpeech.QUEUE_FLUSH
      } else {
        TextToSpeech.QUEUE_ADD
      }
    val utteranceId = "passive_voice_${utteranceCounter.incrementAndGet()}"
    pending[utteranceId] = priority
    val params =
      Bundle().apply {
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
        putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, audioSessionId)
      }
    if (engine.speak(text, androidQueueMode, params, utteranceId) != TextToSpeech.SUCCESS) {
      pending.remove(utteranceId)
      return null
    }
    return decision
  }

  fun stop() {
    pending.clear()
    tts?.stop()
  }

  fun shutdown() {
    pending.clear()
    tts?.shutdown()
    tts = null
    ready.set(false)
    loudnessEnhancer?.release()
    loudnessEnhancer = null
  }

  private val progressListener =
    object : UtteranceProgressListener() {
      override fun onStart(utteranceId: String) = Unit

      override fun onDone(utteranceId: String) {
        pending.remove(utteranceId)
      }

      @Deprecated("Deprecated in Java")
      override fun onError(utteranceId: String) {
        pending.remove(utteranceId)
      }

      override fun onError(utteranceId: String, errorCode: Int) {
        pending.remove(utteranceId)
      }

      override fun onStop(utteranceId: String, interrupted: Boolean) {
        pending.remove(utteranceId)
      }
    }

  private companion object {
    const val TAG = "PassiveVoice/Speech"

    fun attributesFor(stream: AudioStream): AudioAttributes =
      AudioAttributes.Builder().setUsage(stream.usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
  }
}
