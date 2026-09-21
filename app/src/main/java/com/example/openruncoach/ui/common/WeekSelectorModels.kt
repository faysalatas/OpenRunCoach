package com.example.openruncoach.ui.common

import com.example.openruncoach.domain.RunSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class WeekDayUiModel(
  val date: LocalDate,
  val dayLabel: String,
  val dayNumber: String,
  val hasRun: Boolean,
  val isSelected: Boolean,
)

data class WeekSelectorUiModel(
  val monthLabel: String,
  val days: List<WeekDayUiModel>,
)

fun buildWeekSelectorUiModel(
  selectedDate: LocalDate,
  runs: List<RunSummary>,
  zoneId: ZoneId = ZoneId.systemDefault(),
  locale: Locale = Locale.getDefault(),
): WeekSelectorUiModel {
  val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  val runDates = runs.mapTo(mutableSetOf()) { it.startTime.atZone(zoneId).toLocalDate() }
  val days = (0L..6L).map { offset ->
    val date = monday.plusDays(offset)
    WeekDayUiModel(
      date = date,
      dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2),
      dayNumber = date.dayOfMonth.toString(),
      hasRun = date in runDates,
      isSelected = date == selectedDate,
    )
  }
  val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
  val monthLabel = if (days.first().date.month == days.last().date.month) {
    days.first().date.format(monthFormatter)
  } else {
    "${days.first().date.format(DateTimeFormatter.ofPattern("MMM", locale))}–" +
      days.last().date.format(monthFormatter)
  }
  return WeekSelectorUiModel(monthLabel, days)
}
