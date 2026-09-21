package com.example.openruncoach.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.ui.common.coachingActionLabel
import com.example.openruncoach.ui.components.EmptyState
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing

@Composable
fun CoachDetailScreen(runId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val viewModel: CoachDetailViewModel = viewModel(key = "coach-$runId") {
    val database = OpenRunCoachDatabase.getInstance(context)
    val settingsRepository = RunSettingsRepository(context)
    CoachDetailViewModel(
      runId,
      RunRepository(database.runDao(), HealthConnectRepository(context), settingsRepository),
      RunFeedbackRepository(database),
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    TextButton(onClick = onBack) { Text("‹ Back") }
    Text("Coach Analysis", style = MaterialTheme.typography.headlineMedium)
    when {
      state.isLoading -> EmptyState("Loading", "Loading coach analysis…")
      state.errorMessage != null -> EmptyState("Analysis unavailable", state.errorMessage!!)
      state.decision == null -> EmptyState("No analysis", "Analysis is available after feedback on the latest canonical run.")
      else -> Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
          Text(coachingActionLabel(state.decision!!.action), style = MaterialTheme.typography.headlineSmall)
          Text("Score ${state.decision!!.score}", style = MaterialTheme.typography.labelLarge)
          state.decision!!.reasons.forEach { reason -> Text("✓ $reason") }
        }
      }
    }
  }
}
