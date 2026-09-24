package com.passivevoice.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.passivevoice.core.ActionType
import com.passivevoice.core.ContextManager
import com.passivevoice.core.EventNormalizer
import com.passivevoice.core.ScrollAnalyzer
import com.passivevoice.core.ScrollSample
import com.passivevoice.core.UserAction
import com.passivevoice.debug.DebugEventBus
import com.passivevoice.debug.DebugLogEntry
import com.passivevoice.interpreter.GenericInterpreter
import com.passivevoice.settings.NarrationPolicy
import com.passivevoice.settings.PassiveVoiceSettings
import com.passivevoice.settings.SettingsRepository
import com.passivevoice.speech.SpeechDecision
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
  private val handler = Handler(Looper.getMainLooper())
  private val contextManager = ContextManager()
  private lateinit var speechManager: SpeechManager
  private lateinit var settingsRepository: SettingsRepository

  @Volatile private var currentSettings = PassiveVoiceSettings()
  @Volatile private var speechInitialized = false
  @Volatile private var cachedTalkBackActive = false
  private var lastTalkBackCheckMs = 0L
  private val lastContentChangedMsByPackage = mutableMapOf<String, Long>()

  // Accessibility callbacks, the handler and these fields are all confined to the main thread.
  private var pendingClick: PendingClick? = null
  private val pendingScrolls = mutableMapOf<String, PendingScroll>()

  /** A tap whose narration waits briefly so the state change it causes can be observed. */
  private class PendingClick(val source: AccessibilityNodeInfo, val action: UserAction, val className: String?) {
    var windowChanged = false
  }

  private inner class PendingScroll(val key: String, val className: String?) {
    val samples = mutableListOf<ScrollSample>()
    lateinit var latest: UserAction
    val flush = Runnable { flushScroll(this) }
  }

  /** A window change held briefly: splash screens and other transitional windows are replaced by the real one. */
  private class PendingWindow(val action: UserAction, val eventType: String, val className: String?)

  private var pendingWindow: PendingWindow? = null

  private val resolveClickRunnable = Runnable { pendingClick?.let(::resolvePendingClick) }
  private val flushWindowRunnable = Runnable { flushPendingWindow() }

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
        speechManager.setOutput(settings.audioStream, settings.volume)
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
    try {
      handleEvent(event)
    } catch (e: Exception) {
      // A misbehaving app's accessibility tree must never take the service down with it.
      Log.e(TAG, "Failed to handle ${AccessibilityEvent.eventTypeToString(event.eventType)}", e)
    }
  }

  private fun handleEvent(event: AccessibilityEvent) {
    val now = System.currentTimeMillis()
    if (isThrottledContentChange(event, now)) return

    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      // Speak the tap before the screen it opened, and skip looking for a toggled switch in a row that just navigated away.
      pendingClick?.windowChanged = true
      flushPendingClick()
    }

    val source = event.source
    var keepSource = false
    try {
      val node = source?.let(NodeInspector::snapshot) ?: NodeInspector.snapshotFromEvent(event)
      val eventType = AccessibilityEvent.eventTypeToString(event.eventType)
      val className = event.className?.toString()

      if (node?.isPassword == true || event.isPassword) {
        logDebug(now, event.packageName?.toString(), eventType, className, "(password field)", null, null, "Password field")
        return
      }

      val action = EventNormalizer.normalize(event, node, now) ?: return
      when (action.type) {
        ActionType.CLICK, ActionType.TOGGLE ->
          if (source != null) {
            startPendingClick(source, action, className)
            keepSource = true
          } else {
            narrate(action, eventType, className, action.label)
          }
        ActionType.SCROLL -> collectScroll(event, action, className)
        ActionType.WINDOW_OPENED, ActionType.MENU_OPENED -> holdWindow(action, eventType, className)
        else -> narrate(action, eventType, className, debugText(event, action))
      }
      if (action.type != ActionType.WINDOW_OPENED && action.type != ActionType.MENU_OPENED) updateWindowContext(action)
    } finally {
      @Suppress("DEPRECATION") if (!keepSource) source?.recycle()
    }
  }

  /**
   * TYPE_WINDOW_CONTENT_CHANGED can fire dozens of times per second during a single UI update
   * (one event per changed node). It's never narrated, so drop the excess before doing any work.
   */
  private fun isThrottledContentChange(event: AccessibilityEvent, now: Long): Boolean {
    if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return false
    val packageName = event.packageName?.toString() ?: ""
    val lastMs = lastContentChangedMsByPackage[packageName] ?: 0L
    if (now - lastMs < CONTENT_CHANGED_THROTTLE_MS) return true
    lastContentChangedMsByPackage[packageName] = now
    return false
  }

  private fun holdWindow(action: UserAction, eventType: String, className: String?) {
    pendingWindow?.let {
      logDebug(it.action.timestamp, it.action.packageName, it.eventType, it.className, it.action.label, it.action.type.name, null, "Replaced by the next window")
    }
    pendingWindow = PendingWindow(action, eventType, className)
    handler.removeCallbacks(flushWindowRunnable)
    handler.postDelayed(flushWindowRunnable, WINDOW_SETTLE_MS)
  }

  private fun flushPendingWindow() {
    val pending = pendingWindow ?: return
    pendingWindow = null
    handler.removeCallbacks(flushWindowRunnable)
    narrate(pending.action, pending.eventType, pending.className, pending.action.label)
    // Only now: the interpreter compares against the previous window's title.
    updateWindowContext(pending.action)
  }

  private fun startPendingClick(source: AccessibilityNodeInfo, action: UserAction, className: String?) {
    flushPendingWindow()
    flushPendingClick()
    val pending = PendingClick(source, action.copy(before = action.node), className)
    pendingClick = pending
    val delay = currentSettings.speechDelayMs
    if (delay <= 0) resolvePendingClick(pending) else handler.postDelayed(resolveClickRunnable, delay.toLong())
  }

  private fun flushPendingClick() {
    pendingClick?.let(::resolvePendingClick)
  }

  private fun resolvePendingClick(pending: PendingClick) {
    pendingClick = null
    handler.removeCallbacks(resolveClickRunnable)

    // Re-read the tapped node: by now the app has applied whatever the tap changed.
    val after =
      try {
        if (pending.source.refresh()) {
          val snapshot = NodeInspector.snapshot(pending.source)
          if (!pending.windowChanged && !snapshot.isCheckable) {
            snapshot.copy(innerChecked = NodeInspector.innerCheckedState(pending.source))
          } else {
            snapshot
          }
        } else {
          null // The node is gone: the tap navigated away.
        }
      } finally {
        @Suppress("DEPRECATION") pending.source.recycle()
      }

    val original = pending.action
    val node = after ?: original.before
    val checkable = node?.isCheckable == true
    val resolved =
      original.copy(
        type = if (checkable) ActionType.TOGGLE else ActionType.CLICK,
        node = node,
        value = if (checkable) node.isChecked.toString() else null,
        label = after?.label ?: original.label,
      )
    val eventType = AccessibilityEvent.eventTypeToString(AccessibilityEvent.TYPE_VIEW_CLICKED)
    val beforeLabel = original.before?.label
    val debugText = if (beforeLabel != null && after?.label != null && beforeLabel != after.label) "$beforeLabel → ${after.label}" else resolved.label
    narrate(resolved, eventType, pending.className, debugText)
  }

  private fun collectScroll(event: AccessibilityEvent, action: UserAction, className: String?) {
    val container = action.node?.viewIdResourceName ?: className ?: "?"
    val key = "${action.packageName}|$container"
    val pending = pendingScrolls.getOrPut(key) { PendingScroll(key, className) }
    pending.samples += NodeInspector.scrollSample(event)
    pending.latest = action
    handler.removeCallbacks(pending.flush)
    handler.postDelayed(pending.flush, SCROLL_SETTLE_MS)
  }

  private fun flushScroll(pending: PendingScroll) {
    pendingScrolls.remove(pending.key)
    val now = System.currentTimeMillis()
    val previous = contextManager.previousScrollPosition(pending.key)
    val summary = ScrollAnalyzer.summarize(pending.samples, previous, now)
    contextManager.recordScrollPosition(pending.key, ScrollAnalyzer.settledPosition(pending.samples, summary, previous, now))

    val count = pending.samples.size
    val debugText = "$count scroll event${if (count == 1) "" else "s"}"
    if (summary.isBounce) {
      logDebug(now, pending.latest.packageName, SCROLL_EVENT_TYPE, pending.className, debugText, ActionType.SCROLL.name, null, "Spring-back at list edge")
      return
    }
    narrate(pending.latest.copy(scroll = summary), SCROLL_EVENT_TYPE, pending.className, debugText)
  }

  /** The single path every action takes to speech: policy → interpretation → mute → dedup → priority. */
  private fun narrate(action: UserAction, eventType: String, className: String?, debugText: String?) {
    val now = System.currentTimeMillis()
    val settings = currentSettings
    val packageName = action.packageName

    val blockReason = NarrationPolicy.blockReason(settings, packageName, action.type)
    val output =
      if (blockReason == null) GenericInterpreter.interpret(action, contextManager, NarrationPolicy.verbosityFor(settings, packageName)) else null

    var spoken: String? = null
    var suppressedReason: String? = blockReason
    if (output != null) {
      val text = output.text
      when {
        SpeechPolicy.isMuted(talkBackActiveThrottled(now), settings.autoMuteWithTalkBack) -> suppressedReason = "Muted (TalkBack active): \"$text\""
        contextManager.isDuplicate(text, now) -> suppressedReason = "Duplicate: \"$text\""
        else ->
          when (speechManager.speak(text, output.priority, settings.queueMode)) {
            null -> suppressedReason = "Speech engine not ready: \"$text\""
            SpeechDecision.DROP -> suppressedReason = "Lower priority than current speech: \"$text\""
            else -> {
              contextManager.recordSpoken(text, now)
              spoken = text
            }
          }
      }
    }

    if (action.type == ActionType.CLICK || action.type == ActionType.TOGGLE) contextManager.recordClick(action.label, now)
    logDebug(now, packageName, eventType, className, debugText, action.type.name, spoken, suppressedReason)
  }

  private fun updateWindowContext(action: UserAction) {
    val windowTitle =
      if (action.type == ActionType.WINDOW_OPENED || action.type == ActionType.MENU_OPENED) {
        action.label ?: contextManager.state.currentWindowTitle
      } else {
        contextManager.state.currentWindowTitle
      }
    contextManager.updateWindow(action.packageName ?: contextManager.state.currentPackage, windowTitle)
  }

  /** What the debug log may show. Typed text and notification contents are never kept (PRD §25). */
  private fun debugText(event: AccessibilityEvent, action: UserAction): String? =
    when {
      action.type == ActionType.TEXT_INPUT -> "(${event.text.sumOf { it?.length ?: 0 }} characters)"
      event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "(notification)"
      else -> action.label ?: event.text.joinToString(" ")
    }

  private fun logDebug(
    now: Long,
    packageName: String?,
    eventType: String,
    className: String?,
    text: String?,
    interpretation: String?,
    speech: String?,
    suppressedReason: String?,
  ) {
    if (!currentSettings.debugLoggingEnabled) return
    DebugEventBus.log(DebugLogEntry(now, packageName, eventType, className, text, interpretation, speech, suppressedReason))
    // Mirrored to logcat (readable only over adb) so a full session can be reviewed from a computer.
    val outcome = speech?.let { "SPOKE \"$it\"" } ?: suppressedReason?.let { "SUPPRESSED $it" } ?: "-"
    Log.d(DEBUG_TAG, "$eventType | $packageName | ${className ?: "-"} | ${text ?: "-"} | ${interpretation ?: "-"} | $outcome")
  }

  private fun talkBackActiveThrottled(now: Long): Boolean {
    if (now - lastTalkBackCheckMs > TALKBACK_CHECK_INTERVAL_MS) {
      cachedTalkBackActive = TalkBackDetector.isScreenReaderActive(this)
      lastTalkBackCheckMs = now
    }
    return cachedTalkBackActive
  }

  override fun onInterrupt() {
    if (::speechManager.isInitialized) speechManager.stop()
  }

  override fun onDestroy() {
    super.onDestroy()
    handler.removeCallbacksAndMessages(null)
    pendingClick?.let { @Suppress("DEPRECATION") it.source.recycle() }
    pendingClick = null
    pendingWindow = null
    pendingScrolls.clear()
    if (::speechManager.isInitialized) speechManager.shutdown()
    serviceScope.cancel()
    _isRunning.value = false
  }

  companion object {
    private const val TAG = "PassiveVoice/Service"
    private const val DEBUG_TAG = "PassiveVoice/Events"
    private const val TALKBACK_CHECK_INTERVAL_MS = 1000L
    private const val CONTENT_CHANGED_THROTTLE_MS = 250L

    /** Quiet period after the last scroll event before a gesture counts as finished. */
    private const val SCROLL_SETTLE_MS = 300L
    private const val SCROLL_EVENT_TYPE = "SCROLL_GESTURE"

    /** Splash screens hand over to the real activity within a few hundred milliseconds. */
    private const val WINDOW_SETTLE_MS = 400L

    private val _isRunning = MutableStateFlow(false)

    /** True only while this process has a live, connected instance of the service. */
    val isRunning: StateFlow<Boolean> = _isRunning

    /**
     * Reliable across process death: checks the system's list of enabled accessibility services
     * rather than relying on [isRunning], which is only meaningful within this process.
     */
    fun isEnabledInSystemSettings(context: Context): Boolean {
      val expected = ComponentName(context, PassiveVoiceService::class.java)
      val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
      // Entries may be stored in full ("pkg/pkg.Cls") or short ("pkg/.Cls") form; unflatten handles both.
      return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
  }
}
