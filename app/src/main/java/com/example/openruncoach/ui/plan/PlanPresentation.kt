package com.example.openruncoach.ui.plan

import com.example.openruncoach.ui.components.BarState
import com.example.openruncoach.ui.components.ChartBar
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.data.PlanWorkoutPresentation
import com.example.openruncoach.domain.plan.TrainingPlan
import com.example.openruncoach.domain.plan.TrainingWeek
import com.example.openruncoach.domain.plan.PlannedWorkoutEntry
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.ui.workout.displayLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PlanWeekPosition(val label: String) { PAST("Past"), CURRENT("Current"), FUTURE("Upcoming") }

fun TrainingWeek.position(today: LocalDate): PlanWeekPosition = when {
  today.isBefore(startDate) -> PlanWeekPosition.FUTURE
  today.isAfter(startDate.plusDays(6)) -> PlanWeekPosition.PAST
  else -> PlanWeekPosition.CURRENT
}

fun formatPlanWeekRange(start: LocalDate, locale: Locale = Locale.getDefault()): String {
  val end = start.plusDays(6)
  val day = DateTimeFormatter.ofPattern("d", locale)
  val monthDay = DateTimeFormatter.ofPattern("d MMM", locale)
  val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
  return when {
    start.year != end.year -> "${start.format(full)} – ${end.format(full)}"
    start.month != end.month -> "${start.format(monthDay)} – ${end.format(monthDay)}"
    else -> "${start.format(day)}–${end.format(monthDay)}"
  }
}

data class PlanWeekSummary(
  val plannedDistanceMeters: Double?,
  val plannedDurationSeconds: Long?,
  val sessionCount: Int,
  val recordedSessions: Int,
  val completedSessions: Int,
  val actualDistanceMeters: Double?,
  val actualDurationSeconds: Long?,
  val showActuals: Boolean,
)

/** Associations are already one-to-one and executions already resolve canonical actuals. */
fun TrainingWeek.summary(today: LocalDate, metadata: Map<String, PlanWorkoutPresentation> = emptyMap()): PlanWeekSummary {
  val sessions = workouts.filter { it.workout.type != WorkoutType.REST }
  val recorded = sessions.filter { metadata[it.workout.id]?.actual != null || it.executionResult != null }
  val distances = recorded.map {
    val actual = metadata[it.workout.id]?.actual
    if (actual != null) actual.distanceMeters else it.executionResult?.actualDistanceMeters
  }
  val durations = recorded.map { metadata[it.workout.id]?.actual?.durationSeconds ?: it.executionResult?.actualDurationSeconds }
  return PlanWeekSummary(
    targetDistanceMeters, targetDurationSeconds, sessions.size, recorded.size,
    sessions.count { it.executionResult?.isCompleted == true },
    distances.takeIf { it.isNotEmpty() && it.all { distance -> distance != null } }?.sumOf { requireNotNull(it) },
    durations.takeIf { it.isNotEmpty() && it.all { duration -> duration != null && duration > 0 } }?.sumOf { requireNotNull(it) },
    position(today) != PlanWeekPosition.FUTURE && recorded.isNotEmpty(),
  )
}

fun PlannedWorkoutEntry.statusLabel(metadata: PlanWorkoutPresentation?): String = executionResult?.status?.displayLabel()
  ?: when {
    metadata?.actual != null -> "Run recorded · Checkout required"
    metadata?.lifecycle == WorkoutLifecycleState.CHECK_IN_REQUIRED -> "Check-in required"
    metadata?.lifecycle == WorkoutLifecycleState.READY -> "Ready"
    metadata?.lifecycle == WorkoutLifecycleState.CHECKOUT_REQUIRED -> "Checkout required"
    metadata?.lifecycle in setOf(WorkoutLifecycleState.COMPLETED, WorkoutLifecycleState.ANALYZED, WorkoutLifecycleState.NEXT_WORKOUT_CREATED) -> "Completed"
    else -> "Scheduled"
  }

/** Keep a locally selected week through background emissions; reset only for a different plan. */
fun selectedPlanWeek(previousPlanId: String?, selected: Int, plan: TrainingPlan?, today: LocalDate): Int {
  plan ?: return 0
  if (previousPlanId == plan.id) return selected.coerceIn(plan.weeks.indices)
  return plan.weeks.indexOfFirst { it.position(today) == PlanWeekPosition.CURRENT }.takeIf { it >= 0 } ?: 0
}

fun concisePlanText(text: String, limit: Int = 150): String {
  val normalized = text.replace(Regex("\\s+"), " ").trim()
  if (normalized.length <= limit) return normalized
  val prefix = normalized.take(limit - 1).substringBeforeLast(' ', normalized.take(limit - 1))
  return "$prefix…"
}

/** Chart projections preserve generated targets and current canonical actuals as different series. */
internal data class PlanCharts(
  val longRuns: List<ChartBar>,
  val weeklyDistance: List<ChartBar>,
  val longDescription: String,
  val weeklyDescription: String,
)

internal fun planCharts(plan: TrainingPlan, today: LocalDate,
  metadata: Map<String, PlanWorkoutPresentation> = emptyMap(), goalDistanceMeters: Double? = null,
  completionDate: LocalDate? = null, locale: Locale = Locale.getDefault(),
): PlanCharts {
  val longDescriptions = mutableListOf<String>()
  val weekDescriptions = mutableListOf<String>()
  fun label(value: Double) = String.format(locale, "%.1f", value / 1_000)
  fun state(week: TrainingWeek, recorded: Boolean) = when {
    week.position(today) == PlanWeekPosition.CURRENT -> BarState.Current
    recorded -> BarState.Done
    else -> BarState.Planned
  }
  val longs = plan.weeks.mapNotNull { week ->
    val candidates = week.workouts.filter { it.workout.type == WorkoutType.LONG_RUN ||
      (it.originalWorkout.type == WorkoutType.LONG_RUN && (metadata[it.workout.id]?.actual != null || it.executionResult != null)) }
    val actualEntries = candidates.filter { metadata[it.workout.id]?.actual != null || it.executionResult != null }
    val recorded = week.position(today) == PlanWeekPosition.PAST && actualEntries.isNotEmpty()
    val values = (if (recorded) actualEntries else candidates).map { entry ->
      if (recorded) {
        val actual = metadata[entry.workout.id]?.actual
        if (actual != null) actual.distanceMeters else entry.executionResult?.actualDistanceMeters
      } else WorkoutEstimateCalculator.calculate(entry.workout).estimatedTotalDistanceMeters
    }
    if (values.isEmpty() || values.any { it == null || !it.isFinite() || it <= 0 }) return@mapNotNull null
    val distance = values.filterNotNull().max()
    val name = "W${week.weekIndex + 1}"
    longDescriptions += "$name: ${label(distance)} km ${if (recorded) "recorded" else "planned"}"
    ChartBar(name, (distance / 1_000).toFloat(), label(distance), state(week, recorded))
  }.toMutableList()
  if (longs.isNotEmpty() && goalDistanceMeters != null && goalDistanceMeters.isFinite() && goalDistanceMeters > 0 && completionDate != null) {
    val name = completionDate.format(DateTimeFormatter.ofPattern("MMM d", locale))
    longs += ChartBar(name, (goalDistanceMeters / 1_000).toFloat(), label(goalDistanceMeters), BarState.Goal)
    longDescriptions += "$name: ${label(goalDistanceMeters)} km goal target"
  }
  val weekly = plan.weeks.mapNotNull { week ->
    val summary = week.summary(today, metadata)
    val recorded = week.position(today) == PlanWeekPosition.PAST && summary.recordedSessions > 0
    val value = (if (recorded) summary.actualDistanceMeters else summary.plannedDistanceMeters)
      ?.takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
    val name = "W${week.weekIndex + 1}"
    weekDescriptions += "$name: ${label(value)} km ${if (recorded) "recorded" else "planned"}"
    ChartBar(name, (value / 1_000).toFloat(), label(value), state(week, recorded))
  }
  return PlanCharts(longs, weekly, "Long runs. ${longDescriptions.joinToString(". ")}",
    "Weekly distance. ${weekDescriptions.joinToString(". ")}")
}
