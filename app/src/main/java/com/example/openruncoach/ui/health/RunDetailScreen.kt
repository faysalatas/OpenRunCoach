package com.example.openruncoach.ui.health

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.BuildConfig
import com.example.openruncoach.R
import com.example.openruncoach.ui.components.CompletedRunMetric
import com.example.openruncoach.ui.components.CompletedRunMetrics
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
import com.example.openruncoach.domain.workout.VolumeCompletion
import com.example.openruncoach.domain.workout.displayName
import com.example.openruncoach.ui.common.coachingActionLabel
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatActualDuration
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.OpenRunTabs
import com.example.openruncoach.ui.components.OpenRunSection
import com.example.openruncoach.ui.components.OpenRunEvidenceRow
import com.example.openruncoach.ui.components.OpenRunCard
import com.example.openruncoach.ui.components.OpenRunDisclosure
import com.example.openruncoach.ui.components.RunRouteMap
import com.example.openruncoach.theme.OpenRunSpacing
import com.example.openruncoach.ui.workout.heroImageResource
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
      CoachingEngine(environment.clock),
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var selectedTab by rememberSaveable(runId) { mutableStateOf(0) }

  BoxWithConstraints(modifier.fillMaxSize()) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    // Reserve readable header/control/metric space; only the map and plot flex.
    // Small viewports and large fonts retain scrolling instead of shrinking text.
    val checkoutHeight = if (state.feedbackLoaded && state.feedback == null) 56.dp else 0.dp
    val updateHeight = if (state.isUpdatingMetrics || state.metricsUpdateError) 32.dp * fontScale else 0.dp
    // Header, text tabs, metric strip, 48 dp selector, axes, summary and gaps.
    // At default fonts the remaining space goes to a shallow map and dashboard plot.
    val plotBudget = (maxHeight - 500.dp * fontScale - checkoutHeight - updateHeight).coerceAtLeast(0.dp)
    val chartHeight = (plotBudget * 0.4f).coerceIn(88.dp * fontScale, 116.dp * fontScale)
    val mapHeight = (plotBudget - chartHeight).coerceIn(120.dp, 192.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
      when {
        !state.runLoaded || (state.run != null && !state.associationLoaded) -> {
          TextButton(onClick = onBack) { Text("‹ Back") }
          Text("Loading run…", Modifier.padding(ScreenPadding))
        }
        state.run == null -> {
          TextButton(onClick = onBack) { Text("‹ Back") }
          Text("Run not found.", Modifier.padding(ScreenPadding))
        }
        else -> {
          val run = state.run!!
          val averagePace = run.averagePaceSecondsPerKm
          val averageHeartRate = state.metrics?.averageHeartRate?.value ?: run.averageHeartRate
          CompletedRunHeader(
            title = state.plannedWorkout?.name?.takeIf(String::isNotBlank) ?: state.plannedWorkout?.type?.displayName() ?: "Run",
            date = run.startTime.atZone(environment.clock.zone)
              .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm", Locale.getDefault())),
            heroResource = state.plannedWorkout?.type?.heroImageResource(),
            onBack = onBack,
          )
          Column(
            Modifier.padding(horizontal = ScreenPadding, vertical = OpenRunSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small),
          ) {
            OpenRunTabs(
              tabs = listOf("Overview", "Splits", "Analysis", "Coach"),
              selectedIndex = selectedTab,
              onSelected = { selectedTab = it },
              distributeEvenly = true,
            )
            if (state.feedbackLoaded && state.feedback == null && selectedTab != 3) {
              TextButton(onClick = {
                selectedTab = 3
                viewModel.editFeedback()
              }, enabled = !state.isSaving) { Text("Complete post-run checkout ›") }
            }
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
                mapHeight = mapHeight,
                chartHeight = chartHeight,
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
      }
      state.error?.let { Text(it, Modifier.padding(ScreenPadding), color = MaterialTheme.colorScheme.error) }
    }
  }
}

@Composable
private fun CompletedRunHeader(title: String, date: String, heroResource: Int?, onBack: () -> Unit) {
  Box(Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
    heroResource?.let { resource ->
      Image(painterResource(resource), contentDescription = null,
        modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
      Box(Modifier.matchParentSize().background(Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.18f),
        0.55f to Color.Black.copy(alpha = 0.28f),
        1f to MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
      )))
    }
    Column(Modifier.padding(horizontal = ScreenPadding, vertical = OpenRunSpacing.Micro)) {
      TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text("‹ Back", color = if (heroResource != null) Color.White else MaterialTheme.colorScheme.primary)
      }
      Text(title, style = MaterialTheme.typography.headlineSmall,
        color = if (heroResource != null) Color.White else MaterialTheme.colorScheme.onSurface)
      Text(date, style = MaterialTheme.typography.bodySmall,
        color = if (heroResource != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun CompletedOverview(
  state: RunDetailUiState,
  averagePace: Double?,
  averageHeartRate: Double?,
  mapHeight: Dp,
  chartHeight: Dp,
  showProviderActions: Boolean,
  onRefreshMetrics: () -> Unit,
  onInspectHealthData: () -> Unit,
  onToggleSourceDetails: () -> Unit,
  onToggleMetricDetails: () -> Unit,
  onToggleRunRelevance: () -> Unit,
) {
  val run = state.run ?: return
  val distance = run.distanceMeters
  val metrics = buildList {
    distance?.let { add(CompletedRunMetric(formatDistance(it), "Distance", R.drawable.ic_run)) }
    add(CompletedRunMetric(formatActualDuration(run.durationSeconds), "Duration", R.drawable.ic_stopwatch))
    averagePace?.let { add(CompletedRunMetric(formatPace(it).replace(" min/km", " /km"), "Avg. pace", R.drawable.ic_pace)) }
    averageHeartRate?.let { add(CompletedRunMetric("%.0f bpm".format(Locale.getDefault(), it), "Avg. HR", R.drawable.ic_heart)) }
  }
  CompletedRunMetrics(metrics)
  state.routeData?.route?.points?.takeIf { it.size >= 2 }?.let { points ->
    RunRouteMap(
      points = points,
      modifier = Modifier.fillMaxWidth().height(mapHeight),
    )
  }
  state.metrics?.let { runMetrics ->
    state.charts?.let { charts ->
      RunChartsSection(runMetrics, charts, state.plannedWorkout.wholeRunPaceTarget(), chartHeight)
    }
  }
  OpenRunDisclosure("Data sources") {
    LabelValue("Summary", run.displaySourceName)
    state.routeData?.route?.let { route ->
      LabelValue("Route", route.sourceAppName?.takeIf(String::isNotBlank) ?: route.source.displayName())
    }
    state.metrics?.let { metric ->
      (metric.movingAverageCadence ?: metric.averageCadence)?.let {
        LabelValue("Cadence", it.sourceAppName?.takeIf(String::isNotBlank) ?: it.source.displayName())
      }
      metric.averageHeartRate?.let {
        LabelValue("Heart rate", it.sourceAppName?.takeIf(String::isNotBlank) ?: it.source.displayName())
      }
    }
    OpenRunDisclosure("Recording diagnostics") {
      state.charts?.fastestThirtySecondPaceSecondsPerKm?.let {
        LabelValue("Fastest 30 sec", formatPace(it).replace(" min/km", " /km"))
      }
      state.charts?.pace?.let { chart ->
        if (chart.hasClippedPaceSamples(state.plannedWorkout.wholeRunPaceTarget())) {
          Text("Off-scale pace samples clipped for display", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      SourceCard(state, onToggleSourceDetails)
      state.metrics?.let { RunMetricsCard(it, state.showMetricDetails, onToggleMetricDetails) }
      if (showProviderActions) {
        TextButton(onClick = onRefreshMetrics, enabled = !state.isUpdatingMetrics) { Text("Refresh metrics") }
        if (run.isCanonical) TextButton(onClick = onInspectHealthData) { Text("Inspect Health Data") }
      }
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
  val showDelta = state.splitDeltasSecondsPerKm.isNotEmpty()
  val locale = Locale.getDefault()
  val rows = remember(splits, state.splitDeltasSecondsPerKm, locale) {
    splits.map { split ->
      val delta = state.splitDeltasSecondsPerKm[split.ordinal]
      CompletedSplitCells(
        kilometre = split.kilometreLabel(),
        pace = formatPace(split.averagePaceSecondsPerKm).removeSuffix(" min/km"),
        delta = delta?.let(::formatPaceDelta),
        faster = delta != null && delta < 0.0,
        heartRate = split.averageHeartRate?.let { "%.0f".format(locale, it) },
      )
    }
  }
  Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
    BoxWithConstraints {
      val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
      // Explicit numeric column widths are shared by the header and every row.
      val columnWidths = listOfNotNull(44.dp, 72.dp,
        68.dp.takeIf { showDelta }, 40.dp.takeIf { showHeartRate }).map { it * fontScale }
      val columnsWidth = columnWidths.sumOf { it.value.toDouble() }.toFloat().dp
      val tableWidth = maxOf(maxWidth, columnsWidth + 16.dp * (columnWidths.size - 1))
      val columnGap = (tableWidth - columnsWidth) / (columnWidths.size - 1)
      Column(Modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(tableWidth)) {
          SplitRow(
            columnWidths = columnWidths,
            columnGap = columnGap,
            first = "Km",
            pace = "Pace",
            heartRate = if (showHeartRate) "HR" else null,
            delta = if (showDelta) "Δ" else null,
            header = true,
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          rows.forEach { row ->
            SplitRow(
              columnWidths = columnWidths,
              columnGap = columnGap,
              first = row.kilometre,
              pace = row.pace,
              delta = if (showDelta) row.delta ?: "—" else null,
              faster = row.faster,
              heartRate = if (showHeartRate) row.heartRate ?: "—" else null,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
          }
        }
      }
    }
  }
  SplitSummary(state)
  Text(
      splits.map(RunSplit::derivation).distinct().joinToString(" · ") { derivation -> when (derivation) {
        RunSplitDerivation.ROUTE_DERIVED -> "Derived from recorded GPS route"
        RunSplitDerivation.SOURCE_LAP -> "Recorded source laps"
        RunSplitDerivation.DISTANCE_SERIES_DERIVED -> "Derived from recorded distance samples"
        RunSplitDerivation.SPEED_SERIES_DERIVED -> "Derived from recorded speed samples"
      } },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  HalfRunDetails(state)
  OpenRunDisclosure("Split details") {
    splits.forEach { split ->
      LabelValue(
        "${split.ordinal} · ${"%.2f km".format(Locale.getDefault(), split.distanceMeters / 1_000.0)}",
        formatDuration(kotlin.math.round(split.durationSeconds).toLong()),
      )
      split.averageCadence?.let {
        LabelValue("Cadence · split ${split.ordinal}", "%.0f spm".format(Locale.getDefault(), it))
      }
    }
  }
}

private data class CompletedSplitCells(
  val kilometre: String,
  val pace: String,
  val delta: String?,
  val faster: Boolean,
  val heartRate: String?,
)

@Composable
private fun SplitRow(
  columnWidths: List<Dp>,
  columnGap: Dp,
  first: String,
  pace: String,
  heartRate: String? = null,
  delta: String? = null,
  faster: Boolean = false,
  header: Boolean = false,
) {
  val style = (if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium)
    .copy(fontFeatureSettings = "tnum")
  val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
  val cells = listOfNotNull(first, pace, delta, heartRate)
  Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(columnGap)) {
    cells.forEachIndexed { index, text ->
      Text(text, Modifier.width(columnWidths[index]), style = style,
        color = if (!header && delta != null && index == 2 && faster) MaterialTheme.colorScheme.primary else color,
        textAlign = if (index == 0) TextAlign.Start else TextAlign.End, maxLines = 1)
    }
  }
}

@Composable
private fun SplitSummary(state: RunDetailUiState) {
  val halves = state.halfSplit
  RunComparisonSummary(
    firstValue = state.run?.averagePaceSecondsPerKm
      ?.let { formatPace(it).replace(" min/km", " /km") },
    firstLabel = "Average pace",
    secondValue = halves?.let { if (it.direction == com.example.openruncoach.domain.route.HalfSplitDirection.EVEN) "Even split"
      else "${formatPaceDelta(it.deltaSecondsPerKm)} /km" },
    secondLabel = when (halves?.direction) {
      com.example.openruncoach.domain.route.HalfSplitDirection.NEGATIVE -> "Negative split"
      com.example.openruncoach.domain.route.HalfSplitDirection.POSITIVE -> "Positive split"
      com.example.openruncoach.domain.route.HalfSplitDirection.EVEN -> "Equal-distance halves"
      null -> "Half-run pacing"
    },
    accentSecond = halves?.direction == com.example.openruncoach.domain.route.HalfSplitDirection.NEGATIVE,
  )
}

@Composable
private fun HalfRunDetails(state: RunDetailUiState) {
  state.halfSplit?.let {
    OpenRunDisclosure("Half-run details") {
      LabelValue("First-half distance", formatDistance(it.halfDistanceMeters))
      LabelValue("First-half duration", formatDuration(kotlin.math.round(it.firstHalfDurationSeconds).toLong()))
      LabelValue("First-half pace", formatPace(it.firstHalfPaceSecondsPerKm).replace(" min/km", " /km"))
      LabelValue("Second-half distance", formatDistance(it.halfDistanceMeters))
      LabelValue("Second-half duration", formatDuration(kotlin.math.round(it.secondHalfDurationSeconds).toLong()))
      LabelValue("Second-half pace", formatPace(it.secondHalfPaceSecondsPerKm).replace(" min/km", " /km"))
      Text("Equal-distance halves · within ±3 sec/km is even", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun SplitsUnavailable() {
  OpenRunSection("Splits") {
    Text(
      "True split data is not available for this run. OpenRunCoach does not derive splits from a whole-run average.",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun CompletedAnalysis(state: RunDetailUiState, viewModel: RunDetailViewModel) {
  val response = state.workoutResponse
  if (response == null) {
    OpenRunSection("Analysis") {
      Text(
        if (state.plannedWorkout == null) {
          "No planned workout is associated. Assign one, then complete the post-run checkout for analysis."
        } else {
          "Complete the post-run checkout to compare this run with its planned workout."
        }
      )
    }
    WorkoutAssociationCard(state, viewModel)
    return
  }
  AnalysisOutcomeCard(response)
  Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
    PlannedActualTable(response, state.plannedWorkout.wholeRunPaceTarget() != null)
    if (state.plannedWorkout.wholeRunPaceTarget() == null && plannedPaceLabel(response) != null) {
      Text("Whole-run average pace is context, not evidence of work-segment pace adherence.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
  Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
    AnalysisFactRow("Continuity", when (response.continuity) {
      RunContinuityEvidence.CONTINUOUS -> "Continuous"
      RunContinuityEvidence.INTERRUPTED -> "Interrupted"
      RunContinuityEvidence.UNKNOWN -> "Unknown"
    })
    if (response.continuity != RunContinuityEvidence.CONTINUOUS) {
      Text(response.continuity.continuityLabel(), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
  AnalysisFactRow("Interpretation confidence", response.confidence.name.humanize())
  WorkoutAssociationCard(state, viewModel)
  OpenRunDisclosure("Evidence") {
    LabelValue("Prescription", response.adherence.name.humanize())
    if (response.paceResponse != PaceResponse.UNKNOWN) {
      LabelValue("Pace vs. target", response.paceResponse.name.humanize())
    }
    response.reasons.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
  }
}

@Composable
private fun AnalysisFactRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(label, Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
      textAlign = TextAlign.End)
  }
}

@Composable
private fun AnalysisOutcomeCard(response: WorkoutResponse) {
  OpenRunCard(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
    val values = listOfNotNull(
      response.completion.name.humanize() to "Outcome",
      response.sessionCost.name.humanize() to "Session cost",
      response.volumeCompletion.takeUnless { it == VolumeCompletion.UNKNOWN }?.let { it.name.humanize() to "Volume" },
    )
    BoxWithConstraints {
      val columnWidth = maxOf((maxWidth - (values.size - 1).dp) / values.size,
        84.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
      Row(Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Min)) {
        values.forEachIndexed { index, (value, label) ->
          if (index > 0) VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
          Column(Modifier.width(columnWidth).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
private fun PlannedActualTable(response: WorkoutResponse, wholeRunTarget: Boolean) {
  BoxWithConstraints {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val labelWidth = 64.dp * fontScale
    val gap = 8.dp
    val tableWidth = maxOf(maxWidth, 300.dp * fontScale)
    val valueWidth = (tableWidth - labelWidth - gap * 2) / 2
    val widths = listOf(labelWidth, valueWidth, valueWidth)
    val paceTarget = plannedPaceLabel(response)?.removeSuffix(" /km")
    Column(Modifier.horizontalScroll(rememberScrollState())) {
      Column(Modifier.width(tableWidth)) {
        PlannedActualRow(widths, gap, "", "Planned", "Actual", header = true)
        if (response.plannedDistanceMeters != null || response.actualDistanceMeters != null) {
          PlannedActualRow(widths, gap, "Distance", response.plannedDistanceMeters?.let(::formatDistance), response.actualDistanceMeters?.let(::formatDistance))
        }
        if (response.plannedDistanceMeters == null && response.plannedDurationSeconds != null) {
          PlannedActualRow(widths, gap, "Duration", formatDuration(response.plannedDurationSeconds), formatDuration(response.actualDurationSeconds))
        }
        val actualPace = response.actualPaceSecondsPerKm?.let { formatPace(it).replace(" min/km", " /km") }
        if (wholeRunTarget) {
          PlannedActualRow(widths, gap, "Pace", paceTarget, actualPace)
        } else {
          // Segment targets and the whole-run average are deliberately different rows.
          paceTarget?.let { PlannedActualRow(widths, gap, "Work pace", it, null) }
          actualPace?.let { PlannedActualRow(widths, gap, "Avg. pace", null, it) }
        }
        val rpe = if (response.plannedRpeMinimum != null && response.plannedRpeMaximum != null) {
          "${response.plannedRpeMinimum}–${response.plannedRpeMaximum}"
        } else null
        PlannedActualRow(widths, gap, "RPE", rpe, response.reportedRpe.toString())
      }
    }
  }
}

@Composable
private fun PlannedActualRow(widths: List<Dp>, gap: Dp, label: String, planned: String?, actual: String?, header: Boolean = false) {
  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
    listOf(label, planned ?: "—", actual ?: "—").forEachIndexed { index, text ->
      Text(text, Modifier.width(widths[index]),
        style = (if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium)
          .copy(fontFeatureSettings = "tnum", fontWeight = if (!header && index == 2) FontWeight.Medium else FontWeight.Normal),
        color = if (header || index == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = if (index == 0) TextAlign.Start else TextAlign.End)
    }
  }
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
private fun WorkoutAssociationCard(state: RunDetailUiState, viewModel: RunDetailViewModel) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro)) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    Text("Planned workout", style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.run?.relevance == RunRelevance.IGNORED) {
      Text("Restore this run before assigning it to a planned workout.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      return@Column
    }
    val assigned = state.plannedWorkout
    if (assigned == null) {
      Text("This run is not associated with a prescription.", color = MaterialTheme.colorScheme.onSurfaceVariant)
      Button(onClick = viewModel::showAssignmentCandidates, enabled = !state.isUpdatingAssociation) {
        Text("Assign to workout")
      }
    } else {
      val distance = remember(assigned) { WorkoutEstimateCalculator.calculate(assigned).estimatedTotalDistanceMeters }
        ?.let(::formatDistance)
      Text(listOfNotNull(assigned.name.ifBlank { assigned.type.displayName() }, distance).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        TextButton(onClick = viewModel::showAssignmentCandidates, enabled = !state.isUpdatingAssociation,
          contentPadding = PaddingValues(horizontal = 0.dp)) {
          Text("Change association")
        }
        TextButton(onClick = viewModel::unassignWorkout, enabled = !state.isUpdatingAssociation,
          contentPadding = PaddingValues(horizontal = 0.dp)) {
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
  val presentation = coachPresentation(state)
  OpenRunCard {
    Text("Coach Insight", style = MaterialTheme.typography.titleMedium)
    val decision = state.coachingDecision
    if (decision == null) {
      Text(
        if (state.feedback == null) "Complete the post-run checkout to receive a coaching interpretation."
        else "No coaching decision is available for this run.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Text(coachingActionLabel(decision.action), color = MaterialTheme.colorScheme.primary)
      if (presentation.observations.isNotEmpty()) {
        Text("What this run showed", style = MaterialTheme.typography.titleSmall)
        presentation.observations.forEach { Text("• $it") }
      }
      presentation.implication?.let {
        Text("Next training implication", style = MaterialTheme.typography.titleSmall)
        Text(it)
      }
    }
    if (state.feedback != null || state.workoutResponse != null || decision != null) {
      OpenRunDisclosure("More details") {
        state.workoutResponse?.let { LabelValue("Interpretation confidence", it.confidence.name.humanize()) }
        (state.workoutResponse?.reasons.orEmpty() + decision?.reasons.orEmpty()).distinct().forEach {
          Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        state.feedback?.let { FeedbackSummary(it) }
      }
    }
  }
  if (state.feedback == null || state.isEditingFeedback) FeedbackCard(state, viewModel)
  else {
    TextButton(onClick = viewModel::editFeedback) { Text("Edit checkout") }
    if (state.saved) Text("Checkout saved.", color = MaterialTheme.colorScheme.primary)
  }
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
  RunContinuityEvidence.INTERRUPTED -> "Sustained walking/stopped interval detected"
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
