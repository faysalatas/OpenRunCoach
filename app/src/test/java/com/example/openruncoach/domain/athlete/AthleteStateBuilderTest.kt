package com.example.openruncoach.domain.athlete

import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.workout.EffortResponse
import com.example.openruncoach.domain.workout.PaceResponse
import com.example.openruncoach.domain.workout.PaceStability
import com.example.openruncoach.domain.workout.PrescriptionAdherence
import com.example.openruncoach.domain.workout.SessionCost
import com.example.openruncoach.domain.workout.VolumeCompletion
import com.example.openruncoach.domain.workout.WorkoutCompletionOutcome
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutResponse
import com.example.openruncoach.domain.workout.WorkoutResponseConfidence
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AthleteStateBuilderTest {
  private val now = Instant.parse("2026-09-27T12:00:00Z")
  private val calculator = AthletePerformanceProfileCalculator(Clock.fixed(now, ZoneOffset.UTC), ZoneOffset.UTC)
  private val builder = AthleteStateBuilder()

  @Test fun hardFirstFiveKDemonstratesPerformanceWithoutClaimingComfortableTolerance() {
    val run = run("hard-5k", 5_000.0, 0)
    val state = state(listOf(run), listOf(response(run, WorkoutPurpose.GOAL_COMPLETION, 9, SessionCost.HIGH)))

    assertEquals(5_000.0, state.longestCompletedDistanceMeters!!, 0.01)
    assertEquals(5_000.0, state.longestDemonstratedContinuousDistanceMeters!!, 0.01)
    assertEquals(1, state.recentContinuity.continuousRuns)
    assertEquals(0, state.recentContinuity.interruptedRuns)
    assertEquals(0, state.recentContinuity.unknownRuns)
    assertNull(state.longestToleratedDistanceMeters)
    assertEquals(ProfileConfidence.LOW, state.capabilityConfidence)
    assertEquals(CurrentReadiness.CONSERVATIVE, state.currentReadiness)
  }

  @Test fun hardFiveKDoesNotRegressEstablishedEightKEasyTolerance() {
    val easyRuns = listOf(run("easy-6", 6_000.0, 21), run("easy-7", 7_000.0, 14), run("easy-8", 8_000.0, 7))
    val hard = run("hard-5", 5_000.0, 0)
    val responses = easyRuns.map { response(it, WorkoutPurpose.AEROBIC_BASE, 3, SessionCost.NORMAL) } +
      response(hard, WorkoutPurpose.GOAL_COMPLETION, 9, SessionCost.HIGH)

    val state = state(easyRuns + hard, responses)

    assertEquals(8_000.0, state.longestToleratedDistanceMeters!!, 0.01)
    assertEquals(8_000.0, state.longestCompletedDistanceMeters!!, 0.01)
    assertEquals(8_000.0, state.longestDemonstratedContinuousDistanceMeters!!, 0.01)
    assertEquals(4, state.recentContinuity.continuousRuns)
    assertEquals(0, state.recentContinuity.unknownRuns)
    assertEquals(CurrentReadiness.CONSERVATIVE, state.currentReadiness)
  }

  @Test fun onePainfulBadRunChangesReadinessButRetainsCapability() {
    val good = listOf(run("good-6", 6_000.0, 20), run("good-8", 8_000.0, 8))
    val bad = run("bad-4", 4_000.0, 0)
    val responses = good.map { response(it, WorkoutPurpose.AEROBIC_BASE, 3, SessionCost.NORMAL) } +
      response(bad, WorkoutPurpose.AEROBIC_BASE, 8, SessionCost.HIGH, PainLevel.MILD)

    val state = state(good + bad, responses)

    assertEquals(8_000.0, state.longestToleratedDistanceMeters!!, 0.01)
    assertEquals(CurrentReadiness.CONSERVATIVE, state.currentReadiness)
    assertTrue(state.readinessReasons.any { it.contains("pain", ignoreCase = true) })
  }

  @Test fun repeatedPoorResponsesHaveStrongerReadinessEffectThanOneIsolatedRun() {
    val runs = listOf(run("poor-1", 4_000.0, 5), run("poor-2", 4_000.0, 2))
    val responses = runs.map {
      response(it, WorkoutPurpose.AEROBIC_BASE, 8, SessionCost.HIGH, PainLevel.MILD)
    }

    val state = state(runs, responses)

    assertEquals(CurrentReadiness.RECOVERY_PRIORITY, state.currentReadiness)
    assertTrue(state.readinessReasons.any { it.contains("multiple", ignoreCase = true) })
  }

  @Test fun repeatedPositiveEasyEvidenceStrengthensConfidence() {
    val runs = (1..5).map { index -> run("easy-$index", 4_000.0 + index * 400.0, (5 - index) * 6L) }
    val repeated = state(runs, runs.map { response(it, WorkoutPurpose.AEROBIC_BASE, 3, SessionCost.NORMAL) })
    val single = state(listOf(runs.last()), listOf(response(runs.last(), WorkoutPurpose.AEROBIC_BASE, 3, SessionCost.NORMAL)))

    assertEquals(ProfileConfidence.HIGH, repeated.capabilityConfidence)
    assertEquals(ProfileConfidence.LOW, single.capabilityConfidence)
    assertEquals(5, repeated.toleratedSessionCount)
  }

  @Test fun noEvidenceRemainsUnknownWithoutInventedCapabilities() {
    val state = state(emptyList(), emptyList())

    assertEquals(ProfileConfidence.VERY_LOW, state.capabilityConfidence)
    assertEquals(CurrentReadiness.UNKNOWN, state.currentReadiness)
    assertNull(state.longestCompletedDistanceMeters)
    assertNull(state.longestDemonstratedContinuousDistanceMeters)
    assertNull(state.longestToleratedDistanceMeters)
  }

  @Test fun ignoredRunDoesNotBecomeAthleteStateEvidence() {
    val ignored = run("ignored", 10_000.0, 0).copy(relevance = RunRelevance.IGNORED)

    val state = state(listOf(ignored), listOf(response(ignored, WorkoutPurpose.ENDURANCE, 3, SessionCost.NORMAL)))

    assertNull(state.longestCompletedDistanceMeters)
    assertEquals(0.0, state.recentActualVolume7DaysMeters, 0.01)
  }

  @Test fun interruptedResponseDoesNotBecomeContinuousDistance() {
    val run = run("walking-5", 5_000.0, 0)
    val state = state(listOf(run), listOf(response(
      run,
      WorkoutPurpose.GOAL_COMPLETION,
      9,
      SessionCost.HIGH,
      continuity = RunContinuityEvidence.INTERRUPTED,
    )))

    assertNull(state.longestDemonstratedContinuousDistanceMeters)
    assertEquals(0, state.recentContinuity.continuousRuns)
    assertEquals(1, state.recentContinuity.interruptedRuns)
  }

  @Test fun structuredUnknownResponseIsCountedAsUnknown() {
    val run = run("unknown-5", 5_000.0, 0)
    val state = state(listOf(run), listOf(response(
      run,
      WorkoutPurpose.GOAL_COMPLETION,
      9,
      SessionCost.HIGH,
      continuity = RunContinuityEvidence.UNKNOWN,
    )))

    assertNull(state.longestDemonstratedContinuousDistanceMeters)
    assertEquals(0, state.recentContinuity.continuousRuns)
    assertEquals(0, state.recentContinuity.interruptedRuns)
    assertEquals(1, state.recentContinuity.unknownRuns)
  }

  @Test fun unassessedRunRetainsProfileUnknownContinuity() {
    val run = run("unassessed", 1_200.0, 0)
    val state = state(listOf(run), emptyList())

    assertNull(state.longestDemonstratedContinuousDistanceMeters)
    assertEquals(1, state.recentContinuity.unknownRuns)
  }

  @Test fun rebuildIsIdempotentAndDuplicateEvidenceDoesNotAccumulate() {
    val run = run("stable", 6_000.0, 2)
    val response = response(run, WorkoutPurpose.AEROBIC_BASE, 3, SessionCost.NORMAL)
    val profile = calculator.calculate(listOf(run), emptyList(), emptyList())

    assertEquals(
      builder.build(profile, listOf(run), listOf(response)),
      builder.build(profile, listOf(run, run), listOf(response, response)),
    )
  }

  @Test fun recentVolumeUsesActualRunDistanceNotPlannedDistance() {
    val run = run("actual", 3_780.0, 0)
    val response = response(run, WorkoutPurpose.AEROBIC_BASE, 3, SessionCost.NORMAL, plannedDistance = 4_000.0)

    val state = state(listOf(run), listOf(response))

    assertEquals(3_780.0, state.recentActualVolume7DaysMeters, 0.01)
  }

  @Test fun olderValidToleranceSurvivesOutsideAcuteReadinessWindow() {
    val older = run("older-8", 8_000.0, 35)
    val state = state(listOf(older), listOf(response(older, WorkoutPurpose.ENDURANCE, 4, SessionCost.NORMAL)))

    assertEquals(8_000.0, state.longestToleratedDistanceMeters!!, 0.01)
    assertEquals(CurrentReadiness.UNKNOWN, state.currentReadiness)
  }

  private fun state(runs: List<RunSummary>, responses: List<WorkoutResponse>): AthleteState = builder.build(
    calculator.calculate(runs, emptyList(), emptyList()),
    runs,
    responses,
  )

  private fun run(id: String, distance: Double, daysAgo: Long): RunSummary {
    val start = now.minusSeconds(daysAgo * 86_400).minusSeconds(2_000)
    return RunSummary(id, start, start.plusSeconds(2_000), 2_000, distance, null, null, 400.0)
  }

  private fun response(
    run: RunSummary,
    purpose: WorkoutPurpose,
    rpe: Int,
    cost: SessionCost,
    pain: PainLevel = PainLevel.NONE,
    plannedDistance: Double = requireNotNull(run.distanceMeters),
    continuity: RunContinuityEvidence = RunContinuityEvidence.CONTINUOUS,
  ) = WorkoutResponse(
    runId = run.healthConnectRecordId,
    plannedWorkoutId = "workout:${run.healthConnectRecordId}",
    purpose = purpose,
    completion = WorkoutCompletionOutcome.COMPLETED,
    adherence = PrescriptionAdherence.AS_PLANNED,
    volumeCompletion = VolumeCompletion.MET,
    effortResponse = if (rpe <= 5) EffortResponse.ON_TARGET else EffortResponse.ABOVE_TARGET,
    paceResponse = PaceResponse.UNKNOWN,
    continuity = continuity,
    paceStability = PaceStability.STABLE,
    sessionCost = cost,
    pain = pain,
    confidence = WorkoutResponseConfidence.HIGH,
    actualDistanceMeters = run.distanceMeters,
    actualDurationSeconds = run.durationSeconds,
    actualPaceSecondsPerKm = run.averagePaceSecondsPerKm,
    plannedDistanceMeters = plannedDistance,
    plannedDurationSeconds = null,
    plannedPaceMinimumSecondsPerKm = null,
    plannedPaceMaximumSecondsPerKm = null,
    plannedRpeMinimum = 3,
    plannedRpeMaximum = if (purpose == WorkoutPurpose.GOAL_COMPLETION) 9 else 4,
    reportedRpe = rpe,
    qualityWorkCompletionFraction = null,
    reasons = listOf("Deterministic fixture."),
  )
}
