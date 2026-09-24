package com.passivevoice.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextManagerTest {

  @Test
  fun `repeating the same text within the dedup window is suppressed`() {
    val context = ContextManager(dedupWindowMs = 1000L)
    context.recordSpoken("Liked", now = 1_000L)
    assertTrue(context.isDuplicate("Liked", now = 1_500L))
  }

  @Test
  fun `same text outside the dedup window is allowed again`() {
    val context = ContextManager(dedupWindowMs = 1000L)
    context.recordSpoken("Liked", now = 1_000L)
    assertFalse(context.isDuplicate("Liked", now = 3_000L))
  }

  @Test
  fun `different text is never a duplicate`() {
    val context = ContextManager(dedupWindowMs = 1000L)
    context.recordSpoken("Liked", now = 1_000L)
    assertFalse(context.isDuplicate("Unliked", now = 1_050L))
  }
}
