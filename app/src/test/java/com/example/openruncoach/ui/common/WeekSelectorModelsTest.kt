package com.example.openruncoach.ui.common

import com.example.openruncoach.domain.RunSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekSelectorModelsTest {
  private val utc = ZoneId.of("UTC")

  @Test
  fun weekStartsOnMondayAndEndsOnSunday() {
    val state = buildWeekSelectorUiModel(
      selectedDate = LocalDate.of(2026, 9, 23),
      runs = emptyList(),
      zoneId = utc,
      locale = Locale.US,
    )

    assertEquals(LocalDate.of(2026, 9, 21), state.days.first().date)
    assertEquals(LocalDate.of(2026, 9, 27), state.days.last().date)
    assertEquals("September 2026", state.monthLabel)
  }

  @Test
  fun selectedDayAndRunDaysAreMarked() {
    val state = buildWeekSelectorUiModel(
      selectedDate = LocalDate.of(2026, 9, 23),
      runs = listOf(runAt("2026-09-25T07:00:00Z")),
      zoneId = utc,
      locale = Locale.US,
    )

    assertTrue(state.days.single { it.date == LocalDate.of(2026, 9, 23) }.isSelected)
    assertTrue(state.days.single { it.date == LocalDate.of(2026, 9, 25) }.hasRun)
    assertFalse(state.days.single { it.date == LocalDate.of(2026, 9, 24) }.hasRun)
  }

  private fun runAt(start: String): RunSummary {
    val startTime = Instant.parse(start)
    return RunSummary(
      healthConnectRecordId = start,
      startTime = startTime,
      endTime = startTime.plusSeconds(1_800),
      durationSeconds = 1_800,
      distanceMeters = 5_000.0,
      averageHeartRate = null,
      maxHeartRate = null,
      averagePaceSecondsPerKm = 360.0,
    )
  }
}
