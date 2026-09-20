package com.example.openruncoach.ui.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.RunSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun HealthConnectScreen(onRunClick: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val viewModel: HealthConnectViewModel =
    viewModel {
      val healthConnectRepository = HealthConnectRepository(context)
      val runDao = OpenRunCoachDatabase.getInstance(context).runDao()
      HealthConnectViewModel(healthConnectRepository, RunRepository(runDao, healthConnectRepository))
    }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val permissionLauncher =
    rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
      viewModel.refreshStatus()
    }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshStatus() }

  Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val recentRuns = state.recentRuns
    Text("OpenRunCoach", style = MaterialTheme.typography.headlineMedium)
    Text("Latest Run", style = MaterialTheme.typography.titleLarge)

    when {
      state.historyError -> Text("Could not load saved runs.")
      recentRuns == null -> Text("Loading saved runs...")
      recentRuns.isEmpty() -> Text("No runs saved yet. Tap Sync runs to import them.")
      else -> LatestRunDetails(recentRuns.first(), onRunClick)
    }

    Text("Run History", style = MaterialTheme.typography.titleLarge)
    when {
      state.historyError -> Text("Could not load run history.")
      recentRuns == null -> Text("Loading run history...")
      recentRuns.isEmpty() -> Text("No runs in history.")
      else -> recentRuns.forEach { run -> HistoryRunDetails(run, onRunClick) }
    }

    when {
      state.accessError -> Text("Could not check Health Connect access.")
      state.isAvailable == false -> Text("Health Connect unavailable")
      state.hasAllPermissions == false -> Text("Health Connect permissions not granted")
      state.hasAllPermissions == true -> Text("Health Connect permissions granted")
    }
    if (state.isAvailable == true && state.hasAllPermissions == false) {
      Button(onClick = { permissionLauncher.launch(viewModel.requiredPermissions) }) {
        Text("Request permissions")
      }
    }

    Button(onClick = viewModel::syncRuns, enabled = state.syncState != SyncState.Syncing) {
      Text("Sync runs")
    }
    when (val sync = state.syncState) {
      SyncState.Idle -> Unit
      SyncState.Syncing -> Text("Syncing runs...")
      is SyncState.Success -> Text("Synced ${sync.count} runs.")
      SyncState.PermissionRequired -> Text("Grant Health Connect permissions before syncing.")
      SyncState.Unavailable -> Text("Health Connect is unavailable for syncing.")
      SyncState.Error -> Text("Could not sync runs. Saved runs are unchanged.")
    }
  }
}

@Composable
private fun LatestRunDetails(run: RunSummary, onRunClick: (String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Date: ${formatDate(run.startTime)}")
    Text("Distance: ${formatDistance(run.distanceMeters)}")
    Text("Duration: ${formatDuration(run.durationSeconds)}")
    Text("Average Pace: ${formatPace(run.averagePaceSecondsPerKm)}")
    Text("Average HR: ${run.averageHeartRate?.let { "${it.roundToLong()} bpm" } ?: "—"}")
    Text("Max HR: ${run.maxHeartRate?.let { "$it bpm" } ?: "—"}")
    TextButton(onClick = { onRunClick(run.healthConnectRecordId) }) { Text("View feedback") }
  }
}

@Composable
private fun HistoryRunDetails(run: RunSummary, onRunClick: (String) -> Unit) {
  Column(modifier = Modifier.padding(bottom = 8.dp)) {
    Text("Date: ${formatDate(run.startTime)}")
    Text("Distance: ${formatDistance(run.distanceMeters)}")
    Text("Duration: ${formatDuration(run.durationSeconds)}")
    Text("Average Pace: ${formatPace(run.averagePaceSecondsPerKm)}")
    TextButton(onClick = { onRunClick(run.healthConnectRecordId) }) { Text("View feedback") }
  }
}

internal fun formatDate(startTime: Instant): String =
  DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(startTime)

internal fun formatDistance(distanceMeters: Double?): String =
  distanceMeters?.let { "%.2f km".format(Locale.getDefault(), it / 1000) } ?: "—"

internal fun formatDuration(seconds: Long): String =
  "%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)

internal fun formatPace(paceSecondsPerKm: Double?): String =
  paceSecondsPerKm?.roundToLong()?.let { seconds ->
    "%d:%02d min/km".format(seconds / 60, seconds % 60)
  } ?: "—"
