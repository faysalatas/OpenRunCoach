package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutType
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class GoalCompletionReadiness {
  GOAL_COMPLETION_NOT_READY,
  GOAL_COMPLETION_READY,
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
    require(workoutId == null || scheduledDate != null)
    require(reasons.isNotEmpty())
  }
}

data class CompletionDateSuggestion(
  val date: LocalDate,
  val recommended: Boolean,
  val reasons: List<String>,
)

enum class CompletionDateFeasibility { INSUFFICIENT_EVIDENCE, AGGRESSIVE, REASONABLE, CONSERVATIVE }

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
    val continuity = profile.recentContinuity
    val longest = continuity.longestContinuousDistanceMeters ?: profile.longestRunLast28Days?.distanceMeters
    val benchmark = profile.goalBenchmark(goal.distance)?.personalBest
    val hasReliableBenchmark = benchmark?.confidence?.let { it >= ProfileConfidence.MEDIUM } == true
    val averageRpe = profile.feedback.averageRpe
    val completedFraction = profile.feedback.completedFraction
    val availableWeeks = ChronoUnit.DAYS.between(today, date).coerceAtLeast(0) / 7.0
    if (longest == null || longest <= 0.0 ||
      profile.profileConfidence == ProfileConfidence.VERY_LOW ||
      (profile.profileConfidence == ProfileConfidence.LOW && !hasReliableBenchmark) ||
      (continuity.interruptedRuns > 0 && continuity.continuousRuns == 0)
    ) {
      return CompletionDateAssessment(
        CompletionDateFeasibility.INSUFFICIENT_EVIDENCE,
        if (continuity.interruptedRuns > 0 && continuity.continuousRuns == 0) {
          "This date may be achievable, but recent samples include sustained walking or stopped intervals and continuous ${goal.distance.displayName} tolerance has not yet been demonstrated."
        } else {
          "This date may be achievable, but confidence is low because continuous ${goal.distance.displayName} tolerance has not yet been demonstrated."
        },
      )
    }
    val distanceGapFraction = ((goal.distanceMeters - longest) / goal.distanceMeters).coerceAtLeast(0.0)
    val neededWeeks = ceil(distanceGapFraction / 0.08).coerceAtLeast(1.0)
    val frequencySupported = profile.averageRunsPerWeek >= 2.0 &&
      profile.runsLast28Days >= 4 && profile.activeWeeksLast8Weeks >= 3
    val volumeSupported = profile.averageWeeklyDistanceLast4WeeksMeters >= goal.distanceMeters * 1.5
    val responseSupported = averageRpe?.let { it <= 6.5 } == true &&
      completedFraction?.let { it >= 0.75 } == true
    val continuitySupported = continuity.interruptedRuns == 0 || continuity.continuousRuns > continuity.interruptedRuns
    return when {
      availableWeeks < neededWeeks || !frequencySupported || !volumeSupported || !responseSupported || !continuitySupported -> {
        CompletionDateAssessment(
          CompletionDateFeasibility.AGGRESSIVE,
          "This date is aggressive for the current continuous endurance, recent volume, training frequency, and workout response.",
        )
      }
      availableWeeks >= neededWeeks + 2.0 && (hasReliableBenchmark || longest >= goal.distanceMeters * 0.95) -> {
        CompletionDateAssessment(
          CompletionDateFeasibility.CONSERVATIVE,
          "This date leaves additional training time beyond the current evidence-based progression estimate.",
        )
      }
      else -> {
        CompletionDateAssessment(
          CompletionDateFeasibility.REASONABLE,
          "This date is reasonable for the demonstrated endurance, recent volume, training frequency, and workout response.",
        )
      }
    }
  }
}

class GoalCompletionReadinessEvaluator {
  fun evaluate(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    current: GoalCompletionMilestone?,
    now: Instant,
  ): GoalCompletionMilestone {
    val continuity = profile.recentContinuity
    val longest = continuity.longestContinuousDistanceMeters ?: profile.longestRunLast28Days?.distanceMeters
    val enduranceReady = longest?.let { it.isFinite() && it >= goal.distanceMeters * 0.90 } == true
    val consistencyReady = profile.runsLast28Days >= 3 && profile.activeWeeksLast8Weeks >= 2
    val completedFraction = profile.feedback.completedFraction
    val averageRpe = profile.feedback.averageRpe
    val significantPainFrequency = profile.feedback.significantPainFrequency
    val completionReady = completedFraction?.let { it >= 0.75 } == true
    val effortReady = averageRpe?.let { it <= 6.5 } == true
    val painReady = significantPainFrequency?.let { it <= 0.10 } == true
    val benchmarkConfidence = profile.goalBenchmark(goal.distance)?.personalBest?.confidence
    val confidenceReady = profile.profileConfidence >= ProfileConfidence.MEDIUM ||
      benchmarkConfidence?.let { it >= ProfileConfidence.MEDIUM } == true
    val continuityReady = continuity.interruptedRuns == 0 || continuity.continuousRuns > continuity.interruptedRuns
    val performanceReady = enduranceReady && consistencyReady && completionReady && effortReady && painReady && confidenceReady && continuityReady
    val evidenceReasons = buildList {
      add(if (enduranceReady) "Recent endurance is within 10% of ${goal.distance.displayName}." else "Recent continuous distance has not yet reached the readiness range for ${goal.distance.displayName}.")
      add(if (consistencyReady) "Recent training frequency and active weeks support an attempt." else "More consistent recent running is needed before the attempt.")
      if (!confidenceReady) add("Current profile and benchmark confidence are too limited to mark the attempt ready.")
      if (!continuityReady) add("Recent samples show too much sustained walking or stopped time to mark a continuous attempt ready.")
      if (!completionReady) add(
        if (completedFraction == null) "Not enough post-run feedback is available to assess workout completion."
        else "Recent workout completion is too low for a goal attempt."
      )
      if (!effortReady) add(
        if (averageRpe == null) "Not enough RPE feedback is available to assess workout response."
        else "More completed runs with controlled RPE are needed before the attempt."
      )
      if (!painReady) add(
        if (significantPainFrequency == null) "Not enough pain feedback is available to assess goal-attempt readiness."
        else "Recent significant pain reports make postponement advisable."
      )
      if (current?.scheduledDate != null) add(
        if (performanceReady) "Current evidence supports attempting the planned Completion Run."
        else "The planned Completion Run remains a calendar milestone; it does not make the athlete ready."
      )
    }

    val readiness = if (performanceReady) {
      GoalCompletionReadiness.GOAL_COMPLETION_READY
    } else {
      GoalCompletionReadiness.GOAL_COMPLETION_NOT_READY
    }
    val preserveDismissal = readiness == current?.readiness && current.promptDismissed
    return GoalCompletionMilestone(
      goalId = goal.id,
      readiness = readiness,
      scheduledDate = current?.scheduledDate,
      promptDismissed = preserveDismissal,
      reasons = evidenceReasons,
      updatedAt = now,
      planId = current?.planId,
      workoutId = current?.workoutId,
    )
  }
}

class GoalCompletionMilestoneService(private val clock: Clock = Clock.systemDefaultZone()) {
  fun schedule(goalId: String, current: GoalCompletionMilestone, date: LocalDate): GoalCompletionMilestone {
    require(current.goalId == goalId) {
      "Stale Completion Run milestone: requested goal $goalId but milestone belongs to ${current.goalId}."
    }
    require(!date.isBefore(LocalDate.now(clock))) { "Completion Run date cannot be in the past." }
    return current.copy(
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
    return current.copy(
      scheduledDate = null,
      workoutId = null,
      promptDismissed = true,
      reasons = current.reasons + "The athlete chose to continue training and select a date later.",
      updatedAt = clock.instant(),
    )
  }

  fun cancelForCompletedGoal(current: GoalCompletionMilestone): GoalCompletionMilestone = current.copy(
    scheduledDate = null,
    workoutId = null,
    promptDismissed = false,
    reasons = current.reasons + "The goal was completed by an observed qualifying run, so the future milestone is obsolete.",
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
    goal: TrainingGoal? = null,
    profile: AthletePerformanceProfile? = null,
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
      .map { date ->
        val assessment = if (goal != null && profile != null) {
          CompletionDateFeasibilityEvaluator().evaluate(goal, profile, date, today)
        } else null
        date to assessment
      }
      .sortedWith(
        compareBy<Pair<LocalDate, CompletionDateAssessment?>> { (_, assessment) -> assessment.feasibilityRank() }
          .thenByDescending { (date, _) -> date.dayOfWeek == usualLongRunDay }
          .thenByDescending { (date, _) -> date.dayOfWeek in normalTrainingDays }
          .thenByDescending { (date, _) -> date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
          .thenBy { (date, _) -> date },
      )
      .take(3)
      .toList()
    return candidates.mapIndexed { index, (date, assessment) ->
      CompletionDateSuggestion(
        date = date,
        recommended = index == 0,
        reasons = buildList {
          assessment?.let {
            add(it.feasibility.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase))
            add(it.explanation)
          }
          if (date.dayOfWeek == usualLongRunDay) add("Your normal Long Run day")
          else if (date.dayOfWeek in normalTrainingDays) add("Matches a normal training day")
          else add("Fits between planned training days")
          add("Good recovery spacing from demanding workouts")
        },
      )
    }
  }
}

private fun CompletionDateAssessment?.feasibilityRank(): Int = when (this?.feasibility) {
  CompletionDateFeasibility.CONSERVATIVE -> 0
  CompletionDateFeasibility.REASONABLE -> 1
  CompletionDateFeasibility.AGGRESSIVE -> 2
  CompletionDateFeasibility.INSUFFICIENT_EVIDENCE -> 3
  null -> 0
}

private fun StructuredWorkout.isDemanding(): Boolean =
  type in setOf(WorkoutType.LONG_RUN, WorkoutType.TEMPO_RUN, WorkoutType.THRESHOLD, WorkoutType.INTERVALS, WorkoutType.RACE_SPECIFIC) ||
    purpose == WorkoutPurpose.GOAL_COMPLETION
