package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.ProfileConfidence
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class TrainingGoalFactory(
  private val clock: Clock = Clock.systemDefaultZone(),
  private val idProvider: () -> String = { UUID.randomUUID().toString() },
  private val assessmentEvaluator: GoalAssessmentEvaluator = GoalAssessmentEvaluator(),
) {
  fun create(draft: TrainingGoalDraft, profile: AthletePerformanceProfile?): TrainingGoal {
    val validation = validateGoalDraft(draft, LocalDate.now(clock))
    require(validation.isValid) { validation.errors.joinToString(" ") }
    val now = clock.instant()
    val benchmark = profile?.goalBenchmark(draft.distance)
    val snapshot = StartingPerformanceSnapshot(
      bestBenchmark = benchmark?.personalBest?.let { GoalBenchmarkSnapshot(it.durationSeconds, it.runId, it.date) },
      mostRecentBenchmark = benchmark?.mostRecent?.let { GoalBenchmarkSnapshot(it.durationSeconds, it.runId, it.date) },
      averageWeeklyDistanceMeters = profile?.averageWeeklyDistanceLast4WeeksMeters ?: 0.0,
      averageWeeklyDurationSeconds = profile?.averageWeeklyDurationLast4WeeksSeconds ?: 0L,
      longestRecentRunDistanceMeters = profile?.longestRunLast28Days?.distanceMeters,
      longestRecentRunDurationSeconds = profile?.longestRunLast28Days?.durationSeconds,
      averageRunsPerWeek = profile?.averageRunsPerWeek ?: 0.0,
      typicalEasyPaceSecondsPerKm = profile?.typicalEasyPace?.representative,
      typicalEasyHeartRate = profile?.typicalEasyHeartRate?.representative,
      profileConfidence = profile?.profileConfidence ?: ProfileConfidence.VERY_LOW,
      capturedAt = now,
    )
    return TrainingGoal(
      id = idProvider(),
      distance = draft.distance,
      customDistanceMeters = draft.customDistanceMeters,
      goalType = draft.goalType,
      targetTimeSeconds = draft.targetTimeSeconds,
      targetDate = draft.targetDate,
      createdAt = now,
      status = GoalStatus.ACTIVE,
      startingPerformanceSnapshot = snapshot,
      confidenceAtCreation = snapshot.profileConfidence,
      assessmentAtCreation = assessmentEvaluator.evaluate(draft, profile),
    )
  }
}

/** A race goal describes training demands; it does not cap individual workout distances. */
