package com.passivevoice.ui.apps

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.passivevoice.settings.Verbosity
import com.passivevoice.ui.components.CheckboxRow
import com.passivevoice.ui.components.RadioGroup
import com.passivevoice.ui.components.ScreenTitle

@Composable
fun AppProfileScreen(packageName: String, modifier: Modifier = Modifier) {
  val application = LocalContext.current.applicationContext as Application
  val viewModel: AppProfileViewModel = viewModel(key = packageName) { AppProfileViewModel(application, packageName) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val profile = state.profile

  Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    ScreenTitle(viewModel.appLabel)
    Text(packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    CheckboxRow(
      label = "Narrate this app",
      checked = profile.narrationEnabled,
      onCheckedChange = viewModel::setNarrationEnabled,
      summary = "Turn off for banking or other sensitive apps.",
    )

    if (profile.narrationEnabled) {
      HorizontalDivider()
      RadioGroup(
        title = "Verbosity",
        options = listOf(null) + Verbosity.entries,
        selected = profile.verbosity,
        labelFor = { if (it == null) "Use default (${verbosityName(state.globalVerbosity)})" else verbosityName(it) },
        onSelect = viewModel::setVerbosity,
      )

      HorizontalDivider()
      RadioGroup(
        title = "Scroll narration",
        options = listOf(null, true, false),
        selected = profile.scrollNarration,
        labelFor = {
          when (it) {
            null -> "Use default (${if (state.globalScrollNarration) "on" else "off"})"
            true -> "On"
            false -> "Off"
          }
        },
        onSelect = viewModel::setScrollNarration,
      )
    }

    if (!profile.isDefault) {
      HorizontalDivider()
      OutlinedButton(onClick = { viewModel.reset() }) { Text("Reset to defaults") }
    }
  }
}
