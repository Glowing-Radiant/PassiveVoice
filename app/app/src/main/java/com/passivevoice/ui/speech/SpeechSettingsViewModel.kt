package com.passivevoice.ui.speech

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.passivevoice.settings.PassiveVoiceSettings
import com.passivevoice.settings.SettingsRepository
import com.passivevoice.settings.SpeechQueueMode
import com.passivevoice.settings.Verbosity
import com.passivevoice.speech.TtsEngineInfo
import com.passivevoice.speech.TtsEngineManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpeechSettingsViewModel(application: Application) : AndroidViewModel(application) {
  private val settingsRepository = SettingsRepository(application)

  val settings: StateFlow<PassiveVoiceSettings> =
    settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PassiveVoiceSettings())

  val availableEngines: List<TtsEngineInfo> = TtsEngineManager.listAvailableEngines(application)

  fun setVerbosity(verbosity: Verbosity) = viewModelScope.launch { settingsRepository.setVerbosity(verbosity) }

  fun setSpeechRate(rate: Float) = viewModelScope.launch { settingsRepository.setSpeechRate(rate) }

  fun setPitch(pitch: Float) = viewModelScope.launch { settingsRepository.setPitch(pitch) }

  fun setVolume(volume: Float) = viewModelScope.launch { settingsRepository.setVolume(volume) }

  fun setQueueMode(mode: SpeechQueueMode) = viewModelScope.launch { settingsRepository.setQueueMode(mode) }

  fun setTtsEngine(packageName: String?) = viewModelScope.launch { settingsRepository.setTtsEngine(packageName) }
}
