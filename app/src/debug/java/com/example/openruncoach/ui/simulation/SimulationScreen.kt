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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.data.AthleteStateContinuityTrace
import com.example.openruncoach.data.GoalStrategySnapshot
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.targetPaceSecondsPerKm
import com.example.openruncoach.data.simulation.SimulationRunPersistenceTrace
import com.example.openruncoach.domain.athlete.AthleteState
import com.example.openruncoach.domain.simulation.SimulationScenarioKey
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    GoalStrategyCard(state.goalStrategy)
    AthleteStateCard(state.athleteState, viewModel::refreshAthleteState)
    ContinuityTraceCard(
      trace = state.continuityTrace,
      zoneId = environment.clock.zone,
      environmentKey = environment.key,
      activeEnvironmentDatabaseIdentity = state.activeEnvironmentDatabaseIdentity,
      athleteStateRepositoryIdentity = state.athleteStateRepositoryIdentity,
      coachAnalysisRuntimeTrace = state.coachAnalysisRuntimeTrace,
      persistenceTrace = state.persistenceTrace,
    )
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
private fun GoalStrategyCard(snapshot: GoalStrategySnapshot?) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("Goal Strategy", style = MaterialTheme.typography.titleLarge)
      Text("What needs development · independent of today's readiness")
      val goal = snapshot?.goal
      val strategy = snapshot?.strategy
      if (snapshot == null) Text("Rebuilding goal direction…")
      else if (goal == null || strategy == null) Text("No active goal · no actionable strategy")
      else {
        val distanceLabel = if (goal.distance == GoalDistance.CUSTOM) goal.distanceMeters.distance() else goal.distance.displayName
        StateValue("Active Goal", "$distanceLabel · ${goal.goalType.name.displayName()}")
        StateValue("Goal status", goal.status.name.displayName())
        goal.targetTimeSeconds?.let { seconds ->
          StateValue("Target time", "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60))
          val pace = targetPaceSecondsPerKm(seconds, goal.distanceMeters).toLong()
          StateValue("Target pace · goal context only", "%d:%02d min/km".format(Locale.ROOT, pace / 60, pace % 60))
        }
        StateValue("Strategy confidence", strategy.confidence.name.displayName())
        StateValue("Primary", strategy.primaryCapabilities.joinToString(" · ") { it.name.displayName() })
        StateValue("Secondary", strategy.secondaryCapabilities.joinToString(" · ") { it.name.displayName() }.ifEmpty { "None" })
        StateValue("Supporting", strategy.supportingCapabilities.joinToString(" · ") { it.name.displayName() }.ifEmpty { "None" })
        Text("Reasons", style = MaterialTheme.typography.titleMedium)
        strategy.reasons.forEach { Text("• $it") }
      }
    }
  }
}

@Composable
private fun ContinuityTraceCard(
  trace: List<AthleteStateContinuityTrace>,
  zoneId: ZoneId,
  environmentKey: String,
  activeEnvironmentDatabaseIdentity: String?,
  athleteStateRepositoryIdentity: com.example.openruncoach.data.AthleteStateRepositoryIdentityTrace?,
  coachAnalysisRuntimeTrace: com.example.openruncoach.CoachAnalysisRuntimeTrace?,
  persistenceTrace: Map<String, SimulationRunPersistenceTrace>,
) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("Athlete State continuity inputs", style = MaterialTheme.typography.titleLarge)
      Text("Exact structured responses supplied to the Athlete State rebuild.")
      Text("Environment: $environmentKey")
      persistenceTrace.values.firstOrNull()?.let { persistence ->
        Text("Loader DB: ${persistence.databaseIdentity}")
        Text("Loader metrics DAO: ${persistence.daoIdentity}")
        Text("Loader metrics repository: ${persistence.repositoryIdentity}")
      }
      activeEnvironmentDatabaseIdentity?.let { Text("Active environment DB: $it") }
      coachAnalysisRuntimeTrace?.let { identity ->
        Text("Coach Analysis environment: ${identity.environmentKey}")
        Text("Coach Analysis DB: ${identity.databaseIdentity}")
        Text("Coach Analysis metrics DAO: ${identity.metricsDaoIdentity}")
        Text("Coach Analysis response repository: ${identity.workoutResponseRepositoryIdentity}")
        Text("Coach UI response object: ${identity.responseObjectIdentity ?: "MISSING"}")
        Text("Coach UI response run: ${identity.responseRunId ?: "MISSING"}")
        Text("Coach UI continuity: ${identity.responseContinuity ?: "MISSING"}")
      } ?: Text("Coach Analysis identity: not captured since the last scenario load")
      athleteStateRepositoryIdentity?.let { identity ->
        Text("AthleteState DB: ${identity.databaseIdentity}")
        Text("AthleteState metrics DAO: ${identity.metricsDaoIdentity}")
        Text("AthleteState metrics repository: ${identity.metricsRepositoryIdentity}")
        Text("AthleteState response repository: ${identity.workoutResponseRepositoryIdentity}")
      }
      if (trace.isEmpty()) {
        Text("No canonical runs were supplied.")
      } else trace.forEach { entry ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            "${TRACE_DATE_FORMAT.format(entry.startTime.atZone(zoneId))} · ${entry.distanceMeters.distanceOrUnknown()}",
            style = MaterialTheme.typography.titleSmall,
          )
          Text("Run: ${entry.runId}")
          Text("Response: ${if (entry.responseFound) "FOUND" else "MISSING"}")
          persistenceTrace[entry.runId]?.let { persistence ->
            Text("Generated speed samples: ${persistence.generatedSpeedSampleCount}")
            Text("RunMetrics speed samples: ${persistence.metricsSpeedSampleCount}")
            Text("Speed sample entities: ${persistence.speedSampleEntityCount}")
            Text("Persisted speed samples: ${persistence.persistedSpeedSampleCount}")
            Text("Speed samples after load: ${persistence.speedSampleCountAfterLoad}")
            Text("Loader repository samples: ${persistence.loaderRepositorySpeedSampleCount}")
          }
          Text("Room raw sample entities: ${entry.roomRawSpeedSampleCount}")
          Text("Room relation samples: ${entry.roomRelationSpeedSampleCount}")
          Text("Room mapped samples: ${entry.roomMappedSpeedSampleCount}")
          Text("Direct speed samples: ${entry.directSpeedSampleCount}")
          Text("Collection speed samples: ${entry.collectionSpeedSampleCount}")
          Text("Coach Analysis metrics samples: ${entry.coachPathSpeedSampleCount}")
          Text("Coach Analysis response: ${entry.directResponseContinuity?.name ?: "MISSING"}")
          Text("Athlete State input: ${entry.athleteStateContinuity?.name ?: "MISSING"}")
          if (entry.athleteStateContinuity == com.example.openruncoach.domain.metrics.RunContinuityEvidence.UNKNOWN) {
            Text("Reason: structured response had insufficient movement evidence.")
          }
        }
      }
    }
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

private val TRACE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.ROOT)
