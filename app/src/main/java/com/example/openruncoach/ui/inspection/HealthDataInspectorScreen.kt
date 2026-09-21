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
import com.example.openruncoach.data.healthconnect.inspection.HealthConnectDataInspector
import com.example.openruncoach.data.local.OpenRunCoachDatabase
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
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatRunDate
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HealthDataInspectorScreen(runId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val viewModel: HealthDataInspectorViewModel = viewModel(key = "inspector-$runId") {
    val database = OpenRunCoachDatabase.getInstance(context)
    val settings = RunSettingsRepository(context)
    HealthDataInspectorViewModel(
      runId,
      RunRepository(database.runDao(), HealthConnectRepository(context), settings),
      HealthConnectDataInspector(context),
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
  RunMetricType.VO2_MAX -> "VO₂ max"
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
