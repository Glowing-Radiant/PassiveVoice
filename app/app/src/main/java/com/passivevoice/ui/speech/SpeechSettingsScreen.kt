package com.passivevoice.ui.speech

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.passivevoice.settings.SpeechQueueMode
import com.passivevoice.settings.Verbosity
import com.passivevoice.ui.components.CheckboxRow
import com.passivevoice.ui.components.LabeledSlider
import com.passivevoice.ui.components.RadioGroup
import com.passivevoice.ui.components.RadioRow
import com.passivevoice.ui.components.ScreenTitle
import com.passivevoice.ui.components.SectionHeading
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SpeechSettingsScreen(modifier: Modifier = Modifier, viewModel: SpeechSettingsViewModel = viewModel()) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()

  Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    ScreenTitle("Speech settings")

    RadioGroup(
      title = "Verbosity",
      options = Verbosity.entries,
      selected = settings.verbosity,
      labelFor = {
        when (it) {
          Verbosity.MINIMAL -> "Minimal"
          Verbosity.STANDARD -> "Standard"
          Verbosity.DETAILED -> "Detailed: adds roles and list positions"
        }
      },
      onSelect = viewModel::setVerbosity,
    )

    HorizontalDivider()
    SectionHeading("Voice")
    LabeledSlider("Speech rate", settings.speechRate, 0.5f..2f, steps = 14, valueText = ::multiplier, onValueChange = viewModel::setSpeechRate)
    LabeledSlider("Pitch", settings.pitch, 0.5f..2f, steps = 14, valueText = ::multiplier, onValueChange = viewModel::setPitch)
    LabeledSlider("Volume", settings.volume, 0f..1f, steps = 9, valueText = { "${(it * 100).roundToInt()} percent" }, onValueChange = viewModel::setVolume)
    OutlinedButton(onClick = viewModel::playSample) { Text("Play sample") }

    HorizontalDivider()
    SectionHeading("Narrate")
    CheckboxRow("Buttons and links", settings.narrateClicks, viewModel::setNarrateClicks)
    CheckboxRow("Switches and checkboxes", settings.narrateToggles, viewModel::setNarrateToggles)
    CheckboxRow("Tabs and selections", settings.narrateSelections, viewModel::setNarrateSelections)
    CheckboxRow("Scrolling", settings.narrateScroll, viewModel::setNarrateScroll)
    CheckboxRow("Screens and menus opening", settings.narrateWindowChanges, viewModel::setNarrateWindowChanges)

    HorizontalDivider()
    SectionHeading("Timing")
    LabeledSlider(
      title = "Delay after a tap",
      value = settings.speechDelayMs.toFloat(),
      range = 0f..1000f,
      steps = 19,
      valueText = { "${(it / 50).roundToInt() * 50} milliseconds" },
      onValueChange = { viewModel.setSpeechDelayMs((it / 50).roundToInt() * 50) },
    )
    Text("A short delay lets PassiveVoice hear what a tap changed, such as Follow becoming Following.")

    RadioGroup(
      title = "When new speech arrives",
      options = SpeechQueueMode.entries,
      selected = settings.queueMode,
      labelFor = {
        when (it) {
          SpeechQueueMode.INTERRUPT -> "Replace what's being said"
          SpeechQueueMode.QUEUE -> "Wait for it to finish"
        }
      },
      onSelect = viewModel::setQueueMode,
    )

    HorizontalDivider()
    SectionHeading("Text-to-speech engine")
    Column(Modifier.fillMaxWidth().selectableGroup()) {
      RadioRow(label = "System default", selected = settings.ttsEngine == null, onSelect = { viewModel.setTtsEngine(null) })
      viewModel.availableEngines.forEach { engine ->
        RadioRow(label = engine.label, selected = settings.ttsEngine == engine.packageName, onSelect = { viewModel.setTtsEngine(engine.packageName) })
      }
    }
  }
}

private fun multiplier(value: Float): String = String.format(Locale.US, "%.1f times", value)
