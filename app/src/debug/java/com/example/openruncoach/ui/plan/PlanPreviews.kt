package com.example.openruncoach.ui.plan

import android.content.res.Configuration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.openruncoach.data.PreviousPlanSummary
import com.example.openruncoach.data.PlanWorkoutPresentation
import com.example.openruncoach.domain.goal.*
import com.example.openruncoach.domain.plan.*
import com.example.openruncoach.domain.workout.*
import com.example.openruncoach.theme.OpenRunCoachTheme
import com.example.openruncoach.ui.components.OpenRunBottomBar
import com.example.openruncoach.ui.home.HomePreviewData
import com.example.openruncoach.ui.home.HomeDayState
import java.time.LocalDate

/** Synthetic debug-only plan and metadata; production never references these fixtures. */
internal object PlanPreviewData {
  val today: LocalDate = LocalDate.of(2026, 11, 8)
  fun state(): PlanUiState {
    val home = HomePreviewData.state(HomeDayState.BeforeRun)
    val source = home.todaySources!!
    val current = source.plan!!.weeks.first().copy(weekIndex = 1,
      explanation = "The week keeps easy runs around the long run. Keep the effort comfortable.")
    val previousLong = HomePreviewData.workout("previous-long", LocalDate.of(2026, 11, 1), WorkoutType.LONG_RUN, 7_100.0)
    val previousRun = HomePreviewData.run("previous-long-run", "2026-11-01", 7_100.0, 2_970)
    val previousEntry = PlannedWorkoutEntry(WeeklyWorkoutSlot.WEEKEND, previousLong, "Recorded endurance run.",
      WorkoutExecutionResult(previousLong.id, previousRun.healthConnectRecordId, WorkoutExecutionStatus.COMPLETED_AS_PLANNED,
        WorkoutComparisonBasis.ORIGINAL_PRESCRIPTION, 7_100.0, null, 7_100.0, 2_970, null,
        "Fixture completion.", previousRun.endTime))
    val previous = current.copy(weekIndex = 0, startDate = current.startDate.minusWeeks(1),
      workouts = listOf(previousEntry), targetDistanceMeters = 7_100.0)
    val future = (2..7).map { index ->
      val monday = previous.startDate.plusWeeks(index.toLong())
      val longDistance = listOf(8_500.0, 9_000.0, 9_000.0, 9_000.0, 10_000.0, 4_000.0)[index - 2]
      val workouts = listOf(0 to 5_000.0, 2 to 3_000.0, 4 to 5_000.0, 6 to longDistance).mapIndexed { i, (day, distance) ->
        val workout = HomePreviewData.workout("future-$index-$i", monday.plusDays(day.toLong()),
          if (i == 3) WorkoutType.LONG_RUN else if (i == 1) WorkoutType.RECOVERY_RUN else WorkoutType.EASY_RUN, distance)
        val effective = when {
          index == 6 && i == 3 -> workout.copy(date = LocalDate.of(2026, 12, 12), type = WorkoutType.RACE_SPECIFIC,
            purpose = WorkoutPurpose.GOAL_COMPLETION, name = "Completion Run")
          index == 7 && i == 3 -> workout.copy(type = WorkoutType.RECOVERY_RUN, name = "Recovery run")
          else -> workout
        }
        PlannedWorkoutEntry(WeeklyWorkoutSlot.entries[i], effective, effective.explanation)
      }
      current.copy(weekIndex = index, startDate = monday, workouts = workouts,
        targetDistanceMeters = workouts.sumOf { WorkoutEstimateCalculator.calculate(it.workout).estimatedTotalDistanceMeters!! })
    }
    val milestone = GoalCompletionMilestone(source.goal!!.id, GoalCompletionReadiness.GOAL_COMPLETION_NOT_READY,
      LocalDate.of(2026, 12, 12), false, listOf("Explicit synthetic saved date."), source.goal.createdAt, planId = source.plan.id)
    val plan = source.plan.copy(startDate = previous.startDate, weeks = listOf(previous, current) + future,
      completionMilestone = milestone)
    val metadata = plan.weeks.flatMap { it.workouts }.mapNotNull { entry ->
      entry.executionResult?.actualRunId?.let { id ->
        val run = if (id == previousRun.healthConnectRecordId) previousRun else source.canonicalRuns.first { it.healthConnectRecordId == id }
        entry.workout.id to PlanWorkoutPresentation(WorkoutLifecycleState.COMPLETED, false, run)
      }
    }.toMap()
    return PlanUiState(source.goal, plan, 1, false, completionMilestone = milestone, workoutMetadata = metadata,
      completionDateSuggestions = listOf(CompletionDateSuggestion(milestone.scheduledDate!!, true, listOf("Synthetic date suggestion."))),
      previousPlans = listOf(PreviousPlanSummary("previous-plan-fixture", previous.startDate.minusWeeks(8), previous.startDate.minusDays(1),
        TrainingPlanStatus.SUPERSEDED, 8, GoalDistance.TEN_K, null, GoalType.COMPLETE_DISTANCE)))
  }
}

@Composable
internal fun PlanPreview(state: PlanUiState = PlanPreviewData.state(), scroll: ScrollState = rememberScrollState(), actions: PlanActions = PlanActions()) {
  Column(Modifier.fillMaxSize()) {
    PlanContent(state, PlanPreviewData.today, actions, Modifier.weight(1f), scroll)
    OpenRunBottomBar("plan") {}
  }
}

@Preview(name = "Plan light", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable private fun PlanLight() { OpenRunCoachTheme { PlanPreview() } }
@Preview(name = "Plan dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun PlanDark() { OpenRunCoachTheme { PlanPreview() } }
@Preview(name = "Completion sheet light", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable private fun SchedulerLight() { OpenRunCoachTheme { PlanPreview(PlanPreviewData.state().copy(showCompletionScheduler = true)) } }
@Preview(name = "Completion sheet dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun SchedulerDark() { OpenRunCoachTheme { PlanPreview(PlanPreviewData.state().copy(showCompletionScheduler = true)) } }
@Preview(name = "Plan history light", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable private fun HistoryLight() { OpenRunCoachTheme { PlanPreview(PlanPreviewData.state().copy(showPlanHistory = true)) } }
@Preview(name = "Plan history dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun HistoryDark() { OpenRunCoachTheme { PlanPreview(PlanPreviewData.state().copy(showPlanHistory = true)) } }
