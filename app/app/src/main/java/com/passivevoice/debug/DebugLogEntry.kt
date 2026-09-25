package com.passivevoice.debug

data class DebugLogEntry(
  val timestamp: Long,
  val packageName: String?,
  val eventType: String,
  val className: String?,
  val viewId: String? = null,
  val text: String?,
  val interpretation: String?,
  val speech: String?,
  val suppressedReason: String? = null,
)
