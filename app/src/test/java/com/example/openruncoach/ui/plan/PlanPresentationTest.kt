package com.example.openruncoach.ui.plan

import com.example.openruncoach.data.PlanWorkoutPresentation
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.plan.*
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.workout.*
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class PlanPresentationTest {
  private val monday = LocalDate.of(2026, 10, 5)
  private val workout = simpleDurationWorkout("easy", monday, WorkoutType.EASY_RUN, 30, 3, 4, Instant.EPOCH)
  private val entry = PlannedWorkoutEntry(WeeklyWorkoutSlot.EARLY_WEEK, workout, "Build consistency.")
  private val week = TrainingWeek(0, monday, listOf(entry), 8_800.0, 3_600,
    WeekLoadClassification.NORMAL, "Aerobic consistency.", listOf("Keep the effort comfortable."))

  @Test fun weekDateRangesAndPositionsCoverBoundaries() {
    assertEquals("5–11 Oct", formatPlanWeekRange(monday, Locale.US))
    assertEquals("28 Sep – 4 Oct", formatPlanWeekRange(LocalDate.of(2026, 9, 28), Locale.US))
    assertEquals("28 Dec 2026 – 3 Jan 2027", formatPlanWeekRange(LocalDate.of(2026, 12, 28), Locale.US))
    assertEquals(PlanWeekPosition.FUTURE, week.position(monday.minusDays(1)))
    assertEquals(PlanWeekPosition.CURRENT, week.position(monday))
    assertEquals(PlanWeekPosition.CURRENT, week.position(monday.plusDays(6)))
    assertEquals(PlanWeekPosition.PAST, week.position(monday.plusDays(7)))
  }

  @Test fun canonicalActualReplacesHistoricalExecutionVolumeWithoutChangingCompletion() {
    val completed = entry.copy(executionResult = execution(2_100.0))
    val actual = actual(1_950.0)
    val metadata = mapOf(workout.id to PlanWorkoutPresentation(WorkoutLifecycleState.COMPLETED, false, actual))
    val summary = week.copy(workouts = listOf(completed)).summary(monday, metadata)
    assertEquals(1_950.0, requireNotNull(summary.actualDistanceMeters), 0.0)
    assertEquals(8_800.0, requireNotNull(summary.plannedDistanceMeters), 0.0)
    assertEquals(1, summary.completedSessions)
    assertTrue(summary.showActuals)
    assertEquals(2_100.0, requireNotNull(completed.executionResult?.actualDistanceMeters), 0.0)
  }

  @Test fun unknownCanonicalDistanceDoesNotFallBackToStaleExecutionDistance() {
    val completed = entry.copy(executionResult = execution(2_100.0))
    val metadata = mapOf(workout.id to PlanWorkoutPresentation(WorkoutLifecycleState.COMPLETED, false, actual(null)))
    assertNull(week.copy(workouts = listOf(completed)).summary(monday, metadata).actualDistanceMeters)
  }

  @Test fun recordedRunBeforeCheckoutContributesVolumeButDoesNotInventCompletion() {
    val metadata = mapOf(workout.id to PlanWorkoutPresentation(WorkoutLifecycleState.PLANNED, false, actual(1_950.0)))
    val summary = week.summary(monday, metadata)
    assertEquals(1_950.0, requireNotNull(summary.actualDistanceMeters), 0.0)
    assertEquals(1, summary.recordedSessions)
    assertEquals(0, summary.completedSessions)
    assertEquals("Run recorded · Checkout required", entry.statusLabel(metadata[workout.id]))
  }

  @Test fun partialAndStoppedTrainingCountsAsVolumeWithoutCompletingSession() {
    for (status in listOf(WorkoutExecutionStatus.PARTIALLY_COMPLETED, WorkoutExecutionStatus.ABORTED)) {
      val summary = week.copy(workouts = listOf(entry.copy(executionResult = execution(1_160.0).copy(status = status))))
        .summary(monday.plusDays(8))
      assertEquals(1_160.0, requireNotNull(summary.actualDistanceMeters), 0.0)
      assertEquals(0, summary.completedSessions)
      assertTrue(summary.showActuals)
    }
  }

  @Test fun futureWeekSuppressesActualProgressAndEmptyWeekDoesNotInventZeroDistance() {
    assertFalse(week.copy(workouts = listOf(entry.copy(executionResult = execution(1_950.0))))
      .summary(monday.minusDays(7)).showActuals)
    val summary = week.summary(monday)
    assertFalse(summary.showActuals)
    assertNull(summary.actualDistanceMeters)
  }

  @Test fun explicitRestIsNotASessionAndPassingDateDoesNotInventFailure() {
    val rest = simpleDurationWorkout("rest", monday.plusDays(1), WorkoutType.REST, 0, 0, 0, Instant.EPOCH)
    val summary = week.copy(workouts = listOf(entry, PlannedWorkoutEntry(WeeklyWorkoutSlot.MID_WEEK, rest, "Recovery.")))
      .summary(monday.plusDays(20))
    assertEquals(1, summary.sessionCount)
    assertEquals("Scheduled", entry.statusLabel(null))
    assertEquals("Check-in required", entry.statusLabel(PlanWorkoutPresentation(WorkoutLifecycleState.CHECK_IN_REQUIRED, false, null)))
  }

  @Test fun backgroundUpdatesPreserveSelectedWeekAndNewPlanSelectsCurrentWeek() {
    val weeks = (0..2).map { index -> week.copy(weekIndex = index, startDate = monday.plusWeeks(index.toLong()),
      workouts = listOf(entry.copy(workout = workout.copy(id = "easy-$index", date = monday.plusWeeks(index.toLong()))))) }
    val plan = TrainingPlan("plan", "goal", Instant.EPOCH, monday, null, weeks, emptyList(), 1,
      "A provisional plan.", ProfileConfidence.MEDIUM, TrainingPlanStatus.ACTIVE)
    assertEquals(2, selectedPlanWeek("plan", 2, plan, monday.plusDays(1)))
    assertEquals(1, selectedPlanWeek("previous", 2, plan, monday.plusDays(8)))
    assertEquals(2, selectedPlanWeek("plan", 100, plan, monday))
  }

  private fun execution(distance: Double) = WorkoutExecutionResult(workout.id, "canonical",
    WorkoutExecutionStatus.COMPLETED_AS_PLANNED, WorkoutComparisonBasis.ORIGINAL_PRESCRIPTION,
    2_000.0, null, distance, 900, null, "Saved completion decision.", Instant.EPOCH, "physical")

  private fun actual(distance: Double?) = RunSummary("canonical", Instant.EPOCH, Instant.EPOCH.plusSeconds(900),
    900, distance, null, null, null, physicalRunId = "physical")
}
