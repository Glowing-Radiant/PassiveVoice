package com.passivevoice.speech

import com.passivevoice.settings.SpeechQueueMode

enum class SpeechDecision { INTERRUPT, QUEUE, DROP }

/**
 * PRD §11: "Battery 5 percent" may interrupt "Scrolled", but "Scrolled" must never interrupt
 * "Message received from Rahul". Background-level feedback that can't interrupt is dropped rather
 * than queued, since by the time it plays it no longer describes what just happened.
 */
object SpeechArbiter {
  fun decide(incoming: SpeechPriority, speaking: SpeechPriority?, queueMode: SpeechQueueMode): SpeechDecision =
    when {
      speaking == null -> SpeechDecision.INTERRUPT
      incoming.ordinal < speaking.ordinal -> SpeechDecision.INTERRUPT
      incoming == speaking -> if (queueMode == SpeechQueueMode.QUEUE) SpeechDecision.QUEUE else SpeechDecision.INTERRUPT
      incoming.ordinal >= SpeechPriority.LOW.ordinal -> SpeechDecision.DROP
      else -> SpeechDecision.QUEUE
    }
}
