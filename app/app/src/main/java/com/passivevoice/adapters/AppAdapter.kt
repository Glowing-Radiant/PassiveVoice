package com.passivevoice.adapters

import com.passivevoice.core.ContextManager
import com.passivevoice.core.NodeSnapshot
import com.passivevoice.core.UserAction
import com.passivevoice.interpreter.SpeechOutput
import com.passivevoice.settings.Verbosity

/**
 * App-specific narration (PRD §20): understands one app's screens well enough to say
 * "Next reel, by ar.arruuu" where the generic engine can only say "Next item".
 */
interface AppAdapter {
  val packageName: String

  /** Reinterprets an action in this app. Null defers to the generic interpreter. */
  fun interpret(action: UserAction, screen: Screen, context: ContextManager, verbosity: Verbosity): AdapterResult? = null

  /**
   * Called (throttled) when this app's content changes, for screens whose swipes emit no scroll
   * events at all. Returns speech describing what changed, or null.
   */
  fun onContentChanged(screen: Screen, context: ContextManager, verbosity: Verbosity): SpeechOutput? = null
}

sealed interface AdapterResult {
  data class Speak(val output: SpeechOutput) : AdapterResult

  /** The adapter knows this action isn't worth narrating (e.g. it will be described another way). */
  data object Silence : AdapterResult
}

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
  val height: Int
    get() = bottom - top

  val width: Int
    get() = right - left

  /** Area of this node that's actually inside [viewport]. */
  fun visibleArea(viewport: Bounds): Int {
    val w = minOf(right, viewport.right) - maxOf(left, viewport.left)
    val h = minOf(bottom, viewport.bottom) - maxOf(top, viewport.top)
    return if (w > 0 && h > 0) w * h else 0
  }
}

/** A node read from the live screen. [handle] is opaque to adapters; it lets them search beneath this node. */
class ScreenNode(val snapshot: NodeSnapshot, val bounds: Bounds, internal val handle: Any? = null)

/** Read-only view of the current window, valid only during a single adapter call. */
interface Screen {
  /** The visible window area. */
  val viewport: Bounds

  /** Nodes with this full view ID, e.g. "com.instagram.android:id/list". */
  fun byId(viewId: String): List<ScreenNode>

  /** Up to [limit] nodes matching [predicate] beneath [within] (or anywhere in the window), in tree order. */
  fun find(within: ScreenNode? = null, limit: Int = 20, predicate: (ScreenNode) -> Boolean): List<ScreenNode>
}

/** The node that fills most of the screen, e.g. the reel or post the user is looking at. */
fun Screen.mostVisible(nodes: List<ScreenNode>): ScreenNode? =
  nodes
    .filter { it.bounds.visibleArea(viewport) > 0 }
    .maxWithOrNull(compareBy<ScreenNode> { it.bounds.visibleArea(viewport) }.thenBy { -it.bounds.top }) // ties: topmost
