package com.passivevoice.speech

import com.passivevoice.settings.SpeechQueueMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechArbiterTest {

  private fun decide(incoming: SpeechPriority, speaking: SpeechPriority?, mode: SpeechQueueMode = SpeechQueueMode.INTERRUPT) =
    SpeechArbiter.decide(incoming, speaking, mode)

  @Test
  fun `speaks immediately when nothing is playing`() {
    assertEquals(SpeechDecision.INTERRUPT, decide(SpeechPriority.LOW, speaking = null))
  }

  @Test
  fun `critical speech interrupts a scroll announcement, matching PRD battery example`() {
    assertEquals(SpeechDecision.INTERRUPT, decide(SpeechPriority.CRITICAL, speaking = SpeechPriority.LOW))
  }

  @Test
  fun `scroll feedback never interrupts more important speech, matching PRD message example`() {
    assertEquals(SpeechDecision.DROP, decide(SpeechPriority.LOW, speaking = SpeechPriority.NORMAL))
  }

  @Test
  fun `newer equal-priority speech replaces older in interrupt mode and waits in queue mode`() {
    assertEquals(SpeechDecision.INTERRUPT, decide(SpeechPriority.HIGH, SpeechPriority.HIGH, SpeechQueueMode.INTERRUPT))
    assertEquals(SpeechDecision.QUEUE, decide(SpeechPriority.HIGH, SpeechPriority.HIGH, SpeechQueueMode.QUEUE))
  }

  @Test
  fun `less important but meaningful speech waits its turn`() {
    assertEquals(SpeechDecision.QUEUE, decide(SpeechPriority.NORMAL, speaking = SpeechPriority.HIGH))
  }
}
