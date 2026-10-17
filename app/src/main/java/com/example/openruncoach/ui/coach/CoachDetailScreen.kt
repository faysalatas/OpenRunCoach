package com.example.openruncoach.ui.coach

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.WorkoutResponseRepository
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.SimulationRuntimeTrace
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.workout.EffortResponse
import com.example.openruncoach.domain.workout.PaceResponse
import com.example.openruncoach.domain.workout.PrescriptionAdherence
import com.example.openruncoach.domain.workout.SessionCost
import com.example.openruncoach.domain.workout.VolumeCompletion
import com.example.openruncoach.domain.workout.WorkoutCompletionOutcome
import com.example.openruncoach.domain.workout.WorkoutResponse
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.common.coachingActionLabel
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.OpenRunCard
import com.example.openruncoach.ui.components.OpenRunSection
import com.example.openruncoach.ui.components.OpenRunEvidenceRow
import com.example.openruncoach.ui.components.OpenRunCompactMetrics
import com.example.openruncoach.ui.components.OpenRunMetricItem
import com.example.openruncoach.theme.OpenRunSpacing

@Composable
fun CoachDetailScreen(runId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: CoachDetailViewModel = viewModel(key = "${environment.key}:coach-$runId") {
    val database = environment.database
    val settingsRepository = RunSettingsRepository(context)
    val metricsDao = database.runMetricsDao()
    val workoutResponseRepository = WorkoutResponseRepository(database)
    if (SimulationRuntimeTrace.enabled) {
      SimulationRuntimeTrace.recordCoachAnalysis(
        environmentKey = environment.key,
        database = database,
        metricsDao = metricsDao,
        workoutResponseRepository = workoutResponseRepository,
      )
    }
    CoachDetailViewModel(
      runId,
      RunRepository(
        database.runDao(),
        HealthConnectRepository(context),
        settingsRepository,
        clock = environment.clock,
        postRunReconciler = environment.postRunReconciler(),
      ),
      RunFeedbackRepository(database, environment.clock),
      workoutResponseRepository,
      CoachingEngine(environment.clock),
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  if (SimulationRuntimeTrace.enabled) {
    LaunchedEffect(environment.key, state.response) {
      SimulationRuntimeTrace.recordCoachResponse(
        environmentKey = environment.key,
        response = state.response,
        runId = state.response?.runId,
        continuity = state.response?.continuity?.name,
      )
    }
  }

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Comfortable),
  ) {
    TextButton(onClick = onBack) { Text("‹ Back") }
    Text("Coach Analysis", style = MaterialTheme.typography.headlineMedium)
    when {
      state.isLoading -> Text("Loading coach analysis…")
      state.errorMessage != null -> Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
      state.decision == null -> Text("Analysis is available after feedback on the latest canonical run.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      else -> {
        OpenRunCard {
          Text("Coaching response", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(coachingActionLabel(state.decision!!.action), style = MaterialTheme.typography.headlineSmall)
          state.decision!!.reasons.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        state.response?.let { ResponseAnalysis(it) } ?: OpenRunSection("Available evidence") {
          state.decision!!.reasons.forEach { reason -> Text("• $reason") }
        }
      }
    }
  }
}

@Composable
private fun ResponseAnalysis(response: WorkoutResponse) {
  AnalysisCard("Outcome") {
    AnalysisValue("Completion", response.completion.displayName())
    AnalysisValue("Prescription adherence", response.adherence.displayName())
  }
  AnalysisCard("Actual") {
    OpenRunCompactMetrics(listOf(
      OpenRunMetricItem(formatDistance(response.actualDistanceMeters), "Distance"),
      OpenRunMetricItem(formatDuration(response.actualDurationSeconds), "Duration"),
      OpenRunMetricItem(formatPace(response.actualPaceSecondsPerKm), "Avg. pace"),
    ))
  }
  AnalysisCard("Execution evidence") {
    AnalysisValue("Volume", response.volumeCompletion.displayName())
    AnalysisValue("Effort", response.effortResponse.displayName())
    if (response.paceResponse != PaceResponse.UNKNOWN) AnalysisValue("Pace", response.paceResponse.displayName())
    AnalysisValue("Continuity", response.continuity.displayName())
  }
  AnalysisCard("Planned") {
    response.plannedDistanceMeters?.let { AnalysisValue("Distance", formatDistance(it)) }
    response.plannedDurationSeconds?.let { AnalysisValue("Duration", formatDuration(it)) }
    plannedPace(response)?.let { AnalysisValue("Target pace", it) }
    if (response.plannedRpeMinimum != null && response.plannedRpeMaximum != null) {
      AnalysisValue("Effort", "RPE ${response.plannedRpeMinimum}–${response.plannedRpeMaximum}")
    }
  }
  AnalysisCard("Checkout") {
    AnalysisValue("Session cost", response.sessionCost.displayName())
    AnalysisValue("Interpretation confidence", response.confidence.name.displayName())
    AnalysisValue("RPE", "${response.reportedRpe}/10")
    AnalysisValue("Pain", response.pain.displayName())
  }
  AnalysisCard("Why this matters") {
    response.reasons.forEach { Text("• $it") }
  }
}

@Composable
private fun AnalysisCard(title: String, content: @Composable () -> Unit) {
  OpenRunSection(title) { content() }
}

@Composable
private fun AnalysisValue(label: String, value: String) {
  OpenRunEvidenceRow(label, value)
}

private fun plannedPace(response: WorkoutResponse): String? {
  val minimum = response.plannedPaceMinimumSecondsPerKm ?: return null
  val maximum = response.plannedPaceMaximumSecondsPerKm ?: return null
  return "${paceValue(minimum)}–${paceValue(maximum)} min/km"
}

private fun paceValue(secondsPerKm: Double): String {
  val seconds = secondsPerKm.toLong()
  return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun WorkoutCompletionOutcome.displayName() = when (this) {
  WorkoutCompletionOutcome.COMPLETED -> "Completed"
  WorkoutCompletionOutcome.PARTIAL -> "Partially completed"
  WorkoutCompletionOutcome.ABORTED -> "Stopped"
}

private fun PrescriptionAdherence.displayName() = when (this) {
  PrescriptionAdherence.AS_PLANNED -> "As planned"
  PrescriptionAdherence.MINOR_DEVIATION -> "Minor deviation"
  PrescriptionAdherence.MEANINGFUL_DEVIATION -> "Meaningful deviation"
  PrescriptionAdherence.INSUFFICIENT_EVIDENCE -> "Limited quality-work evidence"
}

private fun VolumeCompletion.displayName() = when (this) {
  VolumeCompletion.MET -> "Target met"
  VolumeCompletion.SUBSTANTIALLY_COMPLETED -> "Substantially completed"
  VolumeCompletion.PARTIAL -> "Partial"
  VolumeCompletion.UNKNOWN -> "Not enough data"
}

private fun EffortResponse.displayName() = when (this) {
  EffortResponse.BELOW_TARGET -> "Below target"
  EffortResponse.ON_TARGET -> "On target"
  EffortResponse.ABOVE_TARGET -> "Higher than planned"
  EffortResponse.UNKNOWN -> "Not enough data"
}

private fun PaceResponse.displayName() = when (this) {
  PaceResponse.FASTER_THAN_TARGET -> "Faster than planned"
  PaceResponse.ON_TARGET -> "Within target"
  PaceResponse.SLOWER_THAN_TARGET -> "Slower than planned"
  PaceResponse.UNKNOWN -> "Not enough data"
}

private fun RunContinuityEvidence.displayName() = when (this) {
  RunContinuityEvidence.CONTINUOUS -> "Continuous"
  RunContinuityEvidence.INTERRUPTED -> "Sustained walking/stopped interval"
  RunContinuityEvidence.UNKNOWN -> "Not enough movement samples"
}

private fun SessionCost.displayName() = name.displayName()

private fun PainLevel.displayName() = when (this) {
  PainLevel.NONE -> "No pain"
  PainLevel.MILD -> "Mild pain"
  PainLevel.SIGNIFICANT -> "Significant pain"
}

private fun String.displayName() = lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
