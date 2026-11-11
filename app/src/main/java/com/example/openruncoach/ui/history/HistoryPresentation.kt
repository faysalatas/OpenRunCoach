package com.example.openruncoach.ui.history

import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.aggregateActualPaceSecondsPerKm
import com.example.openruncoach.ui.components.ChartPoint
import com.example.openruncoach.ui.home.HomeProgressTrendCalculator
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToLong

internal data class CalendarRunGroup(val weekStart: LocalDate, val title: String,
  val distanceMeters: Double?, val runs: List<HistoryRunItem>)

/** Pure display grouping. Monday is explicit: locale changes labels, never membership or records. */
internal fun groupRunsByCalendarWeek(items: List<HistoryRunItem>, today: LocalDate, zone: ZoneId,
  locale: Locale = Locale.getDefault()): List<CalendarRunGroup> {
  val current = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
  return items.sortedWith(compareByDescending<HistoryRunItem> { it.run.startTime }.thenBy { it.run.healthConnectRecordId })
    .groupBy { it.run.startTime.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    .toSortedMap(reverseOrder()).map { (monday, runs) -> CalendarRunGroup(monday, when (monday) {
      current -> "THIS WEEK"
      current.minusWeeks(1) -> "LAST WEEK"
      else -> "${monday.format(date)} – ${monday.plusDays(6).format(date)}"
    }, completeHistoryDistance(runs.map { it.run }), runs) }
}

internal data class HistoryMetric(val value: String, val label: String)
internal data class HistoryPresentation(val month: String, val metrics: List<HistoryMetric>,
  val weeks: List<CalendarRunGroup>, val pacePoints: List<ChartPoint>, val paceDelta: String?,
  val paceImproving: Boolean, val easyHeartRate: String?, val longestDistance: String?, val longestDate: String?)

internal fun historyPresentation(state: HistoryUiState, clock: Clock, locale: Locale = Locale.getDefault()): HistoryPresentation {
  val today = LocalDate.now(clock)
  val items = if (state.isLoading || state.errorMessage != null) emptyList() else state.groups.flatMap { it.runs }
  val weeks = groupRunsByCalendarWeek(items, today, clock.zone, locale)
  val current = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  // The PNG hero summarizes this calendar week; its month eyebrow is date context.
  val currentRuns = weeks.firstOrNull { it.weekStart == current }?.runs.orEmpty().map { it.run }
  val metrics = if (state.isLoading || state.errorMessage != null) emptyList() else buildList {
    add(HistoryMetric(String.format(locale, "%d", currentRuns.size), "runs"))
    completeHistoryDistance(currentRuns)?.let { add(HistoryMetric(String.format(locale, "%.1f", it / 1_000), "km")) }
    currentRuns.takeIf { it.isNotEmpty() && it.all { r -> r.durationSeconds > 0 } }?.sumOf { it.durationSeconds }?.let {
      add(HistoryMetric("%d:%02d".format(locale, it / 3_600, (it % 3_600) / 60), "hours"))
    }
    aggregateActualPaceSecondsPerKm(currentRuns)?.takeIf { it.isFinite() && it > 0 }?.let { add(HistoryMetric(historyPace(it), "avg /km")) }
  }
  val trend = if (state.showIgnored) null else HomeProgressTrendCalculator.calculate(today, items.map { it.run },
    state.completedWorkoutTypesByRunId, clock.zone)
  val points = trend?.weeks.orEmpty().map { week -> ChartPoint(week.easyPaceSecondsPerKm?.takeIf { it.isFinite() && it > 0 }?.toFloat(),
    week.easyPaceSecondsPerKm?.takeIf { it.isFinite() && it > 0 }?.let(::historyPace)) }
  val change = trend?.paceChangeSecondsPerKm
  val delta = change?.let { when { it > 0 -> "$it s/km faster"; it < 0 -> "${kotlin.math.abs(it)} s/km slower"; else -> "Steady" } }
  val profile = state.profile.takeUnless { state.showIgnored }
  val hr = profile?.typicalEasyHeartRate?.takeIf { it.contributingRuns > 0 }?.representative?.takeIf { it.isFinite() && it > 0 }
  val longest = profile?.longestRunLast28Days?.takeIf { it.distanceMeters?.let { d -> d.isFinite() && d > 0 } == true }
  return HistoryPresentation(today.format(DateTimeFormatter.ofPattern("MMMM", locale)).uppercase(locale), metrics, weeks,
    points.takeIf { it.any { p -> p.value != null } }.orEmpty(), delta, change?.let { it > 0 } == true,
    hr?.let { String.format(locale, "%.0f", it) }, longest?.distanceMeters?.let { String.format(locale, "%.1f", it / 1_000) },
    longest?.date?.atZone(clock.zone)?.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)))
}

private fun completeHistoryDistance(runs: List<RunSummary>): Double? = runs
  .takeIf { it.isNotEmpty() && it.all { r -> r.distanceMeters?.let { it.isFinite() && it > 0 } == true } }
  ?.sumOf { requireNotNull(it.distanceMeters) }
internal fun historyPace(value: Double): String = value.roundToLong().let { "%d:%02d".format(it / 60, it % 60) }
