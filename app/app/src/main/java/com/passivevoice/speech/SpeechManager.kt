package com.passivevoice.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.passivevoice.settings.SpeechQueueMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Thin wrapper around Android's standard [TextToSpeech] — the PRD explicitly calls for the platform TTS, not a bundled engine. */
class SpeechManager(private val context: Context) {
  private var tts: TextToSpeech? = null
  private val ready = AtomicBoolean(false)
  private val utteranceCounter = AtomicInteger()

  /** Utterances queued or currently playing, so incoming speech can be weighed against them. */
  private val pending = ConcurrentHashMap<String, SpeechPriority>()

  fun initialize(enginePackage: String?, onReady: () -> Unit = {}) {
    tts?.shutdown()
    ready.set(false)
    pending.clear()
    tts =
      TextToSpeech(
        context,
        { status ->
          if (status == TextToSpeech.SUCCESS) {
            tts?.setAudioAttributes(ACCESSIBILITY_AUDIO)
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

  /** Returns what happened to [text], or null when no engine is ready to speak. */
  fun speak(text: String, priority: SpeechPriority, queueMode: SpeechQueueMode, volume: Float): SpeechDecision? {
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
    val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
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

    // Same stream TalkBack uses: follows the accessibility volume rather than media volume.
    val ACCESSIBILITY_AUDIO: AudioAttributes =
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
  }
}
