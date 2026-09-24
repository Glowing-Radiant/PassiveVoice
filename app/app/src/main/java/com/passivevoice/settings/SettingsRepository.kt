package com.passivevoice.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "passive_voice_settings")

/** Local-only settings persistence. No network, no account — matches the PRD's privacy model. */
class SettingsRepository(private val context: Context) {

  object Keys {
    val VERBOSITY = stringPreferencesKey("verbosity")
    val SPEECH_RATE = floatPreferencesKey("speech_rate")
    val PITCH = floatPreferencesKey("pitch")
    val VOLUME = floatPreferencesKey("volume")
    val QUEUE_MODE = stringPreferencesKey("queue_mode")
    val AUTO_MUTE_WITH_TALKBACK = booleanPreferencesKey("auto_mute_with_talkback")
    val TTS_ENGINE = stringPreferencesKey("tts_engine")
    val DEBUG_LOGGING_ENABLED = booleanPreferencesKey("debug_logging_enabled")
  }

  val settings: Flow<PassiveVoiceSettings> =
    context.dataStore.data.map { prefs ->
      val defaults = PassiveVoiceSettings()
      PassiveVoiceSettings(
        verbosity = prefs[Keys.VERBOSITY]?.let { runCatching { Verbosity.valueOf(it) }.getOrNull() } ?: defaults.verbosity,
        speechRate = prefs[Keys.SPEECH_RATE] ?: defaults.speechRate,
        pitch = prefs[Keys.PITCH] ?: defaults.pitch,
        volume = prefs[Keys.VOLUME] ?: defaults.volume,
        queueMode = prefs[Keys.QUEUE_MODE]?.let { runCatching { SpeechQueueMode.valueOf(it) }.getOrNull() } ?: defaults.queueMode,
        autoMuteWithTalkBack = prefs[Keys.AUTO_MUTE_WITH_TALKBACK] ?: defaults.autoMuteWithTalkBack,
        ttsEngine = prefs[Keys.TTS_ENGINE],
        debugLoggingEnabled = prefs[Keys.DEBUG_LOGGING_ENABLED] ?: defaults.debugLoggingEnabled,
      )
    }

  suspend fun setVerbosity(verbosity: Verbosity) = context.dataStore.edit { it[Keys.VERBOSITY] = verbosity.name }

  suspend fun setSpeechRate(rate: Float) = context.dataStore.edit { it[Keys.SPEECH_RATE] = rate }

  suspend fun setPitch(pitch: Float) = context.dataStore.edit { it[Keys.PITCH] = pitch }

  suspend fun setVolume(volume: Float) = context.dataStore.edit { it[Keys.VOLUME] = volume }

  suspend fun setQueueMode(mode: SpeechQueueMode) = context.dataStore.edit { it[Keys.QUEUE_MODE] = mode.name }

  suspend fun setAutoMuteWithTalkBack(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_MUTE_WITH_TALKBACK] = enabled }

  suspend fun setTtsEngine(engine: String?) =
    context.dataStore.edit {
      if (engine == null) it.remove(Keys.TTS_ENGINE) else it[Keys.TTS_ENGINE] = engine
    }

  suspend fun setDebugLoggingEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.DEBUG_LOGGING_ENABLED] = enabled }
}
