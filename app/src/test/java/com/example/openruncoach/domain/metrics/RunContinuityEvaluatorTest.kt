package com.example.openruncoach.domain.metrics

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RunContinuityEvaluatorTest {
  private val startTime = Instant.parse("2026-09-27T08:00:00Z")
  private val evaluator = RunContinuityEvaluator()

  @Test fun denseRunningWithSpeedAloneIsContinuous() {
    assertEquals(RunContinuityEvidence.CONTINUOUS, evaluate(speed = samples(10, 2.5)))
  }

  @Test fun runningAtTheSupportedGapLimitIsContinuous() {
    assertEquals(RunContinuityEvidence.CONTINUOUS, evaluate(speed = samples(15, 2.5)))
  }

  @Test fun denseSustainedWalkingIsInterrupted() {
    assertEquals(RunContinuityEvidence.INTERRUPTED, evaluate(speed = samples(10, 1.0)))
  }

  @Test fun walkingEveryFifteenSecondsIsInterrupted() {
    assertEquals(RunContinuityEvidence.INTERRUPTED, evaluate(speed = samples(15, 1.0)))
  }

  @Test fun tenMinuteWalkEveryTwentySecondsWithoutCadenceIsUnknown() {
    // Full timestamp coverage cannot prove either running or a sustained low interval at this gap.
    assertEquals(RunContinuityEvidence.UNKNOWN, evaluate(speed = samples(20, 1.0)))
  }

  @Test fun walkingEveryThirtySecondsIsUnknown() {
    assertEquals(RunContinuityEvidence.UNKNOWN, evaluate(speed = samples(30, 1.0)))
  }

  @Test fun walkingEverySixtySecondsIsUnknown() {
    assertEquals(RunContinuityEvidence.UNKNOWN, evaluate(speed = samples(60, 1.0)))
  }

  @Test fun stoppedSamplesCannotUseSparseTimestampCoverageToProveRunning() {
    listOf(15L, 20L, 30L, 60L).forEach { spacing ->
      val expected = if (spacing == 15L) RunContinuityEvidence.INTERRUPTED else RunContinuityEvidence.UNKNOWN
      assertEquals("Stopped samples every $spacing seconds", expected, evaluate(speed = samples(spacing, 0.0)))
    }
  }

  @Test fun runningLikeValuesBeyondTheSupportedGapRemainUnknown() {
    // Positive values alone do not justify bridging gaps longer than the shared 15-second limit.
    listOf(20L, 30L, 60L).forEach { spacing ->
      assertEquals("Running samples every $spacing seconds", RunContinuityEvidence.UNKNOWN,
        evaluate(speed = samples(spacing, 2.5)))
    }
  }

  @Test fun unsupportedMiddleGapInRunningEvidenceIsUnknown() {
    val speed = samples(10, 2.5).filterNot {
      it.timestamp.isAfter(startTime.plusSeconds(200)) && it.timestamp.isBefore(startTime.plusSeconds(400))
    }
    assertEquals(RunContinuityEvidence.UNKNOWN, evaluate(speed = speed))
  }

  @Test fun isolatedWalkingPointInDenseRunningDoesNotProveInterruption() {
    val speed = samples(5, 2.5).map {
      if (it.timestamp == startTime.plusSeconds(300)) it.copy(value = 1.0) else it
    }
    // Running evidence still covers the session with a supported ten-second gap across the noise.
    assertEquals(RunContinuityEvidence.CONTINUOUS, evaluate(speed = speed))
  }

  @Test fun lowSamplesCannotFillUnsupportedGapsBetweenRunningSamples() {
    val speed = samples(10, 2.5).mapIndexed { index, sample ->
      if (index % 2 == 1) sample.copy(value = 1.0) else sample
    }
    // No sustained low interval, but the running points themselves are twenty seconds apart.
    assertEquals(RunContinuityEvidence.UNKNOWN, evaluate(speed = speed))
  }

  @Test fun sustainedWalkingDoesNotRequireWholeSessionCoverage() {
    val speed = (0L..30L step 10).map { TimedMetricSample(startTime.plusSeconds(it), 1.0) }
    assertEquals(RunContinuityEvidence.INTERRUPTED, evaluate(speed = speed))
  }

  @Test fun lowIntervalShorterThanThirtySecondsDoesNotProveInterruption() {
    val speed = (0L..20L step 10).map { TimedMetricSample(startTime.plusSeconds(it), 1.0) }
    assertEquals(RunContinuityEvidence.UNKNOWN, evaluate(speed = speed))
  }

  @Test fun cadenceAloneCanProveRunningWithSupportedCoverage() {
    assertEquals(RunContinuityEvidence.CONTINUOUS, evaluate(cadence = samples(10, 165.0)))
  }

  @Test fun cadenceOnlyWalkingFollowsTheSameSupportedGapPolicy() {
    listOf(10L, 15L, 20L, 30L, 60L).forEach { spacing ->
      val expected = if (spacing <= 15) RunContinuityEvidence.INTERRUPTED else RunContinuityEvidence.UNKNOWN
      assertEquals("Walking cadence every $spacing seconds", expected, evaluate(cadence = samples(spacing, 110.0)))
    }
  }

  @Test fun sparseRunningCadenceDoesNotProveContinuity() {
    assertEquals(RunContinuityEvidence.UNKNOWN, evaluate(cadence = samples(20, 165.0)))
  }

  @Test fun bothRunningSeriesWithSupportedCoverageAreContinuous() {
    assertEquals(RunContinuityEvidence.CONTINUOUS,
      evaluate(speed = samples(10, 2.5), cadence = samples(10, 165.0)))
  }

  @Test fun sustainedWalkingInEitherSeriesOverridesRunningInTheOther() {
    assertEquals(RunContinuityEvidence.INTERRUPTED,
      evaluate(speed = samples(10, 1.0), cadence = samples(10, 165.0)))
    assertEquals(RunContinuityEvidence.INTERRUPTED,
      evaluate(speed = samples(10, 2.5), cadence = samples(10, 110.0)))
  }

  @Test fun walkingThresholdsRemainInclusive() {
    assertEquals(RunContinuityEvidence.INTERRUPTED, evaluate(speed = samples(10, 1.25)))
    assertEquals(RunContinuityEvidence.INTERRUPTED, evaluate(cadence = samples(10, 125.0)))
  }

  @Test fun missingMovementEvidenceRemainsUnknown() {
    assertEquals(RunContinuityEvidence.UNKNOWN, evaluator.evaluate(null))
    assertEquals(RunContinuityEvidence.UNKNOWN, evaluate())
    assertEquals(RunContinuityEvidence.UNKNOWN,
      evaluator.evaluate(metrics(RunTimeSeries(heartRate = samples(10, 150.0)))))
  }

  @Test fun invalidSamplesCannotProveRunningOrInterruption() {
    val invalid = samples(10, Double.NaN) + samples(10, Double.POSITIVE_INFINITY) +
      samples(10, Double.NEGATIVE_INFINITY) + samples(10, -1.0)
    assertEquals(RunContinuityEvidence.UNKNOWN, evaluate(speed = invalid, cadence = invalid))
    assertEquals(RunContinuityEvidence.CONTINUOUS, evaluate(speed = samples(10, 2.5) + invalid))
  }

  @Test fun filteringInvalidValuesDoesNotFillAMovementEvidenceGap() {
    val speed = samples(10, 2.5).map {
      if (it.timestamp.isAfter(startTime.plusSeconds(200)) && it.timestamp.isBefore(startTime.plusSeconds(400))) {
        it.copy(value = Double.NaN)
      } else it
    }
    assertEquals(RunContinuityEvidence.UNKNOWN, evaluate(speed = speed))
  }

  @Test fun fewerThanThreeRunningSamplesDoNotProveContinuity() {
    assertEquals(RunContinuityEvidence.UNKNOWN,
      evaluate(speed = listOf(0L, 10L).map { TimedMetricSample(startTime.plusSeconds(it), 2.5) }))
  }

  @Test fun runningEvidenceMustStillCoverEightyPercentOfTheSession() {
    assertEquals(RunContinuityEvidence.UNKNOWN,
      evaluate(speed = samples(10, 2.5).filter { it.timestamp <= startTime.plusSeconds(470) }))
    assertEquals(RunContinuityEvidence.CONTINUOUS,
      evaluate(speed = samples(10, 2.5).filter { it.timestamp <= startTime.plusSeconds(480) }))
  }

  @Test fun unorderedMovementSamplesUseTimestampOrder() {
    assertEquals(RunContinuityEvidence.CONTINUOUS, evaluate(speed = samples(10, 2.5).reversed()))
    assertEquals(RunContinuityEvidence.INTERRUPTED, evaluate(cadence = samples(10, 110.0).reversed()))
  }

  private fun samples(spacingSeconds: Long, value: Double): List<TimedMetricSample<Double>> =
    (0L..600L step spacingSeconds).map { TimedMetricSample(startTime.plusSeconds(it), value) }

  private fun evaluate(
    speed: List<TimedMetricSample<Double>> = emptyList(),
    cadence: List<TimedMetricSample<Double>> = emptyList(),
  ) = evaluator.evaluate(metrics(RunTimeSeries(speed = speed, cadence = cadence)))

  private fun metrics(series: RunTimeSeries) = RunMetrics(
    runId = "run",
    startTime = startTime,
    endTime = startTime.plusSeconds(600),
    durationSeconds = MetricValue(600L, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    series = series,
    generatedAt = startTime,
  )
}
