package com.passivevoice.adapters

import com.passivevoice.core.NodeSnapshot

/** A tree of nodes standing in for a live window. */
class FakeNode(val snapshot: NodeSnapshot, val bounds: Bounds, val children: List<FakeNode> = emptyList())

class FakeScreen(private val root: FakeNode, override val viewport: Bounds = Bounds(0, 0, 1080, 2400)) : Screen {
  private fun all(from: FakeNode): Sequence<FakeNode> = sequence {
    for (child in from.children) {
      yield(child)
      yieldAll(all(child))
    }
  }

  private fun wrap(node: FakeNode) = ScreenNode(node.snapshot, node.bounds, node)

  override fun byId(viewId: String): List<ScreenNode> = all(root).filter { it.snapshot.viewIdResourceName == viewId }.map(::wrap).toList()

  override fun find(within: ScreenNode?, limit: Int, predicate: (ScreenNode) -> Boolean): List<ScreenNode> =
    all((within?.handle as? FakeNode) ?: root).map(::wrap).filter(predicate).take(limit).toList()
}
