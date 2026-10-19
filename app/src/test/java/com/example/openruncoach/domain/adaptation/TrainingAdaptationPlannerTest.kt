package com.example.openruncoach.domain.adaptation

import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.athlete.AthleteStateBuilder
import com.example.openruncoach.domain.athlete.CurrentReadiness
import com.example.openruncoach.domain.goal.GoalStrategy
import com.example.openruncoach.domain.goal.TrainingCapability
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.workout.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class TrainingAdaptationPlannerTest {
  private val today = LocalDate.of(2026, 9, 27)
  private val clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC)
  private val state = AthleteStateBuilder().build(
    AthletePerformanceProfileCalculator(clock).calculate(emptyList(), emptyList(), emptyList()), emptyList(), emptyList(),
  ).copy(capabilityConfidence = ProfileConfidence.HIGH, currentReadiness = CurrentReadiness.NORMAL)
  private val strategy = GoalStrategy(listOf(TrainingCapability.AEROBIC_BASE), emptyList(), emptyList(),
    ProfileConfidence.HIGH, listOf("Build aerobic tolerance."))
  private val easy = StructuredWorkoutSamples.easyRun().copy(date = today.plusDays(1))
  private val planner = TrainingAdaptationPlanner()

  @Test fun significantPainFromAnUnassignedRunPrioritizesRestAndKeepsOriginalIdentity() {
    val result = adapt(listOf(AdaptationObservation("physical", today, PainLevel.SIGNIFICANT, false, null)))
    assertEquals(TrainingAdaptationAction.REST, result.decision.action)
    assertEquals(easy, result.original)
    assertEquals(easy.id, result.effective.id)
    assertEquals(easy.date, result.effective.date)
    assertEquals(WorkoutType.REST, result.effective.type)
    assertTrue(result.effective.blocks.isEmpty())
    assertTrue(result.decision.reasons.isNotEmpty())
  }

  @Test fun fatigueReducesEasyWorkWithoutChangingIntensity() {
    val result = adapt(listOf(AdaptationObservation("physical", today, PainLevel.NONE, true, null)))
    assertEquals(TrainingAdaptationAction.REDUCE, result.decision.action)
    assertEquals(1_680, result.effective.durationMinutes * 60)
    assertEquals(easy.blocks.first().segments.first().primaryIntensity, result.effective.blocks.first().segments.first().primaryIntensity)
  }

  @Test fun repeatedPositiveEasyEvidenceProgressesOnlyOneWorkoutAndOneDimension() {
    val originals = listOf(easy, easy.copy(id = "next", date = today.plusDays(3)))
    val result = planner.adapt(originals, strategy, state, positives(), today)
    assertEquals(listOf(TrainingAdaptationAction.PROGRESS, TrainingAdaptationAction.KEEP), result.map { it.decision.action })
    assertEquals(2_205L, (result.first().effective.blocks.first().segments.first().completion as DurationCompletion).seconds)
    assertEquals(TrainingCapability.AEROBIC_BASE, result.first().decision.capability)
    assertEquals(originals.last(), result.last().effective)
    assertEquals(result, planner.adapt(originals, strategy, state, positives(), today))
  }

  @Test fun duplicateProviderResponsesCannotCreateRepeatedPositiveEvidence() {
    val observation = positives().first()
    val result = adapt(listOf(observation, observation, observation))
    assertEquals(TrainingAdaptationAction.KEEP, result.decision.action)
  }

  @Test fun lowConfidenceOrMissingCheckoutDoesNotAuthorizeProgressionOrQuality() {
    assertEquals(TrainingAdaptationAction.KEEP, adapt(emptyList()).decision.action)
    val unknown = positives() + AdaptationObservation("missing", today, null, false, null)
    assertEquals(TrainingAdaptationAction.KEEP, adapt(unknown).decision.action)
    val quality = StructuredWorkoutSamples.intervals().copy(date = today.plusDays(1))
    val result = planner.adapt(listOf(quality), strategy, state.copy(currentReadiness = CurrentReadiness.UNKNOWN), unknown, today).single()
    assertEquals(TrainingAdaptationAction.REST, result.decision.action)
    assertEquals(quality, result.original)
    val low = planner.adapt(listOf(easy), strategy, state.copy(capabilityConfidence = ProfileConfidence.LOW), positives(), today).single()
    assertEquals(TrainingAdaptationAction.KEEP, low.decision.action)
    assertTrue(low.decision.reasons.single().contains("incomplete"))
  }

  @Test fun aggregateQualityTotalsCannotAuthorizeQualityProgression() {
    val quality = StructuredWorkoutSamples.intervals().copy(date = today.plusDays(1))
    val speed = strategy.copy(primaryCapabilities = listOf(TrainingCapability.SPEED))
    val observations = positives(WorkoutPurpose.VO2MAX)
    val result = planner.adapt(listOf(quality), speed, state, observations, today).single()
    assertEquals(TrainingAdaptationAction.KEEP, result.decision.action)
  }

  @Test fun explicitRepeatedQualityEvidencePreservesWarmupRecoveryAndRepetitions() {
    val quality = StructuredWorkoutSamples.intervals().copy(date = today.plusDays(1))
    val speed = strategy.copy(primaryCapabilities = listOf(TrainingCapability.SPEED))
    val evidence = positives(WorkoutPurpose.VO2MAX).map { it.copy(response = it.response!!.copy(qualityWorkCompletionFraction = 1.0)) }
    val result = planner.adapt(listOf(quality), speed, state, evidence, today).single()
    assertEquals(TrainingAdaptationAction.PROGRESS, result.decision.action)
    val originalRepeat = quality.blocks[1] as RepeatBlock
    val effectiveRepeat = result.effective.blocks[1] as RepeatBlock
    assertEquals(originalRepeat.repetitions, effectiveRepeat.repetitions)
    assertEquals(originalRepeat.segments[1], effectiveRepeat.segments[1])
    assertEquals(840.0, (effectiveRepeat.segments[0].completion as DistanceCompletion).meters, 0.0)
    assertEquals(quality.blocks.first(), result.effective.blocks.first())
    assertEquals(quality.blocks.last(), result.effective.blocks.last())
    assertEquals(originalRepeat.segments[0].primaryIntensity, effectiveRepeat.segments[0].primaryIntensity)
  }

  @Test fun correctionRecomputesFromOriginalAndPainOutsideAcuteWindowDoesNotStayActive() {
    val pain = positives().map { it.copy(pain = PainLevel.SIGNIFICANT) }
    assertEquals(TrainingAdaptationAction.REST, adapt(pain).decision.action)
    assertEquals(TrainingAdaptationAction.PROGRESS, adapt(positives()).decision.action)
    val oldPain = AdaptationObservation("old", today.minusDays(7), PainLevel.SIGNIFICANT, true, null)
    assertEquals(TrainingAdaptationAction.PROGRESS, adapt(positives() + oldPain).decision.action)
  }

  @Test fun wrongGoalCapabilityOrIneligibleFragmentsDoNotProgress() {
    val speed = strategy.copy(primaryCapabilities = listOf(TrainingCapability.SPEED))
    assertEquals(TrainingAdaptationAction.KEEP, planner.adapt(listOf(easy), speed, state, positives(), today).single().decision.action)
    assertEquals(TrainingAdaptationAction.KEEP, adapt(positives().map { it.copy(capabilityEligible = false) }).decision.action)
  }

  @Test fun lockedCompletionsAndOpenDrillsRemainIntactWithAReason() {
    val locked = easy.copy(adjustability = easy.adjustability.copy(canChangeSegmentCompletion = false))
    val result = planner.adapt(listOf(locked), strategy, state, positives(), today).single()
    assertEquals(locked, result.effective)
    assertEquals(TrainingAdaptationAction.KEEP, result.decision.action)
    assertTrue(result.decision.reasons.single().contains("permissions"))
  }

  @Test fun plannedRestAndDistantForecastRemainUnchanged() {
    val rest = easy.copy(type = WorkoutType.REST, blocks = emptyList())
    val distant = easy.copy(id = "distant", date = today.plusDays(7))
    val result = planner.adapt(listOf(rest, distant), strategy, state,
      listOf(AdaptationObservation("pain", today, PainLevel.SIGNIFICANT, false, null)), today)
    assertTrue(result.all { it.decision.action == TrainingAdaptationAction.KEEP && it.effective == it.original })
  }

  @Test fun gentleAssessmentRemainsAvailableWhenEvidenceIsMissing() {
    val assessment = easy.copy(type = WorkoutType.ASSESSMENT, purpose = WorkoutPurpose.ASSESSMENT)
    val result = planner.adapt(listOf(assessment), strategy, state.copy(currentReadiness = CurrentReadiness.UNKNOWN),
      emptyList(), today).single()
    assertEquals(assessment, result.effective)
    assertEquals(TrainingAdaptationAction.KEEP, result.decision.action)
  }

  private fun adapt(observations: List<AdaptationObservation>) = planner.adapt(listOf(easy), strategy, state, observations, today).single()

  private fun positives(purpose: WorkoutPurpose = WorkoutPurpose.AEROBIC_BASE) = (1..3).map { index ->
    val id = "physical:$index"
    val response = WorkoutResponse(id, "workout:$index", purpose, WorkoutCompletionOutcome.COMPLETED,
      PrescriptionAdherence.AS_PLANNED, VolumeCompletion.MET, EffortResponse.ON_TARGET, PaceResponse.UNKNOWN,
      RunContinuityEvidence.CONTINUOUS, PaceStability.STABLE, SessionCost.NORMAL, PainLevel.NONE,
      WorkoutResponseConfidence.HIGH, 5_000.0, 2_100, 420.0, 5_000.0, 2_100,
      null, null, 3, 4, 3, null, listOf("Tolerated fixture."))
    AdaptationObservation(id, today.minusDays(index.toLong()), PainLevel.NONE, false, response)
  }
}
