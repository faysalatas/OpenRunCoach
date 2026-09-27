package com.example.openruncoach.ui.workout

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.StructuredWorkoutRepository
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.averagePaceSecondsPerKm
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.RpeTarget
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutBlock
import com.example.openruncoach.domain.workout.WorkoutDescriptionFormatter
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import com.example.openruncoach.domain.workout.WorkoutExecutionStatus
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing

@Composable
fun WorkoutDetailScreen(workoutId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: WorkoutDetailViewModel = viewModel(key = workoutId) {
    WorkoutDetailViewModel(workoutId, StructuredWorkoutRepository(environment.database))
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
      state.workout != null -> {
        state.executionResult?.let { result ->
          ExecutionCard(state.workout!!, result, state.actualRun, state.feedback)
        }
        StructuredWorkoutDetail(state.workout!!)
      }
      else -> Text(state.errorMessage ?: "Workout not found.", color = MaterialTheme.colorScheme.error)
    }
  }
}

@Composable
private fun ExecutionCard(
  workout: StructuredWorkout,
  result: WorkoutExecutionResult,
  actualRun: RunSummary?,
  feedback: RunFeedback?,
) {
  val estimate = WorkoutEstimateCalculator.calculate(workout)
  val targets = workout.blocks.flatMap(WorkoutBlock::segments)
    .flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }
  val plannedPace = targets.filterIsInstance<PaceTarget>().firstOrNull()
  val plannedRpe = targets.filterIsInstance<RpeTarget>().firstOrNull()
  val actualDistance = actualRun?.distanceMeters ?: result.actualDistanceMeters
  val actualDuration = actualRun?.durationSeconds ?: result.actualDurationSeconds
  val actualPace = actualRun?.averagePaceSecondsPerKm
    ?: averagePaceSecondsPerKm(actualDuration, actualDistance, null)

  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("Execution", style = MaterialTheme.typography.titleLarge)
      Text(
        result.status.executionLabel(),
        color = if (result.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.titleMedium,
      )
      Text(result.explanation, style = MaterialTheme.typography.bodySmall)
      Text("Actual", style = MaterialTheme.typography.titleMedium)
      actualDistance?.let { ExecutionValue("Distance", formatDistance(it)) }
      ExecutionValue("Duration", formatDuration(actualDuration))
      actualPace?.let { ExecutionValue("Average pace", formatPace(it)) }
      Text("Planned", style = MaterialTheme.typography.titleMedium)
      estimate.estimatedTotalDistanceMeters?.let { ExecutionValue("Distance", formatDistance(it)) }
      plannedPace?.let { ExecutionValue("Target pace", WorkoutDescriptionFormatter.paceRange(it)) }
      plannedRpe?.let { ExecutionValue("Effort", "RPE ${it.minimum}–${it.maximum}") }
      feedback?.let {
        Text("Checkout", style = MaterialTheme.typography.titleMedium)
        ExecutionValue("RPE", "${it.rpe}/10")
        ExecutionValue("Pain", it.painLevel.displayName())
      }
    }
  }
}

@Composable
private fun ExecutionValue(label: String, value: String) {
  androidx.compose.foundation.layout.Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label)
    Text(value)
  }
}

private fun WorkoutExecutionStatus.executionLabel() = when (this) {
  WorkoutExecutionStatus.COMPLETED_AS_PLANNED -> "✓ Completed as planned"
  WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION -> "✓ Completed with minor deviation"
  WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION -> "✓ Completed with prescription deviation"
  WorkoutExecutionStatus.PARTIALLY_COMPLETED -> "Partially completed"
  WorkoutExecutionStatus.ABORTED -> "Stopped"
}

private fun PainLevel.displayName() = when (this) {
  PainLevel.NONE -> "No pain"
  PainLevel.MILD -> "Mild pain"
  PainLevel.SIGNIFICANT -> "Significant pain"
}
