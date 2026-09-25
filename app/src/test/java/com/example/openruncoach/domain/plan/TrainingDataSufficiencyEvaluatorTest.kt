package com.example.openruncoach.domain.plan

import com.example.openruncoach.domain.goal.GoalAssessment
import com.example.openruncoach.domain.goal.GoalAssessmentLevel
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalStatus
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.StartingPerformanceSnapshot
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.BenchmarkSource
import com.example.openruncoach.domain.profile.DistanceBenchmark
import com.example.openruncoach.domain.profile.FeedbackAggregates
import com.example.openruncoach.domain.profile.FitnessTrend
import com.example.openruncoach.domain.profile.LongestRecentRun
import com.example.openruncoach.domain.profile.PaceTrend
import com.example.openruncoach.domain.profile.PerformanceBenchmark
import com.example.openruncoach.domain.profile.ProfileBaseline
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.profile.StandardRunningDistance
import com.example.openruncoach.domain.profile.TrainingConsistency
import com.example.openruncoach.domain.profile.ValueTrend
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingDataSufficiencyEvaluatorTest {
  private val now = Instant.parse("2026-09-25T08:00:00Z")
  private val evaluator = TrainingDataSufficiencyEvaluator()

  @Test fun unknownAthleteRequiresAssessment() {
    val result = evaluator.evaluate(profile(0, null, null, 0), goal(), emptyList(), false)
    assertEquals(TrainingDataSufficiency.ASSESSMENT_REQUIRED, result.level)
    assertFalse(result.canGenerateNormalPlan)
    assertEquals(AssessmentRunType.INITIAL_BASELINE, result.recommendedAssessment)
    assertTrue(result.reasons.isNotEmpty())
  }

  @Test fun currentLowConfidenceProfileCanUseAConservativeNormalPlan() {
    val result = evaluator.evaluate(profile(6, 4_800.0, 430.0, 6), goal(), emptyList(), true)
    assertTrue(result.canGenerateNormalPlan)
    assertTrue(result.level != TrainingDataSufficiency.ASSESSMENT_REQUIRED)
    assertTrue(result.canPrescribeEasyPace)
    assertTrue(result.canPrescribeLongRunPace)
  }

  @Test fun completedAssessmentsPreventAnInfiniteAssessmentLoopWithoutHeartRateOrCadence() {
    val result = evaluator.evaluate(
      profile(1, 2_500.0, null, 1),
      goal(),
      listOf(metrics()),
      feedbackAvailable = true,
      completedAssessmentAttempts = 2,
    )
    assertTrue(result.canGenerateNormalPlan)
    assertEquals(TrainingDataSufficiency.PARTIALLY_SUFFICIENT, result.level)
    assertTrue(result.reasons.any { it.contains("without heart-rate or cadence") })
  }

  @Test fun verifiedGoalDistanceBenchmarkIsReported() {
    val benchmark = PerformanceBenchmark(
      StandardRunningDistance.FIVE_K, 5_000.0, 1_800, 360.0, now, "five", ProfileConfidence.HIGH,
      BenchmarkSource.OBSERVED_SESSION,
    )
    val withBenchmark = profile(8, 6_000.0, 420.0, 8).copy(
      performanceBenchmarks = listOf(DistanceBenchmark(StandardRunningDistance.FIVE_K, benchmark, benchmark)),
      profileConfidence = ProfileConfidence.HIGH,
    )
    val result = evaluator.evaluate(withBenchmark, goal().copy(goalType = GoalType.TARGET_TIME, targetTimeSeconds = 1_750), emptyList(), true)
    assertTrue(result.hasGoalDistanceBenchmark)
    assertTrue(result.canPrescribeQualityPace)
  }

  private fun goal() = TrainingGoal(
    "goal", GoalDistance.FIVE_K, null, GoalType.COMPLETE_DISTANCE, null, now, GoalStatus.ACTIVE,
    StartingPerformanceSnapshot(null, null, 0.0, 0, null, null, 0.0, null, null, ProfileConfidence.LOW, now),
    ProfileConfidence.LOW, GoalAssessment(GoalAssessmentLevel.LIMITED_DATA, listOf("Fixture")),
  )

  private fun profile(totalRuns: Int, longest: Double?, easyPace: Double?, feedbackCount: Int) = AthletePerformanceProfile(
    totalCanonicalRuns = totalRuns,
    runsLast7Days = minOf(totalRuns, 2),
    runsLast28Days = totalRuns,
    activeWeeksLast8Weeks = if (totalRuns > 0) 2 else 0,
    distanceLast7DaysMeters = if (totalRuns > 0) 5_000.0 else 0.0,
    durationLast7DaysSeconds = if (totalRuns > 0) 2_400 else 0,
    distanceLast28DaysMeters = if (totalRuns > 0) 10_000.0 else 0.0,
    durationLast28DaysSeconds = if (totalRuns > 0) 4_800 else 0,
    completedWeeklyVolumes = emptyList(),
    averageWeeklyDistanceLast4WeeksMeters = if (totalRuns > 0) 2_500.0 else 0.0,
    averageWeeklyDurationLast4WeeksSeconds = if (totalRuns > 0) 1_200 else 0,
    longestRunLast28Days = longest?.let { LongestRecentRun("run", now, it, 1_200) },
    averageRunsPerWeek = totalRuns / 4.0,
    currentRunningStreakWeeks = if (totalRuns > 0) 1 else 0,
    consistency = if (totalRuns > 0) TrainingConsistency.DEVELOPING else TrainingConsistency.INACTIVE,
    consistencyReasons = listOf("Fixture"),
    performanceBenchmarks = emptyList(),
    recentPaceTrend = PaceTrend.INSUFFICIENT_DATA,
    typicalEasyPace = easyPace?.let { ProfileBaseline(it, it - 20, it + 20, 2, ProfileConfidence.LOW, listOf("Fixture")) },
    typicalEasyHeartRate = null,
    recentAverageHeartRate = null,
    recentHeartRateTrend = ValueTrend.INSUFFICIENT_DATA,
    vo2MaxLatest = null,
    vo2MaxPrevious = null,
    vo2MaxTrend = FitnessTrend.INSUFFICIENT_DATA,
    typicalEasyCadence = null,
    recentCadenceTrend = ValueTrend.INSUFFICIENT_DATA,
    feedback = FeedbackAggregates(feedbackCount, 4.0, 1.0, 0.0, 0.0, 0.0, 0.0, 4.0),
    calculatedAt = now,
    profileConfidence = ProfileConfidence.LOW,
    confidenceReasons = listOf("Fixture"),
    dataQualityNotes = emptyList(),
  )

  private fun metrics() = RunMetrics(
    runId = "run",
    startTime = now,
    endTime = now.plusSeconds(1_200),
    durationSeconds = MetricValue(1_200L, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    distanceMeters = MetricValue(2_500.0, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    averagePaceSecondsPerKm = MetricValue(480.0, MetricSource.DERIVED, MetricConfidence.MEDIUM),
    generatedAt = now,
  )
}
