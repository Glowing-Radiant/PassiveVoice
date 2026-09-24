package com.passivevoice.settings

data class PassiveVoiceSettings(
  val verbosity: Verbosity = Verbosity.STANDARD,
  val speechRate: Float = 1.0f,
  val pitch: Float = 1.0f,
  val volume: Float = 1.0f,
  val queueMode: SpeechQueueMode = SpeechQueueMode.INTERRUPT,
  val autoMuteWithTalkBack: Boolean = true,
  val ttsEngine: String? = null, // null = system default engine
  val debugLoggingEnabled: Boolean = false,
)
