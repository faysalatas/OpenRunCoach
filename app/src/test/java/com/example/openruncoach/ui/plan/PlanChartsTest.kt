package com.example.openruncoach.ui.plan

import com.example.openruncoach.ui.components.BarState
import com.example.openruncoach.domain.workout.WorkoutType
import org.junit.Assert.*
import org.junit.Test

class PlanChartsTest {
  private val source = PlanPreviewData.state()
  private val plan = source.plan!!
  private fun charts(state: PlanUiState = source) = planCharts(state.plan!!, PlanPreviewData.today,
    state.workoutMetadata, state.activeGoal?.distanceMeters, state.completionMilestone?.scheduledDate)

  @Test fun realGeneratedWeeksAndExplicitGoalDateDetermineColumns() {
    val result = charts()
    assertEquals(8, result.weeklyDistance.size)
    assertEquals(7, result.longRuns.size)
    assertEquals(BarState.Goal, result.longRuns.last().state)
    assertEquals(10f, result.longRuns.last().value)
    assertEquals(BarState.Done, result.longRuns.first().state)
    assertEquals(BarState.Current, result.longRuns[1].state)
    assertEquals(8f, result.longRuns[1].value)
  }

  @Test fun missingCanonicalDistanceDoesNotUseStaleExecutionOrPlannedDistance() {
    val id = plan.weeks.first().workouts.first().workout.id
    val record = source.workoutMetadata.getValue(id)
    val missing = source.copy(workoutMetadata = source.workoutMetadata + (id to record.copy(actual = record.actual!!.copy(distanceMeters = null))))
    assertFalse(charts(missing).longRuns.any { it.label == "W1" })
    assertFalse(charts(missing).weeklyDistance.any { it.label == "W1" })
  }

  @Test fun canonicalActualCorrectionChangesOnlyObservedBar() {
    val id = plan.weeks.first().workouts.first().workout.id
    val record = source.workoutMetadata.getValue(id)
    val corrected = source.copy(workoutMetadata = source.workoutMetadata + (id to record.copy(actual = record.actual!!.copy(distanceMeters = 6_500.0))))
    assertEquals(6.5f, charts(corrected).longRuns.first().value)
    assertEquals(6.5f, charts(corrected).weeklyDistance.first().value)
    assertEquals(8f, charts(corrected).longRuns[1].value)
    assertEquals(21f, charts(corrected).weeklyDistance[1].value)
    assertEquals(7_100.0, plan.weeks.first().workouts.first().executionResult!!.actualDistanceMeters!!, 0.0)
  }

  @Test fun noCompletionDateDoesNotInventGoalColumnFromPlanHorizon() {
    assertFalse(charts(source.copy(completionMilestone = null)).longRuns.any { it.state == BarState.Goal })
  }

  @Test fun onlyExistingWeeksAndKnownTargetsAreDrawn() {
    val shorter = plan.copy(weeks = plan.weeks.take(2).map { it.copy(targetDistanceMeters = null) })
    val result = charts(source.copy(plan = shorter, completionMilestone = null))
    assertEquals(1, result.weeklyDistance.size) // Past recorded actual remains valid; current target is unknown.
    assertEquals(2, result.longRuns.size)
  }

  @Test fun assessmentWithoutLongRunsHidesLongChart() {
    val weeks = plan.weeks.map { week -> week.copy(workouts = week.workouts.map { entry ->
      entry.copy(workout = entry.workout.copy(type = WorkoutType.ASSESSMENT),
        originalWorkout = entry.originalWorkout.copy(type = WorkoutType.ASSESSMENT))
    }) }
    assertTrue(charts(source.copy(plan = plan.copy(weeks = weeks))).longRuns.isEmpty())
  }
}
