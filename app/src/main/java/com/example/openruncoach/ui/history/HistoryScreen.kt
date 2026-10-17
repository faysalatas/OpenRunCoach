package com.example.openruncoach.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.example.openruncoach.theme.WorkoutAccents
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunRouteRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.theme.OpenRunShapeTokens
import com.example.openruncoach.theme.OpenRunSpacing
import com.example.openruncoach.ui.common.formatDistance
import com.example.openruncoach.ui.common.formatActualDuration
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.RunRouteCanvas
import com.example.openruncoach.ui.components.RunRouteCanvasStyle

@Composable
fun HistoryScreen(onRunClick: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: HistoryViewModel = viewModel {
    val database = environment.database
    val settingsRepository = RunSettingsRepository(context)
    HistoryViewModel(
      RunRepository(
        database.runDao(),
        HealthConnectRepository(context),
        settingsRepository,
        clock = environment.clock,
        postRunReconciler = environment.postRunReconciler(),
      ),
      RunRouteRepository(database.runRouteDao()),
      WorkoutExecutionRepository(database, environment.clock),
      environment.clock.zone,
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(
      start = ScreenPadding,
      top = ScreenPadding,
      end = ScreenPadding,
      bottom = ScreenPadding,
    ),
    verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small),
  ) {
    item {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(if (state.showIgnored) "Ignored runs" else "History", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
        if (state.showIgnored || state.ignoredCount > 0) {
          TextButton(onClick = viewModel::toggleIgnored) {
            Text(if (state.showIgnored) "Done" else "Ignored (${state.ignoredCount})")
          }
        }
      }
    }
    when {
      state.isLoading -> item { Text("Loading run history…") }
      state.errorMessage != null -> item { Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error) }
      state.groups.isEmpty() -> item {
        Column(
          modifier = Modifier.padding(top = OpenRunSpacing.Comfortable),
          verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small),
        ) {
          Text(if (state.showIgnored) "No ignored runs" else "No runs yet", style = MaterialTheme.typography.titleMedium)
          Text(
            if (state.showIgnored) "Runs you ignore remain available here." else "Your synced runs will appear here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      else -> state.groups.forEach { group ->
        item(key = group.title) {
          Column(verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Small)) {
            Text(
              group.title,
              modifier = Modifier.padding(top = OpenRunSpacing.Standard, bottom = OpenRunSpacing.Micro),
              style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          }
        }
        items(group.runs, key = { it.run.healthConnectRecordId }) { item ->
          HistoryRunRow(item) { onRunClick(item.run.healthConnectRecordId) }
        }
      }
    }
  }
}

@Composable
private fun HistoryRunRow(item: HistoryRunItem, onClick: () -> Unit) {
  val run = item.run
  val accent = WorkoutAccents.forType(item.workoutType)
  Surface(
    modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open ${item.workoutTypeLabel}", onClick = onClick),
    shape = OpenRunShapeTokens.Compact,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Row(
      modifier = Modifier.drawBehind {
        drawLine(accent, Offset(1.5.dp.toPx(), 0f), Offset(1.5.dp.toPx(), size.height), 3.dp.toPx())
      }.padding(horizontal = OpenRunSpacing.Compact, vertical = OpenRunSpacing.Small),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(OpenRunSpacing.Compact),
    ) {
      item.route?.points?.takeIf { it.size >= 2 }?.let { points ->
        RunRouteCanvas(
          points = points,
          style = RunRouteCanvasStyle.COMPACT,
          modifier = Modifier.width(76.dp).height(68.dp),
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(OpenRunSpacing.Micro),
      ) {
        Text(
          item.dateLabel,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(item.workoutTypeLabel, style = MaterialTheme.typography.titleMedium)
        Text(
          listOf(
            formatDistance(run.distanceMeters),
            formatActualDuration(run.durationSeconds),
            formatPace(run.averagePaceSecondsPerKm).replace(" min/km", " /km"),
          ).joinToString(" · "),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
  }
}
