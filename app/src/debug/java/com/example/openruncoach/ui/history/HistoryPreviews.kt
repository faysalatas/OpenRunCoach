package com.example.openruncoach.ui.history

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.theme.OpenRunCoachTheme
import com.example.openruncoach.ui.components.OpenRunBottomBar
import com.example.openruncoach.ui.home.HomePreviewData
import com.example.openruncoach.ui.home.HomeDayState
import java.time.Clock

/** Synthetic read models, confined to the debug source set. */
internal object HistoryPreviewData {
  val clock: Clock = HomePreviewData.clock(HomeDayState.BeforeRun)
  fun state(): HistoryUiState {
    val home = HomePreviewData.state(HomeDayState.BeforeRun).todaySources!!
    val current = home.canonicalRuns.filter { it.startTime.atZone(clock.zone).toLocalDate().dayOfMonth in 2..6 }
    val older = listOf(
      HomePreviewData.run("history-long", "2026-11-01", 7_100.0, 2_970) to WorkoutType.LONG_RUN,
      HomePreviewData.run("history-oct30", "2026-10-30", 5_000.0, 1_950) to WorkoutType.EASY_RUN,
      HomePreviewData.run("history-oct28", "2026-10-28", 3_000.0, 1_320) to WorkoutType.RECOVERY_RUN,
      HomePreviewData.run("history-oct26", "2026-10-26", 5_000.0, 1_970) to WorkoutType.EASY_RUN,
      HomePreviewData.run("history-oct23", "2026-10-23", 5_000.0, 1_990) to WorkoutType.EASY_RUN,
      HomePreviewData.run("history-oct16", "2026-10-16", 5_000.0, 2_000) to WorkoutType.EASY_RUN,
    )
    val types = home.completedWorkoutTypesByRunId + older.associate { it.first.healthConnectRecordId to it.second }
    val runs = (current + older.map { it.first }).sortedByDescending { it.startTime }
    val profile = AthletePerformanceProfileCalculator(clock, clock.zone).calculate(runs, emptyList(), emptyList()).copy(
      typicalEasyHeartRate = home.profile!!.typicalEasyHeartRate)
    val items = runs.map { run -> HistoryRunItem(run, "", when(types[run.healthConnectRecordId]) {
      WorkoutType.EASY_RUN -> "Easy run"; WorkoutType.RECOVERY_RUN -> "Recovery run"; WorkoutType.LONG_RUN -> "Long run"; else -> "Run"
    }, workoutType = types[run.healthConnectRecordId]) }
    return HistoryUiState(false, listOf(RunHistoryGroup("Fixture input", items)), ignoredCount = 2,
      profile = profile, completedWorkoutTypesByRunId = types)
  }
}

@Composable
internal fun RunsPreview(state: HistoryUiState = HistoryPreviewData.state(), scroll: LazyListState = rememberLazyListState(),
  onRun: (String) -> Unit = {}, toggle: () -> Unit = {}) {
  Column(Modifier.fillMaxSize()) {
    HistoryContent(state, HistoryPreviewData.clock, onRun, toggle, Modifier.weight(1f), scroll)
    OpenRunBottomBar("history") {}
  }
}
@Preview(name = "Runs light", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable private fun RunsLight() { OpenRunCoachTheme { RunsPreview() } }
@Preview(name = "Runs dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun RunsDark() { OpenRunCoachTheme { RunsPreview() } }
@Preview(name = "Runs 130%", widthDp = 390, heightDp = 844, fontScale = 1.3f)
@Composable private fun Runs130() { OpenRunCoachTheme { RunsPreview() } }
@Preview(name = "Runs 200%", widthDp = 390, heightDp = 844, fontScale = 2f)
@Composable private fun Runs200() { OpenRunCoachTheme { RunsPreview() } }
