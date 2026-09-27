package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.RunTimeSeries
import com.example.openruncoach.domain.metrics.TimedMetricSample
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutResponseEvaluatorTest {
  private val now = Instant.parse("2026-09-27T08:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val adherenceEvaluator = WorkoutAdherenceEvaluator(clock)
  private val responseEvaluator = WorkoutResponseEvaluator()

  @Test fun realEasyRunIsCompletedButMoreCostlyAndMeaningfullyDifferent() {
    val workout = distanceWorkout(4_000.0, WorkoutPurpose.AEROBIC_BASE, 3, 4, PaceTarget(465.0, 545.0))
    val run = run(3_780.0, 1_571, 415.0)
    val feedback = feedback(rpe = 6, pain = PainLevel.MILD)
    val execution = adherenceEvaluator.evaluate(workout, null, run, feedback)

    val response = responseEvaluator.evaluate(workout, null, execution, run, feedback)

    assertEquals(WorkoutCompletionOutcome.COMPLETED, response.completion)
    assertEquals(VolumeCompletion.SUBSTANTIALLY_COMPLETED, response.volumeCompletion)
    assertEquals(PrescriptionAdherence.MEANINGFUL_DEVIATION, response.adherence)
    assertEquals(EffortResponse.ABOVE_TARGET, response.effortResponse)
    assertEquals(PaceResponse.FASTER_THAN_TARGET, response.paceResponse)
    assertEquals(SessionCost.ELEVATED, response.sessionCost)
    assertEquals(PainLevel.MILD, response.pain)
    assertEquals(3_780.0, response.actualDistanceMeters!!, 0.01)
  }

  @Test fun missingHeartRateDoesNotPreventUsefulEasyRunInterpretation() {
    val workout = distanceWorkout(4_000.0, WorkoutPurpose.AEROBIC_BASE, 3, 4, PaceTarget(465.0, 545.0))
    val run = run(4_000.0, 1_920, 480.0).copy(averageHeartRate = null, maxHeartRate = null)
    val feedback = feedback(rpe = 3)
    val execution = adherenceEvaluator.evaluate(workout, null, run, feedback)

    val response = responseEvaluator.evaluate(workout, null, execution, run, feedback)

    assertEquals(WorkoutCompletionOutcome.COMPLETED, response.completion)
    assertEquals(PrescriptionAdherence.AS_PLANNED, response.adherence)
    assertEquals(SessionCost.NORMAL, response.sessionCost)
    assertTrue(response.confidence != WorkoutResponseConfidence.LOW)
  }

  @Test fun repeatedEvaluationOfSameCanonicalInputsIsDeterministic() {
    val workout = distanceWorkout(4_000.0, WorkoutPurpose.AEROBIC_BASE, 3, 4, PaceTarget(465.0, 545.0))
    val run = run(3_780.0, 1_571, 415.0)
    val feedback = feedback(rpe = 6, pain = PainLevel.MILD)
    val execution = adherenceEvaluator.evaluate(workout, null, run, feedback)

    assertEquals(
      responseEvaluator.evaluate(workout, null, execution, run, feedback),
      responseEvaluator.evaluate(workout, null, execution, run, feedback),
    )
  }

  @Test fun hardContinuousFiveKIsSuccessfulHighCostPerformanceNotFailure() {
    val workout = distanceWorkout(5_000.0, WorkoutPurpose.GOAL_COMPLETION, 4, 5, null)
    val run = run(5_000.0, 1_800, 360.0)
    val feedback = feedback(rpe = 9)
    val execution = adherenceEvaluator.evaluate(workout, null, run, feedback)
    val metrics = metrics(run, List(31) { index -> speedSample(index * 60L, 2.78) })

    val response = responseEvaluator.evaluate(workout, null, execution, run, feedback, metrics)

    assertEquals(WorkoutCompletionOutcome.COMPLETED, response.completion)
    assertEquals(RunContinuityEvidence.CONTINUOUS, response.continuity)
    assertEquals(SessionCost.HIGH, response.sessionCost)
    assertTrue(response.reasons.none { it.contains("impossible", ignoreCase = true) })
  }

  @Test fun hardFiveKWithWalkingFadeAndPainIsMateriallyMoreConservative() {
    val workout = distanceWorkout(5_000.0, WorkoutPurpose.GOAL_COMPLETION, 4, 5, null)
    val run = run(5_000.0, 1_800, 360.0)
    val feedback = feedback(rpe = 9, pain = PainLevel.MILD)
    val execution = adherenceEvaluator.evaluate(workout, null, run, feedback)
    val samples = List(181) { index -> speedSample(index * 10L, if (index < 90) 3.2 else 1.0) }
    val metrics = metrics(run, samples)

    val response = responseEvaluator.evaluate(workout, null, execution, run, feedback, metrics)

    assertEquals(WorkoutCompletionOutcome.COMPLETED, response.completion)
    assertEquals(RunContinuityEvidence.INTERRUPTED, response.continuity)
    assertEquals(PaceStability.STRONG_FADE, response.paceStability)
    assertEquals(SessionCost.HIGH, response.sessionCost)
    assertTrue(response.reasons.any { it.contains("walking or stopped") })
    assertTrue(response.reasons.any { it.contains("pain") })
  }

  @Test fun qualityWorkoutWithoutRepEvidenceDoesNotClaimFullAdherenceFromTotalVolume() {
    val workout = StructuredWorkoutSamples.intervals()
    val estimate = WorkoutEstimateCalculator.calculate(workout)
    val distance = estimate.estimatedTotalDistanceMeters ?: estimate.knownDistanceMeters
    val duration = estimate.estimatedTotalDurationSeconds ?: 2_400L
    val run = run(distance, duration, duration * 1000.0 / distance)
    val feedback = feedback(rpe = 7)
    val execution = adherenceEvaluator.evaluate(workout, null, run, feedback)

    val response = responseEvaluator.evaluate(workout, null, execution, run, feedback)

    assertEquals(PrescriptionAdherence.INSUFFICIENT_EVIDENCE, response.adherence)
    assertEquals(7, response.plannedRpeMinimum)
    assertEquals(8, response.plannedRpeMaximum)
    assertTrue(response.reasons.any { it.contains("quality work") })
  }

  @Test fun longRunKeepsItsStricterEnduranceVolumeTolerance() {
    val workout = distanceWorkout(10_000.0, WorkoutPurpose.ENDURANCE, 3, 5, null)
    val run = run(9_000.0, 5_400, 600.0)
    val feedback = feedback(rpe = 4)
    val execution = adherenceEvaluator.evaluate(workout, null, run, feedback)

    val response = responseEvaluator.evaluate(workout, null, execution, run, feedback)

    assertEquals(WorkoutCompletionOutcome.PARTIAL, response.completion)
    assertEquals(VolumeCompletion.PARTIAL, response.volumeCompletion)
  }

  private fun distanceWorkout(
    distanceMeters: Double,
    purpose: WorkoutPurpose,
    rpeMinimum: Int,
    rpeMaximum: Int,
    pace: PaceTarget?,
  ) = PlannedWorkout(
    id = "planned",
    date = LocalDate.of(2026, 9, 27),
    type = when (purpose) {
      WorkoutPurpose.GOAL_COMPLETION -> WorkoutType.RACE_SPECIFIC
      WorkoutPurpose.ENDURANCE -> WorkoutType.LONG_RUN
      else -> WorkoutType.EASY_RUN
    },
    name = when (purpose) {
      WorkoutPurpose.GOAL_COMPLETION -> "5K Completion Run"
      WorkoutPurpose.ENDURANCE -> "Long Run"
      else -> "Easy Run"
    },
    purpose = purpose,
    blocks = listOf(SingleSegmentBlock(
      id = "block",
      order = 0,
      segment = WorkoutSegment(
        id = "segment",
        order = 0,
        category = SegmentCategory.EASY,
        name = "Run",
        completion = DistanceCompletion(distanceMeters),
        primaryIntensity = RpeTarget(rpeMinimum, rpeMaximum),
        secondaryIntensities = listOfNotNull(pace),
      ),
    )),
    explanation = "Test prescription.",
    cues = emptyList(),
    createdAt = now.minusSeconds(3_600),
  )

  private fun run(distance: Double, duration: Long, pace: Double) = RunSummary(
    healthConnectRecordId = "run",
    startTime = now,
    endTime = now.plusSeconds(duration),
    durationSeconds = duration,
    distanceMeters = distance,
    averageHeartRate = 147.0,
    maxHeartRate = 170,
    averagePaceSecondsPerKm = pace,
  )

  private fun feedback(rpe: Int, pain: PainLevel = PainLevel.NONE) = RunFeedback(
    id = "feedback",
    runId = "run",
    rpe = rpe,
    cardioEffort = CardioEffort.APPROPRIATE,
    muscleFatigue = MuscleFatigue.NONE,
    painLevel = pain,
    completionStatus = CompletionStatus.COMPLETED,
    readiness = 4,
    notes = null,
    createdAt = now,
    updatedAt = now,
  )

  private fun metrics(run: RunSummary, speed: List<TimedMetricSample<Double>>) = RunMetrics(
    runId = run.healthConnectRecordId,
    startTime = run.startTime,
    endTime = run.endTime,
    durationSeconds = MetricValue(run.durationSeconds, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    distanceMeters = run.distanceMeters?.let { MetricValue(it, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH) },
    averagePaceSecondsPerKm = run.averagePaceSecondsPerKm?.let {
      MetricValue(it, MetricSource.DERIVED, MetricConfidence.MEDIUM)
    },
    series = RunTimeSeries(speed = speed),
    generatedAt = now,
  )

  private fun speedSample(seconds: Long, value: Double) = TimedMetricSample(
    timestamp = now.plusSeconds(seconds),
    value = value,
    source = MetricSource.HEALTH_CONNECT,
    confidence = MetricConfidence.HIGH,
  )
}
