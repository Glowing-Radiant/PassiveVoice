package com.passivevoice.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-process log of recent events for the developer inspector (PRD "Live Event Monitor").
 * The service and the UI run in the same process, so a plain in-memory bus is enough — nothing
 * here is persisted, which is intentional: it's a live debugging aid, not an activity record.
 */
object DebugEventBus {
  private const val MAX_ENTRIES = 200

  private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
  val entries: StateFlow<List<DebugLogEntry>> = _entries

  fun log(entry: DebugLogEntry) {
    _entries.update { current -> (listOf(entry) + current).take(MAX_ENTRIES) }
  }

  fun clear() {
    _entries.value = emptyList()
  }
}
