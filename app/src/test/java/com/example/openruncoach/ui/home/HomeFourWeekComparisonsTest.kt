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
