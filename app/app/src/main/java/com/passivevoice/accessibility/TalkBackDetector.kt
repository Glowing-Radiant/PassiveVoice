package com.passivevoice.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager

/**
 * Detects whether a screen-reader style service (TalkBack, or an equivalent spoken-feedback
 * service) is currently active, so PassiveVoice can go silent per the PRD's TalkBack-coexistence
 * requirement (do not speak alongside another screen reader).
 */
object TalkBackDetector {
  private val KNOWN_SCREEN_READER_PACKAGES =
    setOf(
      "com.google.android.marvin.talkback",
      "com.samsung.android.accessibility.talkback",
    )

  fun isScreenReaderActive(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    if (!manager.isEnabled) return false
    val spokenFeedbackServices = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
    return spokenFeedbackServices.any { info ->
      val packageName = info.resolveInfo?.serviceInfo?.packageName
      packageName in KNOWN_SCREEN_READER_PACKAGES || packageName?.contains("talkback", ignoreCase = true) == true
    }
  }
}
