package com.example.openruncoach.domain.route

import com.example.openruncoach.domain.metrics.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RunSplitResolverTest {
  private val start = Instant.parse("2026-10-01T12:00:00Z")

  @Test fun existingValidCacheIsReused() {
    val route = route(1952.169)
    val persisted = RouteSplitDeriver().derive(route, 1952.169, null).splits
    val result = RunSplitResolver.resolve(RunRouteData(route, persisted), 1952.169, null)
    assertEquals(persisted, result.splits)
    assertEquals(SplitDerivationSource.PERSISTED_CACHE, result.splitDerivationSource)
  }

  @Test fun missingCacheForOctoberDistanceReturnsKilometreAndFinalPartial() {
    verify(1952.169, 2)
  }

  @Test fun missingCacheForSeptemberDistanceReturnsFourKilometresAndFinalPartial() {
    verify(4680.438, 5)
  }

  @Test fun corruptOrChangedDistanceCacheIsReplacedDeterministically() {
    val route = route(1952.169).copy(canonicalDistanceMeters = 1950.0)
    val cached = RouteSplitDeriver().derive(route, 1950.0, null).splits
    val input = RunRouteData(route, cached)
    val result = RunSplitResolver.resolve(input, 1952.169, null)
    assertEquals(SplitDerivationSource.ROUTE_DERIVED_ON_READ, result.splitDerivationSource)
    assertEquals(1952.169, result.splits.sumOf { it.distanceMeters }, 0.001)
    assertEquals(result, RunSplitResolver.resolve(input, 1952.169, null))
    assertEquals(cached, input.splits)
    assertEquals(SplitDerivationSource.ROUTE_DERIVED_ON_READ,
      RunSplitResolver.resolve(input.copy(splits = cached.map { it.copy(durationSeconds = -1.0) }), 1952.169, null).splitDerivationSource)
  }

  @Test fun insufficientRealRouteEvidenceRemainsUnavailable() {
    val route = route(1952.169).let { it.copy(points = listOf(it.points.first(), it.points.last())) }
    val result = RunSplitResolver.resolve(RunRouteData(route, emptyList()), 1952.169, null)
    assertTrue(result.splits.isEmpty())
    assertEquals(SplitDerivationSource.UNAVAILABLE, result.splitDerivationSource)
  }

  private fun verify(distance: Double, expectedCount: Int) {
    val route = route(distance)
    val metrics = RunMetrics(route.runId, start, route.points.last().timestamp,
      MetricValue(2000L, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
      series = RunTimeSeries(heartRate = route.points.map { TimedMetricSample(it.timestamp, 145.0) },
        cadence = route.points.map { TimedMetricSample(it.timestamp, 165.0) }), generatedAt = start)
    val result = RunSplitResolver.resolve(RunRouteData(route, emptyList()), distance, metrics)
    assertEquals(expectedCount, result.splits.size)
    assertEquals(0, result.persistedSplitCount)
    assertEquals(SplitDerivationSource.ROUTE_DERIVED_ON_READ, result.splitDerivationSource)
    assertTrue(result.splits.dropLast(1).all { !it.partial })
    assertTrue(result.splits.last().partial)
    assertEquals(distance, result.splits.sumOf { it.distanceMeters }, 0.001)
    assertTrue(result.splits.all { it.averageHeartRate == 145.0 && it.averageCadence == 165.0 })
    assertEquals(route, result.route)
  }

  private fun route(distance: Double) = RunRoute("physical", MetricSource.SAMSUNG_HEALTH, "Samsung Health", MetricConfidence.HIGH,
    (0..400).map { i -> RunRoutePoint(i, start.plusSeconds(i * 5L), distance * i / 400 / 6371008.8 * 180 / Math.PI, 0.0) })
}
