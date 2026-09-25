package com.passivevoice.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.passivevoice.debug.DebugLogEntry
import com.passivevoice.ui.components.ScreenTitle
import com.passivevoice.ui.components.SwitchRow
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DebugScreen(modifier: Modifier = Modifier, viewModel: DebugScreenViewModel = viewModel()) {
  val entries by viewModel.entries.collectAsStateWithLifecycle()
  val loggingEnabled by viewModel.loggingEnabled.collectAsStateWithLifecycle()

  DebugScreenContent(
    entries = entries,
    loggingEnabled = loggingEnabled,
    onLoggingEnabledChange = viewModel::setLoggingEnabled,
    onClear = viewModel::clear,
    modifier = modifier,
  )
}

@Composable
internal fun DebugScreenContent(
  entries: List<DebugLogEntry>,
  loggingEnabled: Boolean,
  onLoggingEnabledChange: (Boolean) -> Unit,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    ScreenTitle("Live Event Monitor")
    Text(
      "Raw accessibility events, how PassiveVoice interpreted them, and what (if anything) it spoke. Kept in memory only.",
      style = MaterialTheme.typography.bodySmall,
    )

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      SwitchRow(label = "Record events", checked = loggingEnabled, onCheckedChange = onLoggingEnabledChange)
      OutlinedButton(onClick = onClear) { Text("Clear") }
    }

    HorizontalDivider()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      items(entries) { entry -> DebugEntryRow(entry) }
    }
  }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

@Composable
private fun DebugEntryRow(entry: DebugLogEntry) {
  // One TalkBack stop per entry rather than one per line.
  Card(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("${timeFormat.format(entry.timestamp)}  ${entry.eventType}", style = MaterialTheme.typography.labelMedium)
      Text(entry.packageName ?: "—", style = MaterialTheme.typography.bodySmall)
      if (entry.className != null) Text(entry.className, style = MaterialTheme.typography.bodySmall)
      if (entry.viewId != null) Text("ID: ${entry.viewId}", style = MaterialTheme.typography.bodySmall)
      if (!entry.text.isNullOrBlank()) Text("Text: ${entry.text}", style = MaterialTheme.typography.bodySmall)
      Text("Interpretation: ${entry.interpretation ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
      when {
        entry.speech != null ->
          Text("Speech: \"${entry.speech}\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        entry.suppressedReason != null ->
          Text("Suppressed: ${entry.suppressedReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }
    }
  }
}
