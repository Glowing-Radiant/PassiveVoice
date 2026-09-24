package com.passivevoice.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.passivevoice.accessibility.PassiveVoiceService
import com.passivevoice.settings.PassiveVoiceSettings
import com.passivevoice.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(val serviceEnabled: Boolean, val settings: PassiveVoiceSettings)

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
  private val settingsRepository = SettingsRepository(application)
  private val serviceEnabled = MutableStateFlow(PassiveVoiceService.isEnabledInSystemSettings(application))

  val uiState: StateFlow<MainUiState> =
    combine(serviceEnabled, settingsRepository.settings) { enabled, settings -> MainUiState(enabled, settings) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState(serviceEnabled.value, PassiveVoiceSettings()))

  /** Call from onResume — the user enables the service in system Settings, outside this process. */
  fun refreshServiceStatus() {
    serviceEnabled.value = PassiveVoiceService.isEnabledInSystemSettings(getApplication())
  }

  fun setAutoMuteWithTalkBack(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setAutoMuteWithTalkBack(enabled) }
  }
}
