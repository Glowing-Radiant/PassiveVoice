package com.passivevoice.interpreter

import com.passivevoice.core.ActionType
import com.passivevoice.core.ContextManager
import com.passivevoice.core.UserAction
import com.passivevoice.settings.Verbosity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GenericInterpreterTest {

  private fun toggleAction(label: String, checked: Boolean) =
    UserAction(
      type = ActionType.TOGGLE,
      packageName = "com.example.app",
      appName = null,
      label = label,
      role = null,
      value = checked.toString(),
      node = null,
      timestamp = 0L,
    )

  @Test
  fun `generic switch announces on and off state, matching PRD Wi-Fi example`() {
    val context = ContextManager()
    assertEquals("Wi-Fi on", GenericInterpreter.interpret(toggleAction("Wi-Fi", checked = true), context, Verbosity.STANDARD)?.text)
    assertEquals("Wi-Fi off", GenericInterpreter.interpret(toggleAction("Wi-Fi", checked = false), context, Verbosity.STANDARD)?.text)
  }

  @Test
  fun `like button uses past-tense verb phrasing, matching PRD Instagram example`() {
    val context = ContextManager()
    assertEquals("Liked", GenericInterpreter.interpret(toggleAction("Like", checked = true), context, Verbosity.STANDARD)?.text)
    assertEquals("Unliked", GenericInterpreter.interpret(toggleAction("Like", checked = false), context, Verbosity.STANDARD)?.text)
  }

  @Test
  fun `follow button announces Following, matching PRD Instagram example`() {
    val context = ContextManager()
    assertEquals("Following", GenericInterpreter.interpret(toggleAction("Follow", checked = true), context, Verbosity.STANDARD)?.text)
    assertEquals("Not following", GenericInterpreter.interpret(toggleAction("Follow", checked = false), context, Verbosity.STANDARD)?.text)
  }

  @Test
  fun `detailed verbosity prefixes role`() {
    val context = ContextManager()
    val output = GenericInterpreter.interpret(toggleAction("Wi-Fi", checked = true), context, Verbosity.DETAILED)
    assertEquals("Switch. Wi-Fi on.", output?.text)
  }

  @Test
  fun `click with a label speaks the label`() {
    val action =
      UserAction(ActionType.CLICK, "com.example.app", null, label = "Share", role = "Button", value = null, node = null, timestamp = 0L)
    assertEquals("Share", GenericInterpreter.interpret(action, ContextManager(), Verbosity.STANDARD)?.text)
  }

  @Test
  fun `click without any label falls back to Selected, matching PRD Scenario C`() {
    val action = UserAction(ActionType.CLICK, "com.example.app", null, label = null, role = null, value = null, node = null, timestamp = 0L)
    assertEquals("Selected", GenericInterpreter.interpret(action, ContextManager(), Verbosity.STANDARD)?.text)
  }

  @Test
  fun `tab selection announces tab name, matching PRD Home tab example`() {
    val action =
      UserAction(ActionType.TAB_CHANGED, "com.example.app", null, label = "Home", role = "Tab", value = null, node = null, timestamp = 0L)
    assertEquals("Home tab", GenericInterpreter.interpret(action, ContextManager(), Verbosity.STANDARD)?.text)
  }

  @Test
  fun `scroll direction follows delta sign`() {
    fun scroll(delta: Int) = UserAction(ActionType.SCROLL, "com.example.app", null, null, null, null, node = null, scrollDelta = delta, timestamp = 0L)
    val context = ContextManager()
    assertEquals("Scrolled down", GenericInterpreter.interpret(scroll(10), context, Verbosity.STANDARD)?.text)
    assertEquals("Scrolled up", GenericInterpreter.interpret(scroll(-10), context, Verbosity.STANDARD)?.text)
    assertEquals("More content", GenericInterpreter.interpret(scroll(0), context, Verbosity.STANDARD)?.text)
  }

  @Test
  fun `window opened is suppressed when it matches the already-announced window`() {
    val action =
      UserAction(ActionType.WINDOW_OPENED, "com.example.app", null, label = "Settings", role = null, value = null, node = null, timestamp = 0L)
    val context = ContextManager()
    assertEquals("Settings", GenericInterpreter.interpret(action, context, Verbosity.STANDARD)?.text)

    context.state.currentWindowTitle = "Settings"
    assertNull(GenericInterpreter.interpret(action, context, Verbosity.STANDARD))
  }
}
