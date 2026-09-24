package com.passivevoice.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.passivevoice.settings.AppProfile
import com.passivevoice.settings.SettingsRepository
import com.passivevoice.settings.Verbosity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppListItem(val app: AppEntry, val summary: String, val isCustomized: Boolean)

class AppListViewModel(application: Application) : AndroidViewModel(application) {
  private val settingsRepository = SettingsRepository(application)
  private val apps = MutableStateFlow<List<AppEntry>?>(null)
  private val _query = MutableStateFlow("")
  val query: StateFlow<String> = _query

  /** Null while installed apps are still loading. Customized apps are listed first. */
  val items: StateFlow<List<AppListItem>?> =
    combine(apps, settingsRepository.settings, _query) { apps, settings, query ->
        apps
          ?.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
          ?.map { app ->
            val profile = settings.appProfiles[app.packageName]
            AppListItem(app, summaryFor(profile), isCustomized = profile != null)
          }
          ?.sortedByDescending { it.isCustomized }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  init {
    viewModelScope.launch { apps.value = withContext(Dispatchers.IO) { InstalledApps.launchable(application) } }
  }

  fun setQuery(query: String) {
    _query.value = query
  }

  private fun summaryFor(profile: AppProfile?): String =
    when {
      profile == null -> "Default"
      !profile.narrationEnabled -> "Narration off"
      else ->
        listOfNotNull(
            profile.verbosity?.let { "${verbosityName(it)} speech" },
            profile.scrollNarration?.let { if (it) "Scrolling on" else "Scrolling off" },
          )
          .joinToString(", ")
    }
}

internal fun verbosityName(verbosity: Verbosity): String = verbosity.name.lowercase().replaceFirstChar { it.uppercase() }
