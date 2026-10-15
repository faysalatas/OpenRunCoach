package com.example.openruncoach.ui.workout

import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.workout.*
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorkoutSummaryPresentationTest {
  private val pace = PaceTarget(465.0, 545.0)
  private val work = WorkoutSegment("work", 0, SegmentCategory.WORK, "Work", DistanceCompletion(400.0),
    pace, listOf(RpeTarget(7, 8)))

  @Test fun continuousRunKeepsGenuineDistancePaceAndEffort() {
    val easy = work.copy(category = SegmentCategory.EASY, completion = DistanceCompletion(2_000.0),
      secondaryIntensities = listOf(RpeTarget(3, 4)))
    val summary = workout(listOf(SingleSegmentBlock("easy", 0, easy))).listSummary()
    assertEquals(paceLabel(), summary.paceLabel)
    assertEquals("RPE 3–4", summary.rpeLabel)
    assertEquals("Easy", summary.effortLabel)
    assertEquals(1, summary.intensityBars)
    assertFalse(summary.structured)
  }

  @Test fun uniqueWorkPaceInRepeatedSessionNeverBecomesWholeRunTarget() {
    val recovery = work.copy(id = "recovery", order = 1, category = SegmentCategory.RECOVERY,
      completion = DurationCompletion(60), primaryIntensity = RpeTarget(2, 3), secondaryIntensities = emptyList())
    val summary = workout(listOf(RepeatBlock("reps", 0, 6, listOf(work, recovery)))).listSummary()
    assertNull(summary.paceLabel)
    assertEquals("6 × 400 m", summary.repetitionLabel)
    assertEquals("RPE 7–8", summary.rpeLabel)
    assertTrue(summary.structured)
  }

  @Test fun warmupWorkCooldownDoesNotFlattenUniqueWorkTarget() {
    val warmup = work.copy(id = "warmup", category = SegmentCategory.WARMUP,
      primaryIntensity = RpeTarget(2, 3), secondaryIntensities = emptyList())
    val summary = workout(listOf(SingleSegmentBlock("warmup", 0, warmup),
      SingleSegmentBlock("work", 1, work), SingleSegmentBlock("cooldown", 2, warmup.copy(id = "cooldown", category = SegmentCategory.COOLDOWN))))
      .listSummary()
    assertNull(summary.paceLabel)
    assertEquals("RPE 7–8", summary.rpeLabel)
    assertTrue(summary.structured)
  }

  @Test fun goalTitlesUseRealDistanceAndTypeWithoutAddingDates() {
    assertEquals("Complete 5K", planGoalTitle(GoalDistance.FIVE_K, null, GoalType.COMPLETE_DISTANCE))
    assertEquals("Improve 10K performance", planGoalTitle(GoalDistance.TEN_K, null, GoalType.IMPROVE_PERFORMANCE))
    assertEquals("${formatWorkoutDistance(7_500.0)} target time", planGoalTitle(GoalDistance.CUSTOM, 7_500.0, GoalType.TARGET_TIME))
  }

  private fun paceLabel() = WorkoutDescriptionFormatter.paceRange(pace).replace(" min/km", " /km")
  private fun workout(blocks: List<WorkoutBlock>) = PlannedWorkout("workout", LocalDate.of(2026, 10, 8),
    WorkoutType.EASY_RUN, "Workout", WorkoutPurpose.AEROBIC_BASE, blocks, "Existing rationale.", emptyList(), Instant.EPOCH)
}
