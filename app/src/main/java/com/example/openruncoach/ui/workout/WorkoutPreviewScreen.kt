package com.example.openruncoach.ui.workout

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.StructuredWorkoutRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.workout.WorkoutDescriptionFormatter
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing

@Composable
fun WorkoutPreviewScreen(
  onBack: () -> Unit,
  onOpenWorkout: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: WorkoutPreviewViewModel = viewModel {
    WorkoutPreviewViewModel(StructuredWorkoutRepository(OpenRunCoachDatabase.getInstance(context)))
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  LaunchedEffect(state.openWorkoutId) {
    state.openWorkoutId?.let {
      onOpenWorkout(it)
      viewModel.consumedNavigation()
    }
  }
  Column(
    modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    TextButton(onClick = onBack) { Text("‹ Back") }
    Text("Workout Model Preview", style = MaterialTheme.typography.headlineMedium)
    Text("Deterministic samples of the shared structured workout model.")
    state.workouts.forEach { workout ->
      Card(Modifier.fillMaxWidth().clickable(enabled = !state.isSaving) { viewModel.open(workout) }) {
        Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
          Text(WorkoutDescriptionFormatter.summary(workout), style = MaterialTheme.typography.titleMedium)
          Text("Purpose: ${WorkoutDescriptionFormatter.purpose(workout.purpose)}", style = MaterialTheme.typography.bodySmall)
        }
      }
    }
    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
}
