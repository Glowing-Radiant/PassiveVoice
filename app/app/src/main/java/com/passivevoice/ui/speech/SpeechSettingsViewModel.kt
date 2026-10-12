package com.passivevoice.ui.speech

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.passivevoice.settings.AudioStream
import com.passivevoice.settings.PassiveVoiceSettings
import com.passivevoice.settings.SettingsRepository
import com.passivevoice.settings.SpeechQueueMode
import com.passivevoice.settings.Verbosity
import com.passivevoice.speech.SpeechManager
import com.passivevoice.speech.SpeechPriority
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

  private var preview: SpeechManager? = null
  private var previewEngine: String? = null

  fun setVerbosity(verbosity: Verbosity) = viewModelScope.launch { settingsRepository.setVerbosity(verbosity) }

  fun setSpeechRate(rate: Float) = viewModelScope.launch { settingsRepository.setSpeechRate(rate) }

  fun setPitch(pitch: Float) = viewModelScope.launch { settingsRepository.setPitch(pitch) }

  fun setVolume(volume: Float) = viewModelScope.launch { settingsRepository.setVolume(volume) }

  fun setAudioStream(stream: AudioStream) = viewModelScope.launch { settingsRepository.setAudioStream(stream) }

  fun setQueueMode(mode: SpeechQueueMode) = viewModelScope.launch { settingsRepository.setQueueMode(mode) }

  fun setTtsEngine(packageName: String?) = viewModelScope.launch { settingsRepository.setTtsEngine(packageName) }

  fun setSpeechDelayMs(delayMs: Int) = viewModelScope.launch { settingsRepository.setSpeechDelayMs(delayMs) }

  fun setNarrateClicks(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNarrateClicks(enabled) }

  fun setNarrateToggles(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNarrateToggles(enabled) }

  fun setNarrateSelections(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNarrateSelections(enabled) }

  fun setNarrateScroll(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNarrateScroll(enabled) }

  fun setNarrateWindowChanges(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNarrateWindowChanges(enabled) }

  fun setNarrateTypedCharacters(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNarrateTypedCharacters(enabled) }

  fun setNarrateTypedWords(enabled: Boolean) = viewModelScope.launch { settingsRepository.setNarrateTypedWords(enabled) }

  fun setAppAdaptersEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAppAdaptersEnabled(enabled) }

  /** Speaks a sample with the current engine, rate, pitch and volume. */
  fun playSample() {
    val current = settings.value
    val speak = {
      preview?.setVoiceParams(current.speechRate, current.pitch)
      preview?.setOutput(current.audioStream, current.volume)
      preview?.speak(SAMPLE_TEXT, SpeechPriority.HIGH, SpeechQueueMode.INTERRUPT)
    }
    val existing = preview
    if (existing != null && previewEngine == current.ttsEngine) {
      speak()
    } else {
      existing?.shutdown()
      previewEngine = current.ttsEngine
      preview = SpeechManager(getApplication()).also { it.initialize(current.ttsEngine) { speak() } }
    }
  }

  override fun onCleared() {
    preview?.shutdown()
  }

  private companion object {
    const val SAMPLE_TEXT = "Liked. Wi-Fi on. Scrolled down."
  }
}
