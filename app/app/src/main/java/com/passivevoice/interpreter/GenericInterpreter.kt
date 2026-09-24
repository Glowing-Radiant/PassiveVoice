package com.passivevoice.interpreter

import com.passivevoice.core.ActionType
import com.passivevoice.core.ContextManager
import com.passivevoice.core.UserAction
import com.passivevoice.settings.Verbosity
import com.passivevoice.speech.SpeechPriority

/**
 * App-agnostic interpreter: no per-package hacks, just label/state heuristics that hold across
 * most apps (a "Like" button behaves the same way in a dozen different apps). Per-app adapters
 * are explicitly out of scope for the MVP (PRD v0.3).
 */
object GenericInterpreter : ActionInterpreter {

  // Common toggle-button verbs and their spoken past-tense state, keyed by a keyword found in the label.
  private val TOGGLE_VERBS =
    listOf(
      "like" to ("Liked" to "Unliked"),
      "follow" to ("Following" to "Not following"),
      "subscribe" to ("Subscribed" to "Unsubscribed"),
      "save" to ("Saved" to "Unsaved"),
      "bookmark" to ("Bookmarked" to "Unbookmarked"),
      "favorite" to ("Favorited" to "Unfavorited"),
      "favourite" to ("Favourited" to "Unfavourited"),
      "star" to ("Starred" to "Unstarred"),
      "mute" to ("Muted" to "Unmuted"),
    )

  override fun interpret(action: UserAction, context: ContextManager, verbosity: Verbosity): SpeechOutput? =
    when (action.type) {
      ActionType.CLICK -> interpretClick(action, verbosity)
      ActionType.TOGGLE -> interpretToggle(action, context, verbosity)
      ActionType.SELECTION -> interpretSelection(action, verbosity)
      ActionType.TAB_CHANGED -> interpretTabChanged(action, verbosity)
      ActionType.SCROLL -> interpretScroll(action, verbosity)
      ActionType.WINDOW_OPENED -> interpretWindowOpened(action, context, verbosity)
      ActionType.MENU_OPENED -> SpeechOutput("Menu opened")
      else -> null // LONG_CLICK, TEXT_INPUT, CONTENT_CHANGED, FOCUS_CHANGED, etc. — collected for debug, not narrated in MVP.
    }

  private fun interpretClick(action: UserAction, verbosity: Verbosity): SpeechOutput {
    val label = action.label
    if (label.isNullOrBlank()) return SpeechOutput("Selected")
    val role = action.role
    return SpeechOutput(
      when (verbosity) {
        Verbosity.DETAILED -> if (role != null) "$role. $label." else label
        else -> label
      }
    )
  }

  private fun interpretToggle(action: UserAction, context: ContextManager, verbosity: Verbosity): SpeechOutput? {
    val label = action.label ?: return null
    val checked = action.value?.toBooleanStrictOrNull() ?: return null
    val key = action.node?.identityKey ?: label
    context.recordToggleState(key, checked)

    val verbMatch = TOGGLE_VERBS.firstOrNull { (verb, _) -> label.contains(verb, ignoreCase = true) }
    val phrase =
      if (verbMatch != null) {
        val (onPhrase, offPhrase) = verbMatch.second
        if (checked) onPhrase else offPhrase
      } else {
        "$label ${if (checked) "on" else "off"}"
      }

    return SpeechOutput(
      when (verbosity) {
        Verbosity.DETAILED -> "${action.role ?: "Switch"}. $phrase."
        else -> phrase
      }
    )
  }

  private fun interpretSelection(action: UserAction, verbosity: Verbosity): SpeechOutput? {
    val label = action.label ?: return null
    return SpeechOutput(
      when (verbosity) {
        Verbosity.DETAILED -> "${action.role ?: "Item"}. $label selected."
        else -> "$label selected"
      }
    )
  }

  private fun interpretTabChanged(action: UserAction, verbosity: Verbosity): SpeechOutput? {
    val label = action.label ?: return null
    return SpeechOutput(
      when (verbosity) {
        Verbosity.DETAILED -> "Tab. $label tab."
        else -> "$label tab"
      }
    )
  }

  private fun interpretScroll(action: UserAction, verbosity: Verbosity): SpeechOutput {
    val phrase =
      when {
        action.scrollDelta > 0 -> "Scrolled down"
        action.scrollDelta < 0 -> "Scrolled up"
        else -> "More content"
      }
    return SpeechOutput(phrase, priority = SpeechPriority.LOW)
  }

  private fun interpretWindowOpened(action: UserAction, context: ContextManager, verbosity: Verbosity): SpeechOutput? {
    val label = action.label ?: return null
    if (label == context.state.currentWindowTitle) return null
    return SpeechOutput(
      when (verbosity) {
        Verbosity.DETAILED -> "$label opened."
        else -> label
      }
    )
  }
}
