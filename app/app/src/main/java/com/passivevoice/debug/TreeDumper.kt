package com.passivevoice.debug

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Developer inspector (PRD §28): logs the active window's accessibility tree, view IDs included,
 * which is what writing an app adapter needs. Debug builds only, triggered over adb:
 *
 *   adb shell am broadcast -a com.passivevoice.DUMP_TREE -p com.passivevoice
 */
object TreeDumper {
  const val ACTION = "com.passivevoice.DUMP_TREE"
  private const val TAG = "PassiveVoice/Tree"
  private const val MAX_NODES = 600
  private const val MAX_TEXT = 240

  @Suppress("DEPRECATION")
  fun dump(service: AccessibilityService) {
    val root = service.rootInActiveWindow
    if (root == null) {
      Log.d(TAG, "No active window")
      return
    }
    Log.d(TAG, "=== ${root.packageName} ===")
    var count = 0
    val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
    stack.addLast(root to 0)
    while (stack.isNotEmpty()) {
      val (node, depth) = stack.removeLast()
      if (count++ < MAX_NODES) {
        Log.d(TAG, "  ".repeat(depth) + describe(node))
        for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { stack.addLast(it to depth + 1) }
      }
      node.recycle()
    }
    Log.d(TAG, "=== end ($count nodes) ===")
  }

  @Suppress("DEPRECATION")
  private fun describe(node: AccessibilityNodeInfo): String {
    val bounds = Rect().also(node::getBoundsInScreen)
    val flags =
      listOfNotNull(
          "clk".takeIf { node.isClickable },
          "chk=${node.isChecked}".takeIf { node.isCheckable },
          "sel".takeIf { node.isSelected },
          "scr".takeIf { node.isScrollable },
          "pwd".takeIf { node.isPassword },
        )
        .joinToString(",")
    return buildString {
      append(node.className?.toString()?.substringAfterLast('.') ?: "?")
      node.viewIdResourceName?.let { append(" #").append(it) }
      if (!node.isPassword) node.text?.let { append(" t=\"").append(it.toString().take(MAX_TEXT)).append('"') }
      node.contentDescription?.let { append(" d=\"").append(it.toString().take(MAX_TEXT)).append('"') }
      node.stateDescription?.let { append(" s=\"").append(it).append('"') }
      node.collectionInfo?.let { append(" coll=${it.rowCount}x${it.columnCount}") }
      if (flags.isNotEmpty()) append(" [").append(flags).append(']')
      append(" ").append(bounds.toShortString())
    }
  }
}
