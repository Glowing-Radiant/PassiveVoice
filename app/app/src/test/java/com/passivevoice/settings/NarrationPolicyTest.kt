package com.passivevoice.settings

import com.passivevoice.core.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NarrationPolicyTest {

  private val app = "com.example.bank"

  @Test
  fun `everything is allowed by default`() {
    val settings = PassiveVoiceSettings()
    ActionType.entries.forEach { assertNull(NarrationPolicy.blockReason(settings, app, it)) }
  }

  @Test
  fun `muting an app blocks all its narration`() {
    val settings = PassiveVoiceSettings(appProfiles = mapOf(app to AppProfile(narrationEnabled = false)))
    assertNotNull(NarrationPolicy.blockReason(settings, app, ActionType.CLICK))
    assertNull(NarrationPolicy.blockReason(settings, "com.example.other", ActionType.CLICK))
  }

  @Test
  fun `per-app scroll setting overrides the global one in both directions`() {
    val globalOff = PassiveVoiceSettings(narrateScroll = false, appProfiles = mapOf(app to AppProfile(scrollNarration = true)))
    assertNull(NarrationPolicy.blockReason(globalOff, app, ActionType.SCROLL))
    assertNotNull(NarrationPolicy.blockReason(globalOff, "com.example.other", ActionType.SCROLL))

    val globalOn = PassiveVoiceSettings(appProfiles = mapOf(app to AppProfile(scrollNarration = false)))
    assertNotNull(NarrationPolicy.blockReason(globalOn, app, ActionType.SCROLL))
  }

  @Test
  fun `global action toggles block only their own action types`() {
    val settings = PassiveVoiceSettings(narrateClicks = false)
    assertNotNull(NarrationPolicy.blockReason(settings, app, ActionType.CLICK))
    assertNull(NarrationPolicy.blockReason(settings, app, ActionType.TOGGLE))
  }

  @Test
  fun `per-app verbosity overrides the global verbosity`() {
    val settings = PassiveVoiceSettings(verbosity = Verbosity.STANDARD, appProfiles = mapOf(app to AppProfile(verbosity = Verbosity.MINIMAL)))
    assertEquals(Verbosity.MINIMAL, NarrationPolicy.verbosityFor(settings, app))
    assertEquals(Verbosity.STANDARD, NarrationPolicy.verbosityFor(settings, "com.example.other"))
  }

  @Test
  fun `app profile keys round-trip through their storage names`() {
    assertEquals("com.example.bank" to "scroll", SettingsRepository.Keys.parseAppKey(SettingsRepository.Keys.appScroll(app).name))
    assertNull(SettingsRepository.Keys.parseAppKey("verbosity"))
  }
}
