package com.example.openruncoach.ui.plan

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.CoachingWorkflowRepository
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.workout.BodyArea
import com.example.openruncoach.domain.workout.IllnessLevel
import com.example.openruncoach.domain.workout.SorenessLevel
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import com.example.openruncoach.domain.workout.displayName
import com.example.openruncoach.ui.components.EmptyState
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing

@Composable
fun PlanScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val viewModel: PlanViewModel = viewModel {
    val database = OpenRunCoachDatabase.getInstance(context)
    val settings = RunSettingsRepository(context)
    PlanViewModel(
      CoachingWorkflowRepository(database),
      RunRepository(database.runDao(), HealthConnectRepository(context), settings),
      RunFeedbackRepository(database),
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    Text("Plan", style = MaterialTheme.typography.headlineMedium)
    when {
      state.isLoading -> EmptyState("Loading", "Loading your planned workout…")
      state.lifecycle == null -> EmptyState("Plan unavailable", state.errorMessage ?: "No workout is planned.")
      else -> {
        val lifecycle = state.lifecycle!!
        val baseline = lifecycle.baselineWorkout
        WorkoutCard("Baseline Planned Workout") {
          Text(baseline.type.displayName(), style = MaterialTheme.typography.headlineSmall)
          Text("${baseline.durationMinutes} min · RPE ${baseline.targetRpeMin}–${baseline.targetRpeMax}")
        }
        when (lifecycle.lifecycleState) {
          WorkoutLifecycleState.PLANNED -> Text("Preparing this workout for check-in.")
          WorkoutLifecycleState.CHECK_IN_REQUIRED -> if (state.showCheckIn) {
            CheckInEditor(state, viewModel)
          } else {
            Button(onClick = viewModel::beginCheckIn) { Text("Check in") }
          }
          WorkoutLifecycleState.READY,
          WorkoutLifecycleState.COMPLETED,
          WorkoutLifecycleState.CHECKOUT_REQUIRED,
          WorkoutLifecycleState.ANALYZED,
          WorkoutLifecycleState.NEXT_WORKOUT_CREATED -> {
            val decision = lifecycle.adjustmentDecision
            if (decision != null) {
              WorkoutCard("Today's Workout") {
                Text(decision.adjustedWorkout.type.displayName(), style = MaterialTheme.typography.headlineSmall)
                Text("${decision.adjustedWorkout.durationMinutes} min · RPE ${decision.adjustedWorkout.targetRpeMin}–${decision.adjustedWorkout.targetRpeMax}")
                if (decision.adjustedWorkout.durationMinutes != baseline.durationMinutes) {
                  Text("Adjusted from ${baseline.durationMinutes} min")
                }
                Text(decision.readinessStatus.name, style = MaterialTheme.typography.labelLarge)
                Text("Why?", style = MaterialTheme.typography.titleMedium)
                decision.reasons.forEach { Text("• $it") }
                decision.adjustments.forEach { Text("• $it") }
              }
            }
            when (lifecycle.lifecycleState) {
              WorkoutLifecycleState.COMPLETED,
              WorkoutLifecycleState.CHECKOUT_REQUIRED -> Text("Complete checkout")
              WorkoutLifecycleState.ANALYZED -> Text("Coach analysis complete")
              WorkoutLifecycleState.NEXT_WORKOUT_CREATED -> Text("Next workout created")
              else -> Unit
            }
          }
        }
      }
    }
    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
}

@Composable
private fun CheckInEditor(state: PlanUiState, viewModel: PlanViewModel) {
  WorkoutCard("Pre-run Check-in") {
    RatingRow("Readiness", state.form.readiness) { value -> viewModel.updateForm { it.copy(readiness = value) } }
    RatingRow("Energy", state.form.energy) { value -> viewModel.updateForm { it.copy(energy = value) } }
    RatingRow("Sleep quality", state.form.sleepQuality) { value -> viewModel.updateForm { it.copy(sleepQuality = value) } }
    RatingRow("Stress", state.form.stress) { value -> viewModel.updateForm { it.copy(stress = value) } }
    RatingRow("Motivation", state.form.motivation) { value -> viewModel.updateForm { it.copy(motivation = value) } }
    ChoiceRow("Soreness", SorenessLevel.entries, state.form.soreness, onSelect = { value ->
      viewModel.updateForm { it.copy(soreness = value, sorenessAreas = if (value == SorenessLevel.NONE) emptySet() else it.sorenessAreas) }
    })
    if (state.form.soreness != SorenessLevel.NONE) {
      MultiChoiceRow("Soreness areas", BodyArea.entries, state.form.sorenessAreas) { area ->
        viewModel.updateForm { form ->
          form.copy(sorenessAreas = if (area in form.sorenessAreas) form.sorenessAreas - area else form.sorenessAreas + area)
        }
      }
    }
    ChoiceRow("Pain", PainLevel.entries, state.form.pain, onSelect = { value -> viewModel.updateForm { it.copy(pain = value) } })
    ChoiceRow("Illness", IllnessLevel.entries, state.form.illness, onSelect = { value -> viewModel.updateForm { it.copy(illness = value) } })
    OutlinedTextField(
      value = state.form.notes,
      onValueChange = { value -> viewModel.updateForm { it.copy(notes = value) } },
      label = { Text("Notes (optional)") },
      modifier = Modifier.fillMaxWidth(),
      minLines = 2,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Button(onClick = viewModel::saveCheckIn, enabled = !state.isSaving) { Text("Adjust workout") }
      TextButton(onClick = viewModel::cancelCheckIn, enabled = !state.isSaving) { Text("Cancel") }
    }
    if (state.isSaving) Text("Saving check-in…")
  }
}

@Composable
private fun RatingRow(label: String, selected: Int, onSelect: (Int) -> Unit) {
  ChoiceRow(label, (1..5).toList(), selected, onSelect) { it.toString() }
}

@Composable
private fun <T> ChoiceRow(
  label: String,
  options: List<T>,
  selected: T,
  onSelect: (T) -> Unit,
  display: (T) -> String = { it.toString().lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase) },
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      options.forEach { option ->
        FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(display(option)) })
      }
    }
  }
}

@Composable
private fun <T> MultiChoiceRow(label: String, options: List<T>, selected: Set<T>, onToggle: (T) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      options.forEach { option ->
        FilterChip(
          selected = option in selected,
          onClick = { onToggle(option) },
          label = { Text(option.toString().lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)) },
        )
      }
    }
  }
}

@Composable
private fun WorkoutCard(title: String, content: @Composable () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      content()
    }
  }
}
