package com.passivevoice.ui.main

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.passivevoice.AppList
import com.passivevoice.accessibility.TalkBackDetector
import com.passivevoice.DebugLog
import com.passivevoice.SpeechSettings
import com.passivevoice.settings.PassiveVoiceSettings
import com.passivevoice.theme.PassiveVoiceTheme
import com.passivevoice.ui.components.CheckboxRow
import com.passivevoice.ui.components.SectionHeading

@Composable
fun MainScreen(onItemClick: (NavKey) -> Unit, modifier: Modifier = Modifier, viewModel: MainScreenViewModel = viewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshServiceStatus() }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  MainScreenContent(
    state = state,
    onOpenAccessibilitySettings = { context ->
      context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    },
    onAutoMuteChanged = viewModel::setAutoMuteWithTalkBack,
    onConfigureSpeech = { onItemClick(SpeechSettings) },
    onConfigureApps = { onItemClick(AppList) },
    onOpenDebugLog = { onItemClick(DebugLog) },
    modifier = modifier,
  )
}

@Composable
internal fun MainScreenContent(
  state: MainUiState,
  onOpenAccessibilitySettings: (Context) -> Unit,
  onAutoMuteChanged: (Boolean) -> Unit,
  onConfigureSpeech: () -> Unit,
  onConfigureApps: () -> Unit,
  onOpenDebugLog: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("PassiveVoice", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text("Speaks what your touch just did, without changing how you touch the phone.", style = MaterialTheme.typography.bodyMedium)

    HorizontalDivider()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        if (state.serviceEnabled) "Status: Running" else "Status: Not enabled",
        style = MaterialTheme.typography.titleMedium,
        color = if (state.serviceEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
      )
      Button(onClick = { onOpenAccessibilitySettings(context) }) {
        Text(if (state.serviceEnabled) "Open Accessibility Settings" else "Enable Accessibility Service")
      }
    }

    HorizontalDivider()

    SectionHeading("Screen readers")
    CheckboxRow(
      label = "Automatically mute when a screen reader is active",
      checked = state.settings.autoMuteWithTalkBack,
      onCheckedChange = onAutoMuteChanged,
      summary = "${TalkBackDetector.KNOWN_SCREEN_READER_NAMES.joinToString()}. Keeps the two from talking over each other.",
    )

    HorizontalDivider()

    SectionHeading("Settings")
    Text("Speech: ${state.settings.verbosity.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = onConfigureSpeech) { Text("Configure Speech") }
    OutlinedButton(onClick = onConfigureApps) { Text("Configure Apps") }
    OutlinedButton(onClick = onOpenDebugLog) { Text("Live Event Monitor") }
  }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
  PassiveVoiceTheme {
    MainScreenContent(
      state = MainUiState(serviceEnabled = false, settings = PassiveVoiceSettings()),
      onOpenAccessibilitySettings = {},
      onAutoMuteChanged = {},
      onConfigureSpeech = {},
      onConfigureApps = {},
      onOpenDebugLog = {},
      modifier = Modifier.padding(16.dp),
    )
  }
}
