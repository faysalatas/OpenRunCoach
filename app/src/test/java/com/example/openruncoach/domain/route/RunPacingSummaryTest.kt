package com.example.openruncoach.domain.route

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RunPacingSummaryTest {
  private val start = Instant.parse("2026-09-26T10:00:00Z")

  @Test fun splitAndPartialDeltasUseCanonical411RatherThanEnrichment405() {
    val raw = com.example.openruncoach.domain.RunSummary("canonical", start, start.plusSeconds(1644), 1644,
      4000.0, null, null, 405.0, sourcePackageName = "com.strava")
    val metrics = com.example.openruncoach.domain.metrics.RunMetrics(raw.healthConnectRecordId, raw.startTime, raw.endTime,
      com.example.openruncoach.domain.metrics.MetricValue(1644L, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
      distanceMeters = com.example.openruncoach.domain.metrics.MetricValue(4755.0, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH),
      averagePaceSecondsPerKm = com.example.openruncoach.domain.metrics.MetricValue(405.0, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH),
      generatedAt = start)
    val actual = com.example.openruncoach.domain.RunActualResolver.resolve(raw, metrics)
    val route = RunRoute("canonical", MetricSource.SAMSUNG_HEALTH, "Samsung Health", MetricConfidence.HIGH, emptyList())
    val full = RunSplit("canonical", 1, RunSplitDerivation.ROUTE_DERIVED, start, start.plusSeconds(401),
      1000.0, 401.0, 401.0, partial = false, confidence = MetricConfidence.HIGH)
    val partial = full.copy(ordinal = 2, startTime = full.endTime, endTime = full.endTime.plusMillis(272680),
      distanceMeters = 680.0, durationSeconds = 272.68, partial = true)
    val data = RunRouteData(route, listOf(full, partial))
    val pacing = RunPacingSummaryCalculator.summarize(actual, data)
    assertEquals(411.0, pacing.wholeRunAveragePaceSecondsPerKm!!, 0.0)
    assertEquals(-10.0, pacing.deltasSecondsPerKm[1]!!, 0.0)
    assertEquals(-10.0, pacing.deltasSecondsPerKm[2]!!, 0.0)
    assertEquals(listOf(full, partial), data.splits)
  }

  @Test fun equalDistanceHalvesUseCanonicalDistanceWhileKeepingSamsungRouteGeometry() {
    val run = com.example.openruncoach.domain.RunSummary("canonical", start, start.plusSeconds(1644), 1644,
      4000.0, null, null, 405.0)
    val points = (0..100).map { index -> RunRoutePoint(index, start.plusMillis(16440L * index),
      4000.0 * index / 100 / 6371008.8 * 180 / Math.PI, 0.0) }
    val route = RunRoute("physical", MetricSource.SAMSUNG_HEALTH, "Samsung Health", MetricConfidence.HIGH, points)
    val pacing = RunPacingSummaryCalculator.summarize(run, RunRouteData(route, emptyList()))
    val halves = requireNotNull(pacing.equalDistanceHalves)
    assertEquals(2000.0, halves.halfDistanceMeters, 0.000001)
    assertEquals(822.0, java.time.Duration.between(start, halves.midpoint).toNanos() / 1000000000.0, 0.000001)
    assertEquals(411.0, halves.firstHalfPaceSecondsPerKm, 0.000001)
    assertEquals(points, route.points)
    assertEquals(MetricSource.SAMSUNG_HEALTH, route.source)
  }

  private fun samples(firstPace: Double, secondPace: Double) = (0..50).map { index ->
    val distance = index * 100.0
    val seconds = minOf(distance, 2500.0) * firstPace / 1000.0 +
      (distance - 2500.0).coerceAtLeast(0.0) * secondPace / 1000.0
    RunDistanceSample(start.plusMillis((seconds * 1000).toLong()), distance)
  }

  @Test fun slowerSplitDeltaIsPositiveFourteenSeconds() {
    assertEquals(14.0, RunPacingSummaryCalculator.splitDeltaSecondsPerKm(401.0, 387.0) ?: error("Missing"), 0.0)
  }
  @Test fun fasterSplitDeltaIsNegativeThirteenSeconds() {
    assertEquals(-13.0, RunPacingSummaryCalculator.splitDeltaSecondsPerKm(374.0, 387.0) ?: error("Missing"), 0.0)
  }
  @Test fun partialSplitUsesItsNormalizedPaceAndMissingAverageIsNotZero() {
    val partialPace = 336.0 * 1000.0 / 780.0
    assertEquals(partialPace - 387.0, RunPacingSummaryCalculator.splitDeltaSecondsPerKm(partialPace, 387.0) ?: error("Missing"), 0.00001)
    assertNull(RunPacingSummaryCalculator.splitDeltaSecondsPerKm(partialPace, null))
    assertNull(RunPacingSummaryCalculator.splitDeltaSecondsPerKm(Double.NaN, 387.0))
  }
  @Test fun progressiveFiveKmComparesEqualHalvesNotFirstAndLastSplits() {
    val result = RunPacingSummaryCalculator.calculate(samples(400.0, 360.0), 5000.0) ?: error("Missing")
    assertEquals(2500.0, result.halfDistanceMeters, 0.0)
    assertEquals(1000.0, result.firstHalfDurationSeconds, 0.001)
    assertEquals(900.0, result.secondHalfDurationSeconds, 0.001)
    assertEquals(-40.0, result.deltaSecondsPerKm, 0.001)
    assertEquals(HalfSplitDirection.NEGATIVE, result.direction)
  }
  @Test fun fadingFiveKmIsPositiveSplit() {
    val result = RunPacingSummaryCalculator.calculate(samples(360.0, 400.0), 5000.0) ?: error("Missing")
    assertEquals(40.0, result.deltaSecondsPerKm, 0.001)
    assertEquals(HalfSplitDirection.POSITIVE, result.direction)
  }
  @Test fun evenSplitUsesThreeSecondTolerance() {
    for (delta in listOf(-3.0, -1.0, 0.0, 2.0, 3.0)) {
      assertEquals(HalfSplitDirection.EVEN,
        RunPacingSummaryCalculator.calculate(samples(380.0, 380.0 + delta), 5000.0)?.direction)
    }
    assertEquals(HalfSplitDirection.POSITIVE, RunPacingSummaryCalculator.calculate(samples(380.0, 384.0), 5000.0)?.direction)
  }
  @Test fun halfwayBoundaryInterpolatesTimestampBetweenActualSamples() {
    val evidence = samples(400.0, 360.0).filter { it.distanceMeters != 2500.0 }
    // 2400 m at 960 s; 2600 m at 1036 s; interpolated 2500 m at 998 s.
    val result = RunPacingSummaryCalculator.calculate(evidence, 5000.0) ?: error("Missing")
    assertEquals(start.plusSeconds(998), result.midpoint)
    assertEquals(998.0, result.firstHalfDurationSeconds, 0.001)
  }
  @Test fun insufficientShortGappedOrNonMonotonicEvidenceHasNoResult() {
    assertNull(RunPacingSummaryCalculator.calculate(emptyList(), 5000.0))
    assertNull(RunPacingSummaryCalculator.calculate(samples(380.0, 380.0).take(4), 300.0))
    assertNull(RunPacingSummaryCalculator.calculate(listOf(samples(380.0, 380.0).first(), samples(380.0, 380.0).last()), 5000.0))
    assertNull(RunPacingSummaryCalculator.calculate(samples(380.0, 380.0).filter { it.distanceMeters !in 100.0..900.0 }, 5000.0))
    assertNull(RunPacingSummaryCalculator.calculate(samples(380.0, 380.0).reversed(), 5000.0))
  }
  @Test fun walkingAndStoppedTimeInSecondHalfRemainInElapsedPace() {
    val evidence = samples(360.0, 360.0).flatMap { sample ->
      when {
        sample.distanceMeters == 3000.0 -> listOf(sample, sample.copy(timestamp = sample.timestamp.plusSeconds(60)))
        sample.distanceMeters > 3000.0 -> listOf(sample.copy(timestamp = sample.timestamp.plusSeconds(60)))
        else -> listOf(sample)
      }
    }
    val result = RunPacingSummaryCalculator.calculate(evidence, 5000.0) ?: error("Missing")
    assertEquals(900.0, result.firstHalfDurationSeconds, 0.001)
    assertEquals(960.0, result.secondHalfDurationSeconds, 0.001)
    assertEquals(24.0, result.deltaSecondsPerKm, 0.001)
    val walking = RunPacingSummaryCalculator.calculate(samples(360.0, 600.0), 5000.0) ?: error("Missing walking evidence")
    assertEquals(600.0, walking.secondHalfPaceSecondsPerKm, 0.001)
    assertEquals(240.0, walking.deltaSecondsPerKm, 0.001)
  }
  @Test fun routeAdapterReusesExistingCalibrationAndDoesNotMutateSplits() {
    val points = (0..50).map { index ->
      val distance = index * 100.0
      val seconds = minOf(distance, 2500.0) * 0.4 + (distance - 2500).coerceAtLeast(0.0) * 0.36
      RunRoutePoint(index, start.plusMillis((seconds * 1000).toLong()), 0.0,
        Math.toDegrees(distance / 6_371_008.8))
    }
    val route = RunRoute("run", MetricSource.SAMSUNG_HEALTH, "Samsung Health", MetricConfidence.HIGH, points)
    val before = RouteSplitDeriver().derive(route, 5000.0, null)
    assertTrue(before.distanceSamples.isEmpty()) // Existing import consumers do not allocate this optional trace.
    assertEquals(points.size, RouteSplitDeriver().derive(route, 5000.0, null, includeDistanceSamples = true).distanceSamples.size)
    val halves = RunPacingSummaryCalculator.fromRoute(before.route, 5000.0, start, points.last().timestamp) ?: error("Missing")
    assertEquals(-40.0, halves.deltaSecondsPerKm, 0.01)
    assertEquals(before.splits, RouteSplitDeriver().derive(route, 5000.0, null).splits)
    assertNull(RunPacingSummaryCalculator.fromRoute(route.copy(confidence = MetricConfidence.LOW), 5000.0, start, points.last().timestamp))
    assertNull(RunPacingSummaryCalculator.fromRoute(route.copy(hasLargeSamplingGap = true), 5000.0, start, points.last().timestamp))
    assertNull(RunPacingSummaryCalculator.fromRoute(route, 5000.0, start.minusSeconds(60), points.last().timestamp))
  }
}
