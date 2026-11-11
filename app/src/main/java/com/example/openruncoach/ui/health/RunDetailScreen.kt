package com.example.openruncoach.ui.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.example.openruncoach.theme.*
import com.example.openruncoach.ui.components.*
import kotlinx.coroutines.launch

import androidx.compose.foundation.horizontalScroll
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
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.data.WorkoutAssignmentState
import com.example.openruncoach.data.WorkoutResponseRepository
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
import com.example.openruncoach.domain.workout.VolumeCompletion
import com.example.openruncoach.domain.workout.displayName
import com.example.openruncoach.ui.common.coachingActionLabel
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatActualDuration
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.OpenRunSection
import com.example.openruncoach.ui.components.OpenRunEvidenceRow
import com.example.openruncoach.ui.components.OpenRunCard
import com.example.openruncoach.ui.components.OpenRunDisclosure
import com.example.openruncoach.ui.components.RunRouteMap
import com.example.openruncoach.theme.OpenRunSpacing
import java.util.Locale
import java.time.format.DateTimeFormatter

@Composable
fun RunDetailScreen(
  runId: String,
  onBack: () -> Unit,
  onInspectHealthData: (String) -> Unit,
  modifier: Modifier = Modifier,
  onCoachAnalysis: (String) -> Unit = {},
  onCheckout: (String) -> Unit = {},
) {
  val viewModel = runDetailViewModel(runId)
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  RunDetailContent(state, LocalOpenRunCoachEnvironment.current.clock, DetailActions(
    onBack = onBack, inspect = { onInspectHealthData(runId) }, coach = { onCoachAnalysis(runId) }, checkout = { onCheckout(runId) },
    refreshMetrics = viewModel::refreshMetrics, toggleSourceDetails = viewModel::toggleSourceDetails,
    toggleMetricDetails = viewModel::toggleMetricDetails, toggleRunRelevance = viewModel::toggleRunRelevance,
    showAssignmentCandidates = viewModel::showAssignmentCandidates, hideAssignmentCandidates = viewModel::hideAssignmentCandidates,
    assignToWorkout = viewModel::assignToWorkout, unassignWorkout = viewModel::unassignWorkout,
  ), modifier, !LocalOpenRunCoachEnvironment.current.isSimulation)
}

@Composable
internal fun runDetailViewModel(runId: String): RunDetailViewModel {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  return viewModel(key = runId) {
    val database = environment.database
    val settingsRepository = RunSettingsRepository(context)
    val profileRepository = environment.profileRepository()
    val metricsRefresher = if (environment.isSimulation) null else {
      RunMetricsAutoRefreshCoordinator.create(context, database, environment.clock)
    }
    RunDetailViewModel(
      runId,
      RunRepository(
        database.runDao(),
        HealthConnectRepository(context),
        settingsRepository,
        clock = environment.clock,
        postRunReconciler = environment.postRunReconciler(),
      ),
      RunFeedbackRepository(database, environment.clock, athleteProfileRepository = profileRepository),
      RunMetricsRepository(database.runMetricsDao()),
      RunRouteRepository(database.runRouteDao()),
      metricsRefresher,
      WorkoutExecutionRepository(database, environment.clock, athleteProfileRepository = profileRepository),
      WorkoutResponseRepository(database),
      CoachingEngine(environment.clock),
    )
  }
}

internal data class DetailActions(
  val onBack: () -> Unit = {}, val inspect: () -> Unit = {}, val coach: () -> Unit = {}, val checkout: () -> Unit = {},
  val refreshMetrics: () -> Unit = {}, val toggleSourceDetails: () -> Unit = {},
  val toggleMetricDetails: () -> Unit = {}, val toggleRunRelevance: () -> Unit = {},
  val showAssignmentCandidates: () -> Unit = {}, val hideAssignmentCandidates: () -> Unit = {},
  val assignToWorkout: (String) -> Unit = {}, val unassignWorkout: () -> Unit = {},
)

@Composable
internal fun RunDetailContent(state: RunDetailUiState, clock: java.time.Clock, actions: DetailActions = DetailActions(),
  modifier: Modifier = Modifier, showProviderActions: Boolean = true,
  scroll: androidx.compose.foundation.ScrollState = rememberScrollState()) {
  val p = LocalOpenRunPalette.current
  var confirmVisibility by rememberSaveable { mutableStateOf(false) }
  val run = state.run
  CompositionLocalProvider(LocalContentColor provides p.ink) {
    Column(modifier.fillMaxSize().background(p.bg).verticalScroll(scroll).testTag("run-detail-scroll")) {
      OpenRunHero(title = runTitle(state), backLabel = "Runs", onBack = actions.onBack,
        subtitle = run?.let { runSubtitle(state, clock) }) {
        if (run != null) BoxWithConstraints(Modifier.fillMaxWidth()) {
          val columns = if (maxWidth < 96.dp * 3 * LocalDensity.current.fontScale) 1 else 3
          val metrics = listOfNotNull(
            run.distanceMeters?.let { formatDistance(it).removeSuffix(" km") to "km" },
            (formatActualDuration(run.durationSeconds) to "time"),
            run.averagePaceSecondsPerKm?.let { paceClock(it) to "pace /km" })
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              row.forEach { (value,label) -> OpenRunMetric(value, label, Modifier.weight(1f), onHero = true) }
            } }
          }
        }
      }
      Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        OpenRunHeroBody(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(bottom = 24.dp)) {
          when {
            !state.runLoaded || (run != null && !state.associationLoaded) -> Text("Loading run…", style = OpenRunType.body)
            run == null -> OpenRunCard { Text("Run not found.", style = OpenRunType.body) }
            else -> {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.workoutResponse?.adherence == com.example.openruncoach.domain.workout.PrescriptionAdherence.AS_PLANNED)
                  OpenRunStatusChip("As planned", tone = OpenRunStatusTone.POSITIVE)
                state.plannedWorkout.wholeRunPaceTarget()?.let {
                  val label = "${paceClock(it.minimumSecondsPerKm)}–${paceClock(it.maximumSecondsPerKm)} /km"
                  OpenRunStatusChip(if (state.workoutResponse?.paceResponse == PaceResponse.ON_TARGET) "Pace in target $label" else "Target $label")
                }
                state.feedback?.let { OpenRunStatusChip("Effort ${it.rpe} / 10") }
              }
              OpenRunSectionHeader("YOUR COACH")
              val coach = coachPresentation(state)
              val summary = listOfNotNull(coach.observations.firstOrNull(), coach.implication).joinToString(". ")
              Text(summary.ifBlank { if (state.feedback == null) "Complete the post-run checkout to receive a coaching interpretation."
                else "No coaching decision is available for this run." }, style = OpenRunType.body)
              state.routeData?.route?.points?.takeIf { it.size >= 2 }?.let {
                RunRouteMap(it, Modifier.fillMaxWidth().height(170.dp).clip(OpenRunShapeTokens.Card))
              }
              if (state.isUpdatingMetrics) Text("Updating run data…", style = OpenRunType.meta)
              if (state.metricsUpdateError) Text("Could not update run metrics. Saved data is still available.", color = MaterialTheme.colorScheme.error)
              state.metrics?.let { RunChartsSection(it, state.charts, state.plannedWorkout.wholeRunPaceTarget()) }
              if (state.metrics?.averageHeartRate == null && run.averageHeartRate != null) OpenRunCard {
                Text("Recorded heart rate", style = OpenRunType.title)
                Text(listOfNotNull(run.averageHeartRate?.let { "avg ${formatNumber(it)}" }, run.maxHeartRate?.let { "max $it" }).joinToString(" · ") + " bpm", style = OpenRunType.meta)
              }
              if (state.routeData?.splits?.isNotEmpty() == true) OpenRunCard {
                Text("Splits", style = OpenRunType.title)
                val splits = state.routeData.splits.filter { it.averagePaceSecondsPerKm.isFinite() && it.averagePaceSecondsPerKm > 0 }
                SplitBars(splits.map { SplitBar(it.kilometreLabel(), (1000 / it.averagePaceSecondsPerKm).toFloat(), paceClock(it.averagePaceSecondsPerKm)) },
                  "Recorded split pace. Width is proportional to speed; the fastest split fills its track. " + splits.joinToString { "${it.kilometreLabel()}: ${paceClock(it.averagePaceSecondsPerKm)} per km" })
                SplitSummary(state)
                OpenRunDisclosure("Split details") { CompletedSplits(state) }
              }
              OpenRunCard {
                Text("Plan vs. run", style = OpenRunType.title)
                CompletedAnalysis(state, actions)
              }
              val cadence = state.metrics?.let { it.movingAverageCadence ?: it.averageCadence }
              val elevation = state.metrics?.elevationGainMeters
              if (cadence != null || elevation != null) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                cadence?.let { OpenRunMiniStatCard("Cadence", "%.0f".format(it.value), "spm", modifier = Modifier.widthIn(min = 140.dp).weight(1f)) }
                elevation?.let { OpenRunMiniStatCard("Elevation", "+${formatNumber(it.value)}", "m", modifier = Modifier.widthIn(min = 140.dp).weight(1f)) }
              }
              OpenRunCard {
                OpenRunSettingsRow("Post-run checkout", if (state.feedback == null) "Complete post-run checkout ›" else "View or edit how this run felt", onClick = actions.checkout, firstRow = true)
                OpenRunSettingsRow("Coach analysis", "What this run means for your plan", onClick = actions.coach)
                if (showProviderActions && run.isCanonical) {
                  OpenRunSettingsRow("Health data inspector", "Where each number comes from", onClick = actions.inspect)
                }
              }
              CompletedCoach(state)
              RunDataSources(state, showProviderActions, actions)
              OpenRunSecondaryButton(if (run.relevance == RunRelevance.IGNORED) "Restore run" else "Ignore run",
                { confirmVisibility = true }, enabled = !state.isUpdatingRelevance)
            }
          }
          state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
      }
    }
    if (confirmVisibility && run != null) AlertDialog(onDismissRequest = { confirmVisibility = false },
      title = { Text(if (run.relevance == RunRelevance.IGNORED) "Restore run?" else "Ignore run?") },
      text = { Text(if (run.relevance == RunRelevance.IGNORED) "This run will return to your history and training evidence."
        else "This run will be hidden from normal history and training evidence. You can restore it from Ignored runs.") },
      confirmButton = { TextButton({ confirmVisibility = false; actions.toggleRunRelevance() }) { Text("Confirm") } },
      dismissButton = { TextButton({ confirmVisibility = false }) { Text("Cancel") } })
  }
}

@Composable
private fun RunDataSources(state: RunDetailUiState, showProviderActions: Boolean, actions: DetailActions) {
  val run = state.run ?: return
  OpenRunDisclosure("Data sources") {
    LabelValue("Summary", run.displaySourceName)
    state.routeData?.route?.let { LabelValue("Route", it.sourceAppName?.takeIf(String::isNotBlank) ?: it.source.displayName()) }
    state.metrics?.let { metrics ->
      (metrics.movingAverageCadence ?: metrics.averageCadence)?.let { LabelValue("Cadence", it.sourceAppName?.takeIf(String::isNotBlank) ?: it.source.displayName()) }
      metrics.averageHeartRate?.let { LabelValue("Heart rate", it.sourceAppName?.takeIf(String::isNotBlank) ?: it.source.displayName()) }
    }
    OpenRunDisclosure("Recording diagnostics") {
      if (state.charts?.pace?.hasClippedPaceSamples(state.plannedWorkout.wholeRunPaceTarget()) == true)
        Text("Off-scale pace samples clipped for display", style = OpenRunType.meta)
      SourceCard(state, actions.toggleSourceDetails)
      state.metrics?.let { RunMetricsCard(it, state.showMetricDetails, actions.toggleMetricDetails) }
      if (showProviderActions) TextButton(actions.refreshMetrics, enabled = !state.isUpdatingMetrics) { Text("Refresh metrics") }
    }
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
private fun CompletedAnalysis(state: RunDetailUiState, viewModel: DetailActions) {
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
private fun WorkoutAssociationCard(state: RunDetailUiState, viewModel: DetailActions) {
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
      Button(onClick = viewModel.showAssignmentCandidates, enabled = !state.isUpdatingAssociation) {
        Text("Assign to workout")
      }
    } else {
      val distance = remember(assigned) { WorkoutEstimateCalculator.calculate(assigned).estimatedTotalDistanceMeters }
        ?.let(::formatDistance)
      Text(listOfNotNull(assigned.name.ifBlank { assigned.type.displayName() }, distance).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        TextButton(onClick = viewModel.showAssignmentCandidates, enabled = !state.isUpdatingAssociation,
          contentPadding = PaddingValues(horizontal = 0.dp)) {
          Text("Change association")
        }
        TextButton(onClick = viewModel.unassignWorkout, enabled = !state.isUpdatingAssociation,
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
      TextButton(onClick = viewModel.hideAssignmentCandidates) { Text("Cancel") }
    }
  }
}

@Composable
private fun CompletedCoach(state: RunDetailUiState) {
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
private fun FeedbackSummary(feedback: RunFeedback) {
  LabelValue("RPE", "${feedback.rpe}/10")
  LabelValue("Pain", feedback.painLevel.displayName())
  LabelValue("Muscle fatigue", feedback.muscleFatigue.displayName())
  LabelValue("Completion", feedback.completionStatus.displayName())
  feedback.readiness?.let { LabelValue("Readiness", "$it/5") }
  feedback.notes?.let { LabelValue("Notes", it) }
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
