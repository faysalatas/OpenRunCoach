package com.example.openruncoach.ui.inspection

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.healthconnect.HealthConnectRunMetricsProvider
import com.example.openruncoach.data.healthconnect.inspection.HealthConnectDataInspector
import com.example.openruncoach.data.samsunghealth.SamsungHealthAvailability
import com.example.openruncoach.data.samsunghealth.SamsungHealthDataSource
import com.example.openruncoach.data.samsunghealth.SamsungDeveloperAuthorization
import com.example.openruncoach.data.samsunghealth.SamsungExercisePermissionState
import com.example.openruncoach.data.samsunghealth.SamsungHealthDetection
import com.example.openruncoach.data.samsunghealth.SamsungHealthExerciseInspector
import com.example.openruncoach.data.samsunghealth.SamsungHealthPermissionManager
import com.example.openruncoach.data.samsunghealth.SamsungMatchSelectionRepository
import com.example.openruncoach.data.samsunghealth.SamsungHealthRunMetricsEnricher
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.displaySourceName
import com.example.openruncoach.domain.inspection.CountMetricSummary
import com.example.openruncoach.domain.inspection.MetricAvailability
import com.example.openruncoach.domain.inspection.MetricInspection
import com.example.openruncoach.domain.inspection.RouteInspectionStatus
import com.example.openruncoach.domain.inspection.RouteMetricSummary
import com.example.openruncoach.domain.inspection.RunMetricType
import com.example.openruncoach.domain.inspection.SeriesMetricSummary
import com.example.openruncoach.domain.inspection.SessionMetricSummary
import com.example.openruncoach.domain.inspection.TotalMetricSummary
import com.example.openruncoach.domain.inspection.Vo2MaxMetricSummary
import com.example.openruncoach.domain.inspection.Vo2MaxTiming
import com.example.openruncoach.domain.samsunghealth.ProviderMetric
import com.example.openruncoach.domain.samsunghealth.ExerciseLogMetricSummary
import com.example.openruncoach.domain.samsunghealth.SampleAlignment
import com.example.openruncoach.domain.samsunghealth.SampleAlignmentStatus
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseCandidate
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseKind
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseSessionInspection
import com.example.openruncoach.domain.samsunghealth.SamsungHealthInspection
import com.example.openruncoach.domain.samsunghealth.SamsungMatchConfidence
import com.example.openruncoach.domain.samsunghealth.SamsungMatchReason
import com.example.openruncoach.domain.samsunghealth.SamsungMatchReasonKind
import com.example.openruncoach.domain.samsunghealth.SamsungPhysicalWorkoutRelationship
import com.example.openruncoach.domain.samsunghealth.SamsungSourceDeviceType
import com.example.openruncoach.domain.samsunghealth.SamsungWorkoutMatchStatus
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatRunDate
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import android.app.Activity

@Composable
fun HealthDataInspectorScreen(runId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  val activity = context as? Activity
  val viewModel: HealthDataInspectorViewModel = viewModel(key = "inspector-$runId") {
    val database = environment.database
    val settings = RunSettingsRepository(context)
    val samsungDataSource = SamsungHealthDataSource(context)
    val samsungPermissionManager = SamsungHealthPermissionManager(samsungDataSource)
    HealthDataInspectorViewModel(
      runId,
      RunRepository(database.runDao(), HealthConnectRepository(context), settings),
      HealthConnectDataInspector(context),
      SamsungHealthExerciseInspector(samsungDataSource, samsungPermissionManager),
      samsungPermissionManager,
      SamsungMatchSelectionRepository(context),
      HealthConnectRunMetricsProvider(),
      SamsungHealthRunMetricsEnricher(),
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val permissionLauncher = rememberLauncherForActivityResult(
    PermissionController.createRequestPermissionResultContract()
  ) { viewModel.inspect() }
  val routeLauncher = rememberLauncherForActivityResult(ExerciseRouteRequestContract()) { route ->
    viewModel.onRouteInspectionResult(route?.route?.size)
  }

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    TextButton(onClick = onBack) { Text("‹ Back") }
    Text("Health Data Inspector", style = MaterialTheme.typography.headlineMedium)
    when {
      state.isLoadingRun -> Text("Loading run…")
      state.run == null -> Text("Run not found.")
      !state.run!!.isCanonical -> Text("This diagnostic is available only for the canonical run.")
      else -> {
        RunHeader(state.run!!)
        if (state.isInspecting && state.inspection == null) Text("Inspecting available Health Connect data…")
        state.inspection?.let { inspection ->
          if (inspection.missingPermissions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
              Text("Some metric permissions are missing. Available metrics are still shown.")
              Button(onClick = { permissionLauncher.launch(inspection.missingPermissions) }) {
                Text("Request additional read permissions")
              }
            }
          }
          SamsungHealthSection(
            state = state,
            canRequestPermission = activity != null,
            onRequestPermission = { activity?.let(viewModel::requestSamsungHealthPermission) },
            onResolveIssue = { activity?.let(viewModel::resolveSamsungHealthIssue) },
            onSelectCandidate = viewModel::selectSamsungWorkout,
          )
          state.unifiedMetrics?.let { UnifiedMetricsResult(it, state.run!!, state.samsungHealthInspection) }
          Text("Health Connect details", style = MaterialTheme.typography.titleLarge)
          inspection.metrics.forEach { metric ->
            MetricSection(
              metric,
              onInspectRoute = if (
                metric.metric == RunMetricType.ROUTE &&
                (metric.summary as? RouteMetricSummary)?.status == RouteInspectionStatus.CONSENT_REQUIRED
              ) {
                { routeLauncher.launch(runId) }
              } else null,
            )
          }
          Button(onClick = viewModel::inspect, enabled = !state.isInspecting) { Text("Refresh inspection") }
        }
      }
    }
    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
}

@Composable
private fun UnifiedMetricsResult(
  metrics: RunMetrics,
  run: RunSummary,
  samsungInspection: SamsungHealthInspection?,
) {
  HorizontalDivider()
  Text("Unified RunMetrics Result", style = MaterialTheme.typography.titleLarge)
  listOfNotNull(
    "Duration" to metrics.durationSeconds.source,
    "Distance" to metrics.distanceMeters?.source,
    "Heart Rate" to metrics.averageHeartRate?.source,
    "Speed" to metrics.averageSpeedMetersPerSecond?.source,
    "Cadence" to (metrics.movingAverageCadence ?: metrics.averageCadence)?.source,
    "Elevation" to metrics.elevationGainMeters?.source,
    "VO₂max" to metrics.vo2Max?.source,
    "Power" to metrics.averagePowerWatts?.source,
  ).forEach { (label, source) ->
    if (source != null) DiagnosticValue(label, metricSourceDisplayName(source, run, samsungInspection))
  }
  if (samsungInspection?.enrichmentMatch?.relationship == SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION) {
    Text("Samsung sensor metrics are derived only from samples inside the canonical activity window.")
  }
}

@Composable
private fun SamsungHealthSection(
  state: HealthDataInspectorUiState,
  canRequestPermission: Boolean,
  onRequestPermission: () -> Unit,
  onResolveIssue: () -> Unit,
  onSelectCandidate: (String) -> Unit,
) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
    HorizontalDivider()
    Text("Samsung Health Data SDK", style = MaterialTheme.typography.titleLarge)
    if (state.isInspectingSamsungHealth) Text("Inspecting optional Samsung Health data…")
    val availability = state.samsungHealthAvailability
    if (availability != null) {
      Text("Status: ${availability.message}", style = MaterialTheme.typography.titleMedium)
      DiagnosticValue("SDK library", if (availability.sdkLibraryLoaded) "Loaded" else "Not loaded")
      DiagnosticValue("Samsung Health", availability.samsungHealthDetection.displayName())
      DiagnosticValue("Exercise read permission", availability.exerciseReadPermission.displayName())
      DiagnosticValue("Developer authorization", availability.developerAuthorization.displayName())
      DiagnosticValue("Last SDK operation", availability.lastOperation)
      val exception = availability.lastException
      if (exception == null) {
        DiagnosticValue("Last exception", "None")
      } else {
        DiagnosticValue("Last exception", exception.exceptionClass)
        DiagnosticValue("Error code", exception.errorCode?.toString() ?: "None")
        DiagnosticValue("Error message", exception.errorMessage)
        DiagnosticValue("Resolvable", yesNo(exception.hasResolution))
      }
      if (availability.status == SamsungHealthAvailability.PERMISSION_REQUIRED) {
        Button(onClick = onRequestPermission, enabled = canRequestPermission && !state.isInspectingSamsungHealth) {
          Text("Request Exercise Permission")
        }
      }
      if (exception?.hasResolution == true) {
        Button(onClick = onResolveIssue, enabled = canRequestPermission && !state.isInspectingSamsungHealth) {
          Text("Resolve Samsung Health issue")
        }
      }
    }
    state.samsungHealthInspection?.let { inspection ->
      HorizontalDivider()
      Text("Run Data Comparison", style = MaterialTheme.typography.titleLarge)
      state.run?.let { CanonicalWorkoutSummary(it) }
      ProviderComparison(inspection)
      IdentityMatchSection(inspection)
      EnrichmentMatchSection(inspection, state.run, onSelectCandidate)
      PlausibleSamsungCandidates(inspection, onSelectCandidate)
    }
  }
}

@Composable
private fun DiagnosticValue(label: String, value: String) {
  Text("$label: $value", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ProviderComparison(inspection: SamsungHealthInspection) {
  Text("Health Connect / Samsung Health", style = MaterialTheme.typography.titleMedium)
  inspection.providerMetrics.forEach { metric ->
    Text(
      "${metric.metric.displayName()}: " +
        "${availabilityMark(metric.healthConnectAvailable)} / ${availabilityMark(metric.samsungHealthAvailable)}"
    )
  }
}

@Composable
private fun CanonicalWorkoutSummary(run: RunSummary) {
  Text("Canonical workout", style = MaterialTheme.typography.titleMedium)
  DiagnosticValue("Source", run.displaySourceName)
  DiagnosticValue("Device", listOfNotNull(run.sourceDeviceManufacturer, run.sourceDeviceModel)
    .joinToString(" ").ifBlank {
      run.sourceDeviceType?.name?.lowercase()?.replaceFirstChar(Char::titlecase) ?: "Not available"
    })
  DiagnosticValue("Distance", formatDistance(run.distanceMeters))
  DiagnosticValue("Duration", formatDuration(run.durationSeconds))
}

@Composable
private fun IdentityMatchSection(inspection: SamsungHealthInspection) {
  HorizontalDivider()
  Text("Exact provider copy", style = MaterialTheme.typography.titleMedium)
  val match = inspection.identityMatch
  DiagnosticValue("Confidence", match.confidence.displayName())
  when (match.status) {
    SamsungWorkoutMatchStatus.NOT_FOUND -> Text("No exact provider copy was found in Samsung Health.")
    SamsungWorkoutMatchStatus.AMBIGUOUS -> Text("Multiple provider copies are too similar to identify one exactly.")
    SamsungWorkoutMatchStatus.MATCHED -> inspection.identitySession?.let { session ->
      SamsungCandidateCapabilitySummary(session)
    }
  }
  match.reasons.forEach { MatchReasonText(it) }
}

@Composable
private fun EnrichmentMatchSection(
  inspection: SamsungHealthInspection,
  run: RunSummary?,
  onSelectCandidate: (String) -> Unit,
) {
  HorizontalDivider()
  Text("Best enrichment candidate", style = MaterialTheme.typography.titleMedium)
  val match = inspection.enrichmentMatch
  DiagnosticValue("Confidence", match.confidence.displayName())
  DiagnosticValue("Relationship", match.relationship.displayName())
  when (match.status) {
    SamsungWorkoutMatchStatus.NOT_FOUND ->
      Text("No additional Samsung Health sensor recording was found for this workout.")
    SamsungWorkoutMatchStatus.AMBIGUOUS -> {
      Text("Multiple sensor recordings are plausible. No enrichment was applied.")
      match.candidateScores.filter { score ->
        score.candidate.metrics.heartRateSampleCount > 0 ||
          score.candidate.metrics.speedSampleCount > 0 ||
          score.candidate.metrics.cadenceSampleCount > 0 ||
          score.candidate.metrics.powerSampleCount > 0 ||
          score.candidate.metrics.powerAvailable ||
          score.candidate.metrics.vo2Max != null ||
          score.candidate.metrics.elevationAvailable
      }.forEach { score ->
        Text("${sourceDisplayName(score.candidate.source.applicationId)} · ${deviceDisplayName(score.candidate)}")
        Button(onClick = { onSelectCandidate(score.candidate.id) }) { Text("Use for enrichment") }
      }
    }
    SamsungWorkoutMatchStatus.MATCHED -> inspection.enrichmentSession?.let { session ->
      SamsungCandidateCapabilitySummary(session)
      if (match.manualSelection) Text("This enrichment source was selected manually.")
      if (run != null) {
        DiagnosticValue("Canonical window", "${formatInstant(run.startTime)} – ${formatInstant(run.endTime)}")
        DiagnosticValue("Samsung window", "${formatInstant(session.candidate.startTime)} – ${formatInstant(session.candidate.endTime)}")
        val sensorStart = if (match.relationship == SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION) run.startTime else session.candidate.startTime
        val sensorEnd = if (match.relationship == SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION) run.endTime else session.candidate.endTime
        DiagnosticValue("Sensor window used", "${formatInstant(sensorStart)} – ${formatInstant(sensorEnd)}")
        match.canonicalCoverage?.let {
          DiagnosticValue("Canonical coverage", "${formatNumber(it * 100)}%")
        }
        if (match.relationship == SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION) {
          val extraSeconds = Duration.between(run.endTime, session.candidate.endTime).seconds
          if (extraSeconds > 0) Text("△ Samsung session continues ${formatDuration(extraSeconds)} beyond canonical activity")
          val distanceDifference = if (run.distanceMeters != null && session.candidate.distanceMeters != null) {
            kotlin.math.abs(session.candidate.distanceMeters - run.distanceMeters)
          } else null
          distanceDifference?.let { Text("△ Samsung total distance differs by ${formatNumber(it)} m") }
          Text("Enrichment strategy: Use Samsung sensor samples only inside canonical time window.")
          ClippedSensorSummary(inspection)
          SessionLevelContext(session)
        }
      }
      if (match.relationship == SamsungPhysicalWorkoutRelationship.EXACT_SESSION) {
        SharedDifferences(inspection)
        CadenceDetails(session)
      }
    }
  }
  match.reasons.forEach { MatchReasonText(it) }
}

@Composable
private fun PlausibleSamsungCandidates(
  inspection: SamsungHealthInspection,
  onSelectCandidate: (String) -> Unit,
) {
  var expanded by remember(inspection.plausibleSessions) { mutableStateOf(false) }
  HorizontalDivider()
  TextButton(onClick = { expanded = !expanded }) {
    Text(if (expanded) "Hide all plausible Samsung candidates" else "Show all plausible Samsung candidates (${inspection.plausibleSessions.size})")
  }
  if (expanded) inspection.plausibleSessions.forEachIndexed { index, session ->
    HorizontalDivider()
    Text("Candidate ${index + 1}", style = MaterialTheme.typography.titleMedium)
    SamsungCandidateCapabilitySummary(session, includeIdentity = true)
    inspection.enrichmentMatch.candidateScores.firstOrNull { it.candidate.id == session.candidate.id }?.let { score ->
      DiagnosticValue("Relationship", score.relationship.displayName())
      DiagnosticValue("Canonical coverage", "${formatNumber(score.canonicalCoverage * 100)}%")
    }
    Button(onClick = { onSelectCandidate(session.candidate.id) }) { Text("Use for enrichment") }
  }
}

@Composable
private fun ClippedSensorSummary(inspection: SamsungHealthInspection) {
  val clipped = inspection.clippedEnrichment ?: return
  Text("Clipped sensor summaries", style = MaterialTheme.typography.titleSmall)
  ClippedMetric("Heart rate", clipped.heartRate, "bpm")
  ClippedMetric("Speed", clipped.speed, "m/s")
  ClippedMetric("Cadence", clipped.cadence, "spm")
  clipped.movingAverageCadence?.let { DiagnosticValue("Moving cadence", "${formatNumber(it)} spm") }
  ClippedMetric("Power", clipped.power, "W")
}

@Composable
private fun ClippedMetric(label: String, summary: ExerciseLogMetricSummary?, unit: String) {
  if (summary == null) {
    DiagnosticValue(label, "Not available")
  } else {
    DiagnosticValue(
      label,
      "${summary.sampleCount} samples · avg ${formatNumber(summary.average)} · " +
        "min ${formatNumber(summary.minimum)} · max ${formatNumber(summary.maximum)} $unit",
    )
  }
}

@Composable
private fun SessionLevelContext(session: SamsungExerciseSessionInspection) {
  Text("Samsung session-level context", style = MaterialTheme.typography.titleSmall)
  OptionalMetric("Samsung session VO₂max", session.vo2Max, "ml/kg/min")
  OptionalMetric("Samsung session elevation gain", session.altitudeGainMeters, "m")
  OptionalMetric("Samsung session altitude min / max", session.minAltitudeMeters, "m", maximum = session.maxAltitudeMeters)
  OptionalMetric("Samsung session calories", session.caloriesKilocalories, "kcal")
  Text("Source session extends beyond canonical activity; these full-session values are diagnostic only.")
}

@Composable
private fun SamsungCandidateCapabilitySummary(
  session: SamsungExerciseSessionInspection,
  includeIdentity: Boolean = false,
) {
  val candidate = session.candidate
  DiagnosticValue("Source", sourceDisplayName(candidate.source.applicationId))
  DiagnosticValue("Device", deviceDisplayName(candidate))
  DiagnosticValue("Distance", formatDistance(candidate.distanceMeters))
  DiagnosticValue("Duration", formatDuration(candidate.durationSeconds))
  DiagnosticValue("ExerciseLog", if (candidate.metrics.exerciseLogExists) "${candidate.metrics.totalLogSamples} samples" else "Not available")
  DiagnosticValue("Heart rate", capability(candidate.metrics.heartRateSampleCount, session.meanHeartRate != null || session.maxHeartRate != null))
  DiagnosticValue("Speed", capability(candidate.metrics.speedSampleCount, session.meanSpeedMetersPerSecond != null))
  DiagnosticValue("Cadence", capability(candidate.metrics.cadenceSampleCount, session.meanCadence != null))
  DiagnosticValue("VO₂max", availabilityMark(session.vo2Max != null))
  DiagnosticValue("Elevation", availabilityMark(candidate.metrics.elevationAvailable))
  DiagnosticValue("Power", capability(candidate.metrics.powerSampleCount, session.meanPowerWatts != null))
  if (includeIdentity) {
    DiagnosticValue("Application ID", candidate.source.applicationId ?: "Not available")
    DiagnosticValue("Exercise type", candidate.exerciseKind.displayName())
    DiagnosticValue("Start / end", "${formatInstant(candidate.startTime)} – ${formatInstant(candidate.endTime)}")
    DiagnosticValue("Data-point UID", candidate.source.uid)
  }
}

private fun capability(sampleCount: Int, summaryAvailable: Boolean): String = when {
  sampleCount > 0 -> "$sampleCount samples"
  summaryAvailable -> "Summary available"
  else -> "Not available"
}

private fun sourceDisplayName(applicationId: String?): String = when (applicationId) {
  "com.sec.android.app.shealth" -> "Samsung Health"
  "com.strava" -> "Strava"
  null, "" -> "Not available"
  else -> applicationId
}

private fun deviceDisplayName(candidate: SamsungExerciseCandidate): String =
  listOfNotNull(candidate.source.manufacturer, candidate.source.deviceName ?: candidate.source.model)
    .distinct().joinToString(" ").ifBlank { candidate.source.deviceType.displayName() }

@Composable
private fun MatchReasonText(reason: SamsungMatchReason) {
  val prefix = when (reason.kind) {
    SamsungMatchReasonKind.POSITIVE -> "✓"
    SamsungMatchReasonKind.NEGATIVE -> "!"
    SamsungMatchReasonKind.NEUTRAL -> "•"
  }
  Text("$prefix ${reason.text}", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SamsungSessionDetails(session: SamsungExerciseSessionInspection) {
  HorizontalDivider()
  Text("Samsung ExerciseSession", style = MaterialTheme.typography.titleMedium)
  Text("${session.candidate.exerciseKind.displayName()} · ${formatInstant(session.candidate.startTime)} – ${formatInstant(session.candidate.endTime)}")
  Text("Duration: ${formatDuration(session.candidate.durationSeconds)}")
  OptionalMetric("Distance", session.candidate.distanceMeters, "m")
  OptionalMetric("Calories", session.caloriesKilocalories, "kcal")
  OptionalMetric("Mean / min / max HR", session.meanHeartRate, "bpm", session.minHeartRate, session.maxHeartRate)
  OptionalMetric("Mean / max speed", session.meanSpeedMetersPerSecond, "m/s", maximum = session.maxSpeedMetersPerSecond)
  OptionalMetric("Mean / max power", session.meanPowerWatts, "W", maximum = session.maxPowerWatts)
  OptionalMetric("VO₂max", session.vo2Max, "ml/kg/min")
  OptionalMetric("Altitude gain / loss", session.altitudeGainMeters, "m", maximum = session.altitudeLossMeters)
  OptionalMetric("Min / max altitude", session.minAltitudeMeters, "m", maximum = session.maxAltitudeMeters)
  OptionalMetric("Incline / decline distance", session.inclineDistanceMeters, "m", maximum = session.declineDistanceMeters)
  session.customTitle?.let { Text("Title: $it") }
  session.comment?.let { Text("Comment: $it") }
  Text("Source", style = MaterialTheme.typography.titleSmall)
  DiagnosticValue("Application ID", session.candidate.source.applicationId ?: "Not available")
  DiagnosticValue("Device type", session.candidate.source.deviceType.displayName())
  DiagnosticValue("Manufacturer", session.candidate.source.manufacturer ?: "Not available")
  DiagnosticValue("Model", session.candidate.source.model ?: "Not available")
  DiagnosticValue("Device name", session.candidate.source.deviceName ?: "Not available")
  DiagnosticValue("Device ID", session.candidate.source.deviceId ?: "Not available")
  DiagnosticValue("Data-point UID", session.candidate.source.uid)
  DiagnosticValue("Data-point start", session.candidate.source.dataPointStartTime?.let(::formatInstant) ?: "Not available")
  DiagnosticValue("Data-point end", session.candidate.source.dataPointEndTime?.let(::formatInstant) ?: "Not available")
  DiagnosticValue("Updated", session.candidate.source.updateTime?.let(::formatInstant) ?: "Not available")
  ExerciseLogSummary("Heart rate", session.heartRateLogSummary, "bpm")
  ExerciseLogSummary("Speed", session.speedLogSummary, "m/s")
  ExerciseLogSummary("Power", session.powerLogSummary, "W")
  ExerciseLogSummary("Count", session.countLogSummary, "count")
}

@Composable
private fun ExerciseLogSummary(label: String, summary: ExerciseLogMetricSummary?, unit: String) {
  if (summary == null) {
    Text("ExerciseLog $label: No samples")
    return
  }
  Text("ExerciseLog $label: ${summary.sampleCount} samples")
  Text(
    "Avg ${formatNumber(summary.average)} $unit · " +
      "min ${formatNumber(summary.minimum)} · max ${formatNumber(summary.maximum)}"
  )
  Text("${formatInstant(summary.firstTimestamp)} – ${formatInstant(summary.lastTimestamp)}")
  summary.averageSampleIntervalSeconds?.let { Text("Average interval: ${formatNumber(it)} sec") }
}

@Composable
private fun OptionalMetric(
  label: String,
  value: Double?,
  unit: String,
  minimum: Double? = null,
  maximum: Double? = null,
) {
  val values = listOfNotNull(value, minimum, maximum)
  Text("$label: ${if (values.isEmpty()) "Not available" else values.joinToString(" / ") { formatNumber(it) } + " $unit"}")
}

@Composable
private fun SharedDifferences(inspection: SamsungHealthInspection) {
  if (inspection.sharedDifferences.isEmpty()) return
  HorizontalDivider()
  Text("Shared metric differences", style = MaterialTheme.typography.titleMedium)
  inspection.sharedDifferences.forEach { difference ->
    val sign = if (difference.absoluteDifference >= 0) "+" else ""
    Text(
      "${difference.metric.displayName()}: Health Connect ${formatNumber(difference.healthConnectValue)}, " +
        "Samsung ${formatNumber(difference.samsungHealthValue)} " +
        "($sign${formatNumber(difference.absoluteDifference)} ${difference.unit})"
    )
  }
  Text("Small differences can reflect provider processing and are diagnostic only.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun CadenceDetails(session: SamsungExerciseSessionInspection) {
  val cadence = session.cadence
  HorizontalDivider()
  Text("Cadence Investigation", style = MaterialTheme.typography.titleMedium)
  Text("Session mean cadence: ${cadence.sessionMeanCadence?.let { "${formatNumber(it)} spm" } ?: "Not available"}")
  Text("Session max cadence: ${cadence.sessionMaxCadence?.let { "${formatNumber(it)} spm" } ?: "Not available"}")
  Text("ExerciseLog exists: ${yesNo(cadence.logExists)}")
  Text("ExerciseLog contains cadence: ${yesNo(cadence.cadenceSampleCount > 0)}")
  Text("${cadence.totalLogSamples} total log samples")
  Text("${cadence.cadenceSampleCount} cadence samples")
  OptionalMetric("Average / minimum / maximum cadence", cadence.averageCadence, "spm", cadence.minimumCadence, cadence.maximumCadence)
  cadence.firstCadenceTimestamp?.let { first ->
    Text("Cadence range: ${formatInstant(first)} – ${formatInstant(cadence.lastCadenceTimestamp ?: first)}")
  }
  Text("Average interval: ${cadence.averageSampleIntervalSeconds?.let { "${formatNumber(it)} sec" } ?: "Not available"}")
  Text("Aligned with HR: ${cadence.heartRateAlignment.displayName()}")
  Text("Aligned with speed: ${cadence.speedAlignment.displayName()}")
  Text(
    if (cadence.detailedCadenceAvailable) "Result: Detailed cadence available through Samsung Health SDK"
    else "Result: No detailed cadence data available for this workout",
    style = MaterialTheme.typography.titleSmall,
  )
}

@Composable
private fun RunHeader(run: RunSummary) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(formatRunDate(run.startTime), style = MaterialTheme.typography.titleLarge)
    Text(formatDistance(run.distanceMeters))
    Text(run.displaySourceName)
    listOfNotNull(run.sourceDeviceManufacturer, run.sourceDeviceModel)
      .joinToString(" ").takeIf(String::isNotBlank)?.let { Text(it) }
  }
}

@Composable
private fun MetricSection(metric: MetricInspection, onInspectRoute: (() -> Unit)?) {
  var showDetails by remember(metric.metric) { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(metric.metric.displayName(), style = MaterialTheme.typography.titleMedium)
      Text(metric.availability.displayName(), color = availabilityColor(metric.availability))
    }
    metric.sourceAppName?.let { Text("Source: $it") }
    Summary(metric)
    metric.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    metric.alternateSources.forEach { alternate ->
      Text("Available from ${alternate.sourceAppName} (${alternate.recordCount} records)")
      Text(alternate.note, style = MaterialTheme.typography.bodySmall)
    }
    if (onInspectRoute != null) Button(onClick = onInspectRoute) { Text("Inspect Route") }
    if (metric.recordCount != null || metric.sourcePackageName != null || metric.recordIds.isNotEmpty()) {
      TextButton(onClick = { showDetails = !showDetails }) {
        Text(if (showDetails) "Hide details" else "Show details")
      }
    }
    if (showDetails) {
      metric.recordCount?.let { Text("Health Connect records: $it") }
      metric.sourcePackageName?.let { Text("Package: $it") }
      if (metric.recordIds.isNotEmpty()) Text("Record IDs: ${metric.recordIds.joinToString()}")
    }
  }
}

@Composable
private fun Summary(metric: MetricInspection) {
  when (val summary = metric.summary) {
    is SessionMetricSummary -> {
      Text(summary.exerciseType)
      Text("${formatInstant(summary.startTime)} – ${formatInstant(summary.endTime)}")
      Text("Duration: ${formatDuration(summary.durationSeconds)}")
      summary.title?.let { Text("Title: $it") }
      summary.notes?.let { Text("Notes: $it") }
      listOfNotNull(summary.deviceManufacturer, summary.deviceModel)
        .joinToString(" ").takeIf(String::isNotBlank)?.let { Text("Device: $it") }
    }
    is SeriesMetricSummary -> {
      Text("${summary.sampleCount} samples")
      Text("Avg ${formatNumber(summary.average)} ${summary.unit} · Min ${formatNumber(summary.minimum)} · Max ${formatNumber(summary.maximum)}")
      Text("Samples ${formatInstant(summary.firstSampleTime)} – ${formatInstant(summary.lastSampleTime)}")
    }
    is TotalMetricSummary -> Text("${formatNumber(summary.value)} ${summary.unit}")
    is CountMetricSummary -> Text("${summary.count} steps")
    is Vo2MaxMetricSummary -> {
      Text("${formatNumber(summary.valueMillilitersPerMinuteKilogram)} ml/kg/min")
      Text(if (summary.timing == Vo2MaxTiming.DURING_WORKOUT) "Recorded during workout" else "Recorded near workout")
      Text("${formatInstant(summary.timestamp)} · ${summary.measurementMethod}")
    }
    is RouteMetricSummary -> {
      Text(summary.status.displayName())
      summary.pointCount?.let { Text("$it route points available") }
    }
    null -> if (metric.availability == MetricAvailability.NOT_AVAILABLE) Text("No data recorded for this workout")
  }
}

@Composable
private fun availabilityColor(availability: MetricAvailability) = when (availability) {
  MetricAvailability.AVAILABLE -> MaterialTheme.colorScheme.primary
  MetricAvailability.ERROR -> MaterialTheme.colorScheme.error
  else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun RunMetricType.displayName() = when (this) {
  RunMetricType.EXERCISE_SESSION -> "Exercise Session"
  RunMetricType.DISTANCE -> "Distance"
  RunMetricType.HEART_RATE -> "Heart Rate"
  RunMetricType.SPEED -> "Speed"
  RunMetricType.CADENCE -> "Cadence"
  RunMetricType.VO2_MAX -> "VO₂max"
  RunMetricType.ELEVATION_GAINED -> "Elevation Gained"
  RunMetricType.POWER -> "Power"
  RunMetricType.CALORIES -> "Calories"
  RunMetricType.STEPS -> "Steps"
  RunMetricType.ROUTE -> "Route"
}

private fun MetricAvailability.displayName() = when (this) {
  MetricAvailability.AVAILABLE -> "✓ Available"
  MetricAvailability.NOT_AVAILABLE -> "No data"
  MetricAvailability.PERMISSION_REQUIRED -> "Permission required"
  MetricAvailability.UNSUPPORTED -> "Unsupported"
  MetricAvailability.ERROR -> "Read error"
}

private fun RouteInspectionStatus.displayName() = name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

private fun formatNumber(value: Double): String = "%.1f".format(Locale.getDefault(), value)

private fun formatInstant(value: Instant): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
  .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault()).format(value)

private fun SamsungHealthDetection.displayName() = when (this) {
  SamsungHealthDetection.DETECTED -> "Detected"
  SamsungHealthDetection.NOT_DETECTED -> "Not detected"
  SamsungHealthDetection.UNKNOWN -> "Unable to determine"
}

private fun SamsungExercisePermissionState.displayName() = when (this) {
  SamsungExercisePermissionState.GRANTED -> "Granted"
  SamsungExercisePermissionState.NOT_GRANTED -> "Not granted"
  SamsungExercisePermissionState.UNABLE_TO_CHECK -> "Unable to check"
}

private fun SamsungDeveloperAuthorization.displayName() = when (this) {
  SamsungDeveloperAuthorization.WORKING -> "Working"
  SamsungDeveloperAuthorization.FAILED -> "Failed"
  SamsungDeveloperAuthorization.UNKNOWN -> "Unknown"
}

private fun ProviderMetric.displayName() = when (this) {
  ProviderMetric.DISTANCE -> "Distance"
  ProviderMetric.DURATION -> "Duration"
  ProviderMetric.AVERAGE_HEART_RATE -> "Average HR"
  ProviderMetric.MAX_HEART_RATE -> "Max HR"
  ProviderMetric.SPEED -> "Speed"
  ProviderMetric.VO2_MAX -> "VO₂max"
  ProviderMetric.CADENCE -> "Cadence"
  ProviderMetric.POWER -> "Power"
  ProviderMetric.ELEVATION -> "Elevation"
}

private fun SamsungExerciseKind.displayName() = when (this) {
  SamsungExerciseKind.RUNNING -> "Running"
  SamsungExerciseKind.TRACK_RUNNING -> "Track running"
  SamsungExerciseKind.OTHER -> "Other"
}

private fun SamsungMatchConfidence.displayName() = name.lowercase()
  .replaceFirstChar { it.titlecase() }

private fun SamsungPhysicalWorkoutRelationship.displayName() = when (this) {
  SamsungPhysicalWorkoutRelationship.EXACT_SESSION -> "Exact session"
  SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION -> "Overlapping session"
  SamsungPhysicalWorkoutRelationship.NO_MATCH -> "No match"
  SamsungPhysicalWorkoutRelationship.AMBIGUOUS -> "Ambiguous"
}

private fun SamsungSourceDeviceType.displayName() = name.lowercase()
  .replaceFirstChar { it.titlecase() }

private fun MetricSource.displayName() = when (this) {
  MetricSource.HEALTH_CONNECT -> "Health Connect"
  MetricSource.SAMSUNG_HEALTH -> "Samsung Health"
  MetricSource.DERIVED -> "Derived"
}

private fun metricSourceDisplayName(
  source: MetricSource,
  run: RunSummary,
  samsungInspection: SamsungHealthInspection?,
): String = when (source) {
  MetricSource.HEALTH_CONNECT -> "Health Connect / ${run.displaySourceName}"
  MetricSource.SAMSUNG_HEALTH -> samsungInspection?.enrichmentSession?.candidate?.let { candidate ->
    "Samsung Health / ${deviceDisplayName(candidate)}"
  } ?: "Samsung Health"
  MetricSource.DERIVED -> "Derived"
}

private fun metricValue(value: Double?, unit: String): String =
  value?.let { "${formatNumber(it)} $unit" } ?: "Not available"

private fun metricPair(first: Double?, second: Double?, unit: String): String = when {
  first == null && second == null -> "Not available"
  else -> "${first?.let(::formatNumber) ?: "–"} / ${second?.let(::formatNumber) ?: "–"} $unit"
}

private fun SampleAlignment.displayName(): String {
  val label = when (status) {
    SampleAlignmentStatus.ALIGNED -> "Approximately aligned"
    SampleAlignmentStatus.PARTIALLY_ALIGNED -> "Partially aligned"
    SampleAlignmentStatus.NOT_ALIGNED -> "Not aligned"
    SampleAlignmentStatus.NO_COMPARISON_DATA -> "No comparison data"
  }
  return if (cadenceSamples > 0 && status != SampleAlignmentStatus.NO_COMPARISON_DATA) {
    "$label ($alignedSamples/$cadenceSamples samples)"
  } else label
}

private fun availabilityMark(available: Boolean) = if (available) "✓" else "–"

private fun yesNo(value: Boolean) = if (value) "Yes" else "No"
