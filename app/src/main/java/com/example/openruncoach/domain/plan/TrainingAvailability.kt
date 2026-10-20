package com.example.openruncoach.domain.plan

import java.time.DayOfWeek
import java.time.LocalDate

/** Athlete-owned calendar constraints, never a performance or readiness signal.
 * Null settings mean legacy scheduling; an empty available set explicitly means no running.
 */
data class TrainingAvailability(
  val availableDays: Set<DayOfWeek>,
  val preferredDays: Set<DayOfWeek> = emptySet(),
) {
  init { require(availableDays.containsAll(preferredDays)) }

  fun withDay(day: DayOfWeek, preference: RunningDayPreference): TrainingAvailability = copy(
    availableDays = if (preference == RunningDayPreference.UNAVAILABLE) availableDays - day else availableDays + day,
    preferredDays = if (preference == RunningDayPreference.PREFERRED) preferredDays + day else preferredDays - day,
  )

  fun preference(day: DayOfWeek): RunningDayPreference = when (day) {
    in preferredDays -> RunningDayPreference.PREFERRED
    in availableDays -> RunningDayPreference.AVAILABLE
    else -> RunningDayPreference.UNAVAILABLE
  }
}

enum class RunningDayPreference { UNAVAILABLE, AVAILABLE, PREFERRED }

class CompletionAvailabilityConflict(val date: LocalDate) : IllegalArgumentException(
  "Completion Run on $date conflicts with an unavailable running day. Change availability or explicitly select another date."
)

fun TrainingAvailability?.requireCompletionDateAvailable(date: LocalDate?) {
  completionDateConflict(date)?.let { throw it }
}

fun TrainingAvailability?.completionDateConflict(date: LocalDate?): CompletionAvailabilityConflict? =
  if (this != null && date != null && date.dayOfWeek !in availableDays) CompletionAvailabilityConflict(date) else null
