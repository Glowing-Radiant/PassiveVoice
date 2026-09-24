package com.passivevoice.core

import android.view.accessibility.AccessibilityEvent

/**
 * Converts a raw [AccessibilityEvent] (+ inspected source node) into a normalized [UserAction].
 * This is where "different apps produce wildly different events for the same action" gets
 * collapsed into one internal model, per the PRD's event-normalization step.
 */
object EventNormalizer {

  fun normalize(event: AccessibilityEvent, node: NodeSnapshot?, now: Long): UserAction? {
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
        else -> return null
      }

    val eventText = event.text.firstOrNull { !it.isNullOrBlank() }?.toString()
    val label =
      if (type == ActionType.WINDOW_OPENED || type == ActionType.MENU_OPENED) {
        // The window's title travels in the event text; the root node's own description can be a
        // splash screen's "Application icon".
        eventText ?: node?.paneTitle ?: node?.label
      } else {
        // Clickable rows usually have no text of their own; the click event carries their children's
        // text instead, and the first entry is the row's title.
        node?.label ?: eventText ?: event.contentDescription?.toString()
      }

    return UserAction(
      type = type,
      packageName = event.packageName?.toString(),
      appName = null,
      label = label,
      role = node?.role,
      value = if (type == ActionType.TOGGLE) node?.isChecked?.toString() else null,
      node = node,
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
}
