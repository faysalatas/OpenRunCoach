package com.example.openruncoach.domain.plan

import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.simpleDurationWorkout
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import org.junit.Assert.*
import org.junit.Test

class TrainingSchedulePolicyTest {
  private val clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC)
  private val monday = LocalDate.now(clock)
  private val policy = TrainingSchedulePolicy()

  @Test fun twoThreeAndFourAvailableDaysUseOnlyThoseDaysWithoutDoublingUp() {
    listOf(
      setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY),
      setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY),
      setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY),
    ).forEach { days ->
      val result = policy.schedule(monday, proposals(), TrainingAvailability(days))
      assertEquals(days.size, result.workouts.size)
      assertTrue(result.workouts.all { it.workout.date.dayOfWeek in days })
      assertEquals(result.workouts.size, result.workouts.map { it.workout.date }.distinct().size)
      assertSpacing(result)
      assertEquals(result, policy.schedule(monday, proposals(), TrainingAvailability(days)))
    }
  }

  @Test fun adjacentOnlyDaysOmitQualityInsteadOfCrowdingDemandingSessions() {
    val result = policy.schedule(monday, proposals(), TrainingAvailability(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
    assertEquals(2, result.workouts.size)
    assertTrue(result.workouts.any { it.workout.type == WorkoutType.LONG_RUN })
    assertFalse(result.workouts.any { it.workout.type == WorkoutType.THRESHOLD })
    assertTrue(result.reasons.any { it.contains("2 of 4") && it.contains("not redistributed") })
    assertTrue(result.reasons.any { it.contains("Threshold") })
  }

  @Test fun nonPreferredAvailableDayRemainsUsableForSafeQualitySpacing() {
    val days = TrainingAvailability(
      setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
      setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    )
    val hardOnly = listOf(entry("quality", 3, WorkoutType.THRESHOLD), entry("long", 6, WorkoutType.LONG_RUN))
    val result = policy.schedule(monday, hardOnly, days)
    assertEquals(2, result.workouts.size)
    assertTrue(result.workouts.any { it.workout.date.dayOfWeek == DayOfWeek.MONDAY })
    assertSpacing(result)
  }

  @Test fun preferenceWinsOverOriginalDateWhenBothAreSafe() {
    val days = TrainingAvailability(DayOfWeek.entries.toSet(), setOf(DayOfWeek.FRIDAY))
    val result = policy.schedule(monday, listOf(entry("easy", 1, WorkoutType.EASY_RUN)), days)
    assertEquals(DayOfWeek.FRIDAY, result.workouts.single().workout.date.dayOfWeek)
  }

  @Test fun continuationUsesTheSamePolicyAndRespectsBothRetainedBoundaries() {
    val before = entry("completed", -1, WorkoutType.LONG_RUN).workout
    val after = entry("saved-future", 7, WorkoutType.THRESHOLD).workout
    val saved = listOf(before, after)
    val days = TrainingAvailability(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY))
    val result = policy.schedule(monday, listOf(entry("new-quality", 1, WorkoutType.THRESHOLD)), days, saved)
    assertEquals(monday.plusDays(2), result.workouts.single().workout.date)
    assertEquals(listOf(before, after), saved)
    assertEquals(result, policy.schedule(monday, listOf(entry("new-quality", 1, WorkoutType.THRESHOLD)), days, saved))
  }

  @Test fun retainedHardSessionCanMakeSpacingImpossibleWithoutMovingHistory() {
    val history = listOf(entry("completed", -1, WorkoutType.LONG_RUN).workout)
    val result = policy.schedule(monday, listOf(entry("hard", 1, WorkoutType.THRESHOLD)),
      TrainingAvailability(setOf(DayOfWeek.MONDAY)), history)
    assertEquals(WorkoutType.REST, result.workouts.single().workout.type)
    assertEquals(monday.minusDays(1), history.single().date)
    assertTrue(result.reasons.any { it.contains("0 of 1") })
  }

  @Test fun noAvailabilityPreservesLegacyProposalsExactly() {
    val proposals = proposals()
    assertEquals(proposals, policy.schedule(monday, proposals, null).workouts)
  }

  @Test fun explicitEmptyAvailabilityProducesRestAndResetIsDifferent() {
    val result = policy.schedule(monday, proposals(), TrainingAvailability(emptySet()))
    assertTrue(result.workouts.all { it.workout.type == WorkoutType.REST && it.workout.blocks.isEmpty() })
    assertEquals(4, policy.schedule(monday, proposals(), null).workouts.size)
  }

  @Test fun completionDateIsPinnedAndProtectedAcrossTheWeekBoundary() {
    val date = monday
    val completion = entry("completion", 0, WorkoutType.LONG_RUN).let {
      val workout = it.workout.copy(purpose = WorkoutPurpose.GOAL_COMPLETION)
      it.copy(workout = workout, originalWorkout = workout)
    }
    val result = policy.schedule(monday, listOf(completion, entry("easy", 1, WorkoutType.EASY_RUN)),
      TrainingAvailability(DayOfWeek.entries.toSet()), completionDate = date)
    assertEquals(date, result.workouts.single().workout.date)
    val preceding = policy.schedule(monday.minusWeeks(1), listOf(entry("preceding-long", -1, WorkoutType.LONG_RUN)),
      TrainingAvailability(setOf(DayOfWeek.SUNDAY)), completionDate = date)
    assertEquals(WorkoutType.REST, preceding.workouts.single().workout.type)
  }

  @Test fun unavailableCompletionDateSurfacesConflictInsteadOfMovingIt() {
    val exception = assertThrows(CompletionAvailabilityConflict::class.java) {
      policy.schedule(monday, proposals(), TrainingAvailability(setOf(DayOfWeek.SATURDAY)), completionDate = monday)
    }
    assertEquals(monday, exception.date)
  }

  @Test fun availabilityChangesOnlyAffectExplicitNewScheduling() {
    val saved = policy.schedule(monday, proposals(), TrainingAvailability(setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)))
    val savedDates = saved.workouts.map { it.workout.date }
    val changed = policy.schedule(monday, proposals(), TrainingAvailability(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)))
    assertEquals(savedDates, saved.workouts.map { it.workout.date })
    assertNotEquals(savedDates, changed.workouts.map { it.workout.date })
    changed.workouts.forEach { assertEquals(it.workout, it.originalWorkout) }
  }

  @Test fun dayTransitionsKeepPreferredDaysInsideAvailableDays() {
    val initial = TrainingAvailability(emptySet())
    val preferred = initial.withDay(DayOfWeek.MONDAY, RunningDayPreference.PREFERRED)
    assertEquals(setOf(DayOfWeek.MONDAY), preferred.availableDays)
    assertEquals(setOf(DayOfWeek.MONDAY), preferred.preferredDays)
    val available = preferred.withDay(DayOfWeek.MONDAY, RunningDayPreference.AVAILABLE)
    assertTrue(available.preferredDays.isEmpty())
    assertTrue(available.withDay(DayOfWeek.MONDAY, RunningDayPreference.UNAVAILABLE).availableDays.isEmpty())
  }

  private fun proposals() = listOf(
    entry("easy", 1, WorkoutType.EASY_RUN), entry("quality", 3, WorkoutType.THRESHOLD),
    entry("recovery", 5, WorkoutType.RECOVERY_RUN), entry("long", 6, WorkoutType.LONG_RUN),
  )

  private fun entry(id: String, offset: Long, type: WorkoutType): PlannedWorkoutEntry {
    val workout = simpleDurationWorkout(id, monday.plusDays(offset), type, 30, 3, 4, clock.instant())
    return PlannedWorkoutEntry(WeeklyWorkoutSlot.EARLY_WEEK, workout, "Fixture")
  }

  private fun assertSpacing(result: ScheduledWeek) {
    result.workouts.map { it.workout }.filter { it.isDemandingForScheduling() }.sortedBy { it.date }
      .zipWithNext().forEach { (first, second) -> assertTrue(ChronoUnit.DAYS.between(first.date, second.date) >= 2) }
  }
}
