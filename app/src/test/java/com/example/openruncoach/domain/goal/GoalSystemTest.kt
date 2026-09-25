package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import com.example.openruncoach.domain.profile.ProfileConfidence
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalSystemTest {
  private val now = Instant.parse("2026-09-24T12:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val evaluator = GoalCapabilityEvaluator()
  private val assessment = GoalAssessmentEvaluator()
  private val factory = TrainingGoalFactory(clock, { "goal-id" })

  @Test fun noPerformanceProfileReportsLimitedCapabilities() {
    val capabilities = evaluator.evaluate(null)
    assertFalse(capabilities.profileAvailable)
    assertEquals(ProfileConfidence.VERY_LOW, capabilities.confidence)
    assertFalse(capabilities.byDistance.getValue(GoalDistance.FIVE_K).hasVerifiedBenchmark)
  }

  @Test fun noPerformanceProfileProducesLimitedDataAssessment() {
    val result = assessment.evaluate(
      TrainingGoalDraft(GoalDistance.FIVE_K, goalType = GoalType.COMPLETE_DISTANCE),
      null,
    )
    assertEquals(GoalAssessmentLevel.LIMITED_DATA, result.level)
    assertTrue(result.reasons.isNotEmpty())
  }

  @Test fun noFiveKBenchmarkExplainsLimitedImprovementData() {
    val capability = evaluator.evaluate(profile(run("short", 4_000.0))).byDistance.getValue(GoalDistance.FIVE_K)
    assertFalse(capability.hasVerifiedBenchmark)
    assertFalse(capability.options.first { it.goalType == GoalType.IMPROVE_PERFORMANCE }.supportedByCurrentData)
  }

  @Test fun validFiveKBenchmarkIsExposed() {
    val capability = evaluator.evaluate(profile(run("five", 5_000.0, 1_800))).byDistance.getValue(GoalDistance.FIVE_K)
    assertTrue(capability.hasVerifiedBenchmark)
    assertEquals(1_800L, capability.benchmark?.personalBest?.durationSeconds)
  }

  @Test fun alreadyDemonstratedDistanceOffersMoreRelevantGoalTypesWithoutChangingChoice() {
    val capability = evaluator.evaluate(profile(run("five", 5_000.0, 1_800))).byDistance.getValue(GoalDistance.FIVE_K)
    val complete = capability.options.single { it.goalType == GoalType.COMPLETE_DISTANCE }
    assertTrue(capability.hasCompletedDistance)
    assertTrue(complete.explanation.contains("already demonstrated"))
    assertTrue(complete.explanation.contains("Improve Performance"))
    assertTrue(complete.explanation.contains("Target Time"))
  }

  @Test fun fourPointEightKmDoesNotCountAsFiveKBenchmark() {
    val capability = evaluator.evaluate(profile(run("almost", 4_800.0))).byDistance.getValue(GoalDistance.FIVE_K)
    assertFalse(capability.hasVerifiedBenchmark)
    assertFalse(capability.hasCompletedDistance)
    assertEquals(4_800.0, capability.longestRecentRunDistanceMeters!!, 0.01)
  }

  @Test fun createsCompleteDistanceGoal() {
    val goal = factory.create(TrainingGoalDraft(GoalDistance.FIVE_K, goalType = GoalType.COMPLETE_DISTANCE), profile(run("five", 5_000.0)))
    assertEquals(GoalType.COMPLETE_DISTANCE, goal.goalType)
    assertNull(goal.targetTimeSeconds)
  }

  @Test fun createsImprovePerformanceGoal() {
    val goal = factory.create(TrainingGoalDraft(GoalDistance.FIVE_K, goalType = GoalType.IMPROVE_PERFORMANCE), profile(run("five", 5_000.0)))
    assertEquals(GoalType.IMPROVE_PERFORMANCE, goal.goalType)
    assertTrue(goal.startingPerformanceSnapshot.bestBenchmark != null)
  }

  @Test fun createsTargetTimeGoal() {
    val goal = factory.create(TrainingGoalDraft(GoalDistance.FIVE_K, goalType = GoalType.TARGET_TIME, targetTimeSeconds = 1_800), profile(run("five", 5_000.0, 1_920)))
    assertEquals(1_800L, goal.targetTimeSeconds)
  }

  @Test fun targetPaceCalculationUsesSelectedDistance() {
    assertEquals(360.0, targetPaceSecondsPerKm(1_800, 5_000.0), 0.001)
    assertEquals(360.0, targetPaceSecondsPerKm(3_600, 10_000.0), 0.001)
  }

  @Test fun improvementPercentageComparesWithCurrentBenchmark() {
    val result = comparePerformance(1_920, 1_800)
    assertEquals(120L, result.differenceSeconds)
    assertEquals(0.0625, result.improvementFraction, 0.0001)
  }

  @Test fun tenKWithoutBenchmarkStillAllowsCompleteGoal() {
    val profile = profile(run("six", 6_000.0))
    val capability = evaluator.evaluate(profile).byDistance.getValue(GoalDistance.TEN_K)
    assertFalse(capability.hasVerifiedBenchmark)
    val goal = factory.create(TrainingGoalDraft(GoalDistance.TEN_K, goalType = GoalType.COMPLETE_DISTANCE), profile)
    assertEquals(10_000.0, goal.distanceMeters, 0.01)
  }

  @Test fun goalCanBeCreatedWithoutAnyCalendarDate() {
    val goal = factory.create(TrainingGoalDraft(GoalDistance.FIVE_K, goalType = GoalType.COMPLETE_DISTANCE), null)
    assertEquals(GoalStatus.ACTIVE, goal.status)
  }

  @Test fun customDistanceIsValidatedAndStored() {
    val goal = factory.create(TrainingGoalDraft(GoalDistance.CUSTOM, 15_000.0, GoalType.COMPLETE_DISTANCE), null)
    assertEquals(15_000.0, goal.distanceMeters, 0.01)
    assertFalse(validateGoalDraft(TrainingGoalDraft(GoalDistance.CUSTOM, 50.0, GoalType.COMPLETE_DISTANCE)).isValid)
  }

  @Test fun oneActiveGoalRuleArchivesExistingActiveGoal() {
    val old = factory.create(TrainingGoalDraft(GoalDistance.FIVE_K, goalType = GoalType.COMPLETE_DISTANCE), null).copy(id = "old")
    val alreadyArchived = old.copy(id = "archive", status = GoalStatus.ARCHIVED)
    val new = old.copy(id = "new", distance = GoalDistance.TEN_K)
    val result = activateGoal(listOf(old, alreadyArchived), new)
    assertEquals(1, result.count { it.status == GoalStatus.ACTIVE })
    assertEquals(GoalStatus.ARCHIVED, result.first { it.id == "old" }.status)
    assertEquals(GoalStatus.ARCHIVED, result.first { it.id == "archive" }.status)
  }

  @Test fun startingSnapshotRemainsStableWhenProfileChanges() {
    val startingProfile = profile(run("starting", 5_000.0, 1_920))
    val goal = factory.create(TrainingGoalDraft(GoalDistance.FIVE_K, goalType = GoalType.IMPROVE_PERFORMANCE), startingProfile)
    profile(run("later", 5_000.0, 1_700))
    assertEquals(1_920L, goal.startingPerformanceSnapshot.bestBenchmark?.durationSeconds)
    assertEquals("starting", goal.startingPerformanceSnapshot.bestBenchmark?.runId)
  }

  @Test fun wellSupportedAssessmentForCloseTarget() {
    val result = assessment.evaluate(targetDraft(1_728), profile(run("five", 5_000.0, 1_800)))
    assertEquals(GoalAssessmentLevel.WELL_SUPPORTED, result.level)
  }

  @Test fun plausibleAssessmentForMeaningfulImprovement() {
    val result = assessment.evaluate(targetDraft(1_620), profile(run("five", 5_000.0, 1_800)))
    assertEquals(GoalAssessmentLevel.PLAUSIBLE, result.level)
  }

  @Test fun limitedDataAssessmentWithoutBenchmark() {
    val result = assessment.evaluate(targetDraft(1_800), profile(run("almost", 4_800.0, 1_800)))
    assertEquals(GoalAssessmentLevel.LIMITED_DATA, result.level)
  }

  @Test fun largeStepAssessmentForLargeTargetChange() {
    val result = assessment.evaluate(targetDraft(1_400), profile(run("five", 5_000.0, 1_800)))
    assertEquals(GoalAssessmentLevel.LARGE_STEP, result.level)
  }

  @Test fun largeDistanceStepUsesObservedLongestRun() {
    val draft = TrainingGoalDraft(GoalDistance.MARATHON, goalType = GoalType.COMPLETE_DISTANCE)
    assertEquals(GoalAssessmentLevel.LARGE_STEP, assessment.evaluate(draft, profile(run("five", 5_000.0))).level)
  }

  @Test fun durationInputSupportsMinutesAndHours() {
    assertEquals(1_800L, parseGoalDuration("30:00"))
    assertEquals(5_400L, parseGoalDuration("1:30:00"))
    assertNull(parseGoalDuration("30:90"))
  }

  private fun targetDraft(seconds: Long) = TrainingGoalDraft(GoalDistance.FIVE_K, goalType = GoalType.TARGET_TIME, targetTimeSeconds = seconds)

  private fun profile(vararg runs: RunSummary): AthletePerformanceProfile =
    AthletePerformanceProfileCalculator(clock, ZoneOffset.UTC).calculate(runs.toList(), emptyList(), emptyList())

  private fun run(id: String, distance: Double, duration: Long = 2_000): RunSummary {
    val start = now.minusSeconds(86_400)
    return RunSummary(id, start, start.plusSeconds(duration), duration, distance, 145.0, 165, duration * 1_000.0 / distance)
  }
}
