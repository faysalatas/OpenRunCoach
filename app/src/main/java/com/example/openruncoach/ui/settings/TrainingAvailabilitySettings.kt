package com.example.openruncoach.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import com.example.openruncoach.domain.plan.RunningDayPreference

@Composable
internal fun TrainingAvailabilitySettings(state: SettingsUiState, viewModel: SettingsViewModel) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("Running days", style = MaterialTheme.typography.titleLarge)
      Text("Unavailable days exclude running. Preferred days are favored when safe; Available days can also be used. These settings do not change training readiness.")
      when {
        state.availabilityLoading -> Text("Loading running days…")
        state.availabilityDraft == null -> {
          Text("No day constraints: use the existing deterministic schedule.")
          TextButton(onClick = viewModel::customizeAvailability, enabled = !state.availabilitySaving) { Text("Choose running days") }
        }
        else -> {
          val draft = requireNotNull(state.availabilityDraft)
          DayOfWeek.entries.forEach { day ->
            val name = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Column {
              Text(name, style = MaterialTheme.typography.titleSmall)
              Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                RunningDayPreference.entries.forEach { preference ->
                  val label = when (preference) {
                    RunningDayPreference.UNAVAILABLE -> "Unavailable"
                    RunningDayPreference.AVAILABLE -> "Available"
                    RunningDayPreference.PREFERRED -> "Preferred"
                  }
                  FilterChip(
                    selected = draft.preference(day) == preference,
                    onClick = { viewModel.selectRunningDay(day, preference) },
                    enabled = !state.availabilitySaving,
                    label = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = "$name, $label" },
                  )
                }
              }
            }
          }
          if (draft.availableDays.isEmpty()) Text("No running days selected. New plans will contain rest until you make days available.")
          state.availabilityConflict?.let { Text(it, color = MaterialTheme.colorScheme.error) }
          TextButton(onClick = viewModel::resetAvailabilityDraft, enabled = !state.availabilitySaving) { Text("Use existing schedule without day constraints") }
        }
      }
      Text("Saving applies to future generation. Existing plans, completed workouts and actual runs keep their saved dates.", style = MaterialTheme.typography.bodyMedium)
      if (state.availabilityDirty) {
        Button(onClick = viewModel::saveAvailability, enabled = !state.availabilitySaving && !state.availabilityLoading) {
          Text(if (state.availabilitySaving) "Saving…" else "Save running days")
        }
        TextButton(onClick = viewModel::discardAvailabilityChanges, enabled = !state.availabilitySaving) { Text("Discard changes") }
      }
      state.availabilityMessage?.let { Text(it) }
      state.availabilityError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
  }
}
