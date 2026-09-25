package com.example.openruncoach.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.displaySourceName
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.ui.common.WeekSelectorUiModel
import com.example.openruncoach.ui.common.coachingActionLabel
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.common.formatHistoryDate
import com.example.openruncoach.ui.common.formatPace
import java.time.LocalDate

val ScreenPadding = 16.dp
val SectionSpacing = 24.dp
val ItemSpacing = 8.dp

@Composable
fun WeekDaySelector(
  state: WeekSelectorUiModel,
  onPreviousWeek: () -> Unit,
  onNextWeek: () -> Unit,
  onDaySelected: (LocalDate) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      TextButton(onClick = onPreviousWeek) { Text("‹") }
      Text(state.monthLabel, style = MaterialTheme.typography.titleMedium)
      TextButton(onClick = onNextWeek) { Text("›") }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      state.days.forEach { day ->
        val colors = if (day.isSelected) {
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else CardDefaults.cardColors()
        Card(
          modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            .clickable { onDaySelected(day.date) },
          colors = colors,
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = ItemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(day.dayLabel, style = MaterialTheme.typography.labelSmall)
            Text(day.dayNumber, fontWeight = if (day.isSelected) FontWeight.Bold else FontWeight.Normal)
            Spacer(Modifier.height(4.dp))
            if (day.hasRun) {
              Surface(Modifier.size(5.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
            } else {
              Spacer(Modifier.size(5.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
fun RunSummaryCard(
  run: RunSummary,
  sourceCount: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(modifier = modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("Run", style = MaterialTheme.typography.titleLarge)
      Text(
        "${formatDistance(run.distanceMeters)} · ${formatDuration(run.durationSeconds)}",
        style = MaterialTheme.typography.headlineSmall,
      )
      Text("Average pace ${formatPace(run.averagePaceSecondsPerKm)}")
      Text("Average HR ${run.averageHeartRate?.let { "%.0f bpm".format(it) } ?: "—"}")
      Text("Source: ${run.displaySourceName}")
      if (sourceCount > 1) Text("$sourceCount sources", style = MaterialTheme.typography.labelMedium)
      TextButton(onClick = onClick) { Text("View Run") }
    }
  }
}

@Composable
fun CoachSummaryCard(
  decision: CoachingDecision,
  onViewAnalysis: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("Coach Analysis", style = MaterialTheme.typography.titleLarge)
      Text(coachingActionLabel(decision.action), style = MaterialTheme.typography.headlineSmall)
      decision.reasons.take(3).forEach { Text("✓ $it") }
      TextButton(onClick = onViewAnalysis) { Text("View analysis") }
    }
  }
}

@Composable
fun NextWorkoutCard(
  weekNumber: Int,
  workoutCount: Int,
  nextWorkoutName: String?,
  nextWorkoutDurationSeconds: Long?,
  nextWorkoutDistanceMeters: Double?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("This Week", style = MaterialTheme.typography.titleLarge)
      Text("Week $weekNumber · $workoutCount runs planned")
      nextWorkoutName?.let { Text("Next: $it", style = MaterialTheme.typography.titleMedium) }
      val estimate = listOfNotNull(
        nextWorkoutDurationSeconds?.let(::formatDuration),
        nextWorkoutDistanceMeters?.let(::formatDistance),
      ).joinToString(" · ")
      if (estimate.isNotBlank()) Text(estimate)
      TextButton(onClick = onClick) { Text("View Plan") }
    }
  }
}

@Composable
fun EmptyState(
  title: String,
  message: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Card(modifier.fillMaxWidth()) {
    Column(
      Modifier.fillMaxWidth().padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(ItemSpacing),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(message, style = MaterialTheme.typography.bodyMedium)
      if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
    }
  }
}

@Composable
fun RunHistoryItem(run: RunSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
    Row(
      Modifier.fillMaxWidth().padding(ScreenPadding),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${formatDistance(run.distanceMeters)} · ${formatDuration(run.durationSeconds)}", fontWeight = FontWeight.SemiBold)
        Text(formatHistoryDate(run.startTime), style = MaterialTheme.typography.bodyMedium)
      }
      Text(formatPace(run.averagePaceSecondsPerKm), style = MaterialTheme.typography.bodyMedium)
    }
  }
}
