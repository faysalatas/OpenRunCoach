package com.example.openruncoach.ui.plan

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
