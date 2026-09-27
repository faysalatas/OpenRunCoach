package com.example.openruncoach.ui.goal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.TrainingGoalRepository
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.domain.goal.TrainingGoalFactory
import com.example.openruncoach.domain.goal.DistanceGoalCapability
import com.example.openruncoach.domain.goal.GoalAssessment
import com.example.openruncoach.domain.goal.GoalAssessmentLevel
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.common.formatHistoryDate
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing
import java.util.Locale

@Composable
fun GoalScreen(onBack: () -> Unit, onOpenTrainingPlan: () -> Unit, modifier: Modifier = Modifier) {
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: GoalViewModel = viewModel {
    val database = environment.database
    val profiles = environment.profileRepository()
    GoalViewModel(
      TrainingGoalRepository(database, profiles, TrainingGoalFactory(environment.clock)),
      profiles,
      environment.clock,
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    TextButton(onClick = if (state.step == GoalCreationStep.OVERVIEW) onBack else viewModel::back) { Text("‹ Back") }
    Text("Training Goal", style = MaterialTheme.typography.headlineMedium)
    when {
      state.isLoading -> Text("Loading your goal…")
      state.step != GoalCreationStep.OVERVIEW -> GoalCreationFlow(state, viewModel)
      state.activeGoal == null -> NoActiveGoal(viewModel::beginCreation)
      else -> ActiveGoal(state.activeGoal!!, state, viewModel, onOpenTrainingPlan)
    }
    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
}

@Composable
private fun NoActiveGoal(onChoose: () -> Unit) {
  GoalCard("Training Goal") {
    Text("You haven't selected a goal yet.")
    Button(onClick = onChoose) { Text("Choose Goal") }
  }
}

@Composable
private fun ActiveGoal(
  goal: TrainingGoal,
  state: GoalUiState,
  viewModel: GoalViewModel,
  onOpenTrainingPlan: () -> Unit,
) {
  val presentation = viewModel.presentationForGoal(goal)
  GoalCard("Current Goal") {
    Text(goal.distanceLabel(), style = MaterialTheme.typography.headlineSmall)
    Text(goal.goalType.label())
    goal.targetTimeSeconds?.let { GoalValue("Target", formatDuration(it)) }
    presentation.targetPaceSecondsPerKm?.let { GoalValue("Target pace", formatPace(it)) }
    GoalValue("Starting benchmark", goal.startingPerformanceSnapshot.bestBenchmark?.durationSeconds?.let(::formatDuration) ?: "Not available")
    TextButton(onClick = viewModel::toggleDetails) { Text(if (state.showDetails) "Hide Goal Details" else "Goal Details") }
    Button(onClick = onOpenTrainingPlan) { Text("View or Generate Training Plan") }
  }

  if (state.showDetails) {
    GoalCard("Goal Details") {
      GoalValue("Distance", goal.distanceLabel())
      GoalValue("Goal type", goal.goalType.label())
      GoalValue("Target time", goal.targetTimeSeconds?.let(::formatDuration) ?: "No target time")
      GoalValue("Target pace", presentation.targetPaceSecondsPerKm?.let(::formatPace) ?: "Not applicable")
      GoalValue("Created", formatHistoryDate(goal.createdAt))
    }
    GoalCard("Starting Performance") {
      GoalValue("Best benchmark", goal.startingPerformanceSnapshot.bestBenchmark?.durationSeconds?.let(::formatDuration) ?: "Not available")
      GoalValue("Most recent benchmark", goal.startingPerformanceSnapshot.mostRecentBenchmark?.durationSeconds?.let(::formatDuration) ?: "Not available")
      GoalValue("4-week average", "${formatDistance(goal.startingPerformanceSnapshot.averageWeeklyDistanceMeters)}/week")
      GoalValue("Longest recent run", formatDistance(goal.startingPerformanceSnapshot.longestRecentRunDistanceMeters))
      GoalValue("Runs per week", "%.1f".format(Locale.getDefault(), goal.startingPerformanceSnapshot.averageRunsPerWeek))
      GoalValue("Typical easy pace", formatPace(goal.startingPerformanceSnapshot.typicalEasyPaceSecondsPerKm))
      GoalValue("Typical easy HR", goal.startingPerformanceSnapshot.typicalEasyHeartRate?.let { "%.0f bpm".format(it) } ?: "Not available")
    }
    GoalCard("Current Comparison") {
      GoalValue("Current best", presentation.benchmarkDurationSeconds?.let(::formatDuration) ?: "No verified benchmark")
      presentation.improvementSinceGoalStartSeconds?.let {
        GoalValue("Since goal start", when {
          it > 0 -> "${formatDuration(it)} faster"
          it < 0 -> "${formatDuration(-it)} slower"
          else -> "No change"
        })
      }
      presentation.comparison?.let {
        GoalValue("Target difference", formatSignedDuration(it.differenceSeconds))
        GoalValue("Required improvement", "%.1f%%".format(Locale.getDefault(), it.improvementFraction * 100.0))
      }
      GoalValue("Current data confidence", state.profile?.profileConfidence?.label() ?: "Very low")
      GoalValue("Confidence at creation", goal.confidenceAtCreation.label())
    }
    AssessmentCard(goal.assessmentAtCreation)
    Row(horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      OutlinedButton(onClick = viewModel::pauseGoal) { Text("Pause Goal") }
      OutlinedButton(onClick = viewModel::archiveGoal) { Text("Archive Goal") }
    }
    Button(onClick = viewModel::beginCreation) { Text("Choose Another Goal") }
    Text("Starting another goal archives the current active goal. Previous goals remain stored.", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun GoalCreationFlow(state: GoalUiState, viewModel: GoalViewModel) {
  when (state.step) {
    GoalCreationStep.DISTANCE -> {
      Text("1 of 5 · What are you training for?", style = MaterialTheme.typography.titleLarge)
      GoalDistance.entries.forEach { distance ->
        SelectableRow(distance.displayName, state.draft.distance == distance) { viewModel.selectDistance(distance) }
      }
      if (state.draft.distance == GoalDistance.CUSTOM) {
        OutlinedTextField(
          value = state.draft.customDistanceKm,
          onValueChange = viewModel::setCustomDistance,
          label = { Text("Distance in km") },
          supportingText = { Text("Between 0.1 km and 200 km") },
          singleLine = true,
        )
      }
      Button(onClick = viewModel::continueFromDistance) { Text("Continue") }
    }
    GoalCreationStep.TYPE -> {
      Text("2 of 5 · What's your goal?", style = MaterialTheme.typography.titleLarge)
      val capability = viewModel.selectedCapability()
      GoalType.entries.forEach { type ->
        val option = capability?.options?.firstOrNull { it.goalType == type }
        val support = option?.let {
          (if (it.supportedByCurrentData) "Supported by current data. " else "Limited data. ") + it.explanation
        }
        SelectableCard(type.label(), type.description(), support, state.draft.goalType == type) {
          viewModel.selectGoalType(type)
        }
      }
      Button(onClick = viewModel::continueFromType) { Text("Continue") }
    }
    GoalCreationStep.CONTEXT -> {
      Text("3 of 5 · Current context", style = MaterialTheme.typography.titleLarge)
      CurrentContext(state.draft.distance, viewModel.selectedCapability(), state)
      Button(onClick = viewModel::continueFromContext) { Text("Continue") }
    }
    GoalCreationStep.DETAILS -> {
      Text("4 of 5 · Goal details", style = MaterialTheme.typography.titleLarge)
      if (state.draft.goalType == GoalType.TARGET_TIME) {
        OutlinedTextField(
          value = state.draft.targetTime,
          onValueChange = viewModel::setTargetTime,
          label = { Text("Target time (MM:SS or H:MM:SS)") },
          singleLine = true,
        )
      }
      Button(onClick = viewModel::review) { Text("Review Goal") }
    }
    GoalCreationStep.REVIEW -> {
      val presentation = viewModel.presentationForDraft()
      Text("5 of 5 · Review", style = MaterialTheme.typography.titleLarge)
      GoalCard("Your Goal") {
        Text(state.draft.distance?.let { distanceLabel(it, state.draft.customDistanceKm.toDoubleOrNull()?.times(1_000.0)) }.orEmpty(), style = MaterialTheme.typography.headlineSmall)
        Text(state.draft.goalType?.label().orEmpty())
        state.draft.targetTime.takeIf(String::isNotBlank)?.let { GoalValue("Target", it) }
        presentation?.targetPaceSecondsPerKm?.let { GoalValue("Target pace", formatPace(it)) }
        GoalValue("Current verified result", presentation?.benchmarkDurationSeconds?.let(::formatDuration) ?: "Not available")
        presentation?.comparison?.let {
          GoalValue("Difference", formatSignedDuration(it.differenceSeconds))
          GoalValue("Required improvement", "%.1f%%".format(Locale.getDefault(), it.improvementFraction * 100.0))
        }
      }
      presentation?.assessment?.let { assessment -> AssessmentCard(assessment) }
      if (state.activeGoal != null) Text("Starting this goal will archive the current active goal.", style = MaterialTheme.typography.bodySmall)
      Button(onClick = viewModel::startGoal, enabled = !state.isSaving) { Text(if (state.isSaving) "Saving…" else "Start Goal") }
    }
    GoalCreationStep.OVERVIEW -> Unit
  }
}

@Composable
private fun CurrentContext(distance: GoalDistance?, capability: DistanceGoalCapability?, state: GoalUiState) {
  GoalCard(distance?.displayName ?: "Current Performance") {
    val benchmark = capability?.benchmark
    if (benchmark?.personalBest != null) {
      Text("Current performance", style = MaterialTheme.typography.titleMedium)
      GoalValue("Best observed", formatDuration(benchmark.personalBest.durationSeconds))
      GoalValue("Most recent", benchmark.mostRecent?.durationSeconds?.let(::formatDuration) ?: "Not available")
      GoalValue("Typical easy pace", formatPace(state.profile?.typicalEasyPace?.representative))
    } else {
      Text("No verified ${distance?.displayName.orEmpty()} benchmark yet.")
      GoalValue("Longest recent run", formatDistance(capability?.longestRecentRunDistanceMeters))
      GoalValue("4-week average", "${formatDistance(state.profile?.averageWeeklyDistanceLast4WeeksMeters)}/week")
      GoalValue("Runs per week", state.profile?.averageRunsPerWeek?.let { "%.1f".format(Locale.getDefault(), it) } ?: "Not available")
    }
    if (capability?.hasCompletedDistance == true) {
      Text("You have already demonstrated ${distance?.displayName.orEmpty()}.", style = MaterialTheme.typography.titleMedium)
      Text("Improve Performance or Target Time may be more relevant alternatives. Complete Distance remains available if you prefer it.")
    }
    Text("Only verified distance benchmarks are shown. Shorter runs are not presented as results for this distance.", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun AssessmentCard(assessment: GoalAssessment) {
  GoalCard("Goal Assessment") {
    Text(assessment.level.label(), style = MaterialTheme.typography.titleMedium)
    Text("This describes how the goal compares with observed data. It is not a prediction.")
    Text("Why?", style = MaterialTheme.typography.titleSmall)
    assessment.reasons.forEach { Text("• $it") }
  }
}

@Composable
private fun SelectableRow(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = ItemSpacing),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = onClick)
    Text(label)
  }
}

@Composable
private fun SelectableCard(title: String, description: String, support: String?, selected: Boolean, onClick: () -> Unit) {
  Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
    Row(Modifier.padding(ScreenPadding), verticalAlignment = Alignment.Top) {
      RadioButton(selected = selected, onClick = onClick)
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description)
        support?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
      }
    }
  }
}

@Composable
private fun GoalCard(title: String, content: @Composable () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      content()
    }
  }
}

@Composable
private fun GoalValue(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label)
    Text(value)
  }
}

private fun TrainingGoal.distanceLabel() = distanceLabel(distance, customDistanceMeters)
private fun distanceLabel(distance: GoalDistance, customMeters: Double?) = if (distance == GoalDistance.CUSTOM) formatDistance(customMeters) else distance.displayName
private fun GoalType.label() = when (this) {
  GoalType.COMPLETE_DISTANCE -> "Complete the distance"
  GoalType.IMPROVE_PERFORMANCE -> "Improve performance"
  GoalType.TARGET_TIME -> "Target time"
}
private fun GoalType.description() = when (this) {
  GoalType.COMPLETE_DISTANCE -> "Build toward completing the selected distance."
  GoalType.IMPROVE_PERFORMANCE -> "Improve from your verified starting performance without setting an exact time."
  GoalType.TARGET_TIME -> "Choose an explicit finish time."
}
private fun GoalAssessmentLevel.label() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
private fun ProfileConfidence.label() = name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
private fun formatSignedDuration(seconds: Long): String = (if (seconds >= 0) "-" else "+") + formatDuration(kotlin.math.abs(seconds))
