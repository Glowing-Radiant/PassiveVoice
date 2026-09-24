package com.passivevoice.accessibility

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.passivevoice.core.NodeSnapshot

/** Reads the useful attributes off an [AccessibilityNodeInfo] / [AccessibilityEvent] into a plain [NodeSnapshot]. */
object NodeInspector {

  /** Builds a snapshot from the event's source node, recycling the node afterwards (pre-API 33). */
  fun snapshotSource(event: AccessibilityEvent): NodeSnapshot? {
    val node = event.source ?: return snapshotFromEvent(event)
    return try {
      snapshot(node)
    } finally {
      @Suppress("DEPRECATION") node.recycle()
    }
  }

  @Suppress("DEPRECATION") // node.isChecked: the API 33+ tri-state getChecked() isn't available down to minSdk 26.
  fun snapshot(node: AccessibilityNodeInfo): NodeSnapshot =
    NodeSnapshot(
      text = node.text?.toString(),
      contentDescription = node.contentDescription?.toString(),
      stateDescription = node.stateDescription?.toString(),
      paneTitle = node.paneTitle?.toString(),
      hintText = node.hintText?.toString(),
      className = node.className?.toString(),
      viewIdResourceName = node.viewIdResourceName,
      isClickable = node.isClickable,
      isCheckable = node.isCheckable,
      isChecked = node.isChecked,
      isSelected = node.isSelected,
      isEnabled = node.isEnabled,
      isPassword = node.isPassword,
      isScrollable = node.isScrollable,
      isEditable = node.isEditable,
    )

  /** Fallback for events whose source node is already gone: use the event's own text/description fields. */
  private fun snapshotFromEvent(event: AccessibilityEvent): NodeSnapshot? {
    val text = event.text.joinToString(" ").takeIf { it.isNotBlank() }
    val description = event.contentDescription?.toString()
    if (text == null && description == null) return null
    return NodeSnapshot(
      text = text,
      contentDescription = description,
      className = event.className?.toString(),
      isChecked = event.isChecked,
      isPassword = event.isPassword,
      isScrollable = event.isScrollable,
      isEnabled = event.isEnabled,
    )
  }
}
