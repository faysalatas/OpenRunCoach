package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.plan.TrainingBlockType
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.FeedbackAggregates
import com.example.openruncoach.domain.profile.FitnessTrend
import com.example.openruncoach.domain.profile.LongestRecentRun
import com.example.openruncoach.domain.profile.PaceTrend
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.profile.TrainingConsistency
import com.example.openruncoach.domain.profile.ValueTrend
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.simpleDurationWorkout
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCompletionSchedulingTest {
  private val now = Instant.parse("2026-09-24T12:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val evaluator = GoalCompletionReadinessEvaluator()
  private val service = GoalCompletionMilestoneService(clock)

  @Test fun performanceReadyHasNoArbitraryDate() {
    val result = evaluator.evaluate(goal(), profile(longest = 4_800.0), null, now)
    assertEquals(GoalCompletionReadiness.GOAL_COMPLETION_READY, result.readiness)
    assertNull(result.scheduledDate)
    assertFalse(result.promptDismissed)
  }

  @Test fun calendarPositionCannotMakeAnUnpreparedAthleteReady() {
    val result = evaluator.evaluate(goal(), profile(longest = 3_500.0), null, now)
    assertEquals(GoalCompletionReadiness.GOAL_COMPLETION_NOT_READY, result.readiness)
    assertNull(result.scheduledDate)
  }

  @Test fun postponeKeepsGoalActiveAndClearsAnyChosenDate() {
    val goal = goal()
    val postponed = service.postpone(scheduled(LocalDate.parse("2026-10-17")))
    assertEquals(GoalStatus.ACTIVE, goal.status)
    assertEquals(GoalCompletionReadiness.GOAL_COMPLETION_READY, postponed.readiness)
    assertNull(postponed.scheduledDate)
    assertTrue(postponed.promptDismissed)
  }

  @Test fun rescheduleReplacesTheOldDate() {
    val changed = service.schedule(scheduled(LocalDate.parse("2026-10-17")), LocalDate.parse("2026-10-18"))
    assertEquals(LocalDate.parse("2026-10-18"), changed.scheduledDate)
    assertFalse(changed.reasons.last().contains("2026-10-17"))
  }

  @Test fun staleGoalIdFailsWithExplicitDiagnostic() {
    val failure = runCatching {
      service.schedule("replacement-goal", scheduled(LocalDate.parse("2026-10-17")), LocalDate.parse("2026-10-18"))
    }.exceptionOrNull()
    assertTrue(failure is IllegalArgumentException)
    assertTrue(failure?.message.orEmpty().contains("Stale Completion Run milestone"))
    assertTrue(failure?.message.orEmpty().contains("replacement-goal"))
  }

  @Test fun poorNewEvidenceWarnsWithoutSilentlyCancellingScheduledDate() {
    val date = LocalDate.parse("2026-10-17")
    val result = evaluator.evaluate(goal(), profile(longest = 3_000.0, significantPain = 0.5), scheduled(date), now.plusSeconds(86_400))
    assertEquals(GoalCompletionReadiness.GOAL_COMPLETION_SCHEDULED, result.readiness)
    assertEquals(date, result.scheduledDate)
    assertTrue(result.reasons.any { it.contains("postponement") })
  }

  @Test fun demonstratedCompletionResultMovesMilestoneToGoalCompleted() {
    val completion = GoalCompletionResult(
      GoalCompletionStatus.GOAL_DISTANCE_DEMONSTRATED,
      true,
      5_000.0,
      5_020.0,
      listOf("Goal distance demonstrated."),
    )
    val result = evaluator.evaluate(goal(), profile(4_800.0), scheduled(LocalDate.parse("2026-10-17")), now, completion)
    assertEquals(GoalCompletionReadiness.GOAL_COMPLETED, result.readiness)
    assertNull(result.scheduledDate)
  }

  @Test fun passingThePlannedDateDoesNotExpireTheGoalOrMilestone() {
    val past = scheduled(LocalDate.parse("2026-09-23"))
    val result = evaluator.evaluate(goal(), profile(4_800.0), past, now)
    assertEquals(GoalStatus.ACTIVE, goal().status)
    assertEquals(GoalCompletionReadiness.GOAL_COMPLETION_SCHEDULED, result.readiness)
    assertEquals(past.scheduledDate, result.scheduledDate)
  }

  @Test fun failedAttemptKeepsGoalActiveAndAllowsAnotherDate() {
    val failed = GoalCompletionResult(
      GoalCompletionStatus.TARGET_NOT_REACHED,
      false,
      5_000.0,
      4_800.0,
      listOf("Target distance was not demonstrated continuously."),
    )
    val current = evaluator.evaluate(
      goal(), profile(4_800.0), scheduled(LocalDate.parse("2026-09-24")), now, failed,
    )
    val rescheduled = service.schedule(current, LocalDate.parse("2026-10-18"))
    assertEquals(GoalStatus.ACTIVE, goal().status)
    assertEquals(LocalDate.parse("2026-10-18"), rescheduled.scheduledDate)
  }

  @Test fun completionDateFeasibilityIsExplainable() {
    val feasibility = CompletionDateFeasibilityEvaluator().evaluate(
      goal(), profile(4_800.0), LocalDate.parse("2026-10-18"), LocalDate.parse("2026-09-24"),
    )
    assertEquals(CompletionDateFeasibility.FEASIBLE, feasibility.feasibility)
    assertTrue(feasibility.explanation.isNotBlank())
  }

  @Test fun missingTrainingEvidenceProducesInsufficientDateConfidence() {
    val feasibility = CompletionDateFeasibilityEvaluator().evaluate(
      goal(), profile(4_800.0).copy(profileConfidence = ProfileConfidence.VERY_LOW),
      LocalDate.parse("2026-10-18"), LocalDate.parse("2026-09-24"),
    )
    assertEquals(CompletionDateFeasibility.INSUFFICIENT_DATA, feasibility.feasibility)
  }

  @Test fun suggestionsAvoidPoorSpacingAroundHardWorkouts() {
    val today = LocalDate.parse("2026-09-24")
    val hard = simpleDurationWorkout(
      "hard", today.plusDays(5), WorkoutType.THRESHOLD, 35, 7, 8, now,
    )
    val longRun = simpleDurationWorkout(
      "long", today.plusDays(10), WorkoutType.LONG_RUN, 50, 3, 4, now,
    )
    val suggestions = GoalCompletionDateSuggester().suggest(today, listOf(hard, longRun))
    assertTrue(suggestions.isNotEmpty())
    assertTrue(suggestions.all { kotlin.math.abs(ChronoUnit.DAYS.between(hard.date, it.date)) > 2 })
    assertEquals(1, suggestions.count { it.recommended })
  }

  private fun scheduled(date: LocalDate) = GoalCompletionMilestone(
    "goal", GoalCompletionReadiness.GOAL_COMPLETION_SCHEDULED, date, false,
    listOf("User selected a date."), now,
  )

  private fun goal() = TrainingGoal(
    "goal", GoalDistance.FIVE_K, null, GoalType.COMPLETE_DISTANCE, null, now, GoalStatus.ACTIVE,
    StartingPerformanceSnapshot(null, null, 10_000.0, 4_200, 4_800.0, 2_100, 3.0, 430.0, null, ProfileConfidence.LOW, now),
    ProfileConfidence.LOW, GoalAssessment(GoalAssessmentLevel.PLAUSIBLE, listOf("Fixture")),
  )

  private fun profile(longest: Double, significantPain: Double = 0.0) = AthletePerformanceProfile(
    totalCanonicalRuns = 6,
    runsLast7Days = 2,
    runsLast28Days = 6,
    activeWeeksLast8Weeks = 3,
    distanceLast7DaysMeters = 9_000.0,
    durationLast7DaysSeconds = 4_000,
    distanceLast28DaysMeters = 24_000.0,
    durationLast28DaysSeconds = 11_000,
    completedWeeklyVolumes = emptyList(),
    averageWeeklyDistanceLast4WeeksMeters = 10_000.0,
    averageWeeklyDurationLast4WeeksSeconds = 4_200,
    longestRunLast28Days = LongestRecentRun("long", now, longest, 2_100),
    averageRunsPerWeek = 3.0,
    currentRunningStreakWeeks = 3,
    consistency = TrainingConsistency.DEVELOPING,
    consistencyReasons = listOf("Fixture"),
    performanceBenchmarks = emptyList(),
    recentPaceTrend = PaceTrend.STABLE,
    typicalEasyPace = null,
    typicalEasyHeartRate = null,
    recentAverageHeartRate = null,
    recentHeartRateTrend = ValueTrend.INSUFFICIENT_DATA,
    vo2MaxLatest = null,
    vo2MaxPrevious = null,
    vo2MaxTrend = FitnessTrend.INSUFFICIENT_DATA,
    typicalEasyCadence = null,
    recentCadenceTrend = ValueTrend.INSUFFICIENT_DATA,
    feedback = FeedbackAggregates(6, 4.0, 0.9, 0.0, 0.0, 0.0, significantPain, 4.0),
    calculatedAt = now,
    profileConfidence = ProfileConfidence.LOW,
    confidenceReasons = listOf("Fixture"),
    dataQualityNotes = emptyList(),
  )
}
