package com.example.openruncoach.data

import com.example.openruncoach.domain.goal.GoalAssessment
import com.example.openruncoach.domain.goal.GoalAssessmentLevel
import com.example.openruncoach.domain.goal.GoalCompletionMilestone
import com.example.openruncoach.domain.goal.GoalCompletionReadiness
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalStatus
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.StartingPerformanceSnapshot
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.plan.TrainingPlanGenerator
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.FeedbackAggregates
import com.example.openruncoach.domain.profile.FitnessTrend
import com.example.openruncoach.domain.profile.LongestRecentRun
import com.example.openruncoach.domain.profile.PaceTrend
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.profile.TrainingConsistency
import com.example.openruncoach.domain.profile.ValueTrend
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingPlanPersistenceMapperTest {
  private val now = Instant.parse("2026-09-24T12:00:00Z")

  @Test fun planRoundTripPreservesWeekAndWorkoutOrdering() {
    val completionDate = LocalDate.parse("2026-10-18")
    val milestone = GoalCompletionMilestone(
      "goal", GoalCompletionReadiness.GOAL_COMPLETION_NOT_READY, completionDate, false,
      listOf("User selected the date."), now,
    )
    val plan = TrainingPlanGenerator(Clock.fixed(now, ZoneOffset.UTC), ZoneOffset.UTC)
      .generate(goal(), profile(), completionMilestone = milestone)
    val snapshot = TrainingPlanPersistenceMapper.toSnapshot(plan)
    val workouts = plan.weeks.flatMap { it.workouts }.associate { it.workout.id to it.workout }
    val roundTrip = TrainingPlanPersistenceMapper.toDomain(snapshot, workouts)

    assertEquals(plan.copy(completionMilestone = roundTrip.completionMilestone), roundTrip)
    assertEquals(completionDate, roundTrip.completionMilestone?.scheduledDate)
    assertEquals(plan.completionMilestone?.workoutId, roundTrip.completionMilestone?.workoutId)
    assertEquals(plan.weeks.map { it.weekIndex }, roundTrip.weeks.map { it.weekIndex })
    assertEquals(
      plan.weeks.flatMap { it.workouts }.map { it.workout.id },
      roundTrip.weeks.flatMap { it.workouts }.map { it.workout.id },
    )
  }

  private fun goal() = TrainingGoal(
    "goal", GoalDistance.TEN_K, null, GoalType.COMPLETE_DISTANCE, null, now, GoalStatus.ACTIVE,
    StartingPerformanceSnapshot(null, null, 10_000.0, 4_200, 4_800.0, 2_100, 3.0, null, null, ProfileConfidence.LOW, now),
    ProfileConfidence.LOW, GoalAssessment(GoalAssessmentLevel.LIMITED_DATA, listOf("Limited history")),
  )

  private fun profile() = AthletePerformanceProfile(
    6, 3, 6, 3, 10_000.0, 4_200, 24_000.0, 15_000, emptyList(), 10_000.0, 4_200,
    LongestRecentRun("long", now, 4_800.0, 2_100), 3.0, 3, TrainingConsistency.DEVELOPING, listOf("Fixture"),
    emptyList(), PaceTrend.STABLE, null, null, 145.0, ValueTrend.STABLE, null, null,
    FitnessTrend.INSUFFICIENT_DATA, null, ValueTrend.INSUFFICIENT_DATA,
    FeedbackAggregates(6, 4.0, 0.9, 0.0, 0.0, 0.0, 0.0, 4.0), now, ProfileConfidence.LOW,
    listOf("Fixture"), emptyList(),
  )
}
