package com.example.openruncoach.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.data.*
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.theme.*
import com.example.openruncoach.ui.components.*
import com.example.openruncoach.ui.common.formatDistance
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(onRunClick: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: HistoryViewModel = viewModel {
    val database = environment.database
    val settingsRepository = RunSettingsRepository(context)
    HistoryViewModel(RunRepository(database.runDao(), HealthConnectRepository(context), settingsRepository,
      clock = environment.clock, postRunReconciler = environment.postRunReconciler()),
      RunRouteRepository(database.runRouteDao()), WorkoutExecutionRepository(database, environment.clock),
      environment.clock.zone, environment.profileRepository())
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  HistoryContent(state, environment.clock, onRunClick, viewModel::toggleIgnored, modifier)
}

@Composable
internal fun HistoryContent(state: HistoryUiState, clock: Clock, onRunClick: (String) -> Unit = {},
  onToggleIgnored: () -> Unit = {}, modifier: Modifier = Modifier, scroll: LazyListState = rememberLazyListState()) {
  val p = LocalOpenRunPalette.current
  val model = historyPresentation(state, clock)
  CompositionLocalProvider(LocalContentColor provides p.ink) {
    LazyColumn(modifier.fillMaxSize().background(p.bg).testTag("runs-scroll"), state = scroll,
      contentPadding = PaddingValues(bottom = 20.dp)) {
      item(key = "hero") {
        OpenRunHero(if (state.showIgnored) "Ignored runs" else "Your runs", eyebrow = model.month) {
          BoxWithConstraints(Modifier.fillMaxWidth().semantics { contentDescription = "This calendar week's recorded runs" }) {
            val columns = (maxWidth / (78.dp * LocalDensity.current.fontScale)).toInt().coerceIn(1, 4)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              model.metrics.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric -> Column(Modifier.weight(1f)) {
                  Text(metric.value, style = OpenRunType.heroMetric.copy(fontSize = 26.sp, lineHeight = 32.sp), color = p.onHero)
                  Text(metric.label, style = OpenRunType.caption, color = p.onHeroSecondary)
                } }
                repeat(columns-row.size) { Spacer(Modifier.weight(1f)) }
              } }
            }
          }
        }
      }
      item(key = "insights") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
          OpenRunHeroBody(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
            if (state.showIgnored || state.ignoredCount > 0) OpenRunSecondaryButton(
              if (state.showIgnored) "Done" else "Ignored (${state.ignoredCount})", onToggleIgnored)
            when {
              state.isLoading -> Text("Loading run history…", style = OpenRunType.body)
              state.errorMessage != null -> OpenRunCard { Text(state.errorMessage, color = MaterialTheme.colorScheme.error) }
              model.weeks.isEmpty() -> OpenRunCard {
                Text(if (state.showIgnored) "No ignored runs" else "No runs yet", style = OpenRunType.cardTitleLg)
                Text(if (state.showIgnored) "Runs you ignore remain available here." else "Your synced runs will appear here.", style = OpenRunType.body, color = p.muted)
              }
              else -> {
                if (model.pacePoints.isNotEmpty()) OpenRunCard {
                  FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Easy pace", style = OpenRunType.title)
                    model.paceDelta?.let { Text(it, style = OpenRunType.meta, color = if (model.paceImproving) p.good else p.muted) }
                  }
                  PaceLineChart(model.pacePoints, "Easy-run pace by calendar week over the last four weeks. Higher means faster. Missing weeks remain gaps.", good = model.paceImproving)
                  Text("Easy runs per week. Higher means faster.", style = OpenRunType.meta, color = p.muted)
                }
                if (model.easyHeartRate != null || model.longestDistance != null) BoxWithConstraints {
                  val stacked = maxWidth < 280.dp * LocalDensity.current.fontScale
                  if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (model.easyHeartRate != null) OpenRunMiniStatCard("Heart rate, easy", model.easyHeartRate, "bpm")
                    if (model.longestDistance != null) OpenRunMiniStatCard("Longest run", model.longestDistance, "km", model.longestDate, good = true)
                  } else Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (model.easyHeartRate != null) OpenRunMiniStatCard("Heart rate, easy", model.easyHeartRate, "bpm", modifier = Modifier.weight(1f).fillMaxHeight())
                    if (model.longestDistance != null) OpenRunMiniStatCard("Longest run", model.longestDistance, "km", model.longestDate, Modifier.weight(1f).fillMaxHeight(), good = true)
                  }
                }
              }
            }
          }
        }
      }
      model.weeks.forEach { group ->
        item(key = "week:${group.weekStart}") { HistoryBodyItem {
          OpenRunSectionHeader(group.title, trailing = {
            group.distanceMeters?.let { Text(formatDistance(it), style = OpenRunType.meta, color = p.muted) }
          })
        } }
        items(group.runs, key = { it.run.healthConnectRecordId }) { item -> HistoryBodyItem {
          val run = item.run
          val dot = when (item.workoutType) {
            WorkoutType.EASY_RUN, WorkoutType.RECOVERY_RUN -> p.runEasy
            WorkoutType.LONG_RUN -> p.runLong
            null -> p.muted
            else -> WorkoutAccents.forType(item.workoutType)
          }
          OpenRunRunRow(run.startTime.atZone(clock.zone).format(DateTimeFormatter.ofPattern("EEE\nd MMM", Locale.getDefault())),
            item.workoutTypeLabel,
            run.distanceMeters?.takeIf { it.isFinite() && it > 0 }?.let(::formatDistance),
            run.averagePaceSecondsPerKm?.takeIf { it.isFinite() && it > 0 }?.let { "${historyPace(it)} /km" },
            Modifier.testTag("history-run:${run.healthConnectRecordId}"), dotColor = dot,
            onClick = { onRunClick(run.healthConnectRecordId) })
        } }
      }
    }
  }
}

@Composable
private fun HistoryBodyItem(content: @Composable ColumnScope.() -> Unit) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp), content = content)
  }
}
