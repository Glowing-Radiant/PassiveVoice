package com.passivevoice.interpreter

import com.passivevoice.core.ActionType
import com.passivevoice.core.ContextManager
import com.passivevoice.core.NodeSnapshot
import com.passivevoice.core.ScrollDirection
import com.passivevoice.core.ScrollEdge
import com.passivevoice.core.ScrollSummary
import com.passivevoice.core.UserAction
import com.passivevoice.settings.Verbosity
import com.passivevoice.speech.SpeechPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenericInterpreterTest {

  private fun interpret(action: UserAction, verbosity: Verbosity = Verbosity.STANDARD, context: ContextManager = ContextManager()) =
    GenericInterpreter.interpret(action, context, verbosity)

  private fun action(type: ActionType, label: String? = null, role: String? = null, value: String? = null) =
    UserAction(type, "com.example.app", null, label = label, role = role, value = value, node = null, timestamp = 0L)

  private fun toggleAction(label: String, checked: Boolean) = action(ActionType.TOGGLE, label = label, value = checked.toString())

  /** A tap whose node read [beforeLabel] when the event fired and [afterLabel] once re-read. */
  private fun click(beforeLabel: String?, afterLabel: String?, beforeSelected: Boolean = false, afterSelected: Boolean = false, innerChecked: Boolean? = null) =
    UserAction(
      type = ActionType.CLICK,
      packageName = "com.example.app",
      appName = null,
      label = afterLabel ?: beforeLabel,
      role = "Button",
      value = null,
      node = NodeSnapshot(contentDescription = afterLabel, isSelected = afterSelected, innerChecked = innerChecked),
      before = NodeSnapshot(contentDescription = beforeLabel, isSelected = beforeSelected),
      timestamp = 0L,
    )

  private fun scroll(direction: ScrollDirection, isPage: Boolean = false, from: Int = -1, to: Int = -1, count: Int = -1) =
    action(ActionType.SCROLL).copy(scroll = ScrollSummary(direction, isPage, from, to, count, sampleCount = 1))

  // Toggles (checkable nodes)

  @Test
  fun `generic switch announces on and off state, matching PRD Wi-Fi example`() {
    assertEquals("Wi-Fi on", interpret(toggleAction("Wi-Fi", checked = true))?.text)
    assertEquals("Wi-Fi off", interpret(toggleAction("Wi-Fi", checked = false))?.text)
  }

  @Test
  fun `like toggle uses past-tense verb phrasing, matching PRD Instagram example`() {
    assertEquals("Liked", interpret(toggleAction("Like", checked = true))?.text)
    assertEquals("Unliked", interpret(toggleAction("Like", checked = false))?.text)
  }

  @Test
  fun `follow toggle announces Following, matching PRD Instagram example`() {
    assertEquals("Following", interpret(toggleAction("Follow", checked = true))?.text)
    assertEquals("Not following", interpret(toggleAction("Follow", checked = false))?.text)
  }

  @Test
  fun `toggle verbs don't match unrelated words`() {
    assertEquals("Start on", interpret(toggleAction("Start", checked = true))?.text)
  }

  @Test
  fun `unlabeled switch still reports its state`() {
    assertEquals("On", interpret(action(ActionType.TOGGLE, value = "true"))?.text)
  }

  @Test
  fun `detailed verbosity prefixes role`() {
    assertEquals("Switch. Wi-Fi on.", interpret(toggleAction("Wi-Fi", checked = true), Verbosity.DETAILED)?.text)
  }

  // Taps on plain buttons: state inferred from what the node looks like after the tap

  @Test
  fun `like button whose label flips to Unlike reads as Liked`() {
    assertEquals("Liked", interpret(click(beforeLabel = "Like", afterLabel = "Unlike"))?.text)
  }

  @Test
  fun `label already flipped when the event fired still reads as Liked`() {
    assertEquals("Liked", interpret(click(beforeLabel = "Unlike", afterLabel = "Unlike"))?.text)
  }

  @Test
  fun `unlike button whose label flips back reads as Unliked`() {
    assertEquals("Unliked", interpret(click(beforeLabel = "Unlike", afterLabel = "Like"))?.text)
  }

  @Test
  fun `follow button that becomes Following reads as Following`() {
    assertEquals("Following", interpret(click(beforeLabel = "Follow", afterLabel = "Following"))?.text)
    assertEquals("Not following", interpret(click(beforeLabel = "Following", afterLabel = "Follow"))?.text)
  }

  @Test
  fun `like button with no visible change is read as-is rather than guessed`() {
    assertEquals("Like", interpret(click(beforeLabel = "Like", afterLabel = "Like"))?.text)
  }

  @Test
  fun `like button that marks itself selected reads as Liked`() {
    assertEquals("Liked", interpret(click(beforeLabel = "Like", afterLabel = "Like", afterSelected = true))?.text)
  }

  @Test
  fun `longer labels containing a verb are not treated as toggles`() {
    assertEquals("Liked videos", interpret(click(beforeLabel = "Liked videos", afterLabel = "Liked videos"))?.text)
  }

  @Test
  fun `non-verb label change speaks the new label`() {
    assertEquals("Requested", interpret(click(beforeLabel = "Request", afterLabel = "Requested"))?.text)
  }

  @Test
  fun `tapping a settings row flips its inner switch`() {
    assertEquals("Wi-Fi on", interpret(click(beforeLabel = "Wi-Fi", afterLabel = "Wi-Fi", innerChecked = true))?.text)
  }

  @Test
  fun `click with a label speaks the label`() {
    assertEquals("Share", interpret(action(ActionType.CLICK, label = "Share", role = "Button"))?.text)
  }

  @Test
  fun `click without any label falls back to Selected, matching PRD Scenario C`() {
    assertEquals("Selected", interpret(action(ActionType.CLICK))?.text)
  }

  // Selection, windows, scrolling

  @Test
  fun `tab selection announces tab name, matching PRD Home tab example`() {
    assertEquals("Home tab", interpret(action(ActionType.TAB_CHANGED, label = "Home", role = "Tab"))?.text)
  }

  @Test
  fun `scroll direction is spoken for all four directions`() {
    assertEquals("Scrolled down", interpret(scroll(ScrollDirection.DOWN))?.text)
    assertEquals("Scrolled up", interpret(scroll(ScrollDirection.UP))?.text)
    assertEquals("Scrolled right", interpret(scroll(ScrollDirection.RIGHT))?.text)
    assertEquals("Scrolled left", interpret(scroll(ScrollDirection.LEFT))?.text)
  }

  @Test
  fun `one-item-at-a-time pager announces next and previous item`() {
    assertEquals("Next item", interpret(scroll(ScrollDirection.FORWARD, isPage = true, from = 4, to = 4))?.text)
    assertEquals("Previous item", interpret(scroll(ScrollDirection.BACKWARD, isPage = true, from = 3, to = 3))?.text)
  }

  @Test
  fun `detailed scroll includes position`() {
    assertEquals("Scrolled down. Items 5 to 9 of 40", interpret(scroll(ScrollDirection.DOWN, from = 4, to = 8, count = 40), Verbosity.DETAILED)?.text)
    assertEquals("Next item. Item 5 of 12", interpret(scroll(ScrollDirection.FORWARD, isPage = true, from = 4, to = 4, count = 12), Verbosity.DETAILED)?.text)
  }

  @Test
  fun `reaching the end of a list says so`() {
    val reachedEnd = scroll(ScrollDirection.DOWN, from = 16, to = 30, count = 31).let { it.copy(scroll = it.scroll!!.copy(edge = ScrollEdge.END)) }
    assertEquals("Scrolled down. End of list", interpret(reachedEnd)?.text)

    val pushedAgain = reachedEnd.copy(scroll = reachedEnd.scroll!!.copy(movedItems = false))
    assertEquals("End of list", interpret(pushedAgain)?.text)
  }

  @Test
  fun `spring-back bounces are not narrated`() {
    val bounce = scroll(ScrollDirection.UP).let { it.copy(scroll = it.scroll!!.copy(isBounce = true)) }
    assertNull(interpret(bounce))
  }

  @Test
  fun `a scroll with no direction information is not narrated`() {
    assertNull(interpret(scroll(ScrollDirection.UNKNOWN)))
  }

  @Test
  fun `the screen a tap just opened is not announced again`() {
    val context = ContextManager()
    context.recordClick("Battery", now = 1_000L)
    assertNull(interpret(action(ActionType.WINDOW_OPENED, label = "Battery").copy(timestamp = 1_500L), context = context))
    assertEquals("Battery", interpret(action(ActionType.WINDOW_OPENED, label = "Battery").copy(timestamp = 9_000L), context = context)?.text)
  }

  @Test
  fun `window opened is suppressed when it matches the already-announced window`() {
    val context = ContextManager()
    val opened = action(ActionType.WINDOW_OPENED, label = "Settings")
    assertEquals("Settings", interpret(opened, context = context)?.text)

    context.state.currentWindowTitle = "Settings"
    assertNull(interpret(opened, context = context))
  }

  // Priorities (PRD §11)

  @Test
  fun `direct feedback outranks screens, which outrank scrolling`() {
    assertEquals(SpeechPriority.HIGH, interpret(action(ActionType.CLICK, label = "Share"))?.priority)
    assertEquals(SpeechPriority.HIGH, interpret(toggleAction("Wi-Fi", checked = true))?.priority)
    assertEquals(SpeechPriority.NORMAL, interpret(action(ActionType.WINDOW_OPENED, label = "Settings"))?.priority)
    assertEquals(SpeechPriority.NORMAL, interpret(scroll(ScrollDirection.FORWARD, isPage = true, from = 1, to = 1))?.priority)
    assertEquals(SpeechPriority.LOW, interpret(scroll(ScrollDirection.DOWN))?.priority)
  }
}
