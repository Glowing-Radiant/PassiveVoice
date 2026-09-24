package com.passivevoice.ui.speech

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.passivevoice.settings.PassiveVoiceSettings
import com.passivevoice.settings.SpeechQueueMode
import com.passivevoice.settings.Verbosity
import com.passivevoice.speech.TtsEngineInfo

@Composable
fun SpeechSettingsScreen(modifier: Modifier = Modifier, viewModel: SpeechSettingsViewModel = viewModel()) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()

  SpeechSettingsContent(
    settings = settings,
    availableEngines = viewModel.availableEngines,
    onVerbosityChange = viewModel::setVerbosity,
    onRateChange = viewModel::setSpeechRate,
    onPitchChange = viewModel::setPitch,
    onVolumeChange = viewModel::setVolume,
    onQueueModeChange = viewModel::setQueueMode,
    onEngineChange = viewModel::setTtsEngine,
    modifier = modifier,
  )
}

@Composable
internal fun SpeechSettingsContent(
  settings: PassiveVoiceSettings,
  availableEngines: List<TtsEngineInfo>,
  onVerbosityChange: (Verbosity) -> Unit,
  onRateChange: (Float) -> Unit,
  onPitchChange: (Float) -> Unit,
  onVolumeChange: (Float) -> Unit,
  onQueueModeChange: (SpeechQueueMode) -> Unit,
  onEngineChange: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text("Speech settings", style = MaterialTheme.typography.headlineSmall)

    LabeledRadioGroup(
      title = "Verbosity",
      options = Verbosity.entries,
      selected = settings.verbosity,
      labelFor = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
      onSelect = onVerbosityChange,
    )

    HorizontalDivider()

    LabeledSlider(title = "Speech rate", value = settings.speechRate, range = 0.5f..2f, onValueChange = onRateChange)
    LabeledSlider(title = "Pitch", value = settings.pitch, range = 0.5f..2f, onValueChange = onPitchChange)
    LabeledSlider(title = "Volume", value = settings.volume, range = 0f..1f, onValueChange = onVolumeChange)

    HorizontalDivider()

    LabeledRadioGroup(
      title = "Queue mode",
      options = SpeechQueueMode.entries,
      selected = settings.queueMode,
      labelFor = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
      onSelect = onQueueModeChange,
    )

    HorizontalDivider()

    Text("TTS engine", style = MaterialTheme.typography.titleMedium)
    Column {
      RadioRow(label = "System default", selected = settings.ttsEngine == null, onSelect = { onEngineChange(null) })
      availableEngines.forEach { engine ->
        RadioRow(label = engine.label, selected = settings.ttsEngine == engine.packageName, onSelect = { onEngineChange(engine.packageName) })
      }
    }
  }
}

@Composable
private fun <T> LabeledRadioGroup(title: String, options: List<T>, selected: T, labelFor: (T) -> String, onSelect: (T) -> Unit) {
  Column {
    Text(title, style = MaterialTheme.typography.titleMedium)
    options.forEach { option -> RadioRow(label = labelFor(option), selected = option == selected, onSelect = { onSelect(option) }) }
  }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onSelect)
    Text(label)
  }
}

@Composable
private fun LabeledSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
  Column {
    Text("$title: ${"%.2f".format(value)}", style = MaterialTheme.typography.titleMedium)
    Slider(value = value, onValueChange = onValueChange, valueRange = range)
  }
}
