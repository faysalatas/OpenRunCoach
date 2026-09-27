package com.example.openruncoach.ui.simulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.domain.athlete.AthleteState
import com.example.openruncoach.domain.simulation.SimulationScenarioKey
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing
import java.util.Locale

@Composable
fun SimulationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: SimulationViewModel = viewModel(key = environment.key) {
    SimulationViewModel(context.applicationContext, environment)
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    TextButton(onClick = onBack) { Text("‹ Back") }
    Text("Simulation", style = MaterialTheme.typography.headlineMedium)
    Text("DEBUG ONLY · SIMULATION MODE", color = MaterialTheme.colorScheme.tertiary)
    Text("Each preset clears only the isolated simulation database, writes deterministic raw inputs, and opens the normal app pipeline.")
    AthleteStateCard(state.athleteState, viewModel::refreshAthleteState)
    SimulationScenarioKey.entries.forEach { scenario ->
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
          Text(scenario.displayName, style = MaterialTheme.typography.titleMedium)
          Text(scenario.summary)
          Button(onClick = { viewModel.load(scenario) }, enabled = !state.isLoading) {
            Text(if (state.isLoading) "Preparing…" else "Load scenario")
          }
        }
      }
    }
    OutlinedButton(onClick = viewModel::reset, enabled = !state.isLoading) {
      Text("Reset Simulation")
    }
    if (environment.isSimulation) {
      OutlinedButton(onClick = viewModel::exit, enabled = !state.isLoading) {
        Text("Exit Simulation")
      }
    }
    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
}

@Composable
private fun AthleteStateCard(state: AthleteState?, onRefresh: () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("Athlete State", style = MaterialTheme.typography.titleLarge)
      Text("Longitudinal capability and current readiness rebuilt from the active environment.")
      if (state == null) {
        Text("No rebuilt state is available yet.")
      } else {
        StateValue("Capability confidence", state.capabilityConfidence.name.displayName())
        StateValue("Current readiness", state.currentReadiness.name.displayName())
        StateValue("Actual volume · 7 days", state.recentActualVolume7DaysMeters.distance())
        StateValue("Actual volume · 28 days", state.recentActualVolume28DaysMeters.distance())
        StateValue("Recent frequency", "%.1f runs/week".format(Locale.ROOT, state.recentRunFrequencyPerWeek))
        StateValue("Longest completed", state.longestCompletedDistanceMeters.distanceOrUnknown())
        StateValue("Longest continuous", state.longestDemonstratedContinuousDistanceMeters.distanceOrUnknown())
        StateValue("Longest comfortable/tolerated", state.longestToleratedDistanceMeters.distanceOrUnknown())
        StateValue("Tolerated aerobic sessions", state.toleratedSessionCount.toString())
        StateValue(
          "Continuity · recent 28 days",
          "${state.recentContinuity.continuousRuns} continuous · " +
            "${state.recentContinuity.interruptedRuns} interrupted · ${state.recentContinuity.unknownRuns} unknown",
        )
        StateValue(
          "Recent response cost",
          "${state.recentHighCostCount} high · ${state.recentElevatedCostCount} elevated" +
            (state.latestSessionCost?.let { " · latest ${it.name.displayName()}" } ?: ""),
        )
        Text("Capability evidence", style = MaterialTheme.typography.titleMedium)
        state.capabilityReasons.forEach { Text("• $it") }
        Text("Readiness evidence", style = MaterialTheme.typography.titleMedium)
        state.readinessReasons.forEach { Text("• $it") }
      }
      OutlinedButton(onClick = onRefresh) { Text("Rebuild Athlete State") }
    }
  }
}

@Composable
private fun StateValue(label: String, value: String) {
  Column {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Text(value)
  }
}

private fun Double?.distanceOrUnknown() = this?.distance() ?: "Unknown · insufficient evidence"

private fun Double.distance() = "%.2f km".format(Locale.ROOT, this / 1_000.0)

private fun String.displayName() = lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
