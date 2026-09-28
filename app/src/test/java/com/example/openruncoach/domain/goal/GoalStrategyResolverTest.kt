package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.athlete.AthleteState
import com.example.openruncoach.domain.athlete.CurrentReadiness
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.profile.RecentContinuitySummary
import com.example.openruncoach.domain.workout.SessionCost
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class GoalStrategyResolverTest {
  private val now = Instant.parse("2026-09-27T12:00:00Z")
  private val resolver = GoalStrategyResolver()

  @Test fun firstFiveKPrioritizesContinuityAndDistance() {
    val strategy = resolve(goal(), state(2_000.0, count = 1))
    assertTrue(strategy.primaryCapabilities.containsAll(listOf(TrainingCapability.CONTINUOUS_RUNNING, TrainingCapability.DISTANCE_TOLERANCE)))
    assertFalse(TrainingCapability.SPEED in strategy.primaryCapabilities)
  }

  @Test fun nearlyCompleteFiveKConsolidatesWithoutCompletingTheGoal() {
    val goal = goal()
    val beginner = resolve(goal, state(2_000.0, count = 1))
    val nearly = resolve(goal, state(4_800.0))
    assertTrue(TrainingCapability.DISTANCE_TOLERANCE in nearly.primaryCapabilities)
    assertTrue(TrainingCapability.AEROBIC_BASE in nearly.primaryCapabilities)
    assertTrue(TrainingCapability.CONTINUOUS_RUNNING in nearly.secondaryCapabilities)
    assertNotEquals(beginner.primaryCapabilities, nearly.primaryCapabilities)
    assertEquals(GoalStatus.ACTIVE, goal.status)
  }

  @Test fun completedDistanceNeverSubstitutesForContinuousDistance() {
    listOf(null, 3_500.0).forEach { continuous ->
      val strategy = resolve(goal(), state(continuous).copy(longestCompletedDistanceMeters = 5_000.0))
      assertTrue(TrainingCapability.CONTINUOUS_RUNNING in strategy.primaryCapabilities)
      assertTrue(strategy.reasons.any { it.contains("separate") })
    }
  }

  @Test fun establishedPerformanceAthleteGetsPerformanceDirection() {
    val strategy = resolve(goal(GoalType.IMPROVE_PERFORMANCE), state(8_000.0))
    assertTrue(TrainingCapability.THRESHOLD in strategy.primaryCapabilities)
    assertTrue(TrainingCapability.RACE_SPECIFIC_ENDURANCE in strategy.primaryCapabilities)
    assertTrue(TrainingCapability.RUNNING_ECONOMY in strategy.secondaryCapabilities)
    assertTrue(TrainingCapability.AEROBIC_BASE in strategy.secondaryCapabilities)
    assertFalse(TrainingCapability.CONTINUOUS_RUNNING in strategy.primaryCapabilities)
  }

  @Test fun targetTimeIsContextAndDoesNotInferTrainingPaces() {
    val goal = goal(GoalType.TARGET_TIME)
    assertEquals(360.0, targetPaceSecondsPerKm(requireNotNull(goal.targetTimeSeconds), goal.distanceMeters), 0.0)
    // An ambitious target changes neither observed capabilities nor training pace evidence.
    assertEquals(resolve(goal, state(8_000.0)), resolve(goal.copy(targetTimeSeconds = 1_200), state(8_000.0)))
    assertTrue(resolve(goal, state(8_000.0)).reasons.any { it.contains("context only") })
  }

  @Test fun performanceGoalWithWeakDistanceEvidenceKeepsFoundations() {
    listOf(GoalType.IMPROVE_PERFORMANCE, GoalType.TARGET_TIME).forEach { type ->
      val strategy = resolve(goal(type), state(2_000.0, count = 1))
      assertTrue(TrainingCapability.DISTANCE_TOLERANCE in strategy.primaryCapabilities)
      assertTrue(TrainingCapability.CONTINUOUS_RUNNING in strategy.primaryCapabilities)
      assertFalse(TrainingCapability.SPEED in strategy.primaryCapabilities)
    }
  }

  @Test fun aSingleHardDistancePerformanceDoesNotProveRepeatedAerobicTolerance() {
    val strategy = resolve(goal(GoalType.TARGET_TIME), state(8_000.0, count = 1).copy(longestToleratedDistanceMeters = 3_000.0))
    assertTrue(TrainingCapability.DISTANCE_TOLERANCE in strategy.primaryCapabilities)
    assertEquals(ProfileConfidence.LOW, strategy.confidence)
  }

  @Test fun tenKCompletionDevelopsEnduranceFromFiveKBase() {
    val strategy = resolve(goal(distance = GoalDistance.TEN_K), state(5_500.0))
    assertEquals(setOf(TrainingCapability.DISTANCE_TOLERANCE, TrainingCapability.AEROBIC_BASE), strategy.primaryCapabilities.toSet())
    assertFalse(TrainingCapability.SPEED in strategy.primaryCapabilities)
  }

  @Test fun longerGoalsHaveDistinctVolumeAndDurabilityDemands() {
    val half = resolve(goal(distance = GoalDistance.HALF_MARATHON), state(8_000.0))
    val marathon = resolve(goal(distance = GoalDistance.MARATHON), state(8_000.0))
    assertTrue(TrainingCapability.WEEKLY_VOLUME in half.secondaryCapabilities)
    assertTrue(TrainingCapability.RECOVERY_TOLERANCE in half.secondaryCapabilities)
    assertTrue(TrainingCapability.WEEKLY_VOLUME in marathon.primaryCapabilities)
    assertTrue(TrainingCapability.RACE_SPECIFIC_ENDURANCE in marathon.secondaryCapabilities)
    assertNotEquals(half, marathon)
  }

  @Test fun longPerformanceGoalKeepsAerobicDurabilityCentral() {
    val strategy = resolve(goal(GoalType.TARGET_TIME, GoalDistance.MARATHON), state(43_000.0))
    assertTrue(TrainingCapability.AEROBIC_BASE in strategy.primaryCapabilities)
    assertTrue(TrainingCapability.RACE_SPECIFIC_ENDURANCE in strategy.primaryCapabilities)
    assertFalse(TrainingCapability.SPEED in strategy.primaryCapabilities)
  }

  @Test fun customDistanceUsesDemandsOfItsActualDistance() {
    val custom = goal(distance = GoalDistance.CUSTOM).copy(customDistanceMeters = 30_000.0)
    val strategy = resolve(custom, state(8_000.0))
    assertTrue(TrainingCapability.RECOVERY_TOLERANCE in strategy.secondaryCapabilities)
    assertTrue(strategy.reasons.any { it.contains("30.00 km") })
  }

  @Test fun missingEvidenceLowersConfidenceWithoutClaimingInability() {
    val strategy = resolve(goal(), state(null, count = 0))
    assertEquals(ProfileConfidence.LOW, strategy.confidence)
    assertTrue(strategy.reasons.any { it.contains("insufficiently observed") })
    assertTrue(strategy.reasons.any { it.contains("does not establish inability") })
    assertFalse(strategy.reasons.any { it.contains("cannot run") })
  }

  @Test fun highConfidenceRequiresMultipleRecentRelevantObservations() {
    assertEquals(ProfileConfidence.HIGH, resolve(goal(), state(8_000.0)).confidence)
    assertEquals(ProfileConfidence.LOW, resolve(goal(), state(8_000.0).copy(recentContinuity = RecentContinuitySummary())).confidence)
    assertEquals(ProfileConfidence.MEDIUM, resolve(goal(), state(8_000.0, count = 3)).confidence)
  }

  @Test fun highCostAndReadinessNeverRedefineGoalDirection() {
    val goal = goal(distance = GoalDistance.TEN_K)
    val baseline = state(6_000.0)
    val acute = baseline.copy(
      latestSessionCost = SessionCost.HIGH, currentReadiness = CurrentReadiness.RECOVERY_PRIORITY,
      recentHighCostCount = 3, recentElevatedCostCount = 4,
      readinessReasons = listOf("Significant pain requires recovery."),
    )
    assertEquals(resolve(goal, baseline), resolve(goal, acute))
    assertFalse(TrainingCapability.RECOVERY_TOLERANCE in resolve(goal, acute).primaryCapabilities)
  }

  @Test fun repeatedInputsAndDifferentCalculationDatesProduceTheSameStrategy() {
    val goal = goal()
    val state = state(4_800.0)
    assertEquals(resolve(goal, state), resolve(goal, state))
    assertEquals(resolve(goal, state), resolve(goal.copy(createdAt = now.plusSeconds(86_400)), state.copy(calculatedAt = now.plusSeconds(86_400))))
    // The model takes no plan, milestone date, deadline, or clock.
  }

  @Test fun resolverRespectsGoalLifecycleRatherThanQualifyingRunsItself() {
    GoalStatus.entries.filter { it != GoalStatus.ACTIVE }.forEach { status ->
      assertNull(resolver.resolve(goal().copy(status = status), state(8_000.0)))
    }
    assertNotNull(resolver.resolve(goal(), state(8_000.0)))
  }

  private fun resolve(goal: TrainingGoal, state: AthleteState) = requireNotNull(resolver.resolve(goal, state))

  private fun goal(type: GoalType = GoalType.COMPLETE_DISTANCE, distance: GoalDistance = GoalDistance.FIVE_K) = TrainingGoal(
    "goal", distance, if (distance == GoalDistance.CUSTOM) 7_500.0 else null, type,
    if (type == GoalType.TARGET_TIME) 1_800L else null, now, GoalStatus.ACTIVE,
    StartingPerformanceSnapshot(null, null, 0.0, 0, null, null, 0.0, null, null, ProfileConfidence.LOW, now),
    ProfileConfidence.LOW, GoalAssessment(GoalAssessmentLevel.LIMITED_DATA, listOf("Fixture")),
  )

  private fun state(continuous: Double?, count: Int = 5) = AthleteState(
    recentActualVolume7DaysMeters = 6_000.0,
    recentActualVolume28DaysMeters = 24_000.0,
    recentRunFrequencyPerWeek = 2.0,
    longestCompletedDistanceMeters = continuous,
    longestDemonstratedContinuousDistanceMeters = continuous,
    longestToleratedDistanceMeters = continuous,
    toleratedSessionCount = count,
    recentContinuity = RecentContinuitySummary(continuousRuns = if (continuous == null) 0 else count),
    recentResponseCount = count,
    recentElevatedCostCount = 0,
    recentHighCostCount = 0,
    latestSessionCost = SessionCost.NORMAL,
    capabilityConfidence = ProfileConfidence.HIGH,
    currentReadiness = CurrentReadiness.NORMAL,
    capabilityReasons = emptyList(), readinessReasons = emptyList(), calculatedAt = now,
  )
}
