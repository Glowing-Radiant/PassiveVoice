package com.passivevoice.core

/** Short-term memory the interpreter and dedup logic reason against. Mutated in place by [ContextManager]. */
data class ContextState(
  var currentPackage: String? = null,
  var currentWindowTitle: String? = null,
  var previousFocusedNodeKey: String? = null,
  var lastClickLabel: String? = null,
  var lastClickAt: Long = 0L,
  var previousText: String? = null,
  val previousToggleStates: MutableMap<String, Boolean> = mutableMapOf(),
  val previousScrollPositions: MutableMap<String, ScrollPosition> = mutableMapOf(),
  var lastSpokenText: String? = null,
  var lastSpeechTimestamp: Long = 0L,
)
