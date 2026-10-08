package com.example.openruncoach.domain

import com.example.openruncoach.domain.metrics.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RunActualResolverTest {
  private val start = Instant.parse("2026-09-20T10:00:00Z")

  @Test fun canonicalSummaryWinsWithoutChangingIndependentEnrichment() {
    val run = run(4680.438, 1923)
    val measurements = metrics(run, 4755.3).copy(
      averageHeartRate = metric(145.0), movingAverageCadence = metric(165.0),
      averageSpeedMetersPerSecond = metric(2.5),
    )
    val original = measurements.copy()
    val actual = RunActualResolver.resolve(run, measurements)
    assertEquals(4680.438, actual.distanceMeters!!, 0.000001)
    assertEquals(1923L, actual.durationSeconds)
    assertEquals(1923000.0 / 4680.438, actual.averagePaceSecondsPerKm!!, 0.000001)
    assertEquals("com.strava", actual.sourcePackageName)
    assertEquals("physical", actual.physicalRunId)
    assertEquals(ActualValueBasis.CANONICAL_SUMMARY, actual.actualProvenance!!.distance.basis)
    assertEquals(original, measurements)
    assertEquals(MetricSource.SAMSUNG_HEALTH, measurements.averageHeartRate!!.source)
    assertEquals(MetricSource.SAMSUNG_HEALTH, measurements.movingAverageCadence!!.source)
  }

  @Test fun octoberRegressionUsesCanonicalDistanceAndDurationEvenWithDifferentStoredPaces() {
    val run = run(1952.169, 848).copy(averagePaceSecondsPerKm = 424.0)
    val actual = RunActualResolver.resolve(run, metrics(run, 2001.5))
    assertEquals(1952.169, actual.distanceMeters!!, 0.000001)
    assertEquals(848000.0 / 1952.169, actual.averagePaceSecondsPerKm!!, 0.000001)
  }

  @Test fun missingDistanceUsesTrustedMatchedFallbackAndKeepsItsOriginDownstream() {
    val run = run(null, 1644)
    val actual = RunActualResolver.resolve(run, metrics(run, 4000.0))
    assertEquals(4000.0, actual.distanceMeters!!, 0.0)
    assertEquals(411.0, actual.averagePaceSecondsPerKm!!, 0.0)
    assertEquals(ActualValueBasis.MATCHED_METRIC_FALLBACK, actual.actualProvenance!!.distance.basis)
    assertEquals("Samsung Health", actual.actualProvenance.distance.source)
    assertEquals(actual, RunActualResolver.resolve(actual))
  }

  @Test fun invalidCanonicalDistanceUsesSameFallbackAsMissingDistance() {
    listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0).forEach { distance ->
      val run = run(distance, 1644)
      assertEquals(4000.0, RunActualResolver.resolve(run, metrics(run, 4000.0)).distanceMeters!!, 0.0)
    }
  }

  @Test fun fallbackRejectsUntrustedConflictWrongRecordAndOriginalLongerInterval() {
    val run = run(null, 1644)
    val valid = metrics(run, 4000.0)
    val invalid = listOf(
      valid.copy(runId = "another-provider-record"),
      valid.copy(startTime = start.minusSeconds(60)),
      valid.copy(endTime = run.endTime.plusSeconds(60)),
      valid.copy(distanceMeters = metric(4000.0).copy(confidence = MetricConfidence.MEDIUM)),
      valid.copy(distanceMeters = metric(4000.0).copy(verification = MetricVerification.CONFLICT)),
      valid.copy(distanceMeters = metric(Double.NaN)),
    )
    invalid.forEach {
      val actual = RunActualResolver.resolve(run, it)
      assertNull(actual.distanceMeters)
      assertNull(actual.averagePaceSecondsPerKm)
      assertEquals(ActualValueBasis.UNAVAILABLE, actual.actualProvenance!!.distance.basis)
    }
  }

  @Test fun trimmedCanonicalSummaryNeverRestoresOriginalVolumeOrInterval() {
    val trimmed = run(1952.169, 848)
    val original = metrics(trimmed, 4755.3).copy(startTime = start.minusSeconds(60),
      endTime = start.plusSeconds(2000), durationSeconds = MetricValue(2060L, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH))
    val actual = RunActualResolver.resolve(trimmed, original)
    assertEquals(trimmed.startTime, actual.startTime)
    assertEquals(trimmed.endTime, actual.endTime)
    assertEquals(trimmed.durationSeconds, actual.durationSeconds)
    assertEquals(trimmed.distanceMeters, actual.distanceMeters)
    assertNull(RunActualResolver.resolve(trimmed.copy(distanceMeters = null), original).distanceMeters)
  }

  @Test fun missingDurationUsesCanonicalIntervalBeforeAnyProviderDuration() {
    val run = run(4000.0, 1644).copy(durationSeconds = 0)
    val actual = RunActualResolver.resolve(run, metrics(run, 4755.0).copy(
      durationSeconds = MetricValue(2000L, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH)))
    assertEquals(1644L, actual.durationSeconds)
    assertEquals(411.0, actual.averagePaceSecondsPerKm!!, 0.0)
    assertEquals(ActualValueBasis.CANONICAL_INTERVAL, actual.actualProvenance!!.duration.basis)
  }

  @Test fun unavailableDurationCannotBecomeSensorSpeedPace() {
    val run = run(4000.0, 0).copy(averagePaceSecondsPerKm = 300.0)
    val actual = RunActualResolver.resolve(run, metrics(run, 4000.0).copy(averageSpeedMetersPerSecond = metric(3.0)))
    assertEquals(0L, actual.durationSeconds)
    assertNull(actual.averagePaceSecondsPerKm)
  }

  private fun run(distance: Double?, duration: Long) = RunSummary("strava", start, start.plusSeconds(duration),
    duration, distance, null, null, 405.0, sourcePackageName = "com.strava", physicalRunId = "physical")
  private fun metrics(run: RunSummary, distance: Double) = RunMetrics(run.healthConnectRecordId, run.startTime, run.endTime,
    MetricValue(run.durationSeconds, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    distanceMeters = metric(distance), averagePaceSecondsPerKm = metric(405.0), generatedAt = start)
  private fun metric(value: Double) = MetricValue(value, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH,
    sourceAppName = "Samsung Health")
}
