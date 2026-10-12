package com.passivevoice.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkBackDetectorTest {

  @Test
  fun `known screen readers are recognised`() {
    assertTrue(TalkBackDetector.isScreenReader("com.google.android.marvin.talkback", "com.google.android.marvin.talkback.TalkBackService"))
    assertTrue(TalkBackDetector.isScreenReader("eu.appsuite.talkforward", null))
    assertTrue(TalkBackDetector.isScreenReader("com.nirenr.talkman", "com.nirenr.talkman.TalkManAccessibilityService"))
    assertTrue(TalkBackDetector.isScreenReader("fyi.quin.backtalk", "com.google.android.marvin.talkback.TalkBackService"))
  }

  @Test
  fun `a TalkBack fork under an unknown package is recognised by its service class`() {
    assertTrue(TalkBackDetector.isScreenReader("com.example.fork", "com.google.android.marvin.talkback.TalkBackService"))
  }

  @Test
  fun `other accessibility services are not screen readers`() {
    assertFalse(TalkBackDetector.isScreenReader("io.github.aaronhewitt.taptype", "io.github.aaronhewitt.taptype.accessibility.TouchPassthroughService"))
    assertFalse(TalkBackDetector.isScreenReader("com.google.android.accessibility.switchaccess", "com.android.switchaccess.SwitchAccessService"))
    assertFalse(TalkBackDetector.isScreenReader("com.passivevoice", "com.passivevoice.accessibility.PassiveVoiceService"))
    assertFalse(TalkBackDetector.isScreenReader(null, null))
  }
}
