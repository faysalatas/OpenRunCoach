package com.example.openruncoach.ui.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.openruncoach.domain.RunSummary
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun HealthConnectScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val viewModel: HealthConnectViewModel =
    viewModel { HealthConnectViewModel(HealthConnectRepository(context)) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val permissionLauncher =
    rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
      viewModel.refresh()
    }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

  Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("OpenRunCoach", style = MaterialTheme.typography.headlineMedium)
    Text("Latest Run", style = MaterialTheme.typography.titleLarge)

    when (val latestRun = state.latestRun) {
      LatestRunState.Loading -> Text("Loading latest run...")
      LatestRunState.Unavailable -> Text("Health Connect unavailable")
      LatestRunState.PermissionRequired -> Text("Grant Health Connect permissions to view runs.")
      LatestRunState.NoRuns -> Text("No running workouts found.")
      LatestRunState.Error -> Text("Could not load the latest run. Please try again.")
      is LatestRunState.Success -> RunDetails(latestRun.run)
    }

    when (state.isAvailable) {
      true -> Text("Health Connect available")
      false -> if (state.latestRun != LatestRunState.Unavailable) Text("Health Connect unavailable")
      null -> Unit
    }
    if (state.isAvailable == true) {
      when (state.hasAllPermissions) {
        true -> Text("Permissions granted")
        false -> Text("Permissions not granted")
        null -> Unit
      }
      if (state.hasAllPermissions == false) {
        Button(onClick = { permissionLauncher.launch(viewModel.requiredPermissions) }) {
          Text("Request permissions")
        }
      }
    }
    Button(onClick = viewModel::refresh) { Text("Refresh") }
  }
}

@Composable
private fun RunDetails(run: RunSummary) {
  val date =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
      .withLocale(Locale.getDefault())
      .withZone(ZoneId.systemDefault())
      .format(run.startTime)
  val duration =
    "%d:%02d:%02d".format(
      run.durationSeconds / 3600,
      (run.durationSeconds % 3600) / 60,
      run.durationSeconds % 60,
    )
  val pace =
    run.averagePaceSecondsPerKm?.roundToLong()?.let { seconds ->
      "%d:%02d min/km".format(seconds / 60, seconds % 60)
    } ?: "—"

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Date: $date")
    Text("Distance: ${run.distanceMeters?.let { "%.2f km".format(Locale.getDefault(), it / 1000) } ?: "—"}")
    Text("Duration: $duration")
    Text("Average Pace: $pace")
    Text("Average HR: ${run.averageHeartRate?.let { "${it.roundToLong()} bpm" } ?: "—"}")
    Text("Max HR: ${run.maxHeartRate?.let { "$it bpm" } ?: "—"}")
  }
}
