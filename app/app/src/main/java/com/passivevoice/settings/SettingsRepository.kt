package com.passivevoice.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
    val SPEECH_DELAY_MS = intPreferencesKey("speech_delay_ms")
    val NARRATE_CLICKS = booleanPreferencesKey("narrate_clicks")
    val NARRATE_TOGGLES = booleanPreferencesKey("narrate_toggles")
    val NARRATE_SELECTIONS = booleanPreferencesKey("narrate_selections")
    val NARRATE_SCROLL = booleanPreferencesKey("narrate_scroll")
    val NARRATE_WINDOW_CHANGES = booleanPreferencesKey("narrate_window_changes")

    // Per-app overrides live under "app:<package>:<field>"; package names never contain ':'.
    private const val APP_PREFIX = "app:"
    const val APP_ENABLED = "enabled"
    const val APP_VERBOSITY = "verbosity"
    const val APP_SCROLL = "scroll"

    fun appEnabled(packageName: String) = booleanPreferencesKey("$APP_PREFIX$packageName:$APP_ENABLED")

    fun appVerbosity(packageName: String) = stringPreferencesKey("$APP_PREFIX$packageName:$APP_VERBOSITY")

    fun appScroll(packageName: String) = booleanPreferencesKey("$APP_PREFIX$packageName:$APP_SCROLL")

    /** Splits "app:<package>:<field>" into (package, field), or null for any other key. */
    fun parseAppKey(name: String): Pair<String, String>? {
      if (!name.startsWith(APP_PREFIX)) return null
      val rest = name.removePrefix(APP_PREFIX)
      val separator = rest.lastIndexOf(':')
      if (separator <= 0) return null
      return rest.substring(0, separator) to rest.substring(separator + 1)
    }
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
        speechDelayMs = prefs[Keys.SPEECH_DELAY_MS] ?: defaults.speechDelayMs,
        narrateClicks = prefs[Keys.NARRATE_CLICKS] ?: defaults.narrateClicks,
        narrateToggles = prefs[Keys.NARRATE_TOGGLES] ?: defaults.narrateToggles,
        narrateSelections = prefs[Keys.NARRATE_SELECTIONS] ?: defaults.narrateSelections,
        narrateScroll = prefs[Keys.NARRATE_SCROLL] ?: defaults.narrateScroll,
        narrateWindowChanges = prefs[Keys.NARRATE_WINDOW_CHANGES] ?: defaults.narrateWindowChanges,
        appProfiles = readAppProfiles(prefs),
      )
    }

  private fun readAppProfiles(prefs: Preferences): Map<String, AppProfile> {
    val profiles = mutableMapOf<String, AppProfile>()
    for ((key, value) in prefs.asMap()) {
      val (packageName, field) = Keys.parseAppKey(key.name) ?: continue
      val profile = profiles[packageName] ?: AppProfile()
      profiles[packageName] =
        when (field) {
          Keys.APP_ENABLED -> profile.copy(narrationEnabled = value as Boolean)
          Keys.APP_VERBOSITY -> profile.copy(verbosity = runCatching { Verbosity.valueOf(value as String) }.getOrNull())
          Keys.APP_SCROLL -> profile.copy(scrollNarration = value as Boolean)
          else -> profile
        }
    }
    return profiles.filterValues { !it.isDefault }
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

  suspend fun setSpeechDelayMs(delayMs: Int) = context.dataStore.edit { it[Keys.SPEECH_DELAY_MS] = delayMs }

  suspend fun setNarrateClicks(enabled: Boolean) = context.dataStore.edit { it[Keys.NARRATE_CLICKS] = enabled }

  suspend fun setNarrateToggles(enabled: Boolean) = context.dataStore.edit { it[Keys.NARRATE_TOGGLES] = enabled }

  suspend fun setNarrateSelections(enabled: Boolean) = context.dataStore.edit { it[Keys.NARRATE_SELECTIONS] = enabled }

  suspend fun setNarrateScroll(enabled: Boolean) = context.dataStore.edit { it[Keys.NARRATE_SCROLL] = enabled }

  suspend fun setNarrateWindowChanges(enabled: Boolean) = context.dataStore.edit { it[Keys.NARRATE_WINDOW_CHANGES] = enabled }

  suspend fun setAppNarrationEnabled(packageName: String, enabled: Boolean) =
    context.dataStore.edit {
      if (enabled) it.remove(Keys.appEnabled(packageName)) else it[Keys.appEnabled(packageName)] = false
    }

  suspend fun setAppVerbosity(packageName: String, verbosity: Verbosity?) =
    context.dataStore.edit {
      if (verbosity == null) it.remove(Keys.appVerbosity(packageName)) else it[Keys.appVerbosity(packageName)] = verbosity.name
    }

  suspend fun setAppScrollNarration(packageName: String, enabled: Boolean?) =
    context.dataStore.edit {
      if (enabled == null) it.remove(Keys.appScroll(packageName)) else it[Keys.appScroll(packageName)] = enabled
    }

  suspend fun resetApp(packageName: String) =
    context.dataStore.edit {
      it.remove(Keys.appEnabled(packageName))
      it.remove(Keys.appVerbosity(packageName))
      it.remove(Keys.appScroll(packageName))
    }
}
