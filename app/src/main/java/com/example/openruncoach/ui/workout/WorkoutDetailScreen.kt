package com.example.openruncoach.ui.workout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.data.StructuredWorkoutRepository
import com.example.openruncoach.domain.workout.GuidanceConfidence
import com.example.openruncoach.domain.workout.NoIntensityTarget
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.RepeatBlock
import com.example.openruncoach.domain.workout.RpeTarget
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutBlock
import com.example.openruncoach.domain.workout.WorkoutDescriptionFormatter
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutExecutionStatus
import com.example.openruncoach.domain.workout.WorkoutSegment
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.common.formatUserDate
import com.example.openruncoach.ui.components.OpenRunCard
import com.example.openruncoach.ui.components.OpenRunCompactMetrics
import com.example.openruncoach.ui.components.OpenRunEvidenceRow
import com.example.openruncoach.ui.components.OpenRunMetricItem
import com.example.openruncoach.ui.components.OpenRunSection
import com.example.openruncoach.ui.components.OpenRunStatusChip
import com.example.openruncoach.ui.components.OpenRunStatusTone
import com.example.openruncoach.ui.components.OpenRunTabs
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.theme.OpenRunSpacing

private enum class PlannedWorkoutTab { OVERVIEW, STRUCTURE, COACH }

@Composable
fun WorkoutDetailScreen(workoutId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: WorkoutDetailViewModel = viewModel(key = workoutId) {
    WorkoutDetailViewModel(workoutId, StructuredWorkoutRepository(environment.database))
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var selectedTab by rememberSaveable(workoutId) { mutableStateOf(PlannedWorkoutTab.OVERVIEW) }

  Column(
    modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Comfortable),
  ) {
    when {
      state.isLoading -> Text("Loading workout…", Modifier.padding(ScreenPadding))
      state.workout == null -> Column(Modifier.padding(ScreenPadding)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text(state.errorMessage ?: "Workout not found.", color = MaterialTheme.colorScheme.error)
      }
      else -> {
        val workout = state.workout!!
        PlannedWorkoutHero(workout, state.executionResult?.status, onBack)
        Column(
          Modifier.padding(horizontal = ScreenPadding, vertical = OpenRunSpacing.Micro),
          verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Comfortable),
        ) {
          OpenRunTabs(
            tabs = listOf("Overview", "Structure", "Coach"),
            selectedIndex = selectedTab.ordinal,
            onSelected = { selectedTab = PlannedWorkoutTab.entries[it] },
            distributeEvenly = true,
          )
          when (selectedTab) {
            PlannedWorkoutTab.OVERVIEW -> PlannedOverview(workout, state)
            PlannedWorkoutTab.STRUCTURE -> PlannedStructure(workout)
            PlannedWorkoutTab.COACH -> PlannedCoach(workout)
          }
        }
      }
    }
  }
}

@Composable
private fun PlannedWorkoutHero(
  workout: StructuredWorkout,
  executionStatus: WorkoutExecutionStatus?,
  onBack: () -> Unit,
) {
  Box(Modifier.fillMaxWidth().height(210.dp)) {
    workout.type.heroImageResource()?.let { resource ->
      Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = Modifier.matchParentSize(),
        contentScale = ContentScale.Crop,
      )
      Box(Modifier.matchParentSize().background(Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.28f),
        1f to Color.Black.copy(alpha = 0.82f),
      )))
    }
    Column(
      Modifier.matchParentSize().padding(ScreenPadding),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      TextButton(onClick = onBack) { Text("‹ Back", color = Color.White) }
      Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
        Text(workout.name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(formatUserDate(workout.date), style = MaterialTheme.typography.bodyMedium,
          color = Color.White.copy(alpha = 0.78f))
        OpenRunStatusChip(
          text = executionStatus?.detailLabel() ?: "Scheduled",
          tone = if (executionStatus?.isCompletedStatus() == true) OpenRunStatusTone.POSITIVE else OpenRunStatusTone.NEUTRAL,
        )
      }
    }
  }
}

@Composable
private fun PlannedOverview(workout: StructuredWorkout, state: WorkoutDetailUiState) {
  val estimate = WorkoutEstimateCalculator.calculate(workout)
  val pace = workout.allTargets().filterIsInstance<PaceTarget>().distinct().singleOrNull()
  val rpe = workout.allTargets().filterIsInstance<RpeTarget>()
  val minimumDuration = estimate.estimatedMinimumDurationSeconds
  val maximumDuration = estimate.estimatedMaximumDurationSeconds
  val metrics = buildList {
    estimate.estimatedTotalDistanceMeters?.let { add(OpenRunMetricItem(formatDistance(it), "Distance")) }
    pace?.let {
      add(OpenRunMetricItem(WorkoutDescriptionFormatter.paceRange(it).replace(" min/km", " /km"), "Target pace"))
    }
    rpe.maxOfOrNull(RpeTarget::maximum)?.let { add(OpenRunMetricItem(intensityLabel(it), "Intensity")) }
    if (size < 4 && minimumDuration != null && maximumDuration != null) {
      add(OpenRunMetricItem(
        "~${WorkoutDescriptionFormatter.durationRange(minimumDuration, maximumDuration)}",
        "Expected duration",
      ))
    }
  }
  if (metrics.isNotEmpty()) OpenRunCard {
    OpenRunCompactMetrics(metrics, columns = metrics.size.coerceAtMost(4))
  }
  OpenRunSection("Workout description") {
    Text(workout.conciseExplanation())
  }
  state.executionResult?.let { execution ->
    OpenRunSection("Recorded execution") {
      OpenRunEvidenceRow("Status", execution.status.detailLabel())
      execution.actualDistanceMeters?.let { OpenRunEvidenceRow("Actual distance", formatDistance(it)) }
      OpenRunEvidenceRow("Actual duration", formatDuration(execution.actualDurationSeconds))
      state.actualRun?.averagePaceSecondsPerKm?.let {
        OpenRunEvidenceRow("Actual average pace", formatPace(it).replace(" min/km", " /km"))
      }
    }
  }
}

@Composable
private fun PlannedStructure(workout: StructuredWorkout) {
  Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Standard)) {
    workout.blocks.sortedBy(WorkoutBlock::order).forEach { block ->
      OpenRunCard {
        if (block is RepeatBlock) {
          Text(
            "${block.repetitions} × ${block.label ?: block.segments.firstOrNull()?.name.orEmpty()}",
            style = MaterialTheme.typography.titleMedium,
          )
        } else {
          Text(block.label ?: block.segments.firstOrNull()?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
        }
        block.segments.sortedBy(WorkoutSegment::order).forEach { segment ->
          StructureSegment(segment)
        }
      }
    }
  }
}

@Composable
private fun StructureSegment(segment: WorkoutSegment) {
  val details = buildList {
    add(WorkoutDescriptionFormatter.completion(segment.completion))
    if (segment.primaryIntensity !is NoIntensityTarget) {
      add(WorkoutDescriptionFormatter.intensity(segment.primaryIntensity).replace(" min/km", " /km"))
    }
    segment.secondaryIntensities.forEach {
      add(WorkoutDescriptionFormatter.intensity(it).replace(" min/km", " /km"))
    }
  }
  Column(
    Modifier.fillMaxWidth().padding(top = OpenRunSpacing.Small),
    verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro),
  ) {
    Text(segment.name)
    Text(details.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun PlannedCoach(workout: StructuredWorkout) {
  OpenRunSection("Why this workout?") {
    Text(workout.conciseExplanation())
    Text(
      WorkoutDescriptionFormatter.purpose(workout.purpose),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
    )
  }
  val paceConfidence = workout.allTargets().filterIsInstance<PaceTarget>().map(PaceTarget::confidence).firstOrNull()
  paceConfidence?.let {
    OpenRunSection("Prescription confidence") {
      OpenRunEvidenceRow("Pace guidance", it.confidenceLabel())
    }
  }
  if (workout.cues.isNotEmpty()) {
    OpenRunSection("Coach cues") {
      workout.cues.take(4).forEach { cue -> Text("• $cue") }
    }
  }
}

private fun StructuredWorkout.allTargets() = blocks.flatMap(WorkoutBlock::segments)
  .flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }

private fun StructuredWorkout.conciseExplanation(): String {
  val normalized = explanation.replace('\n', ' ').trim()
  if (normalized.length <= 260) return normalized
  return normalized.take(257).trimEnd() + "…"
}

private fun intensityLabel(maximumRpe: Int): String = when (maximumRpe) {
  in 1..4 -> "Easy"
  in 5..6 -> "Moderate"
  in 7..8 -> "Hard"
  else -> "Very hard"
}

private fun GuidanceConfidence.confidenceLabel(): String = when (this) {
  GuidanceConfidence.LOW -> "Low"
  GuidanceConfidence.MEDIUM -> "Medium"
  GuidanceConfidence.HIGH -> "High"
  GuidanceConfidence.UNCALIBRATED -> "Uncalibrated"
}

private fun WorkoutExecutionStatus.detailLabel(): String = when (this) {
  WorkoutExecutionStatus.COMPLETED_AS_PLANNED -> "Completed as planned"
  WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION -> "Completed with minor deviation"
  WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION -> "Completed with prescription deviation"
  WorkoutExecutionStatus.PARTIALLY_COMPLETED -> "Partially completed"
  WorkoutExecutionStatus.ABORTED -> "Stopped"
}

private fun WorkoutExecutionStatus.isCompletedStatus(): Boolean = when (this) {
  WorkoutExecutionStatus.COMPLETED_AS_PLANNED,
  WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION,
  WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION -> true
  WorkoutExecutionStatus.PARTIALLY_COMPLETED,
  WorkoutExecutionStatus.ABORTED -> false
}
