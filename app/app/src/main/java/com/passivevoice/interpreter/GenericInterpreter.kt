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

/**
 * App-agnostic interpreter: no per-package hacks, just label/state heuristics that hold across
 * most apps (a "Like" button behaves the same way in a dozen different apps). Per-app adapters
 * are explicitly out of scope until PRD v0.3.
 */
object GenericInterpreter : ActionInterpreter {

  private enum class VerbForm { BASE, STATE, NEGATED }

  /** A toggle-style action verb and how to speak each resulting state. */
  private class ToggleVerb(verb: String, val onPhrase: String, val offPhrase: String) {
    private val negated = Regex("\\bun$verb\\b", RegexOption.IGNORE_CASE) // "Unlike": liked, offering to undo
    private val state = Regex("\\b$verb(d|ed|red|ing)\\b", RegexOption.IGNORE_CASE) // "Liked", "Following"
    private val base = Regex("\\b${verb}s?\\b", RegexOption.IGNORE_CASE) // "Like", "Follow"

    fun formIn(label: String): VerbForm? =
      when {
        negated.containsMatchIn(label) -> VerbForm.NEGATED
        state.containsMatchIn(label) -> VerbForm.STATE
        base.containsMatchIn(label) -> VerbForm.BASE
        else -> null
      }

    /** Stricter match for plain buttons, where "Liked videos" must not read as a like action. */
    fun exactFormOf(label: String): VerbForm? {
      val word = label.replace(Regex("[^\\p{L} ]"), "").trim()
      return if (word.isNotEmpty() && ' ' !in word) formIn(word) else null
    }
  }

  private val TOGGLE_VERBS =
    listOf(
      ToggleVerb("like", "Liked", "Unliked"),
      ToggleVerb("follow", "Following", "Not following"),
      ToggleVerb("subscribe", "Subscribed", "Unsubscribed"),
      ToggleVerb("save", "Saved", "Unsaved"),
      ToggleVerb("bookmark", "Bookmarked", "Unbookmarked"),
      ToggleVerb("favorite", "Favorited", "Unfavorited"),
      ToggleVerb("favourite", "Favourited", "Unfavourited"),
      ToggleVerb("star", "Starred", "Unstarred"),
      ToggleVerb("mute", "Muted", "Unmuted"),
    )

  override fun interpret(action: UserAction, context: ContextManager, verbosity: Verbosity): SpeechOutput? =
    when (action.type) {
      ActionType.CLICK -> if (context.wasJustSelected(action.label, action.timestamp)) null else interpretClick(action, verbosity)
      ActionType.TOGGLE -> interpretToggle(action, verbosity)
      ActionType.SELECTION -> interpretSelection(action, verbosity)
      ActionType.TAB_CHANGED -> interpretTabChanged(action, verbosity)
      ActionType.SCROLL -> interpretScroll(action, verbosity)
      ActionType.WINDOW_OPENED -> interpretWindowOpened(action, context, verbosity)
      ActionType.MENU_OPENED -> SpeechOutput("Menu opened")
      else -> null // LONG_CLICK, CONTENT_CHANGED (TEXT_INPUT is TypingEcho's), FOCUS_CHANGED, etc. — collected for debug, not narrated.
    }

  private fun interpretClick(action: UserAction, verbosity: Verbosity): SpeechOutput {
    val after = action.node
    val before = action.before
    val label = action.label

    val phrase =
      after?.innerChecked?.let { togglePhrase(label, it) }
        ?: verbStatePhrase(before, after)
        ?: stateChangePhrase(label, before, after)
        ?: label?.takeIf { it.isNotBlank() }
        ?: return SpeechOutput("Selected", SpeechPriority.HIGH)

    val role = action.role
    return SpeechOutput(if (verbosity == Verbosity.DETAILED && role != null) "$role. $phrase." else phrase, SpeechPriority.HIGH)
  }

  /** Like/Follow-style buttons that aren't formal checkboxes: infer the resulting state from the label. */
  private fun verbStatePhrase(before: NodeSnapshot?, after: NodeSnapshot?): String? {
    val afterLabel = after?.label ?: return null
    for (verb in TOGGLE_VERBS) {
      val afterForm = verb.exactFormOf(afterLabel) ?: continue
      val beforeForm = before?.label?.let(verb::exactFormOf)
      return when {
        afterForm == VerbForm.NEGATED || afterForm == VerbForm.STATE -> verb.onPhrase
        beforeForm == VerbForm.NEGATED || beforeForm == VerbForm.STATE -> verb.offPhrase
        after.isSelected -> verb.onPhrase
        before?.isSelected == true -> verb.offPhrase
        // A bare "Like" with no other evidence is ambiguous (not yet liked, or just unliked); say it as-is.
        else -> null
      }
    }
    return null
  }

  private fun stateChangePhrase(label: String?, before: NodeSnapshot?, after: NodeSnapshot?): String? {
    if (before == null || after == null) return null
    val afterLabel = after.label
    if (afterLabel != null && before.label != null && afterLabel != before.label) return afterLabel
    val name = afterLabel ?: label ?: return null
    if (after.isSelected != before.isSelected) return if (after.isSelected) "$name selected" else "$name not selected"
    val state = after.stateDescription
    if (state != null && state != before.stateDescription) return "$name, $state"
    return null
  }

  private fun interpretToggle(action: UserAction, verbosity: Verbosity): SpeechOutput? {
    val checked = action.value?.toBooleanStrictOrNull() ?: action.node?.isChecked ?: return null
    val phrase = togglePhrase(action.label, checked)
    return SpeechOutput(
      if (verbosity == Verbosity.DETAILED) "${action.role ?: "Switch"}. $phrase." else phrase,
      SpeechPriority.HIGH,
    )
  }

  private fun togglePhrase(label: String?, checked: Boolean): String {
    if (label.isNullOrBlank()) return if (checked) "On" else "Off"
    val verb = TOGGLE_VERBS.firstOrNull { it.formIn(label) != null }
    return when {
      verb != null -> if (checked) verb.onPhrase else verb.offPhrase
      else -> "$label ${if (checked) "on" else "off"}"
    }
  }

  private fun interpretSelection(action: UserAction, verbosity: Verbosity): SpeechOutput? {
    val label = action.label ?: return null
    return SpeechOutput(
      if (verbosity == Verbosity.DETAILED) "${action.role ?: "Item"}. $label selected." else "$label selected",
      SpeechPriority.HIGH,
    )
  }

  private fun interpretTabChanged(action: UserAction, verbosity: Verbosity): SpeechOutput? {
    val label = action.label ?: return null
    return SpeechOutput(if (verbosity == Verbosity.DETAILED) "Tab. $label tab." else "$label tab", SpeechPriority.HIGH)
  }

  private fun interpretScroll(action: UserAction, verbosity: Verbosity): SpeechOutput? {
    val scroll = action.scroll ?: return null
    if (scroll.isBounce) return null
    // No direction, no page, no edge: typically a list's initial layout, which tells the user nothing.
    if (scroll.direction == ScrollDirection.UNKNOWN && !scroll.isPageChange && scroll.edge == null) return null
    val movement =
      if (scroll.isPageChange) {
        if (scroll.direction == ScrollDirection.FORWARD) "Next item" else "Previous item"
      } else {
        when (scroll.direction) {
          ScrollDirection.UP -> "Scrolled up"
          ScrollDirection.DOWN -> "Scrolled down"
          ScrollDirection.LEFT -> "Scrolled left"
          ScrollDirection.RIGHT -> "Scrolled right"
          ScrollDirection.FORWARD -> "Scrolled forward"
          ScrollDirection.BACKWARD -> "Scrolled back"
          ScrollDirection.UNKNOWN -> "Scrolled"
        }
      }
    val edge =
      when (scroll.edge) {
        ScrollEdge.END -> "End of list"
        ScrollEdge.START -> if (scroll.direction == ScrollDirection.LEFT) "Start of list" else "Top of list"
        null -> null
      }
    // Pushing against an edge that's already reached moves nothing; the edge is the whole story.
    val phrase = if (edge != null && !scroll.movedItems) null else movement
    val position = if (verbosity == Verbosity.DETAILED) positionPhrase(scroll) else null
    return SpeechOutput(
      listOfNotNull(phrase, edge, position).joinToString(". "),
      // Moving to the next reel/photo is the whole point of the gesture; a list scroll is background feedback.
      if (scroll.isPageChange) SpeechPriority.NORMAL else SpeechPriority.LOW,
    )
  }

  private fun positionPhrase(scroll: ScrollSummary): String? {
    if (scroll.itemCount <= 0 || scroll.fromIndex < 0) return null
    return if (scroll.toIndex < 0 || scroll.fromIndex == scroll.toIndex) {
      "Item ${scroll.fromIndex + 1} of ${scroll.itemCount}"
    } else {
      "Items ${scroll.fromIndex + 1} to ${scroll.toIndex + 1} of ${scroll.itemCount}"
    }
  }

  private fun interpretWindowOpened(action: UserAction, context: ContextManager, verbosity: Verbosity): SpeechOutput? {
    val label = action.label ?: return null
    if (label == context.state.currentWindowTitle) return null
    // Tapping "Battery" already said "Battery"; announcing the Battery screen too is redundant.
    if (context.wasJustTapped(label, action.timestamp)) return null
    return SpeechOutput(if (verbosity == Verbosity.DETAILED) "$label opened." else label)
  }
}
