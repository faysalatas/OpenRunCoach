package com.example.openruncoach.ui.home

import com.example.openruncoach.domain.RunSummary
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeFourWeekComparisonsTest {
  private val currentWeekStart = LocalDate.of(2026, 9, 28)

  @Test
  fun comparesCurrentMetricsWithFourCompleteHistoricalWeeks() {
    val history = (1L..4L).map { weeksAgo ->
      run(
        id = "week-$weeksAgo",
        date = currentWeekStart.minusWeeks(weeksAgo),
        distanceMeters = 10_000.0,
        durationSeconds = 3_600,
      )
    }

    val result = calculateFourWeekComparisons(
      currentWeekStart = currentWeekStart,
      runs = history,
      currentDistanceMeters = 12_000.0,
      currentPaceSecondsPerKm = 330.0,
      zoneId = ZoneOffset.UTC,
    )

    assertEquals(30.0, result.paceDifferenceSecondsPerKm!!, 0.001)
    assertEquals(2_000.0, result.distanceDifferenceMeters!!, 0.001)
  }

  @Test
  fun doesNotTreatMissingHistoricalWeekAsZero() {
    val incompleteHistory = (1L..3L).map { weeksAgo ->
      run(
        id = "week-$weeksAgo",
        date = currentWeekStart.minusWeeks(weeksAgo),
        distanceMeters = 10_000.0,
        durationSeconds = 3_600,
      )
    }

    val result = calculateFourWeekComparisons(
      currentWeekStart = currentWeekStart,
      runs = incompleteHistory,
      currentDistanceMeters = 12_000.0,
      currentPaceSecondsPerKm = 330.0,
      zoneId = ZoneOffset.UTC,
    )

    assertNull(result.paceDifferenceSecondsPerKm)
    assertNull(result.distanceDifferenceMeters)
  }

  @Test fun selectedWeekUsesWeightedCanonicalActualsDespiteEnrichmentDistanceDisagreement() {
    val first = run("first", currentWeekStart, 4000.0, 1644)
    val second = run("second", currentWeekStart.plusDays(1), 2000.0, 900)
    val actuals = listOf(first, second).map { canonical ->
      com.example.openruncoach.domain.RunActualResolver.resolve(canonical, measurements(canonical, 5000.0))
    }
    val week = buildWeeklyMetrics(currentWeekStart, actuals, null, ZoneOffset.UTC)
    assertEquals(6000.0, week.totalDistanceMeters!!, 0.0)
    assertEquals(424.0, week.averagePaceSecondsPerKm!!, 0.0)
    assertEquals(411.0, week.days[0].averagePaceSecondsPerKm!!, 0.0)
    assertEquals(450.0, week.days[1].averagePaceSecondsPerKm!!, 0.0)
  }

  @Test fun fourWeekPaceWeightsCanonicalDistancesInsteadOfProviderMeasurementsOrWeeklyMeans() {
    val actuals = (1L..4L).map { ago ->
      val canonical = run("week-$ago", currentWeekStart.minusWeeks(ago), ago * 1000.0, 400L * ago + 100L)
      com.example.openruncoach.domain.RunActualResolver.resolve(canonical, measurements(canonical, 6000.0))
    }
    val result = calculateFourWeekComparisons(currentWeekStart, actuals, 3000.0, 400.0, ZoneOffset.UTC)
    assertEquals(40.0, result.paceDifferenceSecondsPerKm!!, 0.000001)
    assertEquals(500.0, result.distanceDifferenceMeters!!, 0.000001)
  }

  private fun measurements(run: RunSummary, distance: Double) = com.example.openruncoach.domain.metrics.RunMetrics(
    run.healthConnectRecordId, run.startTime, run.endTime,
    com.example.openruncoach.domain.metrics.MetricValue(run.durationSeconds,
      com.example.openruncoach.domain.metrics.MetricSource.HEALTH_CONNECT,
      com.example.openruncoach.domain.metrics.MetricConfidence.HIGH),
    distanceMeters = com.example.openruncoach.domain.metrics.MetricValue(distance,
      com.example.openruncoach.domain.metrics.MetricSource.SAMSUNG_HEALTH,
      com.example.openruncoach.domain.metrics.MetricConfidence.HIGH), generatedAt = run.endTime)

  private fun run(
    id: String,
    date: LocalDate,
    distanceMeters: Double?,
    durationSeconds: Long,
  ): RunSummary {
    val start = date.atTime(8, 0).toInstant(ZoneOffset.UTC)
    return RunSummary(
      healthConnectRecordId = id,
      startTime = start,
      endTime = start.plusSeconds(durationSeconds),
      durationSeconds = durationSeconds,
      distanceMeters = distanceMeters,
      averageHeartRate = null,
      maxHeartRate = null,
      averagePaceSecondsPerKm = distanceMeters?.let { durationSeconds * 1_000.0 / it },
    )
  }
}
