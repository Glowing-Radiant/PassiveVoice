package com.passivevoice.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import com.passivevoice.settings.SpeechQueueMode
import java.util.concurrent.atomic.AtomicBoolean

/** Thin wrapper around Android's standard [TextToSpeech] — the PRD explicitly calls for the platform TTS, not a bundled engine. */
class SpeechManager(private val context: Context) {
  private var tts: TextToSpeech? = null
  private val ready = AtomicBoolean(false)
  private var utteranceCounter = 0

  fun initialize(enginePackage: String?, onReady: () -> Unit = {}) {
    tts?.shutdown()
    ready.set(false)
    tts =
      TextToSpeech(
        context,
        { status ->
          ready.set(status == TextToSpeech.SUCCESS)
          if (ready.get()) onReady() else Log.w(TAG, "TextToSpeech init failed (status=$status, engine=$enginePackage)")
        },
        enginePackage,
      )
  }

  fun setVoiceParams(rate: Float, pitch: Float) {
    tts?.setSpeechRate(rate)
    tts?.setPitch(pitch)
  }

  fun speak(text: String, queueMode: SpeechQueueMode, volume: Float) {
    val engine = tts ?: return
    if (!ready.get()) return
    val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
    val androidQueueMode = if (queueMode == SpeechQueueMode.QUEUE) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
    engine.speak(text, androidQueueMode, params, "passive_voice_${utteranceCounter++}")
  }

  fun stop() {
    tts?.stop()
  }

  fun shutdown() {
    tts?.shutdown()
    tts = null
    ready.set(false)
  }

  private companion object {
    const val TAG = "PassiveVoice/Speech"
  }
}
