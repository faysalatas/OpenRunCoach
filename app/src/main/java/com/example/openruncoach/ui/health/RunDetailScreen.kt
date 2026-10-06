package com.example.openruncoach.ui.health

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunMetricsRepository
import com.example.openruncoach.data.RunRouteRepository
import com.example.openruncoach.data.RunMetricsAutoRefreshCoordinator
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.GoalCompletionRepository
import com.example.openruncoach.data.TrainingGoalRepository
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.data.WorkoutAssignmentState
import com.example.openruncoach.data.WorkoutResponseRepository
import com.example.openruncoach.data.StructuredWorkoutRepository
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.BuildConfig
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.PossibleDuplicate
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.displaySourceName
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.MetricVerification
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.route.RunSplit
import com.example.openruncoach.domain.route.RunSplitDerivation
import com.example.openruncoach.domain.workout.PaceResponse
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutBlock
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutResponse
import com.example.openruncoach.domain.workout.displayName
import com.example.openruncoach.ui.common.coachingActionLabel
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.OpenRunTabs
import com.example.openruncoach.ui.components.OpenRunCompactMetrics
import com.example.openruncoach.ui.components.OpenRunMetricItem
import com.example.openruncoach.ui.components.OpenRunSection
import com.example.openruncoach.ui.components.OpenRunEvidenceRow
import com.example.openruncoach.ui.components.OpenRunCard
import com.example.openruncoach.ui.components.OpenRunDisclosure
import com.example.openruncoach.ui.components.RunRouteCanvas
import com.example.openruncoach.ui.components.RunRouteCanvasStyle
import com.example.openruncoach.theme.OpenRunSpacing
import java.util.Locale
import java.time.format.DateTimeFormatter

@Composable
fun RunDetailScreen(
  runId: String,
  onBack: () -> Unit,
  onInspectHealthData: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: RunDetailViewModel = viewModel(key = runId) {
    val database = environment.database
    val settingsRepository = RunSettingsRepository(context)
    val profileRepository = environment.profileRepository()
    val metricsRefresher = if (environment.isSimulation) null else {
      RunMetricsAutoRefreshCoordinator.create(context, database, profileRepository)
    }
    RunDetailViewModel(
      runId,
      RunRepository(
        database.runDao(),
        HealthConnectRepository(context),
        settingsRepository,
        clock = environment.clock,
        athleteProfileRepository = profileRepository,
      ),
      RunFeedbackRepository(database, environment.clock, athleteProfileRepository = profileRepository),
      RunMetricsRepository(database.runMetricsDao()),
      RunRouteRepository(database.runRouteDao()),
      metricsRefresher,
      TrainingGoalRepository(database, profileRepository),
      GoalCompletionRepository(database, environment.clock),
      WorkoutExecutionRepository(database, environment.clock, athleteProfileRepository = profileRepository),
      WorkoutResponseRepository(database),
      StructuredWorkoutRepository(database),
      CoachingEngine(environment.clock),
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var selectedTab by rememberSaveable(runId) { mutableStateOf(0) }

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Comfortable),
  ) {
    TextButton(onClick = onBack) { Text("‹ Back") }
    when {
      !state.runLoaded -> Text("Loading run…")
      state.run == null -> Text("Run not found.")
      else -> {
        val run = state.run!!
        val averagePace = state.metrics?.averagePaceSecondsPerKm?.value ?: run.averagePaceSecondsPerKm
        val averageHeartRate = state.metrics?.averageHeartRate?.value ?: run.averageHeartRate
        Text(
          state.plannedWorkout?.name ?: state.plannedWorkout?.type?.displayName() ?: "Run",
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          run.startTime.atZone(environment.clock.zone)
            .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm", Locale.getDefault())),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OpenRunTabs(
          tabs = listOf("Overview", "Splits", "Analysis", "Coach"),
          selectedIndex = selectedTab,
          onSelected = { selectedTab = it },
          distributeEvenly = true,
        )
        if (state.isUpdatingMetrics) Text("Updating run data…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.metricsUpdateError) Text(
          "Could not update run metrics. Saved data is still available.",
          color = MaterialTheme.colorScheme.error,
        )
        when (selectedTab) {
          0 -> CompletedOverview(
            state = state,
            averagePace = averagePace,
            averageHeartRate = averageHeartRate,
            showProviderActions = !environment.isSimulation,
            onRefreshMetrics = viewModel::refreshMetrics,
            onInspectHealthData = { onInspectHealthData(runId) },
            onToggleSourceDetails = viewModel::toggleSourceDetails,
            onToggleMetricDetails = viewModel::toggleMetricDetails,
            onToggleRunRelevance = viewModel::toggleRunRelevance,
          )
          1 -> CompletedSplits(state)
          2 -> CompletedAnalysis(state, viewModel)
          3 -> CompletedCoach(state, viewModel)
        }
      }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
}

@Composable
private fun CompletedOverview(
  state: RunDetailUiState,
  averagePace: Double?,
  averageHeartRate: Double?,
  showProviderActions: Boolean,
  onRefreshMetrics: () -> Unit,
  onInspectHealthData: () -> Unit,
  onToggleSourceDetails: () -> Unit,
  onToggleMetricDetails: () -> Unit,
  onToggleRunRelevance: () -> Unit,
) {
  val run = state.run ?: return
  val distance = state.metrics?.distanceMeters?.value ?: run.distanceMeters
  val metrics = buildList {
    distance?.let { add(OpenRunMetricItem(formatDistance(it), "Distance")) }
    add(OpenRunMetricItem(formatDuration(run.durationSeconds), "Duration"))
    averagePace?.let { add(OpenRunMetricItem(formatPace(it).replace(" min/km", " /km"), "Avg. pace")) }
    averageHeartRate?.let { add(OpenRunMetricItem("%.0f bpm".format(Locale.getDefault(), it), "Avg. HR")) }
  }
  OpenRunCard {
    OpenRunCompactMetrics(metrics, columns = metrics.size.coerceAtMost(4))
  }
  state.routeData?.route?.points?.takeIf { it.size >= 2 }?.let { points ->
    RunRouteCanvas(
      points = points,
      style = RunRouteCanvasStyle.DETAIL,
      modifier = Modifier.fillMaxWidth().height(260.dp),
    )
  }
  state.metrics?.let { runMetrics ->
    state.charts?.let { charts ->
      RunChartsSection(runMetrics, charts, state.plannedWorkout.wholeRunPaceTarget())
    }
  }
  OpenRunDisclosure("Recording details") {
    SourceCard(state, onToggleSourceDetails)
    state.metrics?.let { RunMetricsCard(it, state.showMetricDetails, onToggleMetricDetails) }
    if (showProviderActions) {
      TextButton(onClick = onRefreshMetrics, enabled = !state.isUpdatingMetrics) { Text("Refresh metrics") }
      if (run.isCanonical) TextButton(onClick = onInspectHealthData) { Text("Inspect Health Data") }
    }
  }
  TextButton(onClick = onToggleRunRelevance, enabled = !state.isUpdatingRelevance) {
    Text(if (run.relevance == RunRelevance.IGNORED) "Restore run" else "Ignore run")
  }
}

@Composable
private fun CompletedSplits(state: RunDetailUiState) {
  val splits = state.routeData?.splits.orEmpty()
  if (splits.isEmpty()) {
    SplitsUnavailable()
    return
  }
  val showHeartRate = splits.any { it.averageHeartRate != null }
  val showCadence = splits.any { it.averageCadence != null }
  OpenRunSection("Splits") {
    SplitRow(
      first = "Split",
      pace = "Pace",
      heartRate = if (showHeartRate) "HR" else null,
      cadence = if (showCadence) "Cadence" else null,
      header = true,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    splits.forEach { split ->
      SplitRow(
        first = if (split.partial) {
          "%.2f km".format(Locale.getDefault(), split.distanceMeters / 1_000.0)
        } else "${split.ordinal} km",
        pace = formatPace(split.averagePaceSecondsPerKm).replace(" min/km", " /km"),
        heartRate = if (showHeartRate) {
          split.averageHeartRate?.let { "%.0f".format(Locale.getDefault(), it) } ?: "—"
        } else null,
        cadence = if (showCadence) {
          split.averageCadence?.let { "%.0f".format(Locale.getDefault(), it) } ?: "—"
        } else null,
      )
    }
    Text(
      when (splits.first().derivation) {
        RunSplitDerivation.ROUTE_DERIVED -> "Derived from recorded GPS route"
        RunSplitDerivation.SOURCE_LAP -> "Recorded source laps"
        RunSplitDerivation.DISTANCE_SERIES_DERIVED -> "Derived from recorded distance samples"
        RunSplitDerivation.SPEED_SERIES_DERIVED -> "Derived from recorded speed samples"
      },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  SplitSummary(splits)
}

@Composable
private fun SplitRow(
  first: String,
  pace: String,
  heartRate: String? = null,
  cadence: String? = null,
  header: Boolean = false,
) {
  val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
  val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
  Row(Modifier.fillMaxWidth().padding(vertical = OpenRunSpacing.Small)) {
    Text(first, Modifier.weight(0.7f), style = style, color = color)
    Text(pace, Modifier.weight(1.3f), style = style, color = color)
    if (heartRate != null) Text(heartRate, Modifier.weight(0.8f), style = style, color = color)
    if (cadence != null) Text(cadence, Modifier.weight(1f), style = style, color = color)
  }
}

@Composable
private fun SplitSummary(splits: List<RunSplit>) {
  val totalDistance = splits.sumOf(RunSplit::distanceMeters)
  val totalDuration = splits.sumOf(RunSplit::durationSeconds)
  val fullSplits = splits.filterNot(RunSplit::partial)
  if (totalDistance <= 0.0 || totalDuration <= 0.0) return
  OpenRunSection("Summary") {
    OpenRunEvidenceRow(
      "Average pace",
      formatPace(totalDuration * 1_000.0 / totalDistance).replace(" min/km", " /km"),
    )
    fullSplits.minByOrNull(RunSplit::averagePaceSecondsPerKm)?.let {
      OpenRunEvidenceRow("Fastest full split", "${it.ordinal} · ${formatPace(it.averagePaceSecondsPerKm).replace(" min/km", " /km")}")
    }
    fullSplits.maxByOrNull(RunSplit::averagePaceSecondsPerKm)?.let {
      OpenRunEvidenceRow("Slowest full split", "${it.ordinal} · ${formatPace(it.averagePaceSecondsPerKm).replace(" min/km", " /km")}")
    }
  }
}

@Composable
private fun SplitsUnavailable() {
  OpenRunCard {
    Text("Splits", style = MaterialTheme.typography.titleMedium)
    Text(
      "True split data is not available for this run. OpenRunCoach does not derive splits from a whole-run average.",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun CompletedAnalysis(state: RunDetailUiState, viewModel: RunDetailViewModel) {
  WorkoutAssociationCard(state, viewModel)
  val response = state.workoutResponse
  if (response == null) {
    OpenRunCard {
      Text(
        if (state.plannedWorkout == null) {
          "No planned workout is associated. Assign one, then complete the post-run checkout for analysis."
        } else {
          "Complete the post-run checkout to compare this run with its planned workout."
        }
      )
    }
    return
  }
  OpenRunSection("Execution") {
    LabelValue("Completion", response.completion.name.humanize())
    LabelValue("Adherence", response.adherence.name.humanize())
    LabelValue("Volume", response.volumeCompletion.name.humanize())
    if (response.paceResponse != PaceResponse.UNKNOWN) {
      LabelValue("Pace vs. target", response.paceResponse.name.humanize())
    }
    LabelValue("Continuity", response.continuity.continuityLabel())
    val targetRpe = if (response.plannedRpeMinimum != null && response.plannedRpeMaximum != null) {
      " (target ${response.plannedRpeMinimum}–${response.plannedRpeMaximum})"
    } else ""
    LabelValue("RPE", "${response.reportedRpe}$targetRpe")
    LabelValue("Session cost", response.sessionCost.name.humanize())
    LabelValue("Interpretation confidence", response.confidence.name.humanize())
  }
  OpenRunSection("Planned vs. actual") {
    response.plannedDistanceMeters?.let { LabelValue("Target distance", formatDistance(it)) }
    response.actualDistanceMeters?.let { LabelValue("Actual distance", formatDistance(it)) }
    plannedPaceLabel(response)?.let { LabelValue("Target pace", it) }
    response.actualPaceSecondsPerKm?.let {
      LabelValue("Actual average pace", formatPace(it).replace(" min/km", " /km"))
    }
    if (response.plannedRpeMinimum != null && response.plannedRpeMaximum != null) {
      LabelValue("Target RPE", "${response.plannedRpeMinimum}–${response.plannedRpeMaximum}")
    }
    LabelValue("Actual RPE", response.reportedRpe.toString())
  }
}

@Composable
private fun WorkoutAssociationCard(state: RunDetailUiState, viewModel: RunDetailViewModel) {
  OpenRunSection("Planned workout") {
    if (state.run?.relevance == RunRelevance.IGNORED) {
      Text("Restore this run before assigning it to a planned workout.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      return@OpenRunSection
    }
    val assigned = state.plannedWorkout
    if (assigned == null) {
      Text("This run is not associated with a prescription.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      Button(onClick = viewModel::showAssignmentCandidates, enabled = !state.isUpdatingAssociation) {
        Text("Assign to workout")
      }
    } else {
      LabelValue("Workout", assigned.name.ifBlank { assigned.type.displayName() })
      WorkoutEstimateCalculator.calculate(assigned).estimatedTotalDistanceMeters
        ?.let { LabelValue("Planned distance", formatDistance(it)) }
      Row(horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        TextButton(onClick = viewModel::showAssignmentCandidates, enabled = !state.isUpdatingAssociation) {
          Text("Change association")
        }
        TextButton(onClick = viewModel::unassignWorkout, enabled = !state.isUpdatingAssociation) {
          Text("Unassign")
        }
      }
    }
    if (state.showAssignmentCandidates) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      if (state.assignmentCandidates.isEmpty()) {
        Text("No plausible active-plan workouts within three days.")
      } else state.assignmentCandidates.forEach { candidate ->
        val pace = candidate.workout.assignmentPaceTarget()?.let {
          "${paceClock(it.minimumSecondsPerKm)}–${paceClock(it.maximumSecondsPerKm)} /km"
        }
        TextButton(
          onClick = { viewModel.assignToWorkout(candidate.workout.id) },
          enabled = !state.isUpdatingAssociation,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(Modifier.fillMaxWidth()) {
            Text("${candidate.workout.date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))} · ${candidate.workout.name.ifBlank { candidate.workout.type.displayName() }}")
            Text(
              listOfNotNull(
                candidate.plannedDistanceMeters?.let(::formatDistance),
                pace,
                when (candidate.associationState) {
                  WorkoutAssignmentState.AVAILABLE -> null
                  WorkoutAssignmentState.ASSIGNED_TO_THIS_RUN -> "Currently assigned"
                  WorkoutAssignmentState.ASSIGNED_TO_ANOTHER_RUN -> "Already associated"
                },
              ).joinToString(" · ").ifBlank { "Structured workout" },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      TextButton(onClick = viewModel::hideAssignmentCandidates) { Text("Cancel") }
    }
  }
}

@Composable
private fun CompletedCoach(state: RunDetailUiState, viewModel: RunDetailViewModel) {
  OpenRunCard {
    Text("Coach Insight", style = MaterialTheme.typography.titleMedium)
    val decision = state.coachingDecision
    if (decision == null) {
      Text(
        "Complete the post-run checkout to receive a coaching interpretation.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Text(coachingActionLabel(decision.action), color = MaterialTheme.colorScheme.primary)
      (state.workoutResponse?.reasons ?: decision.reasons).take(3).forEach { reason -> Text(reason) }
    }
  }
  FeedbackCard(state, viewModel)
}

private fun StructuredWorkout?.wholeRunPaceTarget(): PaceTarget? {
  this ?: return null
  val segments = blocks.flatMap(WorkoutBlock::segments)
  if (segments.size != 1) return null
  return (listOf(segments.single().primaryIntensity) + segments.single().secondaryIntensities)
    .filterIsInstance<PaceTarget>()
    .singleOrNull()
}

private fun StructuredWorkout.assignmentPaceTarget(): PaceTarget? = blocks
  .flatMap(WorkoutBlock::segments)
  .flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }
  .filterIsInstance<PaceTarget>()
  .firstOrNull()

private fun plannedPaceLabel(response: WorkoutResponse): String? {
  val minimum = response.plannedPaceMinimumSecondsPerKm ?: return null
  val maximum = response.plannedPaceMaximumSecondsPerKm ?: return null
  return "${paceClock(minimum)}–${paceClock(maximum)} /km"
}

private fun paceClock(secondsPerKm: Double): String {
  val seconds = secondsPerKm.toLong()
  return "%d:%02d".format(Locale.getDefault(), seconds / 60, seconds % 60)
}

private fun String.humanize(): String = lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)

private fun RunContinuityEvidence.continuityLabel(): String = when (this) {
  RunContinuityEvidence.CONTINUOUS -> "Continuous"
  RunContinuityEvidence.INTERRUPTED -> "Sustained walking/stopped interval"
  RunContinuityEvidence.UNKNOWN -> "Not enough movement samples"
}

@Composable
private fun RunMetricsCard(metrics: RunMetrics, showDetails: Boolean, onToggleDetails: () -> Unit) {
  DetailCard("Run Metrics") {
    val heartRate = listOfNotNull(
      metrics.averageHeartRate?.value?.let { "${formatNumber(it)} bpm avg" },
      metrics.maxHeartRate?.value?.let { "${formatNumber(it)} max" },
    ).joinToString(" · ")
    if (heartRate.isNotBlank()) LabelValue("Heart rate", heartRate)
    metrics.averagePaceSecondsPerKm?.let { LabelValue("Pace", formatPace(it.value)) }
    val cadence = listOfNotNull(
      metrics.movingAverageCadence?.value?.let { "${formatNumber(it)} spm moving avg" },
      metrics.averageCadence?.value?.let { "${formatNumber(it)} spm session avg" },
    ).joinToString(" · ")
    if (cadence.isNotBlank()) LabelValue("Cadence", cadence)
    metrics.vo2Max?.let { LabelValue("VO₂max", formatNumber(it.value)) }
    metrics.elevationGainMeters?.let { LabelValue("Elevation", "+${formatNumber(it.value)} m") }
    TextButton(onClick = onToggleDetails) { Text(if (showDetails) "Hide metric details" else "Metric details") }
    if (showDetails) MetricDetails(metrics)
  }
}

@Composable
private fun MetricDetails(metrics: RunMetrics) {
  listOf(
    Triple("Distance", metrics.distanceMeters, "m"),
    Triple("Average heart rate", metrics.averageHeartRate, "bpm"),
    Triple("Maximum heart rate", metrics.maxHeartRate, "bpm"),
    Triple("Average speed", metrics.averageSpeedMetersPerSecond, "m/s"),
    Triple("Cadence", metrics.averageCadence, "spm"),
    Triple("Moving cadence", metrics.movingAverageCadence, "spm"),
    Triple("Average power", metrics.averagePowerWatts, "W"),
    Triple("Maximum power", metrics.maxPowerWatts, "W"),
    Triple("VO₂max", metrics.vo2Max, "ml/kg/min"),
    Triple("Elevation gain", metrics.elevationGainMeters, "m"),
  ).forEach { (label, metric, unit) ->
    if (metric != null) {
      Text(label, style = MaterialTheme.typography.titleSmall)
      LabelValue("Value", "${formatNumber(metric.value)} $unit")
      MetricProvenance(metric)
    }
  }
}

@Composable
private fun MetricProvenance(metric: MetricValue<out Number>) {
  LabelValue("Source", metric.sourceAppName ?: metric.source.displayName())
  metric.sourceDeviceName?.let { LabelValue("Device", it) }
  LabelValue("Confidence", metric.confidence.displayName())
  when (metric.verification) {
    MetricVerification.VERIFIED -> LabelValue("Verification", "Verified against ${metric.secondarySource?.displayName() ?: "another provider"}")
    MetricVerification.CLOSE_MATCH -> LabelValue("Verification", "Close match with ${metric.secondarySource?.displayName() ?: "another provider"}")
    MetricVerification.CONFLICT -> LabelValue("Verification", "Provider conflict")
    MetricVerification.UNVERIFIED -> Unit
  }
  metric.verificationReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun FeedbackCard(state: RunDetailUiState, viewModel: RunDetailViewModel) {
  DetailCard("Post-run Checkout") {
    when {
      !state.feedbackLoaded -> Text("Loading feedback…")
      state.isEditingFeedback -> FeedbackEditor(state, viewModel)
      state.feedback == null -> {
        Text("Add how the run felt to receive a coach analysis.")
        Button(onClick = viewModel::editFeedback) { Text("Complete checkout") }
      }
      else -> {
        FeedbackSummary(state.feedback)
        Button(onClick = viewModel::editFeedback) { Text("Edit checkout") }
      }
    }
    if (state.saved) Text("Checkout saved.", color = MaterialTheme.colorScheme.primary)
  }
}

@Composable
private fun FeedbackSummary(feedback: RunFeedback) {
  LabelValue("RPE", "${feedback.rpe}/10")
  LabelValue("Pain", feedback.painLevel.displayName())
  LabelValue("Muscle fatigue", feedback.muscleFatigue.displayName())
  LabelValue("Completion", feedback.completionStatus.displayName())
  feedback.readiness?.let { LabelValue("Readiness", "$it/5") }
  feedback.notes?.let { LabelValue("Notes", it) }
}

@Composable
private fun FeedbackEditor(state: RunDetailUiState, viewModel: RunDetailViewModel) {
  Text("How hard was this run?", style = MaterialTheme.typography.titleMedium)
  OutlinedTextField(
    value = state.form.rpe,
    onValueChange = { value -> viewModel.updateForm { it.copy(rpe = value) } },
    label = { Text("RPE: 1–10") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    singleLine = true,
  )
  ChoiceRow(
    "Cardio",
    listOf(CardioEffort.EASY to "Easy", CardioEffort.APPROPRIATE to "Appropriate", CardioEffort.HARD to "Hard", CardioEffort.LIMIT to "Limit"),
    state.form.cardioEffort,
  ) { selected -> viewModel.updateForm { it.copy(cardioEffort = selected) } }
  ChoiceRow(
    "Muscle fatigue",
    listOf(MuscleFatigue.NONE to "None", MuscleFatigue.MILD to "Mild", MuscleFatigue.MODERATE to "Moderate", MuscleFatigue.SEVERE to "Severe"),
    state.form.muscleFatigue,
  ) { selected -> viewModel.updateForm { it.copy(muscleFatigue = selected) } }
  ChoiceRow(
    "Pain",
    listOf(PainLevel.NONE to "None", PainLevel.MILD to "Mild", PainLevel.SIGNIFICANT to "Significant"),
    state.form.painLevel,
  ) { selected -> viewModel.updateForm { it.copy(painLevel = selected) } }
  ChoiceRow(
    "Completion",
    listOf(CompletionStatus.COMPLETED to "Completed", CompletionStatus.PARTIAL to "Partial", CompletionStatus.ABORTED to "Aborted"),
    state.form.completionStatus,
  ) { selected -> viewModel.updateForm { it.copy(completionStatus = selected) } }
  OutlinedTextField(
    value = state.form.readiness,
    onValueChange = { value -> viewModel.updateForm { it.copy(readiness = value) } },
    label = { Text("Readiness: 1–5 (optional)") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    singleLine = true,
  )
  OutlinedTextField(
    value = state.form.notes,
    onValueChange = { value -> viewModel.updateForm { it.copy(notes = value) } },
    label = { Text("Notes (optional)") },
    minLines = 3,
  )
  Row(horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
    Button(onClick = viewModel::saveFeedback, enabled = !state.isSaving) {
      Text(if (state.feedback == null) "Save checkout" else "Update checkout")
    }
    TextButton(onClick = viewModel::cancelFeedbackEdit, enabled = !state.isSaving) { Text("Cancel") }
  }
  if (state.isSaving) Text("Saving checkout…")
}

@Composable
private fun SourceCard(state: RunDetailUiState, onToggleDetails: () -> Unit) {
  val run = state.run ?: return
  DetailCard("Source") {
    Text(run.displaySourceName, style = MaterialTheme.typography.titleMedium)
    if (state.possibleDuplicates.isNotEmpty()) Text("Possible duplicate", color = MaterialTheme.colorScheme.error)
    TextButton(onClick = onToggleDetails) {
      Text(if (state.showSourceDetails) "Hide duplicate/source details" else "Show duplicate/source details")
    }
    if (state.showSourceDetails) {
      if (run.sourcePackageName.isNotBlank()) LabelValue("Source package", run.sourcePackageName)
      val device = listOfNotNull(run.sourceDeviceManufacturer, run.sourceDeviceModel).joinToString(" ")
      if (device.isNotBlank()) LabelValue("Device", device)
      LabelValue("Record", if (run.isCanonical) "Canonical run" else "Non-canonical duplicate")
      if (BuildConfig.DEBUG) {
        LabelValue("Provider record ID", run.healthConnectRecordId)
        LabelValue("Physical run ID", run.physicalRunId)
        LabelValue("Match decision", run.canonicalMatchDecision.name.lowercase().replaceFirstChar(Char::titlecase))
        LabelValue("Workout association", state.plannedWorkout?.id ?: "Unmatched")
      }
      run.canonicalSelectionReason?.let { LabelValue("Selection", it) }
      DuplicateDetails(state.possibleDuplicates)
      if (state.duplicateRecords.size > 1) {
        Text("Sources", style = MaterialTheme.typography.titleMedium)
        state.duplicateRecords.forEach { record ->
          Text("${record.displaySourceName} · ${formatDistance(record.distanceMeters)} · ${if (record.isCanonical) "Canonical" else "Provider record"}")
          if (BuildConfig.DEBUG) {
            Text(
              "${record.healthConnectRecordId} · ${record.startTime}–${record.endTime} · " +
                "${formatDuration(record.durationSeconds)} · ${record.canonicalMatchDecision.name}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            record.canonicalSelectionReason?.let {
              Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DuplicateDetails(duplicates: List<PossibleDuplicate>) {
  duplicates.forEach { duplicate ->
    Text("${duplicate.decision.name.lowercase().replaceFirstChar(Char::titlecase)} match")
    duplicate.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (duplicate.differentSourceApplications) Text("Different source: ${duplicate.otherSourceName}")
  }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
  OpenRunSection(title) { content() }
}

@Composable
private fun LabelValue(label: String, value: String) {
  OpenRunEvidenceRow(label, value)
}

@Composable
private fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      options.forEach { (value, text) ->
        FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(text) })
      }
    }
  }
}

private fun PainLevel.displayName() = when (this) {
  PainLevel.NONE -> "No pain"
  PainLevel.MILD -> "Mild pain"
  PainLevel.SIGNIFICANT -> "Significant pain"
}
private fun MuscleFatigue.displayName() = name.lowercase().replaceFirstChar(Char::titlecase)
private fun CompletionStatus.displayName() = name.lowercase().replaceFirstChar(Char::titlecase)
private fun MetricSource.displayName() = when (this) {
  MetricSource.HEALTH_CONNECT -> "Health Connect"
  MetricSource.SAMSUNG_HEALTH -> "Samsung Health"
  MetricSource.DERIVED -> "Derived"
}

private fun MetricConfidence.displayName() = name.lowercase().replaceFirstChar(Char::titlecase)

private fun formatNumber(value: Double): String = "%.1f".format(Locale.getDefault(), value)
