package com.passivevoice.ui.debug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.passivevoice.debug.DebugEventBus
import com.passivevoice.debug.DebugLogEntry
import com.passivevoice.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebugScreenViewModel(application: Application) : AndroidViewModel(application) {
  private val settingsRepository = SettingsRepository(application)

  val entries: StateFlow<List<DebugLogEntry>> = DebugEventBus.entries

  val loggingEnabled: StateFlow<Boolean> =
    settingsRepository.settings.map { it.debugLoggingEnabled }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  fun setLoggingEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setDebugLoggingEnabled(enabled) }
  }

  fun clear() = DebugEventBus.clear()
}
