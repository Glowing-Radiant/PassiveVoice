package com.passivevoice.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.passivevoice.ui.components.ScreenTitle

@Composable
fun AppListScreen(onAppClick: (String) -> Unit, modifier: Modifier = Modifier, viewModel: AppListViewModel = viewModel()) {
  val items by viewModel.items.collectAsStateWithLifecycle()
  val query by viewModel.query.collectAsStateWithLifecycle()

  Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    ScreenTitle("Apps")
    Text("Choose how much PassiveVoice says in each app, or turn it off in sensitive apps such as banking.")
    OutlinedTextField(
      value = query,
      onValueChange = viewModel::setQuery,
      label = { Text("Search apps") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    val list = items
    when {
      list == null -> Text("Loading apps")
      list.isEmpty() -> Text("No apps match.", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
      else ->
        LazyColumn {
          items(list, key = { it.app.packageName }) { item ->
            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 56.dp)
                  .clickable(onClickLabel = "configure") { onAppClick(item.app.packageName) }
                  .padding(vertical = 8.dp)
            ) {
              Text(item.app.label, style = MaterialTheme.typography.bodyLarge)
              Text(item.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
          }
        }
    }
  }
}
