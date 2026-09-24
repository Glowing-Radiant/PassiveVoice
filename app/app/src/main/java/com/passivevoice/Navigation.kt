package com.passivevoice

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.passivevoice.ui.apps.AppListScreen
import com.passivevoice.ui.apps.AppProfileScreen
import com.passivevoice.ui.debug.DebugScreen
import com.passivevoice.ui.main.MainScreen
import com.passivevoice.ui.speech.SpeechSettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  val screenModifier = Modifier.safeDrawingPadding().padding(16.dp)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> { MainScreen(onItemClick = { navKey -> backStack.add(navKey) }, modifier = screenModifier) }
        entry<SpeechSettings> { SpeechSettingsScreen(modifier = screenModifier) }
        entry<AppList> { AppListScreen(onAppClick = { packageName -> backStack.add(AppSettings(packageName)) }, modifier = screenModifier) }
        entry<AppSettings> { key -> AppProfileScreen(packageName = key.packageName, modifier = screenModifier) }
        entry<DebugLog> { DebugScreen(modifier = screenModifier) }
      },
  )
}
