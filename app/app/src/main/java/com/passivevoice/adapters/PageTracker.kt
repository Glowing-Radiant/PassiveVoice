package com.passivevoice.adapters

enum class PageMove { FIRST, NEXT, PREVIOUS }

/**
 * Follows which item a one-at-a-time feed is showing when the app reports no scroll events —
 * only what's on screen. Going back to the item before the current one reads as "previous".
 */
class PageTracker(private val capacity: Int = 50) {
  private val history = ArrayDeque<String>()
  private var active = false

  /** Returns how the feed moved to [key], or null if it's still showing the same item. */
  fun observe(key: String): PageMove? {
    if (active && history.lastOrNull() == key) return null
    val move =
      when {
        !active -> PageMove.FIRST
        history.size >= 2 && history[history.size - 2] == key -> PageMove.PREVIOUS
        else -> PageMove.NEXT
      }
    active = true
    if (move == PageMove.PREVIOUS) {
      history.removeLast()
    } else if (history.lastOrNull() != key) {
      history.addLast(key)
      if (history.size > capacity) history.removeFirst()
    }
    return move
  }

  /** The feed is no longer on screen; the next item seen starts a fresh visit. */
  fun leave() {
    active = false
  }
}
