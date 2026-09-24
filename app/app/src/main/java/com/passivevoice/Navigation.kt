package com.passivevoice

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.passivevoice.ui.debug.DebugScreen
import com.passivevoice.ui.main.MainScreen
import com.passivevoice.ui.speech.SpeechSettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(onItemClick = { navKey -> backStack.add(navKey) }, modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
        entry<SpeechSettings> { SpeechSettingsScreen(modifier = Modifier.safeDrawingPadding().padding(16.dp)) }
        entry<DebugLog> { DebugScreen(modifier = Modifier.safeDrawingPadding().padding(16.dp)) }
      },
  )
}
