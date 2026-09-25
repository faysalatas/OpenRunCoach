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

  @Test fun fourPointEightKmDoesNotCompleteFiveK() {
    val result = evaluator.evaluate(goal(), run(4_800.0), metrics(4_800.0), WorkoutLifecycleState.COMPLETED, feedback())
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

  private fun run(distance: Double) = RunSummary(
    healthConnectRecordId = "run",
    startTime = now,
    endTime = now.plusSeconds(2_100),
    durationSeconds = 2_100,
    distanceMeters = distance,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = 2_100_000.0 / distance,
  )

  private fun metrics(distance: Double) = RunMetrics(
    runId = "run",
    startTime = now,
    endTime = now.plusSeconds(2_100),
    durationSeconds = MetricValue(2_100L, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    distanceMeters = MetricValue(distance, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    averagePaceSecondsPerKm = MetricValue(2_100_000.0 / distance, MetricSource.DERIVED, MetricConfidence.HIGH),
    generatedAt = now,
  )

  private fun feedback() = RunFeedback(
    "feedback", "run", 4, CardioEffort.APPROPRIATE, MuscleFatigue.MILD, PainLevel.NONE,
    CompletionStatus.COMPLETED, null, null, now, now,
  )
}
