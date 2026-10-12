package com.passivevoice.settings

data class PassiveVoiceSettings(
  val verbosity: Verbosity = Verbosity.STANDARD,
  val speechRate: Float = 1.0f,
  val pitch: Float = 1.0f,
  /** 1.0 = full volume of the chosen stream; above that, speech is boosted with a loudness enhancer. */
  val volume: Float = 1.0f,
  val audioStream: AudioStream = AudioStream.ACCESSIBILITY,
  val queueMode: SpeechQueueMode = SpeechQueueMode.INTERRUPT,
  val autoMuteWithTalkBack: Boolean = true,
  val ttsEngine: String? = null, // null = system default engine
  val debugLoggingEnabled: Boolean = false,
  /** How long to wait after a tap before speaking, so a state change the tap causes ("Follow" → "Following") can be heard instead of the old label. */
  val speechDelayMs: Int = 250,
  val narrateClicks: Boolean = true,
  val narrateToggles: Boolean = true,
  val narrateSelections: Boolean = true,
  val narrateScroll: Boolean = true,
  val narrateWindowChanges: Boolean = true,
  /** Echo each typed or deleted character. */
  val narrateTypedCharacters: Boolean = true,
  /** Speak the whole word once a space ends it. */
  val narrateTypedWords: Boolean = true,
  /** Richer narration in apps PassiveVoice knows (Instagram, YouTube): "Next reel, by …" instead of "Next item". */
  val appAdaptersEnabled: Boolean = true,
  val appProfiles: Map<String, AppProfile> = emptyMap(),
)

/** Per-app overrides (PRD §23). Null fields inherit the global setting. */
data class AppProfile(
  val narrationEnabled: Boolean = true,
  val verbosity: Verbosity? = null,
  val scrollNarration: Boolean? = null,
) {
  val isDefault: Boolean
    get() = narrationEnabled && verbosity == null && scrollNarration == null
}
