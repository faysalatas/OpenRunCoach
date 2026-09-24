package com.example.openruncoach.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.StructuredWorkoutRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing

@Composable
fun WorkoutDetailScreen(workoutId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val viewModel: WorkoutDetailViewModel = viewModel(key = workoutId) {
    WorkoutDetailViewModel(workoutId, StructuredWorkoutRepository(OpenRunCoachDatabase.getInstance(context)))
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  Column(
    modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    TextButton(onClick = onBack) { Text("‹ Back") }
    Text("Planned Workout", style = MaterialTheme.typography.headlineMedium)
    when {
      state.isLoading -> Text("Loading workout…")
      state.workout != null -> StructuredWorkoutDetail(state.workout!!)
      else -> Text(state.errorMessage ?: "Workout not found.", color = MaterialTheme.colorScheme.error)
    }
  }
}
