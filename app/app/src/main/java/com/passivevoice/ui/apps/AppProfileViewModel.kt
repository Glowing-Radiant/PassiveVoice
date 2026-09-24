package com.passivevoice.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.passivevoice.settings.AppProfile
import com.passivevoice.settings.SettingsRepository
import com.passivevoice.settings.Verbosity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppProfileUiState(val profile: AppProfile, val globalVerbosity: Verbosity, val globalScrollNarration: Boolean)

class AppProfileViewModel(application: Application, private val packageName: String) : AndroidViewModel(application) {
  private val settingsRepository = SettingsRepository(application)

  val appLabel: String = InstalledApps.labelFor(application, packageName)

  val uiState: StateFlow<AppProfileUiState> =
    settingsRepository.settings
      .map { AppProfileUiState(it.appProfiles[packageName] ?: AppProfile(), it.verbosity, it.narrateScroll) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppProfileUiState(AppProfile(), Verbosity.STANDARD, true))

  fun setNarrationEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAppNarrationEnabled(packageName, enabled) }

  fun setVerbosity(verbosity: Verbosity?) = viewModelScope.launch { settingsRepository.setAppVerbosity(packageName, verbosity) }

  fun setScrollNarration(enabled: Boolean?) = viewModelScope.launch { settingsRepository.setAppScrollNarration(packageName, enabled) }

  fun reset() = viewModelScope.launch { settingsRepository.resetApp(packageName) }
}
