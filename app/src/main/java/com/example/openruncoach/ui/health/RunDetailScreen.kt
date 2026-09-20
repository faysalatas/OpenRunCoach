package com.example.openruncoach.ui.health

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel

@Composable
fun RunDetailScreen(runId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val viewModel: RunDetailViewModel = viewModel(key = runId) {
    val database = OpenRunCoachDatabase.getInstance(context)
    RunDetailViewModel(
      runId,
      RunRepository(database.runDao(), HealthConnectRepository(context)),
      RunFeedbackRepository(database),
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val run = state.run

  Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    TextButton(onClick = onBack) { Text("Back") }
    Text("Run Details", style = MaterialTheme.typography.headlineMedium)
    when {
      !state.runLoaded -> Text("Loading run...")
      run == null -> Text("Run not found.")
      else -> {
        Text("Date: ${formatDate(run.startTime)}")
        Text("Distance: ${formatDistance(run.distanceMeters)}")
        Text("Duration: ${formatDuration(run.durationSeconds)}")
        Text("Average Pace: ${formatPace(run.averagePaceSecondsPerKm)}")
        Text("Average HR: ${run.averageHeartRate?.let { "%.0f bpm".format(it) } ?: "—"}")
        Text("Max HR: ${run.maxHeartRate?.let { "$it bpm" } ?: "—"}")
      }
    }

    Text("Feedback", style = MaterialTheme.typography.titleLarge)
    when {
      !state.feedbackLoaded -> Text("Loading feedback...")
      run == null -> Unit
      else -> {
        Text("How hard was this run?", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
          value = state.form.rpe,
          onValueChange = { value -> viewModel.updateForm { it.copy(rpe = value) } },
          label = { Text("RPE: 1-10") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
        )
        ChoiceRow(
          label = "Cardio:",
          options = listOf(CardioEffort.EASY to "Easy", CardioEffort.APPROPRIATE to "Appropriate", CardioEffort.HARD to "Hard", CardioEffort.LIMIT to "Limit"),
          selected = state.form.cardioEffort,
          onSelect = { selected -> viewModel.updateForm { it.copy(cardioEffort = selected) } },
        )
        ChoiceRow(
          label = "Muscle fatigue:",
          options = listOf(MuscleFatigue.NONE to "None", MuscleFatigue.MILD to "Mild", MuscleFatigue.MODERATE to "Moderate", MuscleFatigue.SEVERE to "Severe"),
          selected = state.form.muscleFatigue,
          onSelect = { selected -> viewModel.updateForm { it.copy(muscleFatigue = selected) } },
        )
        ChoiceRow(
          label = "Pain:",
          options = listOf(PainLevel.NONE to "None", PainLevel.MILD to "Mild", PainLevel.SIGNIFICANT to "Significant"),
          selected = state.form.painLevel,
          onSelect = { selected -> viewModel.updateForm { it.copy(painLevel = selected) } },
        )
        ChoiceRow(
          label = "Completion:",
          options = listOf(CompletionStatus.COMPLETED to "Completed", CompletionStatus.PARTIAL to "Partial", CompletionStatus.ABORTED to "Aborted"),
          selected = state.form.completionStatus,
          onSelect = { selected -> viewModel.updateForm { it.copy(completionStatus = selected) } },
        )
        OutlinedTextField(
          value = state.form.readiness,
          onValueChange = { value -> viewModel.updateForm { it.copy(readiness = value) } },
          label = { Text("Readiness for next run: 1-5 (optional)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
        )
        OutlinedTextField(
          value = state.form.notes,
          onValueChange = { value -> viewModel.updateForm { it.copy(notes = value) } },
          label = { Text("Notes (optional)") },
          minLines = 3,
        )
        Button(onClick = viewModel::saveFeedback, enabled = !state.isSaving) {
          Text(if (state.feedback == null) "Save Feedback" else "Update Feedback")
        }
        if (state.isSaving) Text("Saving feedback...")
        if (state.saved) Text("Feedback saved.")
      }
    }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  }
}

@Composable
private fun <T> ChoiceRow(
  label: String,
  options: List<Pair<T, String>>,
  selected: T,
  onSelect: (T) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label)
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      options.forEach { (value, text) ->
        FilterChip(
          selected = selected == value,
          onClick = { onSelect(value) },
          label = { Text(text) },
        )
      }
    }
  }
}
