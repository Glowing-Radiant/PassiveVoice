package com.passivevoice.speech

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech

data class TtsEngineInfo(val packageName: String, val label: String)

object TtsEngineManager {
  fun listAvailableEngines(context: Context): List<TtsEngineInfo> {
    val packageManager = context.packageManager
    val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
    @Suppress("DEPRECATION")
    val resolveInfos = packageManager.queryIntentServices(intent, 0)
    return resolveInfos
      .mapNotNull { resolveInfo ->
        val packageName = resolveInfo.serviceInfo?.packageName ?: return@mapNotNull null
        TtsEngineInfo(packageName = packageName, label = resolveInfo.loadLabel(packageManager).toString())
      }
      .distinctBy { it.packageName }
  }
}
