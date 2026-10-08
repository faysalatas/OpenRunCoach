package com.example.openruncoach.domain.evidence

import com.example.openruncoach.domain.*
import com.example.openruncoach.domain.athlete.AthleteStateBuilder
import com.example.openruncoach.domain.athlete.CurrentReadiness
import com.example.openruncoach.domain.metrics.*
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import com.example.openruncoach.domain.workout.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class TrainingEvidencePolicyTest {
  private val now = Instant.parse("2026-10-08T12:00:00Z")
  private val calculator = AthletePerformanceProfileCalculator(Clock.fixed(now, ZoneOffset.UTC), ZoneOffset.UTC)

  @Test fun realStandaloneFragmentsRemainActiveButDoNotSupplyLongitudinalEvidence() {
    listOf(5.861 to 54L, 63.7 to 48L, 387.378 to 124L).forEach { (distance, duration) ->
      val run = run("fragment", distance, duration)
      val decisions = TrainingEvidencePolicy.evaluate(run, metrics(run), feedback(run))
      listOf(TrainingEvidencePurpose.SESSION_FREQUENCY, TrainingEvidencePurpose.EASY_PACE,
        TrainingEvidencePurpose.EASY_HEART_RATE, TrainingEvidencePurpose.EASY_CADENCE,
        TrainingEvidencePurpose.CAPABILITY).forEach { purpose -> assertFalse("$purpose", decisions.getValue(purpose).eligible) }
      assertTrue(decisions.getValue(TrainingEvidencePurpose.ACTUAL_VOLUME).eligible)
      assertTrue(decisions.getValue(TrainingEvidencePurpose.SAFETY_RECOVERY).eligible)
      assertEquals(RunRelevance.ACTIVE, run.relevance)
    }
  }

  @Test fun shortAuthoritativePrescriptionSupportsTrainingEvidence() {
    val run = run("planned", 200.0, 90)
    val workout = StructuredWorkoutSamples.easyRun().copy(blocks = listOf(SingleSegmentBlock("short-block", 0,
      WorkoutSegment("short", 0, SegmentCategory.EASY, "Short easy run", DurationCompletion(90), RpeTarget(3, 4)),
    )))
    val decisions = TrainingEvidencePolicy.evaluate(run, metrics(run), feedback(run), associatedWorkout = workout)
    assertTrue(decisions.getValue(TrainingEvidencePurpose.SESSION_FREQUENCY).eligible)
    assertTrue(decisions.getValue(TrainingEvidencePurpose.EASY_PACE).eligible)
    assertTrue(decisions.getValue(TrainingEvidencePurpose.CAPABILITY).eligible)
    val profile = calculator.calculate(listOf(run), listOf(metrics(run)), listOf(feedback(run)), associatedWorkouts = mapOf(run.physicalRunId to workout))
    assertEquals(1, profile.runsLast28Days)
    assertEquals(1, profile.typicalEasyPace?.contributingRuns)
  }

  @Test fun abortedPainfulLongRunRetainsActualVolumeAndRecoveryEvidence() {
    val run = run("aborted", 1160.2, 600)
    val feedback = feedback(run).copy(completionStatus = CompletionStatus.ABORTED, painLevel = PainLevel.SIGNIFICANT, rpe = 9)
    val workout = StructuredWorkoutSamples.longRun()
    val execution = WorkoutAdherenceEvaluator(Clock.fixed(now, ZoneOffset.UTC)).evaluate(workout, null, run, feedback)
    val decisions = TrainingEvidencePolicy.evaluate(run, metrics(run), feedback, execution, workout)
    assertTrue(decisions.getValue(TrainingEvidencePurpose.ACTUAL_VOLUME).eligible)
    assertTrue(decisions.getValue(TrainingEvidencePurpose.SAFETY_RECOVERY).eligible)
    assertFalse(decisions.getValue(TrainingEvidencePurpose.EASY_PACE).eligible)
    assertFalse(decisions.getValue(TrainingEvidencePurpose.CAPABILITY).eligible)
    val profile = calculator.calculate(listOf(run), listOf(metrics(run)), listOf(feedback), listOf(execution))
    assertEquals(1160.2, profile.distanceLast7DaysMeters, 0.001)
    assertEquals(1.0, profile.feedback.significantPainFrequency!!, 0.0)
    val response = WorkoutResponseEvaluator().evaluate(workout, null, execution, run, feedback, metrics(run))
    val state = AthleteStateBuilder().build(profile, listOf(run), listOf(response))
    assertEquals(CurrentReadiness.RECOVERY_PRIORITY, state.currentReadiness)
    assertEquals(1160.2, state.recentActualVolume7DaysMeters, 0.001)
    assertEquals(WorkoutExecutionStatus.ABORTED, execution.status)
  }

  @Test fun ignoredRunIsExcludedForEveryPurpose() {
    val run = run("ignored", 3000.0, 1500).copy(relevance = RunRelevance.IGNORED)
    assertTrue(TrainingEvidencePolicy.evaluate(run, metrics(run), feedback(run)).values.none { it.eligible })
  }

  @Test fun realTinyRunCannotSetAnyEasyBaselineOrMeaningfulFrequency() {
    val fragment = run("cab0974e-5b8d-4415-bf62-fbf30f810db6", 5.861, 54)
    val normal = run("normal", 3000.0, 1500)
    val profile = calculator.calculate(listOf(fragment, normal), listOf(metrics(fragment), metrics(normal)), listOf(feedback(fragment), feedback(normal)))
    assertEquals(2, profile.totalCanonicalRuns)
    assertEquals(1, profile.runsLast28Days)
    assertEquals(0.25, profile.averageRunsPerWeek, 0.0)
    assertEquals(3005.861, profile.distanceLast28DaysMeters, 0.001)
    assertEquals(2, profile.feedback.feedbackCount)
    assertEquals(500.0, profile.typicalEasyPace!!.observedMaximum, 0.001)
    assertEquals(145.0, profile.typicalEasyHeartRate!!.observedMinimum, 0.001)
    assertEquals(165.0, profile.typicalEasyCadence!!.observedMinimum, 0.001)
    assertEquals(1, profile.typicalEasyPace.contributingRuns)
  }

  @Test fun standaloneBoundaryIsDeterministicAndExplained() {
    val before = run("before", 600.0, 299)
    val at = run("at", 600.0, 300)
    assertFalse(TrainingEvidencePolicy.evaluate(before).getValue(TrainingEvidencePurpose.SESSION_FREQUENCY).eligible)
    assertTrue(TrainingEvidencePolicy.evaluate(at).getValue(TrainingEvidencePurpose.SESSION_FREQUENCY).eligible)
    assertTrue(TrainingEvidencePolicy.evaluate(before).values.all { it.reason.isNotBlank() })
  }

  private fun run(id: String, distance: Double, duration: Long) = RunSummary(id, now.minusSeconds(3600),
    now.minusSeconds(3600).plusSeconds(duration), duration, distance, if (distance < 10) 99.91 else 145.0,
    160L, duration * 1000.0 / distance)

  private fun feedback(run: RunSummary) = RunFeedback("feedback-${run.physicalRunId}", run.physicalRunId, 3,
    CardioEffort.EASY, MuscleFatigue.NONE, PainLevel.NONE, CompletionStatus.COMPLETED, null, null, now, now)

  private fun metrics(run: RunSummary) = RunMetrics(run.healthConnectRecordId, run.startTime, run.endTime,
    MetricValue(run.durationSeconds, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    distanceMeters = MetricValue(run.distanceMeters!!, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    averageHeartRate = MetricValue(run.averageHeartRate!!, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    averageCadence = MetricValue(if (requireNotNull(run.distanceMeters) < 10) 106.07 else 165.0, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH),
    generatedAt = now)
}
