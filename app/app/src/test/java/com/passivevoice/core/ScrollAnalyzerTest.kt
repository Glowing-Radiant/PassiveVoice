package com.passivevoice.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollAnalyzerTest {

  private fun sample(dx: Int? = null, dy: Int? = null, scrollX: Int = 0, scrollY: Int = 0, from: Int = -1, to: Int = -1, count: Int = -1) =
    ScrollSample(dx, dy, scrollX, scrollY, from, to, count)

  @Test
  fun `a fling's many deltas collapse into one direction`() {
    val summary = ScrollAnalyzer.summarize(List(20) { sample(dx = 0, dy = 40) }, previous = null)
    assertEquals(ScrollDirection.DOWN, summary.direction)
    assertEquals(20, summary.sampleCount)
  }

  @Test
  fun `the initial movement wins over a small bounce back`() {
    val summary = ScrollAnalyzer.summarize(listOf(sample(dx = 0, dy = -300), sample(dx = 0, dy = 20)), previous = null)
    assertEquals(ScrollDirection.UP, summary.direction)
  }

  @Test
  fun `an overscroll push and spring-back in one burst is a push against the end`() {
    val atEnd = ScrollPosition(index = 16, scrollX = 0, scrollY = 0, edge = ScrollEdge.END, settledAt = 1_000L)
    val burst = listOf(sample(dx = 0, dy = 60, from = 16, to = 30, count = 31), sample(dx = 0, dy = 40, from = 16, to = 30, count = 31), sample(dx = 0, dy = -150, from = 16, to = 30, count = 31))
    val summary = ScrollAnalyzer.summarize(burst, atEnd, now = 9_000L)
    assertEquals(ScrollDirection.DOWN, summary.direction)
    assertEquals(ScrollEdge.END, summary.edge)
    assertFalse(summary.movedItems)
  }

  @Test
  fun `horizontal carousels are reported as left and right, not up and down`() {
    assertEquals(ScrollDirection.RIGHT, ScrollAnalyzer.summarize(listOf(sample(dx = 120, dy = 0)), null).direction)
    assertEquals(ScrollDirection.LEFT, ScrollAnalyzer.summarize(listOf(sample(dx = -120, dy = 0)), null).direction)
  }

  @Test
  fun `without deltas, direction comes from where the container settled last time`() {
    val previous = ScrollPosition(index = null, scrollX = 0, scrollY = 500)
    assertEquals(ScrollDirection.UP, ScrollAnalyzer.summarize(listOf(sample(scrollY = 200)), previous).direction)
    assertEquals(ScrollDirection.DOWN, ScrollAnalyzer.summarize(listOf(sample(scrollY = 900)), previous).direction)
  }

  @Test
  fun `a single-item pager moving to the next item is a page change`() {
    val previous = ScrollPosition(index = 3, scrollX = 0, scrollY = 0)
    // Mid-swipe two items are visible; once settled only the new one is.
    val summary = ScrollAnalyzer.summarize(listOf(sample(dx = 0, dy = 900, from = 3, to = 4), sample(dx = 0, dy = 900, from = 4, to = 4)), previous)
    assertTrue(summary.isPageChange)
    assertEquals(ScrollDirection.FORWARD, summary.direction)
  }

  @Test
  fun `a multi-item list is never a page change`() {
    val previous = ScrollPosition(index = 0, scrollX = 0, scrollY = 0)
    val summary = ScrollAnalyzer.summarize(listOf(sample(dx = 0, dy = 400, from = 3, to = 9, count = 40)), previous)
    assertFalse(summary.isPageChange)
    assertEquals(ScrollDirection.DOWN, summary.direction)
  }

  // Modeled on a ColorOS Settings list of 31 rows: fling to the bottom, then the overscroll spring-back.

  @Test
  fun `a fling that reaches the bottom reports the end of the list`() {
    val summary = ScrollAnalyzer.summarize(listOf(sample(dx = 0, dy = 900, from = 16, to = 30, count = 31)), ScrollPosition(0, 0, 0))
    assertEquals(ScrollDirection.DOWN, summary.direction)
    assertEquals(ScrollEdge.END, summary.edge)
    assertTrue(summary.movedItems)
    assertFalse(summary.isBounce)
  }

  @Test
  fun `the spring-back right after reaching the end is a bounce, not a scroll up`() {
    val atEnd = ScrollPosition(index = 16, scrollX = 0, scrollY = 0, edge = ScrollEdge.END, settledAt = 1_000L)
    val summary = ScrollAnalyzer.summarize(listOf(sample(dx = 0, dy = -120, from = 16, to = 30, count = 31)), atEnd, now = 1_600L)
    assertTrue(summary.isBounce)
  }

  @Test
  fun `pushing against the end again reports the edge without claiming movement`() {
    val atEnd = ScrollPosition(index = 16, scrollX = 0, scrollY = 0, edge = ScrollEdge.END, settledAt = 1_000L)
    val summary = ScrollAnalyzer.summarize(listOf(sample(dx = 0, dy = 80, from = 16, to = 30, count = 31)), atEnd, now = 9_000L)
    assertEquals(ScrollEdge.END, summary.edge)
    assertFalse(summary.movedItems)
    assertFalse(summary.isBounce)
  }

  @Test
  fun `scrolling up from the end long after reaching it is a real scroll`() {
    val atEnd = ScrollPosition(index = 16, scrollX = 0, scrollY = 0, edge = ScrollEdge.END, settledAt = 1_000L)
    val summary = ScrollAnalyzer.summarize(listOf(sample(dx = 0, dy = -600, from = 10, to = 24, count = 31)), atEnd, now = 9_000L)
    assertEquals(ScrollDirection.UP, summary.direction)
    assertFalse(summary.isBounce)
  }

  @Test
  fun `item indices decide direction even when pixel deltas disagree`() {
    val summary = ScrollAnalyzer.summarize(listOf(sample(dx = 0, dy = -30, from = 8, to = 14, count = 40)), ScrollPosition(2, 0, 0))
    assertEquals(ScrollDirection.DOWN, summary.direction)
  }

  @Test
  fun `no usable information is reported as unknown`() {
    assertEquals(ScrollDirection.UNKNOWN, ScrollAnalyzer.summarize(listOf(sample()), previous = null).direction)
  }
}
