package com.passivevoice.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/**
 * Detects whether a screen reader (TalkBack or one of its peers) is currently active, so
 * PassiveVoice can go silent per the PRD's TalkBack-coexistence requirement (do not speak
 * alongside another screen reader).
 */
object TalkBackDetector {
  /** Screen readers by package. User-facing names, for the settings screen, are in [KNOWN_SCREEN_READER_NAMES]. */
  private val KNOWN_SCREEN_READER_PACKAGES =
    setOf(
      "com.google.android.marvin.talkback", // TalkBack
      "com.android.talkback", // TalkBack, open-source builds
      "com.samsung.android.accessibility.talkback", // Samsung TalkBack
      "eu.appsuite.talkforward", // Talk Forward
      "com.nirenr.talkman", // Jieshuo
      "fyi.quin.backtalk", // BackTalk
    )

  val KNOWN_SCREEN_READER_NAMES = listOf("TalkBack", "Talk Forward", "Jieshuo", "BackTalk")

  fun isScreenReaderActive(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    if (!manager.isEnabled) return false
    // Not filtered to spoken feedback: some screen readers declare generic feedback, and only known ones match anyway.
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
      val service = info.resolveInfo?.serviceInfo
      isScreenReader(service?.packageName, service?.name)
    }
  }

  /** Whether the accessibility service [className] in [packageName] is a screen reader. */
  fun isScreenReader(packageName: String?, className: String?): Boolean {
    if (packageName == null) return false
    return packageName in KNOWN_SCREEN_READER_PACKAGES ||
      packageName.contains("talkback", ignoreCase = true) ||
      packageName.contains("talkforward", ignoreCase = true) ||
      // TalkBack forks (BackTalk, sideloaded Talk Forward builds) keep TalkBack's service class under a new package.
      className?.endsWith(".TalkBackService") == true
  }
}
