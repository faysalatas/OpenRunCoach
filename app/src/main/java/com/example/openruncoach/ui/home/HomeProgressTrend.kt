package com.example.openruncoach.ui.home

import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.workout.WorkoutType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

data class HomeProgressWeek(
  val startDate: LocalDate,
  val easyPaceSecondsPerKm: Double?,
  val totalDistanceMeters: Double?,
)

data class HomeProgressSummary(
  val weeks: List<HomeProgressWeek>,
  val firstEasyPaceSecondsPerKm: Double?,
  val latestEasyPaceSecondsPerKm: Double?,
  val paceChangeSecondsPerKm: Long?,
)

/** Pure presentation aggregation over canonical run history and authoritative workout links. */
object HomeProgressTrendCalculator {
  fun calculate(
    referenceDate: LocalDate,
    canonicalRuns: List<RunSummary>,
    completedWorkoutTypesByRunId: Map<String, WorkoutType>,
    zoneId: ZoneId,
  ): HomeProgressSummary {
    val currentWeek = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val firstWeek = currentWeek.minusWeeks(3)
    val endExclusive = currentWeek.plusWeeks(1)
    val runs = canonicalRuns.filter { run ->
      run.isCanonical && run.relevance == RunRelevance.ACTIVE && run.durationSeconds > 0L &&
        run.startTime.atZone(zoneId).toLocalDate().let { date -> date >= firstWeek && date < endExclusive }
    }
    val weeks = (0L..3L).map { offset ->
      val start = firstWeek.plusWeeks(offset)
      val end = start.plusWeeks(1)
      val weekRuns = runs.filter { run ->
        run.startTime.atZone(zoneId).toLocalDate().let { date -> date >= start && date < end }
      }
      val validVolumeRuns = weekRuns.filter { it.distanceMeters.isValidDistance() }
      val easyRuns = validVolumeRuns.filter { run ->
        completedWorkoutTypesByRunId[run.healthConnectRecordId] == WorkoutType.EASY_RUN
      }
      val easyDistance = easyRuns.sumOf { requireNotNull(it.distanceMeters) }
      HomeProgressWeek(
        startDate = start,
        easyPaceSecondsPerKm = easyDistance.takeIf { it > 0.0 }?.let { distance ->
          easyRuns.sumOf(RunSummary::durationSeconds) * 1_000.0 / distance
        },
        totalDistanceMeters = validVolumeRuns.takeIf { it.isNotEmpty() }
          ?.sumOf { requireNotNull(it.distanceMeters) },
      )
    }
    val observedPaces = weeks.mapNotNull(HomeProgressWeek::easyPaceSecondsPerKm)
    val first = observedPaces.firstOrNull()
    val latest = observedPaces.lastOrNull()
    return HomeProgressSummary(
      weeks = weeks,
      firstEasyPaceSecondsPerKm = first,
      latestEasyPaceSecondsPerKm = latest,
      paceChangeSecondsPerKm = if (observedPaces.size >= 2 && first != null && latest != null) {
        first.roundToLong() - latest.roundToLong()
      } else null,
    )
  }
}

private fun Double?.isValidDistance(): Boolean = this?.let { it.isFinite() && it > 0.0 } == true
