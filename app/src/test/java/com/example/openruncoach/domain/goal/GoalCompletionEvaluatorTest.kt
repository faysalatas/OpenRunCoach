package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.RunTimeSeries
import com.example.openruncoach.domain.metrics.TimedMetricSample
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCompletionEvaluatorTest {
  private val now = Instant.parse("2026-09-25T08:00:00Z")
  private val evaluator = GoalCompletionEvaluator()

  @Test fun physicalCheckoutIdentityPreservesStrictGoalEvaluationAcrossProviderRepresentations() {
    val providerRun = run(5030.0).copy(physicalRunId = "physical")
    val result = evaluator.evaluate(goal(), providerRun, metrics(5030.0), WorkoutLifecycleState.COMPLETED,
      feedback().copy(runId = "physical"))
    assertEquals(GoalCompletionStatus.GOAL_DISTANCE_DEMONSTRATED, result.status)
    val unrelated = evaluator.evaluate(goal(), providerRun, metrics(5030.0), WorkoutLifecycleState.COMPLETED,
      feedback().copy(runId = "other-physical"))
    assertEquals(GoalCompletionStatus.INVALID_RUN, unrelated.status)
  }

  @Test fun correctedCanonicalDistanceCannotCompleteGoalUsingOriginalProviderVolume() {
    val result = evaluator.evaluate(goal(), run(4780.0), metrics(5030.0), WorkoutLifecycleState.COMPLETED, feedback())
    assertEquals(GoalCompletionStatus.TARGET_NOT_REACHED, result.status)
    assertEquals(4780.0, result.observedDistanceMeters!!, 0.0)
    assertFalse(result.completed)
  }

  @Test fun fourPointSevenEightKmDoesNotCompleteFiveK() {
    val result = evaluator.evaluate(goal(), run(4_780.0), metrics(4_780.0), WorkoutLifecycleState.COMPLETED, feedback())
    assertFalse(result.completed)
    assertEquals(GoalCompletionStatus.TARGET_NOT_REACHED, result.status)
    assertTrue(result.reasons.any { it.contains("below") })
  }

  @Test fun completedRunAtOrBeyondTargetCompletesFiveKExplainably() {
    val result = evaluator.evaluate(goal(), run(5_030.0), metrics(5_030.0), WorkoutLifecycleState.ANALYZED, feedback())
    assertTrue(result.completed)
    assertEquals(GoalCompletionStatus.GOAL_DISTANCE_DEMONSTRATED, result.status)
    assertTrue(result.reasons.any { it == "Goal distance demonstrated." })
  }

  @Test fun fiveKWithSustainedWalkingDoesNotCompleteContinuousDistanceGoal() {
    val walkingSamples = listOf(0L, 10L, 20L, 30L, 40L).map { seconds ->
      TimedMetricSample(now.plusSeconds(seconds), 1.0)
    }
    val result = evaluator.evaluate(
      goal(),
      run(5_050.0),
      metrics(5_050.0).copy(series = RunTimeSeries(speed = walkingSamples)),
      WorkoutLifecycleState.COMPLETED,
      feedback(),
    )
    assertFalse(result.completed)
    assertEquals(GoalCompletionStatus.CONTINUITY_NOT_DEMONSTRATED, result.status)
    assertTrue(result.reasons.any { it.contains("walking or stopped") })
  }

  @Test fun fiveKWithSparseWalkingEveryTwentySecondsDoesNotCompleteContinuousDistanceGoal() {
    val walkingSamples = (0L..600L step 20).map { seconds ->
      TimedMetricSample(now.plusSeconds(seconds), 1.0)
    }
    val result = evaluator.evaluate(
      goal(),
      run(5_050.0, durationSeconds = 600),
      metrics(5_050.0, durationSeconds = 600).copy(series = RunTimeSeries(speed = walkingSamples)),
      WorkoutLifecycleState.COMPLETED,
      feedback(),
    )

    assertEquals(5_050.0, result.observedDistanceMeters!!, 0.0)
    assertFalse(result.completed)
    assertEquals(GoalCompletionStatus.CONTINUITY_NOT_DEMONSTRATED, result.status)
    assertTrue(result.reasons.any { it.contains("evidence was insufficient") })
  }

  @Test fun qualifyingRunCompletesGoalRegardlessOfWorkoutName() {
    val result = evaluator.evaluate(goal(), run(5_010.0), metrics(5_010.0), WorkoutLifecycleState.COMPLETED, feedback())
    assertTrue(result.completed)
    assertEquals(GoalCompletionStatus.GOAL_DISTANCE_DEMONSTRATED, result.status)
  }

  @Test fun targetDistanceWithoutContinuitySamplesDoesNotInventContinuousCompletion() {
    val result = evaluator.evaluate(
      goal(), run(5_010.0), metrics(5_010.0).copy(series = RunTimeSeries()),
      WorkoutLifecycleState.COMPLETED, feedback(),
    )
    assertFalse(result.completed)
    assertEquals(GoalCompletionStatus.CONTINUITY_NOT_DEMONSTRATED, result.status)
    assertTrue(result.reasons.any { it.contains("could not be verified") })
  }

  @Test fun partialFeedbackDoesNotCompleteGoalEvenAtDistance() {
    val partial = feedback().copy(completionStatus = CompletionStatus.PARTIAL)
    val result = evaluator.evaluate(goal(), run(5_100.0), metrics(5_100.0), WorkoutLifecycleState.ANALYZED, partial)
    assertEquals(GoalCompletionStatus.WORKOUT_NOT_COMPLETED, result.status)
  }

  private fun goal() = TrainingGoal(
    id = "goal",
    distance = GoalDistance.FIVE_K,
    customDistanceMeters = null,
    goalType = GoalType.COMPLETE_DISTANCE,
    targetTimeSeconds = null,
    createdAt = now,
    status = GoalStatus.ACTIVE,
    startingPerformanceSnapshot = StartingPerformanceSnapshot(null, null, 0.0, 0, null, null, 0.0, null, null, ProfileConfidence.LOW, now),
    confidenceAtCreation = ProfileConfidence.LOW,
    assessmentAtCreation = GoalAssessment(GoalAssessmentLevel.LIMITED_DATA, listOf("Fixture")),
  )

  private fun run(distance: Double, durationSeconds: Long = 2_100) = RunSummary(
    healthConnectRecordId = "run",
    startTime = now,
    endTime = now.plusSeconds(durationSeconds),
    durationSeconds = durationSeconds,
    distanceMeters = distance,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = durationSeconds * 1_000.0 / distance,
  )

  private fun metrics(distance: Double, durationSeconds: Long = 2_100) = RunMetrics(
    runId = "run",
    startTime = now,
    endTime = now.plusSeconds(durationSeconds),
    durationSeconds = MetricValue(durationSeconds, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    distanceMeters = MetricValue(distance, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    averagePaceSecondsPerKm = MetricValue(durationSeconds * 1_000.0 / distance, MetricSource.DERIVED, MetricConfidence.HIGH),
    series = RunTimeSeries(speed = (0L..durationSeconds step 10).map { seconds ->
      TimedMetricSample(now.plusSeconds(seconds), 2.4)
    }),
    generatedAt = now,
  )

  private fun feedback() = RunFeedback(
    "feedback", "run", 4, CardioEffort.APPROPRIATE, MuscleFatigue.MILD, PainLevel.NONE,
    CompletionStatus.COMPLETED, null, null, now, now,
  )
}
