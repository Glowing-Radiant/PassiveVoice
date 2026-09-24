package com.passivevoice.core

/**
 * Holds a single [ContextState] and answers the deduplication question described in the PRD:
 * without short-term memory, one physical tap can produce "Like button." / "Like." / "Liked."
 * within milliseconds. This collapses repeats of the same spoken text within [dedupWindowMs].
 */
class ContextManager(private val dedupWindowMs: Long = 1200L) {
  val state = ContextState()

  fun previousToggleState(key: String): Boolean? = state.previousToggleStates[key]

  fun previousScrollPosition(key: String): ScrollPosition? = state.previousScrollPositions[key]

  fun recordToggleState(key: String, checked: Boolean) {
    state.previousToggleStates[key] = checked
  }

  fun recordScrollPosition(key: String, position: ScrollPosition) {
    state.previousScrollPositions[key] = position
  }

  fun recordClick(label: String?, now: Long) {
    state.lastClickLabel = label
    state.lastClickAt = now
  }

  /** Whether a screen titled [title] is simply the one the user just tapped their way into. */
  fun wasJustTapped(title: String, now: Long): Boolean =
    state.lastClickLabel.equals(title, ignoreCase = true) && now - state.lastClickAt <= TAP_TO_WINDOW_MS

  private companion object {
    const val TAP_TO_WINDOW_MS = 3000L
  }

  fun recordFocus(key: String) {
    state.previousFocusedNodeKey = key
  }

  fun updateWindow(packageName: String?, windowTitle: String?) {
    state.currentPackage = packageName
    state.currentWindowTitle = windowTitle
  }

  /** Returns true if [text] should be suppressed as a near-duplicate of the last thing spoken. */
  fun isDuplicate(text: String, now: Long): Boolean {
    val last = state.lastSpokenText ?: return false
    return last == text && (now - state.lastSpeechTimestamp) <= dedupWindowMs
  }

  fun recordSpoken(text: String, now: Long) {
    state.lastSpokenText = text
    state.lastSpeechTimestamp = now
  }
}
