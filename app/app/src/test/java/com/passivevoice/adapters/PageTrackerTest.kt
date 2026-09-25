package com.passivevoice.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageTrackerTest {

  @Test
  fun `forward, back and forward again`() {
    val tracker = PageTracker()
    assertEquals(PageMove.FIRST, tracker.observe("A"))
    assertNull(tracker.observe("A"))
    assertEquals(PageMove.NEXT, tracker.observe("B"))
    assertEquals(PageMove.NEXT, tracker.observe("C"))
    assertEquals(PageMove.PREVIOUS, tracker.observe("B"))
    assertEquals(PageMove.PREVIOUS, tracker.observe("A"))
    assertEquals(PageMove.NEXT, tracker.observe("B"))
  }

  @Test
  fun `returning to the feed starts a fresh visit`() {
    val tracker = PageTracker()
    tracker.observe("A")
    tracker.leave()
    assertEquals(PageMove.FIRST, tracker.observe("A"))
    assertEquals(PageMove.NEXT, tracker.observe("B"))
  }
}
