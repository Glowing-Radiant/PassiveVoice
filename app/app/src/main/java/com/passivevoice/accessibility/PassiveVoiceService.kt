package com.passivevoice.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.passivevoice.core.ActionType
import com.passivevoice.core.ContextManager
import com.passivevoice.core.EventNormalizer
import com.passivevoice.core.UserAction
import com.passivevoice.debug.DebugEventBus
import com.passivevoice.debug.DebugLogEntry
import com.passivevoice.interpreter.GenericInterpreter
import com.passivevoice.settings.PassiveVoiceSettings
import com.passivevoice.settings.SettingsRepository
import com.passivevoice.speech.SpeechManager
import com.passivevoice.speech.SpeechPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The core of PassiveVoice: observes [AccessibilityEvent]s, never touches the touchscreen or
 * dispatches gestures, and speaks a short confirmation of what the resulting UI change was.
 * See the "Touch handling philosophy" section of the PRD — this class must never call
 * `dispatchGesture()`, request touch-exploration mode, or intercept motion events.
 */
class PassiveVoiceService : AccessibilityService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val contextManager = ContextManager()
  private lateinit var speechManager: SpeechManager
  private lateinit var settingsRepository: SettingsRepository

  @Volatile private var currentSettings = PassiveVoiceSettings()
  @Volatile private var speechInitialized = false
  @Volatile private var cachedTalkBackActive = false
  private var lastTalkBackCheckMs = 0L
  private val lastContentChangedMsByPackage = mutableMapOf<String, Long>()

  override fun onServiceConnected() {
    super.onServiceConnected()
    speechManager = SpeechManager(applicationContext)
    settingsRepository = SettingsRepository(applicationContext)
    _isRunning.value = true

    serviceScope.launch {
      settingsRepository.settings.collect { settings ->
        val engineChanged = speechInitialized && settings.ttsEngine != currentSettings.ttsEngine
        val needsInit = !speechInitialized || engineChanged
        currentSettings = settings
        if (needsInit) {
          speechInitialized = true
          speechManager.initialize(settings.ttsEngine) { speechManager.setVoiceParams(settings.speechRate, settings.pitch) }
        } else {
          speechManager.setVoiceParams(settings.speechRate, settings.pitch)
        }
      }
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {
    val now = System.currentTimeMillis()
    val settings = currentSettings

    // TYPE_WINDOW_CONTENT_CHANGED can fire dozens of times per second during a single UI update
    // (one event per changed node) — it's never narrated in the MVP, so drop the excess before
    // doing any normalization/interpretation work or flooding the debug log with it.
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
      val packageName = event.packageName?.toString() ?: ""
      val lastMs = lastContentChangedMsByPackage[packageName] ?: 0L
      if (now - lastMs < CONTENT_CHANGED_THROTTLE_MS) return
      lastContentChangedMsByPackage[packageName] = now
    }

    val action = runCatching { EventNormalizer.normalize(event, contextManager) }.getOrNull()
    updateWindowContext(event, action)

    val output = action?.let { runCatching { GenericInterpreter.interpret(it, contextManager, settings.verbosity) }.getOrNull() }

    var suppressedReason: String? = null
    var spokenText: String? = null
    if (output != null) {
      val talkBackActive = talkBackActiveThrottled(now)
      val muted = SpeechPolicy.isMuted(talkBackActive, settings.autoMuteWithTalkBack)
      val duplicate = contextManager.isDuplicate(output.text, now)
      when {
        muted -> suppressedReason = "Muted (TalkBack active): \"${output.text}\""
        duplicate -> suppressedReason = "Duplicate: \"${output.text}\""
        else -> {
          speechManager.speak(output.text, settings.queueMode, settings.volume)
          contextManager.recordSpoken(output.text, now)
          spokenText = output.text
        }
      }
    }

    if (settings.debugLoggingEnabled) {
      DebugEventBus.log(buildDebugEntry(event, action, spokenText, suppressedReason, now))
    }
  }

  private fun updateWindowContext(event: AccessibilityEvent, action: UserAction?) {
    val packageName = event.packageName?.toString() ?: contextManager.state.currentPackage
    val windowTitle =
      if (action != null && (action.type == ActionType.WINDOW_OPENED || action.type == ActionType.MENU_OPENED)) {
        action.label ?: contextManager.state.currentWindowTitle
      } else {
        contextManager.state.currentWindowTitle
      }
    contextManager.updateWindow(packageName, windowTitle)
  }

  private fun talkBackActiveThrottled(now: Long): Boolean {
    if (now - lastTalkBackCheckMs > TALKBACK_CHECK_INTERVAL_MS) {
      cachedTalkBackActive = TalkBackDetector.isScreenReaderActive(this)
      lastTalkBackCheckMs = now
    }
    return cachedTalkBackActive
  }

  private fun buildDebugEntry(
    event: AccessibilityEvent,
    action: UserAction?,
    spokenText: String?,
    suppressedReason: String?,
    now: Long,
  ): DebugLogEntry =
    DebugLogEntry(
      timestamp = now,
      packageName = event.packageName?.toString(),
      eventType = AccessibilityEvent.eventTypeToString(event.eventType),
      className = event.className?.toString(),
      text = action?.label ?: event.text.joinToString(" "),
      interpretation = action?.type?.name,
      speech = spokenText,
      suppressedReason = suppressedReason,
    )

  override fun onInterrupt() {
    if (::speechManager.isInitialized) speechManager.stop()
  }

  override fun onDestroy() {
    super.onDestroy()
    if (::speechManager.isInitialized) speechManager.shutdown()
    serviceScope.cancel()
    _isRunning.value = false
  }

  companion object {
    private const val TALKBACK_CHECK_INTERVAL_MS = 1000L
    private const val CONTENT_CHANGED_THROTTLE_MS = 250L

    private val _isRunning = MutableStateFlow(false)

    /** True only while this process has a live, connected instance of the service. */
    val isRunning: StateFlow<Boolean> = _isRunning

    /**
     * Reliable across process death: checks the system's list of enabled accessibility services
     * rather than relying on [isRunning], which is only meaningful within this process.
     */
    fun isEnabledInSystemSettings(context: Context): Boolean {
      val expected = ComponentName(context, PassiveVoiceService::class.java).flattenToString()
      val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
      return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
  }
}
