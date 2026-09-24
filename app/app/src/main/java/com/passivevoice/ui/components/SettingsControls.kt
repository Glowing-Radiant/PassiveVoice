package com.passivevoice.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Every control here is one TalkBack stop that reads its label, role and state together.

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.headlineSmall, modifier = modifier.semantics { heading() })
}

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, summary: String? = null) {
  Row(
    modifier =
      modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = null)
    LabelWithSummary(label, summary, Modifier.padding(start = 12.dp))
  }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Switch(checked = checked, onCheckedChange = null)
    Text(label, modifier = Modifier.padding(start = 12.dp))
  }
}

@Composable
fun <T> RadioGroup(title: String, options: List<T>, selected: T, labelFor: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.selectableGroup()) {
    SectionHeading(title)
    options.forEach { option -> RadioRow(label = labelFor(option), selected = option == selected, onSelect = { onSelect(option) }) }
  }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Text(label, modifier = Modifier.padding(start = 12.dp))
  }
}

/**
 * Saves only when the drag (or TalkBack adjustment) finishes, and announces the slider by name
 * with a human-readable value instead of a bare percentage. [bigSteps] become TalkBack actions
 * ("Increase by 1") so a wide range doesn't take dozens of single-step swipes.
 */
@Composable
fun LabeledSlider(
  title: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  steps: Int,
  valueText: (Float) -> String,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  bigSteps: List<Pair<Float, String>> = emptyList(),
) {
  var current by remember(value) { mutableFloatStateOf(value) }
  val jumpActions =
    bigSteps.flatMap { (amount, amountText) ->
      listOf(1f to "Increase by $amountText", -1f to "Decrease by $amountText").map { (sign, label) ->
        CustomAccessibilityAction(label) {
          // Snap to the 0.1 grid all our sliders use, so repeated jumps don't drift off the steps.
          current = ((current + sign * amount) * 10).roundToInt().div(10f).coerceIn(range.start, range.endInclusive)
          onValueChange(current)
          true
        }
      }
    }
  Column(modifier) {
    Text("$title: ${valueText(current)}", style = MaterialTheme.typography.titleMedium)
    Slider(
      value = current,
      onValueChange = { current = it },
      onValueChangeFinished = { onValueChange(current) },
      valueRange = range,
      steps = steps,
      modifier =
        Modifier.semantics {
          contentDescription = title
          stateDescription = valueText(current)
          if (jumpActions.isNotEmpty()) customActions = jumpActions
        },
    )
  }
}

@Composable
private fun LabelWithSummary(label: String, summary: String?, modifier: Modifier = Modifier) {
  Column(modifier) {
    Text(label)
    if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
