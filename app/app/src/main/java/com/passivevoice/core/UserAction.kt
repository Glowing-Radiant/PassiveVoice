package com.passivevoice.core

/** Normalized representation of a user-caused UI change, independent of the raw Android event that produced it. */
data class UserAction(
  val type: ActionType,
  val packageName: String?,
  val appName: String?,
  val label: String?,
  val role: String?,
  val value: String?,
  val node: NodeSnapshot?,
  /** The node as it was when the event fired, when [node] has since been re-read to observe a state change. */
  val before: NodeSnapshot? = null,
  val scroll: ScrollSummary? = null,
  /** For [ActionType.TEXT_INPUT]: what was typed or deleted. Spoken, never logged. */
  val textChange: TextChange? = null,
  val timestamp: Long,
  val confidence: Float = 1.0f,
)
