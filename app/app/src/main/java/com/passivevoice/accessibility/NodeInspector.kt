package com.passivevoice.accessibility

import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.passivevoice.core.NodeSnapshot
import com.passivevoice.core.ScrollSample

/** Reads the useful attributes off an [AccessibilityNodeInfo] / [AccessibilityEvent] into a plain [NodeSnapshot]. */
object NodeInspector {

  @Suppress("DEPRECATION") // node.isChecked: the API 33+ tri-state getChecked() isn't available down to minSdk 26.
  fun snapshot(node: AccessibilityNodeInfo): NodeSnapshot {
    val isPassword = node.isPassword
    return NodeSnapshot(
      // Password contents never leave the node, even into memory.
      text = if (isPassword) null else node.text?.toString(),
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
      isPassword = isPassword,
      isScrollable = node.isScrollable,
      isEditable = node.isEditable,
    )
  }

  /** Finds the first checkable descendant (a row's trailing switch) within a small, bounded search. */
  @Suppress("DEPRECATION")
  fun innerCheckedState(node: AccessibilityNodeInfo): Boolean? {
    if (node.isCheckable) return null
    val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
    for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it to 1) }
    var visited = 0
    var result: Boolean? = null
    while (queue.isNotEmpty()) {
      val (child, depth) = queue.removeFirst()
      if (result == null && visited < MAX_INNER_SEARCH_NODES) {
        visited++
        if (child.isCheckable) {
          result = child.isChecked
        } else if (depth < MAX_INNER_SEARCH_DEPTH) {
          for (i in 0 until child.childCount) child.getChild(i)?.let { queue.add(it to depth + 1) }
        }
      }
      child.recycle()
    }
    return result
  }

  private const val MAX_INNER_SEARCH_DEPTH = 3
  private const val MAX_INNER_SEARCH_NODES = 24

  /** Fallback for events whose source node is already gone: use the event's own text/description fields. */
  fun snapshotFromEvent(event: AccessibilityEvent): NodeSnapshot? {
    val text = event.text.joinToString(" ").takeIf { it.isNotBlank() }
    val description = event.contentDescription?.toString()
    if (text == null && description == null) return null
    return NodeSnapshot(
      text = if (event.isPassword) null else text,
      contentDescription = description,
      className = event.className?.toString(),
      isChecked = event.isChecked,
      isPassword = event.isPassword,
      isScrollable = event.isScrollable,
      isEnabled = event.isEnabled,
    )
  }

  fun scrollSample(event: AccessibilityEvent): ScrollSample {
    // Both deltas stay at -1 (undefined) when the app doesn't report them.
    val hasDeltas = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !(event.scrollDeltaX == -1 && event.scrollDeltaY == -1)
    return ScrollSample(
      deltaX = if (hasDeltas) event.scrollDeltaX else null,
      deltaY = if (hasDeltas) event.scrollDeltaY else null,
      scrollX = event.scrollX,
      scrollY = event.scrollY,
      fromIndex = event.fromIndex,
      toIndex = event.toIndex,
      itemCount = event.itemCount,
    )
  }
}
