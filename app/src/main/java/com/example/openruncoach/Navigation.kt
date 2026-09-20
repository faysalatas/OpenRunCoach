package com.example.openruncoach

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.openruncoach.ui.health.HealthConnectScreen
import com.example.openruncoach.ui.health.RunDetailScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          HealthConnectScreen(
            onRunClick = { runId -> backStack.add(RunDetail(runId)) },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
        entry<RunDetail> { key ->
          RunDetailScreen(
            runId = key.runId,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
          )
        }
      },
  )
}
