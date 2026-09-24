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
import com.passivevoice.settings.AudioStream
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
    // Same ranges as Android's own text-to-speech settings; each step is 0.1.
    val multiplierJumps = listOf(0.5f to "0.5", 1f to "1")
    LabeledSlider(
      "Speech rate", settings.speechRate, 0.1f..6f, steps = 58, valueText = ::multiplier,
      onValueChange = viewModel::setSpeechRate, bigSteps = multiplierJumps,
    )
    LabeledSlider(
      "Pitch", settings.pitch, 0.2f..4f, steps = 37, valueText = ::multiplier,
      onValueChange = viewModel::setPitch, bigSteps = multiplierJumps,
    )
    LabeledSlider(
      "Volume", settings.volume, 0f..3f, steps = 29, valueText = ::volumeText,
      onValueChange = viewModel::setVolume, bigSteps = listOf(0.5f to "50 percent"),
    )
    Text("Above 100 percent, speech is amplified beyond the channel's normal maximum. Very high boosts can distort.")
    OutlinedButton(onClick = viewModel::playSample) { Text("Play sample") }

    RadioGroup(
      title = "Audio channel",
      options = AudioStream.entries,
      selected = settings.audioStream,
      labelFor = { it.label },
      onSelect = viewModel::setAudioStream,
    )

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

private fun volumeText(value: Float): String {
  val percent = (value * 10).roundToInt() * 10
  return if (percent > 100) "$percent percent, boosted" else "$percent percent"
}
