package com.passivevoice.core

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.passivevoice.accessibility.NodeInspector

/**
 * Converts a raw [AccessibilityEvent] (+ inspected source node) into a normalized [UserAction].
 * This is where "different apps produce wildly different events for the same action" gets
 * collapsed into one internal model, per the PRD's event-normalization step.
 */
object EventNormalizer {

  fun normalize(event: AccessibilityEvent, context: ContextManager): UserAction? {
    val node = NodeInspector.snapshotSource(event)
    val packageName = event.packageName?.toString()
    val now = System.currentTimeMillis()

    val type =
      when (event.eventType) {
        AccessibilityEvent.TYPE_VIEW_CLICKED -> if (node?.isCheckable == true) ActionType.TOGGLE else ActionType.CLICK
        AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> ActionType.LONG_CLICK
        AccessibilityEvent.TYPE_VIEW_SELECTED -> if (node?.role == "Tab") ActionType.TAB_CHANGED else ActionType.SELECTION
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> ActionType.FOCUS_CHANGED
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> ActionType.SCROLL
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> ActionType.TEXT_INPUT
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> classifyWindowStateChange(node)
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> ActionType.CONTENT_CHANGED
        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> ActionType.CONTENT_CHANGED
        else -> ActionType.UNKNOWN
      }

    if (type == ActionType.UNKNOWN) return null

    val value =
      when (type) {
        ActionType.TOGGLE -> node?.isChecked?.toString()
        ActionType.TEXT_INPUT -> event.text.joinToString(" ")
        ActionType.SCROLL -> scrollDelta(event).toString()
        else -> null
      }

    return UserAction(
      type = type,
      packageName = packageName,
      appName = null,
      label = node?.label ?: event.contentDescription?.toString(),
      role = node?.role,
      value = value,
      node = node,
      scrollDelta = if (type == ActionType.SCROLL) scrollDelta(event) else 0,
      timestamp = now,
      confidence = if (node != null) 1.0f else 0.6f,
    )
  }

  private fun classifyWindowStateChange(node: NodeSnapshot?): ActionType =
    when {
      node?.className?.contains("Menu", ignoreCase = true) == true -> ActionType.MENU_OPENED
      node?.className?.contains("Popup", ignoreCase = true) == true -> ActionType.MENU_OPENED
      else -> ActionType.WINDOW_OPENED
    }

  private fun scrollDelta(event: AccessibilityEvent): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      if (event.scrollDeltaY != 0) return event.scrollDeltaY
      if (event.scrollDeltaX != 0) return event.scrollDeltaX
    }
    // Pre-API 28 fallback: infer direction from list position.
    return event.toIndex - event.fromIndex
  }
}
