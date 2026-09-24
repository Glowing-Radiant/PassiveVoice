package com.passivevoice.speech

/**
 * Decides *whether* PassiveVoice is allowed to speak right now. The "what to say" decision lives
 * in the interpreter; this is purely the mute gate described in the PRD's TalkBack-coexistence
 * section: the service stays fully active (observing, interpreting) even while muted, so it can
 * resume instantly once the reason for muting goes away.
 */
object SpeechPolicy {
  fun isMuted(talkBackActive: Boolean, autoMuteWithTalkBack: Boolean): Boolean = talkBackActive && autoMuteWithTalkBack
}
