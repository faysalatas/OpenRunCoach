package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutType
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class GoalCompletionReadiness {
  GOAL_COMPLETION_NOT_READY,
  GOAL_COMPLETION_READY,
  GOAL_COMPLETION_SCHEDULED,
  GOAL_COMPLETED,
}

data class GoalCompletionMilestone(
  val goalId: String,
  val readiness: GoalCompletionReadiness,
  val scheduledDate: LocalDate?,
  val promptDismissed: Boolean,
  val reasons: List<String>,
  val updatedAt: Instant,
  val planId: String? = null,
  val workoutId: String? = null,
) {
  init {
    require(goalId.isNotBlank())
    require(planId == null || planId.isNotBlank())
    require(workoutId == null || workoutId.isNotBlank())
    require((readiness == GoalCompletionReadiness.GOAL_COMPLETION_SCHEDULED) == (scheduledDate != null))
    require(reasons.isNotEmpty())
  }
}

data class CompletionDateSuggestion(
  val date: LocalDate,
  val recommended: Boolean,
  val reasons: List<String>,
)

enum class CompletionDateFeasibility { FEASIBLE, AGGRESSIVE, INSUFFICIENT_DATA }

data class CompletionDateAssessment(
  val feasibility: CompletionDateFeasibility,
  val explanation: String,
)

class CompletionDateFeasibilityEvaluator {
  fun evaluate(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    date: LocalDate,
    today: LocalDate,
  ): CompletionDateAssessment {
    val longest = profile.longestRunLast28Days?.distanceMeters
    if (longest == null || longest <= 0.0 || profile.profileConfidence == com.example.openruncoach.domain.profile.ProfileConfidence.VERY_LOW) {
      return CompletionDateAssessment(
        CompletionDateFeasibility.INSUFFICIENT_DATA,
        "There is not enough recent training data to estimate this date reliably.",
      )
    }
    val availableWeeks = (ChronoUnit.DAYS.between(today, date).coerceAtLeast(0) / 7.0)
    val distanceRatio = goal.distanceMeters / longest
    val consistent = profile.runsLast28Days >= 3 && profile.activeWeeksLast8Weeks >= 2
    val neededWeeks = ((distanceRatio - 1.0).coerceAtLeast(0.0) / 0.08).coerceAtLeast(1.0)
    return if (consistent && (longest >= goal.distanceMeters * 0.9 || availableWeeks >= neededWeeks)) {
      CompletionDateAssessment(
        CompletionDateFeasibility.FEASIBLE,
        "This date is consistent with the available endurance and training history.",
      )
    } else {
      CompletionDateAssessment(
        CompletionDateFeasibility.AGGRESSIVE,
        "This date may be ambitious based on current endurance and training consistency.",
      )
    }
  }
}

class GoalCompletionReadinessEvaluator {
  fun evaluate(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    current: GoalCompletionMilestone?,
    now: Instant,
    completionResult: GoalCompletionResult? = null,
  ): GoalCompletionMilestone {
    if (goal.status == GoalStatus.COMPLETED || current?.readiness == GoalCompletionReadiness.GOAL_COMPLETED || completionResult?.completed == true) {
      return milestone(goal, GoalCompletionReadiness.GOAL_COMPLETED, null, false, listOf("The selected goal distance has been completed."), now)
    }
    val longest = profile.longestRunLast28Days?.distanceMeters
    val enduranceReady = longest?.let { it.isFinite() && it >= goal.distanceMeters * 0.90 } == true
    val consistencyReady = profile.runsLast28Days >= 3 && profile.activeWeeksLast8Weeks >= 2
    val completionReady = profile.feedback.completedFraction?.let { it >= 0.75 } ?: true
    val painReady = profile.feedback.significantPainFrequency?.let { it <= 0.10 } ?: true
    val performanceReady = enduranceReady && consistencyReady && completionReady && painReady
    val evidenceReasons = buildList {
      add(if (enduranceReady) "Recent endurance is within 10% of ${goal.distance.displayName}." else "Recent continuous distance has not yet reached the readiness range for ${goal.distance.displayName}.")
      add(if (consistencyReady) "Recent training frequency and active weeks support an attempt." else "More consistent recent running is needed before the attempt.")
      if (!completionReady) add("Recent workout completion is too low for a goal attempt.")
      if (!painReady) add("Recent significant pain reports make postponement advisable.")
    }

    if (current?.readiness == GoalCompletionReadiness.GOAL_COMPLETION_SCHEDULED) {
      return current.copy(
        reasons = evidenceReasons + if (performanceReady) {
          "The selected date remains supported by current performance evidence."
        } else {
          "New evidence suggests postponement. The selected date has not been cancelled."
        },
        updatedAt = now,
      )
    }

    val readiness = if (performanceReady) {
      GoalCompletionReadiness.GOAL_COMPLETION_READY
    } else {
      GoalCompletionReadiness.GOAL_COMPLETION_NOT_READY
    }
    val preserveDismissal = readiness == current?.readiness && current.promptDismissed
    return milestone(goal, readiness, null, preserveDismissal, evidenceReasons, now)
  }

  private fun milestone(
    goal: TrainingGoal,
    readiness: GoalCompletionReadiness,
    date: LocalDate?,
    dismissed: Boolean,
    reasons: List<String>,
    now: Instant,
  ) = GoalCompletionMilestone(goal.id, readiness, date, dismissed, reasons, now)
}

class GoalCompletionMilestoneService(private val clock: Clock = Clock.systemDefaultZone()) {
  fun schedule(goalId: String, current: GoalCompletionMilestone, date: LocalDate): GoalCompletionMilestone {
    require(current.goalId == goalId) {
      "Stale Completion Run milestone: requested goal $goalId but milestone belongs to ${current.goalId}."
    }
    require(current.readiness in setOf(
      GoalCompletionReadiness.GOAL_COMPLETION_NOT_READY,
      GoalCompletionReadiness.GOAL_COMPLETION_READY,
      GoalCompletionReadiness.GOAL_COMPLETION_SCHEDULED,
    )) { "The Completion Run is not ready to schedule." }
    require(!date.isBefore(LocalDate.now(clock))) { "Completion Run date cannot be in the past." }
    return current.copy(
      readiness = GoalCompletionReadiness.GOAL_COMPLETION_SCHEDULED,
      scheduledDate = date,
      workoutId = null,
      promptDismissed = false,
      reasons = current.reasons + "The athlete selected $date as the Completion Run date.",
      updatedAt = clock.instant(),
    )
  }

  fun schedule(current: GoalCompletionMilestone, date: LocalDate): GoalCompletionMilestone =
    schedule(current.goalId, current, date)

  fun postpone(current: GoalCompletionMilestone): GoalCompletionMilestone {
    require(current.readiness in setOf(
      GoalCompletionReadiness.GOAL_COMPLETION_READY,
      GoalCompletionReadiness.GOAL_COMPLETION_SCHEDULED,
    )) { "The Completion Run is not available to postpone." }
    return current.copy(
      readiness = GoalCompletionReadiness.GOAL_COMPLETION_READY,
      scheduledDate = null,
      workoutId = null,
      promptDismissed = true,
      reasons = current.reasons + "The athlete chose to continue training and select a date later.",
      updatedAt = clock.instant(),
    )
  }

  fun complete(current: GoalCompletionMilestone): GoalCompletionMilestone = current.copy(
    readiness = GoalCompletionReadiness.GOAL_COMPLETED,
    scheduledDate = null,
    workoutId = null,
    promptDismissed = false,
    reasons = current.reasons + "The canonical completed run demonstrated the goal distance.",
    updatedAt = clock.instant(),
  )
}

class GoalCompletionDateSuggester(
  private val minimumLeadDays: Long = 3,
  private val searchDays: Long = 21,
  private val hardWorkoutSpacingDays: Long = 2,
) {
  fun suggest(
    today: LocalDate,
    plannedWorkouts: List<StructuredWorkout>,
    availableDays: Set<DayOfWeek>? = null,
  ): List<CompletionDateSuggestion> {
    val usualLongRunDay = plannedWorkouts.filter { it.type == WorkoutType.LONG_RUN }
      .groupingBy { it.date.dayOfWeek }.eachCount().maxByOrNull { it.value }?.key
    val normalTrainingDays = plannedWorkouts.groupingBy { it.date.dayOfWeek }.eachCount().keys
    val demandingWorkouts = plannedWorkouts.filter { it.isDemanding() }
    val candidates = (minimumLeadDays..searchDays).asSequence()
      .map(today::plusDays)
      .filter { availableDays == null || it.dayOfWeek in availableDays }
      .filter { candidate ->
        demandingWorkouts.none { workout ->
          val replacesLongRun = workout.type == WorkoutType.LONG_RUN && workout.date == candidate
          !replacesLongRun && kotlin.math.abs(ChronoUnit.DAYS.between(workout.date, candidate)) <= hardWorkoutSpacingDays
        }
      }
      .sortedWith(
        compareByDescending<LocalDate> { it.dayOfWeek == usualLongRunDay }
          .thenByDescending { it.dayOfWeek in normalTrainingDays }
          .thenByDescending { it.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
          .thenBy { it },
      )
      .take(3)
      .toList()
    return candidates.mapIndexed { index, date ->
      CompletionDateSuggestion(
        date = date,
        recommended = index == 0,
        reasons = buildList {
          if (date.dayOfWeek == usualLongRunDay) add("Your normal Long Run day")
          else if (date.dayOfWeek in normalTrainingDays) add("Matches a normal training day")
          else add("Fits between planned training days")
          add("Good recovery spacing from demanding workouts")
        },
      )
    }
  }
}

private fun StructuredWorkout.isDemanding(): Boolean =
  type in setOf(WorkoutType.LONG_RUN, WorkoutType.TEMPO_RUN, WorkoutType.THRESHOLD, WorkoutType.INTERVALS, WorkoutType.RACE_SPECIFIC) ||
    purpose == WorkoutPurpose.GOAL_COMPLETION
