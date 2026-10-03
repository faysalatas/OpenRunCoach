package com.example.openruncoach.domain.route

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.RunTimeSeries
import com.example.openruncoach.domain.metrics.TimedMetricSample
import java.time.Instant
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSplitDeriverTest {
  private val start = Instant.parse("2026-09-26T07:37:00Z")
  private val deriver = RouteSplitDeriver()

  @Test fun steadyFourKilometreRouteProducesNearEqualSplits() {
    val result = derive(route(4_000.0, 1_920) { it }, 4_000.0)

    assertEquals(4, result.splits.size)
    assertTrue(result.splits.all { !it.partial })
    assertTrue(result.splits.maxOf { it.durationSeconds } - result.splits.minOf { it.durationSeconds } < 2.0)
  }

  @Test fun progressiveRouteProducesFasterLaterSplits() {
    val result = derive(route(4_000.0, 1_920) { fraction ->
      0.65 * fraction + 0.35 * fraction * fraction
    }, 4_000.0)

    assertEquals(4, result.splits.size)
    assertTrue(result.splits.zipWithNext().all { (first, second) ->
      second.durationSeconds < first.durationSeconds
    })
  }

  @Test fun walkingInterruptionSlowsAffectedSplit() {
    val result = derive(route(4_000.0, 2_100) { fraction ->
      when {
        fraction <= 0.3 -> fraction * 1.35
        fraction <= 0.6 -> 0.405 + (fraction - 0.3) * (11.0 / 60.0)
        else -> 0.46 + (fraction - 0.6) * 1.35
      }
    }, 4_000.0)

    assertEquals(4, result.splits.size)
    assertTrue(result.splits.maxOf { it.durationSeconds } > result.splits.minOf { it.durationSeconds } * 1.5)
  }

  @Test fun partialRouteProducesThreeFullAndOnePartialSplit() {
    val result = derive(route(3_780.0, 1_571) { it }, 3_780.0)

    assertEquals(4, result.splits.size)
    assertEquals(listOf(false, false, false, true), result.splits.map(RunSplit::partial))
    assertEquals(780.0, result.splits.last().distanceMeters, 1.0)
    assertEquals(3_780.0, result.splits.sumOf(RunSplit::distanceMeters), 1.0)
  }

  @Test fun impossibleGpsOutlierIsRejectedWithoutExplodingDistance() {
    val input = route(4_000.0, 1_920) { it }
    val middle = input.points.size / 2
    val withOutlier = input.copy(points = input.points.mapIndexed { index, point ->
      if (index == middle) point.copy(latitude = point.latitude + 1.0) else point
    })

    val result = derive(withOutlier, 4_000.0)

    assertEquals(4, result.splits.size)
    assertTrue(result.route.rejectedPointCount >= 1)
    assertEquals(4_000.0, result.route.derivedDistanceMeters ?: 0.0, 25.0)
  }

  @Test fun veryLargeSamplingGapRejectsSplitDerivation() {
    val input = route(4_000.0, 1_920) { it }
    val withGap = input.copy(points = input.points.filterNot { point ->
      val second = point.timestamp.epochSecond - start.epochSecond
      second in 650L..1_270L
    }.mapIndexed { index, point -> point.copy(sequence = index) })

    val result = derive(withGap, 4_000.0)

    assertTrue(result.splits.isEmpty())
    assertTrue(result.route.hasLargeSamplingGap)
    assertEquals(MetricConfidence.LOW, result.route.confidence)
  }

  @Test fun splitHeartRateAndCadenceComeFromSamplesInsideEachBoundary() {
    val input = route(2_000.0, 1_000) { it }
    val samples = (0L..1_000L step 10L).map { second ->
      TimedMetricSample(start.plusSeconds(second), 140.0 + second / 500.0)
    }
    val cadence = (0L..1_000L step 10L).map { second ->
      TimedMetricSample(start.plusSeconds(second), 160.0 + second / 500.0)
    }
    val result = deriver.derive(
      input,
      2_000.0,
      metrics(2_000.0, 1_000, RunTimeSeries(heartRate = samples, cadence = cadence)),
    )

    assertEquals(2, result.splits.size)
    assertNotNull(result.splits.first().averageHeartRate)
    assertNotNull(result.splits.first().averageCadence)
    assertTrue(result.splits.last().averageHeartRate!! > result.splits.first().averageHeartRate!!)
  }

  private fun derive(route: RunRoute, canonicalDistance: Double) = deriver.derive(
    route,
    canonicalDistance,
    metrics(canonicalDistance, route.points.last().timestamp.epochSecond - start.epochSecond),
  )

  private fun route(
    distanceMeters: Double,
    durationSeconds: Long,
    distanceFraction: (Double) -> Double,
  ): RunRoute {
    val points = (0L..durationSeconds step 5L).toMutableList()
    if (points.last() != durationSeconds) points += durationSeconds
    return RunRoute(
      runId = "run",
      source = MetricSource.SAMSUNG_HEALTH,
      sourceAppName = "Samsung Health",
      confidence = MetricConfidence.HIGH,
      points = points.mapIndexed { index, second ->
        val fraction = second.toDouble() / durationSeconds
        RunRoutePoint(
          sequence = index,
          timestamp = start.plusSeconds(second),
          latitude = 52.52,
          longitude = 13.405 + distanceMeters * distanceFraction(fraction) /
            (111_320.0 * cos(Math.toRadians(52.52))),
          altitudeMeters = 15.0,
          horizontalAccuracyMeters = 4.0,
        )
      },
    )
  }

  private fun metrics(
    distanceMeters: Double,
    durationSeconds: Long,
    series: RunTimeSeries = RunTimeSeries(),
  ) = RunMetrics(
    runId = "run",
    startTime = start,
    endTime = start.plusSeconds(durationSeconds),
    durationSeconds = MetricValue(durationSeconds, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    distanceMeters = MetricValue(distanceMeters, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    series = series,
    generatedAt = start.plusSeconds(durationSeconds),
  )
}
