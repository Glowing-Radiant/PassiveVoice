package com.passivevoice.adapters

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.passivevoice.accessibility.NodeInspector
import java.io.Closeable

/** [Screen] backed by the service's live window. Every node it fetches is released on [close]. */
class LiveScreen(private val service: AccessibilityService) : Screen, Closeable {
  private val fetched = mutableListOf<AccessibilityNodeInfo>()

  private val root: AccessibilityNodeInfo? by lazy { service.rootInActiveWindow?.also(fetched::add) }

  override val viewport: Bounds by lazy { root?.let(::boundsOf) ?: Bounds(0, 0, 0, 0) }

  override fun byId(viewId: String): List<ScreenNode> =
    root?.findAccessibilityNodeInfosByViewId(viewId).orEmpty().map { node ->
      fetched += node
      toScreenNode(node)
    }

  override fun find(within: ScreenNode?, limit: Int, predicate: (ScreenNode) -> Boolean): List<ScreenNode> {
    val start = (within?.handle as? AccessibilityNodeInfo) ?: root ?: return emptyList()
    val results = mutableListOf<ScreenNode>()
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    for (i in start.childCount - 1 downTo 0) start.getChild(i)?.let { fetched += it; stack.addLast(it) }
    var visited = 0
    while (stack.isNotEmpty() && results.size < limit && visited < MAX_SEARCH_NODES) {
      val node = stack.removeLast()
      visited++
      val screenNode = toScreenNode(node)
      if (predicate(screenNode)) results += screenNode
      for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let { fetched += it; stack.addLast(it) }
    }
    return results
  }

  @Suppress("DEPRECATION")
  override fun close() {
    fetched.forEach { it.recycle() }
    fetched.clear()
  }

  private fun toScreenNode(node: AccessibilityNodeInfo) = ScreenNode(NodeInspector.snapshot(node), boundsOf(node), node)

  private fun boundsOf(node: AccessibilityNodeInfo): Bounds {
    val rect = Rect().also(node::getBoundsInScreen)
    return Bounds(rect.left, rect.top, rect.right, rect.bottom)
  }

  private companion object {
    const val MAX_SEARCH_NODES = 400
  }
}
