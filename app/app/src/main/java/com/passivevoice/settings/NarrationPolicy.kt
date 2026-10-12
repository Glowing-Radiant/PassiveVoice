package com.passivevoice.settings

import com.passivevoice.core.ActionType

/** Resolves global settings plus per-app overrides into what may be spoken for a given app. */
object NarrationPolicy {

  fun verbosityFor(settings: PassiveVoiceSettings, packageName: String?): Verbosity =
    packageName?.let { settings.appProfiles[it]?.verbosity } ?: settings.verbosity

  /** Null when [type] may be narrated in [packageName]; otherwise a short reason for the debug log. */
  fun blockReason(settings: PassiveVoiceSettings, packageName: String?, type: ActionType): String? {
    val profile = packageName?.let { settings.appProfiles[it] }
    if (profile?.narrationEnabled == false) return "Narration off for this app"
    return when (type) {
      ActionType.CLICK -> "Button narration off".takeUnless { settings.narrateClicks }
      ActionType.TOGGLE -> "Switch narration off".takeUnless { settings.narrateToggles }
      ActionType.SELECTION, ActionType.TAB_CHANGED -> "Selection narration off".takeUnless { settings.narrateSelections }
      ActionType.SCROLL -> "Scroll narration off".takeUnless { profile?.scrollNarration ?: settings.narrateScroll }
      ActionType.WINDOW_OPENED, ActionType.MENU_OPENED -> "Screen narration off".takeUnless { settings.narrateWindowChanges }
      ActionType.TEXT_INPUT -> "Typing echo off".takeUnless { settings.narrateTypedCharacters || settings.narrateTypedWords }
      else -> null
    }
  }
}
