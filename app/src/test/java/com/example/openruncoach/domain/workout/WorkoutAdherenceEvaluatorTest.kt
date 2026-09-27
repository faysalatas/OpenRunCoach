package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutAdherenceEvaluatorTest {
  private val now = Instant.parse("2026-09-26T08:00:00Z")
  private val evaluator = WorkoutAdherenceEvaluator(Clock.fixed(now, ZoneOffset.UTC))

  @Test fun easyRunSlightlyShorterIsCompletedWithMinorDeviationAndKeepsActualVolume() {
    val result = evaluator.evaluate(distanceWorkout(4_000.0), null, run(3_780.0), feedback())

    assertEquals(WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION, result.status)
    assertTrue(result.isCompleted)
    assertEquals(3_780.0, result.actualDistanceMeters!!, 0.01)
    assertEquals(4_000.0, result.intendedDistanceMeters!!, 0.01)
  }

  @Test fun easyRunAtPrescriptionIsCompletedAsPlanned() {
    val result = evaluator.evaluate(distanceWorkout(4_000.0), null, run(4_030.0), feedback())

    assertEquals(WorkoutExecutionStatus.COMPLETED_AS_PLANNED, result.status)
  }

  @Test fun completedEasyRunWithHighRpeKeepsCompletionButRecordsMeaningfulPrescriptionDeviation() {
    val result = evaluator.evaluate(
      distanceWorkout(4_000.0),
      null,
      run(3_780.0),
      feedback(rpe = 6),
    )

    assertEquals(WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION, result.status)
    assertTrue(result.isCompleted)
    assertTrue(result.explanation.contains("differed meaningfully"))
  }

  @Test fun completedEasyRunClearlyFasterThanTrustedPaceGuidanceRecordsMeaningfulDeviation() {
    val base = distanceWorkout(4_000.0)
    val block = base.blocks.single() as SingleSegmentBlock
    val workout = base.copy(
      blocks = listOf(block.copy(segment = block.segment.copy(
        secondaryIntensities = listOf(PaceTarget(465.0, 545.0, GuidanceConfidence.MEDIUM)),
      ))),
    )
    val actual = run(3_780.0).copy(
      endTime = now.plusSeconds(1_571),
      durationSeconds = 1_571,
      averagePaceSecondsPerKm = 415.0,
    )

    val result = evaluator.evaluate(workout, null, actual, feedback(rpe = 3))

    assertEquals(WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION, result.status)
    assertTrue(result.isCompleted)
  }

  @Test fun majorShortfallReportedAsPartialRemainsPartial() {
    val result = evaluator.evaluate(
      distanceWorkout(4_000.0),
      null,
      run(2_100.0),
      feedback(completion = CompletionStatus.PARTIAL, rpe = 9),
    )

    assertEquals(WorkoutExecutionStatus.PARTIALLY_COMPLETED, result.status)
    assertFalse(result.isCompleted)
  }

  @Test fun goalCompletionWorkoutShortOfFiveKCanBeUsefulButIsNotAnExactPrescriptionMatch() {
    val result = evaluator.evaluate(
      distanceWorkout(5_000.0, WorkoutPurpose.GOAL_COMPLETION),
      null,
      run(4_780.0),
      feedback(),
    )

    assertEquals(WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION, result.status)
    assertTrue(result.isCompleted)
  }

  @Test fun adjustedWorkoutIsTheComparisonBasisWithoutOverwritingOriginalPrescription() {
    val planned = distanceWorkout(4_500.0)
    val adjusted = AdjustedWorkout(planned.id, WorkoutType.EASY_RUN, 30, 3, 4)
    val actual = run(3_780.0).copy(durationSeconds = 1_720)

    val result = evaluator.evaluate(planned, adjusted, actual, feedback())

    assertEquals(WorkoutComparisonBasis.ADJUSTED_PRESCRIPTION, result.comparisonBasis)
    assertEquals(1_800L, result.intendedDurationSeconds)
    assertEquals(1_720L, result.actualDurationSeconds)
    assertEquals(4_500.0, (planned.blocks.single().segments.single().completion as DistanceCompletion).meters, 0.01)
  }

  @Test fun intervalTotalDistanceCannotHideHalfCompletedQualityWork() {
    val intervals = StructuredWorkoutSamples.intervals()
    val result = evaluator.evaluate(
      intervals,
      null,
      run(WorkoutEstimateCalculator.calculate(intervals).knownDistanceMeters),
      feedback(),
      WorkoutExecutionEvidence(qualityWorkCompletionFraction = 0.5),
    )

    assertEquals(WorkoutExecutionStatus.PARTIALLY_COMPLETED, result.status)
  }

  @Test fun qualityWorkoutWithoutSegmentEvidenceIsNotClaimedAsFullyFollowedFromTotalVolume() {
    val intervals = StructuredWorkoutSamples.intervals()
    val result = evaluator.evaluate(
      intervals,
      null,
      run(WorkoutEstimateCalculator.calculate(intervals).knownDistanceMeters),
      feedback(),
    )

    assertEquals(WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION, result.status)
    assertTrue(result.explanation.contains("could not be fully verified"))
  }

  @Test fun userReportedAbortIsNotHiddenByMatchingDistance() {
    val result = evaluator.evaluate(
      distanceWorkout(4_000.0),
      null,
      run(4_000.0),
      feedback(completion = CompletionStatus.ABORTED),
    )

    assertEquals(WorkoutExecutionStatus.ABORTED, result.status)
  }

  private fun distanceWorkout(
    distanceMeters: Double,
    purpose: WorkoutPurpose = WorkoutPurpose.AEROBIC_BASE,
  ) = PlannedWorkout(
    id = "planned",
    date = LocalDate.of(2026, 9, 26),
    type = if (purpose == WorkoutPurpose.GOAL_COMPLETION) WorkoutType.RACE_SPECIFIC else WorkoutType.EASY_RUN,
    name = "Easy Run",
    purpose = purpose,
    blocks = listOf(
      SingleSegmentBlock(
        id = "block",
        order = 0,
        segment = WorkoutSegment(
          id = "segment",
          order = 0,
          category = SegmentCategory.EASY,
          name = "Easy",
          completion = DistanceCompletion(distanceMeters),
          primaryIntensity = RpeTarget(3, 4),
        ),
      ),
    ),
    explanation = "Test prescription.",
    cues = emptyList(),
    createdAt = now.minusSeconds(3_600),
  )

  private fun run(distanceMeters: Double) = RunSummary(
    healthConnectRecordId = "run",
    startTime = now,
    endTime = now.plusSeconds(1_800),
    durationSeconds = 1_800,
    distanceMeters = distanceMeters,
    averageHeartRate = 140.0,
    maxHeartRate = 160,
    averagePaceSecondsPerKm = 1_800_000.0 / distanceMeters,
  )

  private fun feedback(
    completion: CompletionStatus = CompletionStatus.COMPLETED,
    rpe: Int = 3,
  ) = RunFeedback(
    id = "feedback",
    runId = "run",
    rpe = rpe,
    cardioEffort = CardioEffort.APPROPRIATE,
    muscleFatigue = MuscleFatigue.NONE,
    painLevel = PainLevel.NONE,
    completionStatus = completion,
    readiness = 4,
    notes = null,
    createdAt = now,
    updatedAt = now,
  )
}
