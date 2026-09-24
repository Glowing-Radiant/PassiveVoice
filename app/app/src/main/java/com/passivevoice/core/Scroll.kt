package com.passivevoice.core

import kotlin.math.abs

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT, FORWARD, BACKWARD, UNKNOWN }

enum class ScrollEdge { START, END }

/** One TYPE_VIEW_SCROLLED event. Index fields are -1 when the app didn't report them. */
data class ScrollSample(
  val deltaX: Int?, // null when the app didn't report deltas (or pre-API 28)
  val deltaY: Int?,
  val scrollX: Int,
  val scrollY: Int,
  val fromIndex: Int,
  val toIndex: Int,
  val itemCount: Int,
)

/** Where a scroll container came to rest after the previous gesture. */
data class ScrollPosition(
  val index: Int?,
  val scrollX: Int,
  val scrollY: Int,
  val edge: ScrollEdge? = null,
  val settledAt: Long = 0L,
)

data class ScrollSummary(
  val direction: ScrollDirection,
  /** A one-item-at-a-time container (reels, shorts, photo viewers) moved to a different item. */
  val isPageChange: Boolean,
  val fromIndex: Int,
  val toIndex: Int,
  val itemCount: Int,
  val sampleCount: Int,
  /** The gesture ended at the start or end of the list, moving towards it. */
  val edge: ScrollEdge? = null,
  /** Different items are visible than before the gesture (false for a nudge or an overscroll push). */
  val movedItems: Boolean = true,
  /** Spring-back from an overscroll at the edge the previous gesture reached; not a user action. */
  val isBounce: Boolean = false,
)

/**
 * A single fling emits dozens of scroll events; this reduces the whole burst to one net movement,
 * comparing against where the container settled last time when the events themselves carry no deltas.
 */
object ScrollAnalyzer {

  /** Lists that overscroll-and-bounce (e.g. ColorOS) emit a separate spring-back burst within this window. */
  const val BOUNCE_WINDOW_MS = 1500L

  fun summarize(samples: List<ScrollSample>, previous: ScrollPosition?, now: Long = 0L): ScrollSummary {
    require(samples.isNotEmpty())
    val first = samples.first()
    val last = samples.last()

    val reported = samples.filter { it.deltaX != null && it.deltaY != null }
    val netX = reported.sumOf { it.deltaX!! }
    val netY = reported.sumOf { it.deltaY!! }
    val travelX = reported.sumOf { abs(it.deltaX!!) }
    val travelY = reported.sumOf { abs(it.deltaY!!) }
    val hasPixelMovement = travelX + travelY > 0

    val startIndex = previous?.index ?: first.fromIndex.takeIf { it >= 0 }
    val endIndex = last.fromIndex.takeIf { it >= 0 }
    val indexDelta = if (startIndex != null && endIndex != null) endIndex - startIndex else 0

    val showsSingleItem = last.fromIndex >= 0 && last.fromIndex == last.toIndex
    val isPageChange = showsSingleItem && indexDelta != 0

    val direction =
      when {
        isPageChange -> if (indexDelta > 0) ScrollDirection.FORWARD else ScrollDirection.BACKWARD
        // Item indices are the most trustworthy signal; pixel deltas only decide the axis.
        indexDelta != 0 ->
          when {
            !hasPixelMovement -> if (indexDelta > 0) ScrollDirection.FORWARD else ScrollDirection.BACKWARD
            travelY >= travelX -> if (indexDelta > 0) ScrollDirection.DOWN else ScrollDirection.UP
            else -> if (indexDelta > 0) ScrollDirection.RIGHT else ScrollDirection.LEFT
          }
        // Same items still visible: an overscroll push and its spring-back can land in one burst with a
        // net movement *away* from the edge, so the first movement is the better sign of intent.
        else -> reported.firstOrNull { it.deltaX != 0 || it.deltaY != 0 }?.let { directionOf(it.deltaX!!, it.deltaY!!) }
          ?: pixelDirection(netX, netY, first, last, previous)
      }

    val atStart = last.fromIndex == 0
    val atEnd = last.itemCount > 0 && last.toIndex == last.itemCount - 1
    val towardsStart = direction == ScrollDirection.UP || direction == ScrollDirection.LEFT || direction == ScrollDirection.BACKWARD
    val towardsEnd = direction == ScrollDirection.DOWN || direction == ScrollDirection.RIGHT || direction == ScrollDirection.FORWARD
    val edge =
      when {
        atEnd && towardsEnd -> ScrollEdge.END
        atStart && towardsStart -> ScrollEdge.START
        else -> null
      }

    val recentlyAtEdge = previous?.edge != null && now - previous.settledAt <= BOUNCE_WINDOW_MS
    val isBounce =
      recentlyAtEdge && indexDelta == 0 && !isPageChange &&
        ((previous.edge == ScrollEdge.END && towardsStart) || (previous.edge == ScrollEdge.START && towardsEnd))

    return ScrollSummary(
      direction = direction,
      isPageChange = isPageChange,
      fromIndex = last.fromIndex,
      toIndex = last.toIndex,
      itemCount = last.itemCount,
      sampleCount = samples.size,
      edge = edge,
      movedItems = indexDelta != 0 || endIndex == null,
      isBounce = isBounce,
    )
  }

  private fun directionOf(dx: Int, dy: Int): ScrollDirection =
    if (abs(dy) >= abs(dx)) {
      if (dy > 0) ScrollDirection.DOWN else ScrollDirection.UP
    } else {
      if (dx > 0) ScrollDirection.RIGHT else ScrollDirection.LEFT
    }

  private fun pixelDirection(netX: Int, netY: Int, first: ScrollSample, last: ScrollSample, previous: ScrollPosition?): ScrollDirection {
    val positionDeltaY = last.scrollY - (previous?.scrollY ?: first.scrollY)
    val positionDeltaX = last.scrollX - (previous?.scrollX ?: first.scrollX)
    return when {
      netY != 0 && abs(netY) >= abs(netX) -> if (netY > 0) ScrollDirection.DOWN else ScrollDirection.UP
      netX != 0 -> if (netX > 0) ScrollDirection.RIGHT else ScrollDirection.LEFT
      positionDeltaY != 0 && abs(positionDeltaY) >= abs(positionDeltaX) -> if (positionDeltaY > 0) ScrollDirection.DOWN else ScrollDirection.UP
      positionDeltaX != 0 -> if (positionDeltaX > 0) ScrollDirection.RIGHT else ScrollDirection.LEFT
      else -> ScrollDirection.UNKNOWN
    }
  }

  fun settledPosition(samples: List<ScrollSample>, summary: ScrollSummary, previous: ScrollPosition?, now: Long): ScrollPosition {
    val last = samples.last()
    return ScrollPosition(
      index = last.fromIndex.takeIf { it >= 0 },
      scrollX = last.scrollX,
      scrollY = last.scrollY,
      // A spring-back can arrive in several bursts; keep treating the list as resting at that edge.
      edge = if (summary.isBounce) previous?.edge else summary.edge,
      settledAt = now,
    )
  }
}
