package com.example.openruncoach.domain.plan

import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class ScheduledWeek(val workouts: List<PlannedWorkoutEntry>, val reasons: List<String>)

/** Shared placement contract for generation and continuation. Only pass NEW prescriptions.
 * Retained workouts on either side are immutable constraints, including across week/horizon boundaries.
 * Search is bounded by the generator's four weekly sessions and seven calendar days.
 */
class TrainingSchedulePolicy(private val minimumDemandingSpacingDays: Int = 2) {
  init { require(minimumDemandingSpacingDays >= 2) }

  fun schedule(
    weekStart: LocalDate,
    proposals: List<PlannedWorkoutEntry>,
    availability: TrainingAvailability?,
    retainedWorkouts: List<StructuredWorkout> = emptyList(),
    completionDate: LocalDate? = null,
  ): ScheduledWeek {
    availability.requireCompletionDateAvailable(completionDate)
    require(proposals.isNotEmpty())
    if (availability == null && retainedWorkouts.isEmpty()) return ScheduledWeek(proposals, emptyList())
    require(proposals.size <= 5) { "Schedule one generated week at a time." }
    val running = proposals.filter { it.workout.type != WorkoutType.REST }
      .sortedWith(compareBy<PlannedWorkoutEntry> { it.workout.date }.thenBy { it.workout.id })
    val days = (0L..6L).map(weekStart::plusDays)
      .filter { availability == null || it.dayOfWeek in availability.availableDays }
    val retained = retainedWorkouts.filter { it.type != WorkoutType.REST }
    var best = emptyList<PlannedWorkoutEntry>()
    var bestScore: List<Long>? = null

    fun compatible(workout: StructuredWorkout, date: LocalDate, placed: List<PlannedWorkoutEntry>): Boolean {
      if (date !in days) return false
      val pinned = completionDate != null && workout.date == completionDate && workout.isMilestone()
      if (pinned && date != completionDate) return false
      if (completionDate != null && !pinned) {
        val offset = ChronoUnit.DAYS.between(completionDate, date)
        if (offset == 0L || offset == -1L) return false
        if (offset > 0 && workout.type != WorkoutType.RECOVERY_RUN) return false
        if (workout.isDemandingForScheduling() && abs(offset) <= 2) return false
      }
      return (retained + placed.map { it.workout }).none { other ->
        val spacing = abs(ChronoUnit.DAYS.between(other.date, date))
        spacing == 0L || (workout.isDemandingForScheduling() && other.isDemandingForScheduling() &&
          spacing < if (pinned || other.date == completionDate && other.isMilestone() ||
            workout.purpose == WorkoutPurpose.GOAL_COMPLETION || other.purpose == WorkoutPurpose.GOAL_COMPLETION
          ) maxOf(3, minimumDemandingSpacingDays) else minimumDemandingSpacingDays)
      }
    }

    fun score(placed: List<PlannedWorkoutEntry>): List<Long> = listOf(
      placed.count { it.workout.isMilestone() && it.workout.date == completionDate }.toLong(),
      placed.size.toLong(),
      placed.count { it.workout.type == WorkoutType.LONG_RUN }.toLong(),
      // Prefer aerobic work when constrained days cannot safely fit the requested quality work.
      placed.count { !it.workout.isDemandingForScheduling() }.toLong(),
      placed.count { availability != null && it.workout.date.dayOfWeek in availability.preferredDays }.toLong(),
      -placed.sumOf { entry -> abs(ChronoUnit.DAYS.between(running.single { it.workout.id == entry.workout.id }.workout.date, entry.workout.date)) },
    )

    fun better(candidate: List<Long>, previous: List<Long>?): Boolean {
      if (previous == null) return true
      candidate.indices.forEach { index -> if (candidate[index] != previous[index]) return candidate[index] > previous[index] }
      return false
    }

    fun search(index: Int, placed: List<PlannedWorkoutEntry>) {
      if (index == running.size) {
        val candidate = score(placed)
        if (better(candidate, bestScore)) { best = placed; bestScore = candidate }
        return
      }
      val entry = running[index]
      val candidates = if (availability == null || entry.workout.isMilestone() && entry.workout.date == completionDate) {
        listOf(entry.workout.date)
      } else days
      candidates.forEach { date ->
        if (compatible(entry.workout, date, placed)) {
          val moved = entry.workout.copy(date = date)
          search(index + 1, placed + entry.copy(workout = moved, originalWorkout = moved))
        }
      }
      search(index + 1, placed)
    }
    search(0, emptyList())
    val pinned = running.singleOrNull { it.workout.isMilestone() && it.workout.date == completionDate }
    require(pinned == null || best.any { it.workout.id == pinned.workout.id }) {
      "Completion Run on $completionDate conflicts with retained training. Explicitly select another date."
    }
    val rests = proposals.filter { it.workout.type == WorkoutType.REST }
      .filter { rest -> best.none { it.workout.date == rest.workout.date } }
    val reasons = buildList {
      if (availability != null) add("Running days are athlete constraints; preferred days are used when safe, and other available days remain usable.")
      if (best.size < running.size) {
        add("Scheduled ${best.size} of ${running.size} requested runs: available days, retained training and demanding-session recovery spacing limit safe frequency. Omitted volume is not redistributed.")
        add("Omitted: " + running.filter { entry -> best.none { it.workout.id == entry.workout.id } }.joinToString { it.workout.name } + ".")
      }
    }
    val scheduled = (best + rests).ifEmpty {
      val template = proposals.first()
      val rest = template.workout.copy(
        id = "${template.workout.id}:availability-rest", date = weekStart,
        type = WorkoutType.REST, name = "Rest", purpose = WorkoutPurpose.RECOVERY, blocks = emptyList(),
        explanation = "No running prescription fits the available days and recovery spacing.",
        cues = listOf("Update running days in Settings when your availability changes."),
      )
      listOf(template.copy(workout = rest, originalWorkout = rest, generationReason = rest.explanation))
    }
    return ScheduledWeek(scheduled.sortedBy { it.workout.date }, reasons)
  }
}

fun StructuredWorkout.isDemandingForScheduling(): Boolean = type in setOf(
  WorkoutType.LONG_RUN, WorkoutType.TEMPO_RUN, WorkoutType.THRESHOLD, WorkoutType.INTERVALS,
  WorkoutType.RACE_SPECIFIC, WorkoutType.STRIDES,
) || purpose in setOf(WorkoutPurpose.GOAL_COMPLETION, WorkoutPurpose.RACE_SPECIFIC)

private fun StructuredWorkout.isMilestone(): Boolean = purpose in setOf(WorkoutPurpose.GOAL_COMPLETION, WorkoutPurpose.RACE_SPECIFIC)
