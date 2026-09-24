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
  val scrollDelta: Int = 0,
  val timestamp: Long,
  val confidence: Float = 1.0f,
)
