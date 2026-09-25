package com.example.openruncoach.domain.plan

import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalCompletionMilestone
import com.example.openruncoach.domain.goal.GoalCompletionReadiness
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.profile.TrainingConsistency
import com.example.openruncoach.domain.workout.DistanceCompletion
import com.example.openruncoach.domain.workout.DurationCompletion
import com.example.openruncoach.domain.workout.GuidanceConfidence
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.PlannedWorkout
import com.example.openruncoach.domain.workout.RepeatBlock
import com.example.openruncoach.domain.workout.RpeTarget
import com.example.openruncoach.domain.workout.SegmentCategory
import com.example.openruncoach.domain.workout.SingleSegmentBlock
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutOrigin
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutSegment
import com.example.openruncoach.domain.workout.WorkoutType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

data class PlanGenerationConfig(
  val defaultDetailedWeeks: Int = 4,
  val maxDetailedWeeks: Int = 12,
  val recoveryWeekFrequency: Int = 4,
  val veryLowConfidenceProgression: Double = 0.03,
  val lowConfidenceProgression: Double = 0.05,
  val mediumConfidenceProgression: Double = 0.07,
  val highConfidenceProgression: Double = 0.08,
  val recoveryVolumeFactor: Double = 0.80,
  val maximumLongRunIncreaseMeters: Double = 750.0,
  val lowConfidenceLongRunIncreaseFraction: Double = 0.04,
  val establishedLongRunIncreaseFraction: Double = 0.07,
  val minimumHardSessionSpacingDays: Int = 2,
  val beginnerHardSessionLimit: Int = 1,
  val establishedHardSessionLimit: Int = 2,
  val minimumEasyRunMeters: Double = 2_000.0,
  val minimumRecoveryRunMeters: Double = 1_500.0,
  val practicalDistanceIncrementMeters: Double = 250.0,
  val longRunRoundingMeters: Double = 100.0,
  val goalSessionCapabilityFraction: Double = 0.90,
  val defaultEasyDurationMinutes: Int = 30,
  val slotDayOffsets: Map<WeeklyWorkoutSlot, Long> = mapOf(
    WeeklyWorkoutSlot.EARLY_WEEK to 1L,
    WeeklyWorkoutSlot.MID_WEEK to 3L,
    WeeklyWorkoutSlot.LATE_WEEK to 5L,
    WeeklyWorkoutSlot.WEEKEND to 6L,
  ),
) {
  init {
    require(defaultDetailedWeeks in 1..maxDetailedWeeks)
    require(maxDetailedWeeks in 1..52)
    require(recoveryWeekFrequency >= 2)
    require(recoveryVolumeFactor in 0.5..0.95)
    require(minimumHardSessionSpacingDays >= 2)
    require(beginnerHardSessionLimit in 0..establishedHardSessionLimit)
    require(minimumEasyRunMeters > 0.0 && minimumRecoveryRunMeters > 0.0)
    require(practicalDistanceIncrementMeters > 0.0 && longRunRoundingMeters > 0.0)
    require(goalSessionCapabilityFraction in 0.5..1.0)
    require(slotDayOffsets.keys.containsAll(WeeklyWorkoutSlot.entries))
    require(
      slotDayOffsets.getValue(WeeklyWorkoutSlot.WEEKEND) -
        slotDayOffsets.getValue(WeeklyWorkoutSlot.MID_WEEK) >= minimumHardSessionSpacingDays
    )
  }
}

data class WorkoutEligibility(
  val thresholdEligible: Boolean,
  val intervalEligible: Boolean,
  val reasons: List<String>,
)

private enum class GoalSessionKind { COMPLETION, CONTROLLED, GOAL_ATTEMPT }

class TrainingPlanGenerator(
  private val clock: Clock = Clock.systemDefaultZone(),
  private val zoneId: ZoneId = clock.zone,
  private val config: PlanGenerationConfig = PlanGenerationConfig(),
  private val paceEngine: PacePrescriptionEngine = PacePrescriptionEngine(),
  private val validator: TrainingPlanValidator = TrainingPlanValidator(),
  private val sufficiencyEvaluator: TrainingDataSufficiencyEvaluator = TrainingDataSufficiencyEvaluator(),
) {
  fun generate(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    runMetricsHistory: List<RunMetrics> = emptyList(),
    completedAssessmentAttempts: Int = 0,
    completionMilestone: GoalCompletionMilestone? = null,
  ): TrainingPlan {
    val createdAt = clock.instant()
    val startDate = LocalDate.now(clock.withZone(zoneId))
    val planId = "plan:${goal.id}:${createdAt.toEpochMilli()}"
    val sufficiency = sufficiencyEvaluator.evaluate(
      profile = profile,
      goal = goal,
      runMetricsHistory = runMetricsHistory,
      feedbackAvailable = profile.feedback.feedbackCount > 0,
      completedAssessmentAttempts = completedAssessmentAttempts,
    )
    if (!sufficiency.canGenerateNormalPlan) {
      return assessmentPlan(planId, goal, profile, sufficiency, startDate, createdAt, completionMilestone)
    }
    val scheduledCompletionDate = completionMilestone
      ?.takeIf { it.readiness == GoalCompletionReadiness.GOAL_COMPLETION_SCHEDULED }
      ?.scheduledDate
      ?.takeUnless { it.isBefore(startDate) }
    val scheduledCompletionWeek = scheduledCompletionDate?.let {
      (ChronoUnit.DAYS.between(startDate, it) / 7).toInt()
    }
    val normalWeekCount = config.defaultDetailedWeeks
    val weekCount = if (scheduledCompletionWeek != null) {
      scheduledCompletionWeek + 1
    } else {
      normalWeekCount
    }
    val startingFrequency = startingFrequency(profile)
    val eligibility = qualityEligibility(profile)
    val volumeProgression = progressionFor(profile.profileConfidence)
    var progressiveWeek = 0
    var previousDistance = baselineDistance(profile)
    var previousLongRun = profile.longestRunLast28Days?.distanceMeters?.takeIf { it > 0.0 }
    var goalSessionScheduled = false
    val completionWeek = scheduledCompletionWeek
    val blockTypes = (0 until weekCount).map { weekIndex ->
      blockType(goal, profile, weekIndex, recoveryWeek(goal, weekIndex, completionWeek))
    }

    val weeks = (0 until weekCount).map { weekIndex ->
      val recovery = recoveryWeek(goal, weekIndex, completionWeek)
      val frequency = frequencyForWeek(startingFrequency, profile, weekIndex)
      val frequencyIncreased = frequency > frequencyForWeek(startingFrequency, profile, weekIndex - 1)
      if (!recovery) progressiveWeek++
      val progressionSteps = (progressiveWeek - 1 - if (frequencyIncreased) 1 else 0).coerceAtLeast(0)
      val buildFactor = 1.0 + volumeProgression * progressionSteps
      val plannedDistance = baselineDistance(profile)?.let { baseline ->
        if (recovery) (previousDistance ?: baseline) * config.recoveryVolumeFactor else baseline * buildFactor
      }?.coerceAtLeast(0.0)
      val longRun = longRunDistance(profile, previousLongRun, recovery, weekIndex)?.let { distance ->
        if (goal.goalType == GoalType.COMPLETE_DISTANCE && weekIndex != completionWeek) {
          min(distance, preGoalDistanceLimit(goal.distanceMeters))
        } else distance
      }
      val currentBlock = blockTypes[weekIndex]
      val goalSession = if (weekIndex == completionWeek) {
        when (goal.goalType) {
          GoalType.COMPLETE_DISTANCE -> GoalSessionKind.COMPLETION
          GoalType.IMPROVE_PERFORMANCE -> GoalSessionKind.CONTROLLED
          GoalType.TARGET_TIME -> GoalSessionKind.GOAL_ATTEMPT
        }
      } else if (goal.goalType == GoalType.COMPLETE_DISTANCE) {
        null
      } else {
        goalSessionFor(goal, profile, weekIndex, weekCount, recovery, currentBlock, goalSessionScheduled)
      }
      val qualityType = if (goalSession == null) {
        qualityWorkout(goal, profile, eligibility, weekIndex, recovery || frequencyIncreased, frequency)
      } else null
      val workouts = buildWorkouts(
        planId, goal, profile, weekIndex, startDate.plusWeeks(weekIndex.toLong()), frequency,
        qualityType, longRun, plannedDistance, recovery, currentBlock, goalSession,
        scheduledCompletionDate.takeIf { goalSession == GoalSessionKind.COMPLETION }, createdAt,
      )
      if (goalSession != null) goalSessionScheduled = true
      val estimates = workouts.map { WorkoutEstimateCalculator.calculate(it.workout) }
      val prescribedDistance = estimates.sumOf { it.knownDistanceMeters }.takeIf { it > 0.0 }
      val expectedDuration = estimates.map { it.estimatedTotalDurationSeconds }
        .takeIf { values -> values.all { it != null } }
        ?.sumOf { requireNotNull(it) }
      val reasons = weekReasons(
        goal, profile, frequency, frequencyIncreased, longRun, recovery, qualityType, eligibility, goalSession,
      )
      TrainingWeek(
        weekIndex = weekIndex,
        startDate = startDate.plusWeeks(weekIndex.toLong()),
        workouts = workouts,
        targetDistanceMeters = prescribedDistance ?: plannedDistance,
        targetDurationSeconds = expectedDuration,
        loadClassification = when {
          recovery -> WeekLoadClassification.RECOVERY
          weekIndex == 0 -> WeekLoadClassification.NORMAL
          qualityType != null || goalSession == GoalSessionKind.GOAL_ATTEMPT || buildFactor > 1.04 -> WeekLoadClassification.BUILD
          else -> WeekLoadClassification.NORMAL
        },
        explanation = focusFor(currentBlock, goal),
        reasons = reasons,
      ).also { week ->
        previousDistance = week.targetDistanceMeters ?: previousDistance
        if (!recovery) previousLongRun = longRun ?: previousLongRun
      }
    }
    val plan = TrainingPlan(
      id = planId,
      goalId = goal.id,
      createdAt = createdAt,
      startDate = startDate,
      endDate = weeks.last().startDate.plusDays(6),
      weeks = weeks,
      blocks = buildBlocks(blockTypes),
      generationVersion = TRAINING_PLAN_GENERATOR_VERSION,
      explanation = planExplanation(goal, profile, startingFrequency, eligibility, completionMilestone),
      confidence = profile.profileConfidence,
      status = TrainingPlanStatus.ACTIVE,
      completionMilestone = completionMilestone?.copy(
        planId = planId,
        workoutId = weeks.flatMap { it.workouts }.map { it.workout }
          .singleOrNull { workout ->
            workout.date == scheduledCompletionDate && workout.purpose in setOf(
              WorkoutPurpose.GOAL_COMPLETION,
              WorkoutPurpose.RACE_SPECIFIC,
            )
          }?.id,
      ),
    )
    validator.requireValid(plan, goal, requireGoalDistanceSession = goalSessionScheduled)
    return plan
  }

  fun startingFrequency(profile: AthletePerformanceProfile): Int = when {
    profile.totalCanonicalRuns == 0 -> 2
    profile.profileConfidence == ProfileConfidence.VERY_LOW -> if (profile.runsLast28Days >= 3) 3 else 2
    profile.averageRunsPerWeek >= 4.0 -> 4
    profile.averageRunsPerWeek >= 2.5 -> 3
    profile.runsLast28Days >= 3 -> 3
    else -> 2
  }

  private fun frequencyForWeek(startingFrequency: Int, profile: AthletePerformanceProfile, weekIndex: Int): Int {
    if (weekIndex < 0) return startingFrequency
    val readyForFourthRun = startingFrequency == 3 &&
      weekIndex >= config.recoveryWeekFrequency && profile.averageRunsPerWeek >= 3.5 &&
      profile.activeWeeksLast8Weeks >= 4 && profile.consistency == TrainingConsistency.CONSISTENT &&
      profile.profileConfidence >= ProfileConfidence.MEDIUM
    return if (readyForFourthRun) 4 else startingFrequency
  }

  fun qualityEligibility(profile: AthletePerformanceProfile): WorkoutEligibility {
    val completion = profile.feedback.completedFraction
    val pain = profile.feedback.painFrequency
    val enoughRuns = profile.runsLast28Days >= 6
    val enoughWeeks = profile.activeWeeksLast8Weeks >= 3
    val confidence = profile.profileConfidence == ProfileConfidence.MEDIUM || profile.profileConfidence == ProfileConfidence.HIGH
    val completionReady = completion == null || completion >= 0.75
    val painReady = pain == null || pain <= 0.20
    val effortReady = profile.feedback.averageRpe == null || profile.feedback.averageRpe <= 7.0
    val threshold = enoughRuns && enoughWeeks && confidence && completionReady && painReady && effortReady
    val intervals = threshold && profile.runsLast28Days >= 10 && profile.activeWeeksLast8Weeks >= 4 &&
      profile.consistency == TrainingConsistency.CONSISTENT && profile.profileConfidence == ProfileConfidence.HIGH &&
      (completion == null || completion >= 0.80) && (pain == null || pain <= 0.10) &&
      (profile.feedback.averageRpe == null || profile.feedback.averageRpe <= 6.0)
    val reasons = buildList {
      add(if (enoughRuns) "${profile.runsLast28Days} recent runs support quality preparation." else "More recent runs are needed before regular quality work.")
      add(if (enoughWeeks) "Training spans ${profile.activeWeeksLast8Weeks} active weeks." else "More consistent active weeks are needed.")
      add(if (confidence) "Profile confidence supports cautious quality work." else "Limited profile confidence keeps training aerobic.")
      if (!painReady) add("Recent pain reports prevent hard workout progression.")
      if (!completionReady) add("Workout completion consistency needs to improve first.")
      if (!effortReady) add("Recent perceived effort is too high for added quality work.")
      add(if (intervals) "Interval eligibility requirements are met." else "Interval eligibility requires stronger consistency and confidence.")
    }
    return WorkoutEligibility(threshold, intervals, reasons)
  }

  private fun isRecoveryWeek(weekIndex: Int) = (weekIndex + 1) % config.recoveryWeekFrequency == 0

  private fun recoveryWeek(goal: TrainingGoal, weekIndex: Int, completionWeek: Int?): Boolean {
    if (goal.goalType != GoalType.COMPLETE_DISTANCE || completionWeek == null) return isRecoveryWeek(weekIndex)
    if (weekIndex == completionWeek) return false
    return isRecoveryWeek(weekIndex) || weekIndex == completionWeek - 1
  }

  private fun baselineDistance(profile: AthletePerformanceProfile): Double? = maxOf(
    profile.averageWeeklyDistanceLast4WeeksMeters, profile.distanceLast7DaysMeters,
  ).takeIf { it > 0.0 }

  private fun progressionFor(confidence: ProfileConfidence): Double = when (confidence) {
    ProfileConfidence.VERY_LOW -> config.veryLowConfidenceProgression
    ProfileConfidence.LOW -> config.lowConfidenceProgression
    ProfileConfidence.MEDIUM -> config.mediumConfidenceProgression
    ProfileConfidence.HIGH -> config.highConfidenceProgression
  }

  private fun longRunDistance(
    profile: AthletePerformanceProfile,
    previous: Double?,
    recovery: Boolean,
    weekIndex: Int,
  ): Double? {
    val current = previous ?: profile.longestRunLast28Days?.distanceMeters ?: return null
    if (recovery) return roundTo(current * config.recoveryVolumeFactor, config.longRunRoundingMeters)
    if (weekIndex == 0) return roundTo(current, config.longRunRoundingMeters)
    val fraction = if (profile.profileConfidence <= ProfileConfidence.LOW) {
      config.lowConfidenceLongRunIncreaseFraction
    } else config.establishedLongRunIncreaseFraction
    return roundTo(current + min(current * fraction, config.maximumLongRunIncreaseMeters), config.longRunRoundingMeters)
  }

  private fun blockType(goal: TrainingGoal, profile: AthletePerformanceProfile, weekIndex: Int, recovery: Boolean): TrainingBlockType {
    if (recovery) return TrainingBlockType.RECOVERY
    val cycle = weekIndex / config.recoveryWeekFrequency
    if (profile.profileConfidence <= ProfileConfidence.LOW || profile.activeWeeksLast8Weeks < 3) {
      return if (cycle == 0) TrainingBlockType.FOUNDATION else TrainingBlockType.BASE
    }
    return when {
      cycle == 0 -> TrainingBlockType.BASE
      goal.goalType == GoalType.COMPLETE_DISTANCE -> TrainingBlockType.BUILD
      cycle == 1 -> TrainingBlockType.BUILD
      else -> TrainingBlockType.SPECIFIC
    }
  }

  private fun goalSessionFor(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    weekIndex: Int,
    weekCount: Int,
    recovery: Boolean,
    blockType: TrainingBlockType,
    alreadyScheduled: Boolean,
  ): GoalSessionKind? {
    if (recovery || !goalDistanceSupported(goal, profile)) return null
    val finalTrainingWeek = (weekCount - 1 downTo 0).first { !isRecoveryWeek(it) }
    if (goal.goalType == GoalType.COMPLETE_DISTANCE) {
      return if (!alreadyScheduled && weekIndex == weekCount - 1) GoalSessionKind.COMPLETION else null
    }
    if (alreadyScheduled) return null
    val introductionWeek = when {
      weekCount <= 2 -> finalTrainingWeek
      else -> minOf(2, finalTrainingWeek)
    }
    if (weekIndex != introductionWeek) return null
    return when (goal.goalType) {
      GoalType.COMPLETE_DISTANCE -> error("Completion sessions are handled at the final plan week.")
      GoalType.IMPROVE_PERFORMANCE, GoalType.TARGET_TIME -> GoalSessionKind.CONTROLLED
    }
  }

  private fun goalDistanceSupported(goal: TrainingGoal, profile: AthletePerformanceProfile): Boolean =
    profile.longestRunLast28Days?.distanceMeters?.let {
      it >= goal.distanceMeters * config.goalSessionCapabilityFraction
    } == true

  private fun hasAlreadyDemonstrated(goal: TrainingGoal, profile: AthletePerformanceProfile): Boolean {
    val benchmark = profile.performanceBenchmarks.any { benchmark ->
      benchmark.personalBest?.actualDistanceMeters?.let { it >= goal.distanceMeters } == true
    }
    return benchmark || profile.longestRunLast28Days?.distanceMeters?.let { it >= goal.distanceMeters } == true
  }

  private fun qualityWorkout(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    eligibility: WorkoutEligibility,
    weekIndex: Int,
    recovery: Boolean,
    frequency: Int,
  ): WorkoutType? {
    if (recovery || frequency < 3 || weekIndex == 0 || !eligibility.thresholdEligible) return null
    if (goal.goalType == GoalType.COMPLETE_DISTANCE) {
      return if (weekIndex >= 5 && profile.profileConfidence >= ProfileConfidence.MEDIUM) WorkoutType.TEMPO_RUN else null
    }
    val specificForSpeed = goal.distance == GoalDistance.FIVE_K || goal.distance == GoalDistance.TEN_K
    return if (eligibility.intervalEligible && specificForSpeed && weekIndex >= 8 && weekIndex % 2 == 0) {
      WorkoutType.INTERVALS
    } else if (goal.distance == GoalDistance.HALF_MARATHON || goal.distance == GoalDistance.MARATHON) {
      WorkoutType.TEMPO_RUN
    } else WorkoutType.THRESHOLD
  }

  private fun buildWorkouts(
    planId: String,
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    weekIndex: Int,
    weekStart: LocalDate,
    frequency: Int,
    quality: WorkoutType?,
    longRunMeters: Double?,
    weeklyDistanceBudget: Double?,
    recoveryWeek: Boolean,
    blockType: TrainingBlockType,
    goalSession: GoalSessionKind?,
    completionDate: LocalDate?,
    createdAt: Instant,
  ): List<PlannedWorkoutEntry> {
    val slots = when (frequency) {
      2 -> listOf(WeeklyWorkoutSlot.EARLY_WEEK, WeeklyWorkoutSlot.WEEKEND)
      3 -> listOf(WeeklyWorkoutSlot.EARLY_WEEK, WeeklyWorkoutSlot.MID_WEEK, WeeklyWorkoutSlot.WEEKEND)
      else -> listOf(WeeklyWorkoutSlot.EARLY_WEEK, WeeklyWorkoutSlot.MID_WEEK, WeeklyWorkoutSlot.LATE_WEEK, WeeklyWorkoutSlot.WEEKEND)
    }
    val goalSessionUsesLongRun = goalSession != null && completionDate != null
    val fixedDistance = (if (goalSessionUsesLongRun) goal.distanceMeters else longRunMeters ?: 0.0) +
      if (goalSession != null && !goalSessionUsesLongRun) goal.distanceMeters else qualityDistance(quality)
    val flexibleSlots = slots.count { slot ->
      slot != WeeklyWorkoutSlot.WEEKEND &&
        !(slot == WeeklyWorkoutSlot.MID_WEEK && (quality != null || (goalSession != null && !goalSessionUsesLongRun)))
    }
    val flexibleDistances = if (weeklyDistanceBudget != null && flexibleSlots > 0) {
      practicalDistances(weeklyDistanceBudget - fixedDistance, flexibleSlots, config.minimumEasyRunMeters)
    } else emptyList()
    var flexibleIndex = 0

    val entries = slots.mapIndexed { order, slot ->
      val session = when {
        slot == WeeklyWorkoutSlot.WEEKEND && goalSessionUsesLongRun -> goalSession
        slot == WeeklyWorkoutSlot.MID_WEEK && goalSession != null && !goalSessionUsesLongRun -> goalSession
        else -> null
      }
      val type = when {
        session == GoalSessionKind.COMPLETION -> WorkoutType.LONG_RUN
        session == GoalSessionKind.CONTROLLED && profile.profileConfidence <= ProfileConfidence.LOW -> WorkoutType.EASY_RUN
        session == GoalSessionKind.CONTROLLED || session == GoalSessionKind.GOAL_ATTEMPT -> WorkoutType.RACE_SPECIFIC
        slot == WeeklyWorkoutSlot.WEEKEND -> WorkoutType.LONG_RUN
        recoveryWeek && order == 0 -> WorkoutType.RECOVERY_RUN
        quality != null && slot == WeeklyWorkoutSlot.MID_WEEK -> quality
        frequency >= 4 && slot == WeeklyWorkoutSlot.LATE_WEEK -> WorkoutType.RECOVERY_RUN
        goal.goalType != GoalType.COMPLETE_DISTANCE && weekIndex % 2 == 1 && slot == WeeklyWorkoutSlot.EARLY_WEEK -> WorkoutType.STRIDES
        profile.profileConfidence == ProfileConfidence.VERY_LOW && profile.totalCanonicalRuns < 3 -> WorkoutType.RUN_WALK
        else -> WorkoutType.EASY_RUN
      }
      val isFlexible = slot != WeeklyWorkoutSlot.WEEKEND &&
        !(slot == WeeklyWorkoutSlot.MID_WEEK && (quality != null || (goalSession != null && !goalSessionUsesLongRun)))
      val flexibleDistance = if (isFlexible) flexibleDistances.getOrNull(flexibleIndex++) else null
      val distance = when {
        session != null -> goal.distanceMeters
        type == WorkoutType.LONG_RUN -> longRunMeters
        type == WorkoutType.RECOVERY_RUN -> flexibleDistance?.let { practicalDistance(it * 0.8, config.minimumRecoveryRunMeters) }
        else -> flexibleDistance
      }?.let { prescribed ->
        if (goal.goalType == GoalType.COMPLETE_DISTANCE && session != GoalSessionKind.COMPLETION) {
          min(prescribed, preGoalDistanceLimit(goal.distanceMeters))
        } else prescribed
      }
      val id = "$planId:w$weekIndex:o$order"
      val reason = generationReason(type, session, goal, profile, distance)
      PlannedWorkoutEntry(
        slot,
        workout(
          id,
          if (session != null && completionDate != null) completionDate else weekStart.plusDays(config.slotDayOffsets.getValue(slot)),
          type,
          session,
          distance,
          goal,
          profile,
          blockType,
          createdAt,
          reason,
        ),
        reason,
      )
    }
    return if (goalSession != null && completionDate != null) {
      adaptAroundScheduledCompletion(entries, planId, weekIndex, weekStart, requireNotNull(completionDate), createdAt)
    } else entries
  }

  private fun adaptAroundScheduledCompletion(
    entries: List<PlannedWorkoutEntry>,
    planId: String,
    weekIndex: Int,
    weekStart: LocalDate,
    completionDate: LocalDate,
    createdAt: Instant,
  ): List<PlannedWorkoutEntry> {
    val completion = entries.single { entry ->
      entry.workout.date == completionDate && entry.workout.purpose in setOf(
        WorkoutPurpose.GOAL_COMPLETION,
        WorkoutPurpose.RACE_SPECIFIC,
      )
    }
    val retained = entries.filter { entry ->
      if (entry.workout.id == completion.workout.id) return@filter true
      val offset = ChronoUnit.DAYS.between(completionDate, entry.workout.date)
      when {
        offset in 0..2 -> false
        offset == -1L -> false
        offset in -2L..-1L && entry.workout.type in setOf(
          WorkoutType.TEMPO_RUN,
          WorkoutType.THRESHOLD,
          WorkoutType.INTERVALS,
          WorkoutType.RACE_SPECIFIC,
          WorkoutType.LONG_RUN,
        ) -> false
        else -> true
      }
    }.toMutableList()
    val nextDay = completionDate.plusDays(1)
    if (!nextDay.isAfter(weekStart.plusDays(6))) {
      val restId = "$planId:w$weekIndex:post-completion-rest"
      retained += PlannedWorkoutEntry(
        WeeklyWorkoutSlot.WEEKEND,
        PlannedWorkout(
          id = restId,
          date = nextDay,
          type = WorkoutType.REST,
          name = "Post-completion Rest",
          purpose = WorkoutPurpose.RECOVERY,
          blocks = emptyList(),
          explanation = "Rest after the scheduled Completion Run instead of adding another endurance workout.",
          cues = listOf("Recover and record any pain or unusual fatigue before resuming training."),
          createdAt = createdAt,
          goalId = completion.workout.goalId,
          origin = WorkoutOrigin.GENERATED,
        ),
        "Replaces nearby provisional training to protect recovery after the user-scheduled Completion Run.",
      )
    }
    return retained.sortedWith(compareBy<PlannedWorkoutEntry> { it.workout.date }.thenBy { it.workout.id })
  }

  private fun qualityDistance(type: WorkoutType?): Double = when (type) {
    WorkoutType.TEMPO_RUN, WorkoutType.THRESHOLD, WorkoutType.INTERVALS -> 5_000.0
    else -> 0.0
  }

  private fun practicalDistance(value: Double, minimum: Double): Double =
    floor(maxOf(value, minimum) / config.practicalDistanceIncrementMeters) * config.practicalDistanceIncrementMeters

  private fun practicalDistances(total: Double, count: Int, minimum: Double): List<Double> {
    if (count <= 0) return emptyList()
    val safeTotal = maxOf(total, minimum * count)
    val raw = safeTotal / count
    val lower = practicalDistance(raw, minimum)
    val upper = lower + config.practicalDistanceIncrementMeters
    val upperCount = round((safeTotal - lower * count) / config.practicalDistanceIncrementMeters)
      .toInt().coerceIn(0, count)
    return List(count) { index -> if (index < upperCount) upper else lower }
  }

  private fun workout(
    id: String,
    date: LocalDate,
    type: WorkoutType,
    goalSession: GoalSessionKind?,
    distanceMeters: Double?,
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    blockType: TrainingBlockType,
    createdAt: Instant,
    reason: String,
  ): PlannedWorkout {
    val purpose = when {
      goalSession == GoalSessionKind.COMPLETION -> WorkoutPurpose.GOAL_COMPLETION
      goalSession == GoalSessionKind.CONTROLLED || goalSession == GoalSessionKind.GOAL_ATTEMPT -> WorkoutPurpose.RACE_SPECIFIC
      type == WorkoutType.LONG_RUN -> WorkoutPurpose.ENDURANCE
      type == WorkoutType.RECOVERY_RUN -> WorkoutPurpose.RECOVERY
      type == WorkoutType.TEMPO_RUN || type == WorkoutType.THRESHOLD -> WorkoutPurpose.LACTATE_THRESHOLD
      type == WorkoutType.INTERVALS -> WorkoutPurpose.VO2MAX
      type == WorkoutType.STRIDES -> WorkoutPurpose.RUNNING_ECONOMY
      else -> WorkoutPurpose.AEROBIC_BASE
    }
    val pace = distanceMeters?.let { paceEngine.prescribe(profile, goal, type, purpose, it, blockType) }
    val blocks = when {
      goalSession != null -> listOf(
        singleDistanceBlock(
          id, 0, SegmentCategory.EASY,
          when (goalSession) {
            GoalSessionKind.COMPLETION -> "Continuous completion run"
            GoalSessionKind.CONTROLLED -> "Controlled continuous run"
            GoalSessionKind.GOAL_ATTEMPT -> "Goal-pace continuous run"
          },
          requireNotNull(distanceMeters), pace,
          if (goalSession == GoalSessionKind.COMPLETION) 4 else if (goalSession == GoalSessionKind.CONTROLLED) 5 else 7,
          if (goalSession == GoalSessionKind.COMPLETION) 5 else if (goalSession == GoalSessionKind.CONTROLLED) 6 else 9,
        )
      )
      type == WorkoutType.LONG_RUN && distanceMeters != null -> listOf(
        singleDistanceBlock(id, 0, SegmentCategory.EASY, "Easy long run", distanceMeters, pace, 3, 4)
      )
      type == WorkoutType.RECOVERY_RUN && distanceMeters != null -> listOf(
        singleDistanceBlock(id, 0, SegmentCategory.RECOVERY, "Gentle recovery running", distanceMeters, pace, 2, 3)
      )
      type == WorkoutType.RUN_WALK -> listOf(
        RepeatBlock(
          "$id:block:0", 0, 6,
          listOf(
            timeSegment(id, 0, SegmentCategory.EASY, "Easy run", 180, 3, 4),
            timeSegment(id, 1, SegmentCategory.WALK, "Walk", 60, 2, 2),
          ),
          "6 × run/walk",
        )
      )
      type == WorkoutType.STRIDES && distanceMeters != null -> listOf(
        singleDistanceBlock(id, 0, SegmentCategory.EASY, "Easy running", distanceMeters, pace, 3, 4),
        RepeatBlock(
          "$id:block:1", 1, 4,
          listOf(
            timeSegment(id, 0, SegmentCategory.STRIDE, "Relaxed stride", 20, 6, 7),
            timeSegment(id, 1, SegmentCategory.RECOVERY, "Easy recovery", 60, 2, 3),
          ),
          "4 × relaxed strides",
        ),
      )
      type == WorkoutType.TEMPO_RUN || type == WorkoutType.THRESHOLD -> qualityBlocks(id, type, goal, profile, blockType)
      type == WorkoutType.INTERVALS -> intervalBlocks(id, goal, profile, blockType)
      distanceMeters != null -> listOf(
        singleDistanceBlock(id, 0, SegmentCategory.EASY, "Easy running", distanceMeters, pace, 3, 4)
      )
      else -> listOf(
        SingleSegmentBlock(
          "$id:block:0", 0,
          timeSegment(id, 0, SegmentCategory.EASY, "Easy running", config.defaultEasyDurationMinutes * 60L, 3, 4),
        )
      )
    }
    val name = when (goalSession) {
      GoalSessionKind.COMPLETION -> "${goal.distance.displayName} Completion Run"
      GoalSessionKind.CONTROLLED -> "Controlled ${goal.distance.displayName}"
      GoalSessionKind.GOAL_ATTEMPT -> "${goal.distance.displayName} Goal Attempt"
      null -> if (type == WorkoutType.STRIDES) "Easy Run + Strides"
        else type.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)
    }
    return PlannedWorkout(
      id, date, type, name, purpose, blocks, reason,
      cues(type, goalSession, goal, profile, pace), createdAt, goal.id, WorkoutOrigin.GENERATED,
    )
  }

  private fun qualityBlocks(
    id: String,
    type: WorkoutType,
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    blockType: TrainingBlockType,
  ): List<com.example.openruncoach.domain.workout.WorkoutBlock> {
    val easyPace = paceEngine.prescribe(profile, goal, WorkoutType.EASY_RUN, WorkoutPurpose.AEROBIC_BASE, 1_000.0, blockType)
    val workPace = paceEngine.prescribe(profile, goal, type, WorkoutPurpose.LACTATE_THRESHOLD, 1_000.0, blockType)
    val recoveryPace = paceEngine.prescribe(profile, goal, WorkoutType.RECOVERY_RUN, WorkoutPurpose.RECOVERY, 500.0, blockType)
    return listOf(
      singleDistanceBlock(id, 0, SegmentCategory.WARMUP, "Easy warm-up", 1_000.0, easyPace, 3, 4),
      RepeatBlock(
        "$id:block:1", 1, 2,
        listOf(
          distanceSegment(id, 0, SegmentCategory.WORK, if (type == WorkoutType.THRESHOLD) "Threshold effort" else "Controlled tempo", 1_000.0, workPace, 7, 8),
          distanceSegment(id, 1, SegmentCategory.RECOVERY, "Easy recovery", 500.0, recoveryPace, 2, 3),
        ),
        "2 × controlled work",
      ),
      singleDistanceBlock(id, 2, SegmentCategory.COOLDOWN, "Easy cool-down", 1_000.0, easyPace, 2, 3),
    )
  }

  private fun intervalBlocks(
    id: String,
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    blockType: TrainingBlockType,
  ): List<com.example.openruncoach.domain.workout.WorkoutBlock> {
    val easyPace = paceEngine.prescribe(profile, goal, WorkoutType.EASY_RUN, WorkoutPurpose.AEROBIC_BASE, 1_000.0, blockType)
    val workPace = paceEngine.prescribe(profile, goal, WorkoutType.INTERVALS, WorkoutPurpose.VO2MAX, 400.0, blockType)
    val recoveryPace = paceEngine.prescribe(profile, goal, WorkoutType.RECOVERY_RUN, WorkoutPurpose.RECOVERY, 200.0, blockType)
    return listOf(
      singleDistanceBlock(id, 0, SegmentCategory.WARMUP, "Easy warm-up", 1_000.0, easyPace, 3, 4),
      RepeatBlock(
        "$id:block:1", 1, 5,
        listOf(
          distanceSegment(id, 0, SegmentCategory.WORK, "Controlled interval", 400.0, workPace, 8, 8),
          distanceSegment(id, 1, SegmentCategory.RECOVERY, "Easy recovery", 200.0, recoveryPace, 2, 3),
        ),
        "5 × 400 m",
      ),
      singleDistanceBlock(id, 2, SegmentCategory.COOLDOWN, "Easy cool-down", 1_000.0, easyPace, 2, 3),
    )
  }

  private fun singleDistanceBlock(
    workoutId: String,
    order: Int,
    category: SegmentCategory,
    name: String,
    meters: Double,
    pace: PacePrescription?,
    rpeMin: Int,
    rpeMax: Int,
  ) = SingleSegmentBlock(
    "$workoutId:block:$order", order,
    distanceSegment(workoutId, 0, category, name, meters, pace, rpeMin, rpeMax),
  )

  private fun distanceSegment(
    workoutId: String,
    order: Int,
    category: SegmentCategory,
    name: String,
    meters: Double,
    pace: PacePrescription?,
    rpeMin: Int,
    rpeMax: Int,
  ) = WorkoutSegment(
    id = "$workoutId:segment:${category.name}:$order",
    order = order,
    category = category,
    name = name,
    completion = DistanceCompletion(meters),
    primaryIntensity = pace?.let { PaceTarget(it.minSecondsPerKm, it.maxSecondsPerKm, it.confidence) }
      ?: RpeTarget(rpeMin, rpeMax, GuidanceConfidence.HIGH),
    secondaryIntensities = if (pace == null) emptyList() else listOf(RpeTarget(rpeMin, rpeMax, GuidanceConfidence.HIGH)),
  )

  private fun timeSegment(
    workoutId: String,
    order: Int,
    category: SegmentCategory,
    name: String,
    seconds: Long,
    rpeMin: Int,
    rpeMax: Int,
  ) = WorkoutSegment(
    id = "$workoutId:segment:${category.name}:$order",
    order = order,
    category = category,
    name = name,
    completion = DurationCompletion(seconds),
    primaryIntensity = RpeTarget(rpeMin, rpeMax, GuidanceConfidence.HIGH),
  )

  private fun generationReason(
    type: WorkoutType,
    goalSession: GoalSessionKind?,
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    distanceMeters: Double?,
  ): String = when (goalSession) {
    GoalSessionKind.COMPLETION -> "Added as a continuous ${goal.distance.displayName} completion run because recent endurance supports the full distance at controlled effort."
    GoalSessionKind.CONTROLLED -> "Added to practise the full ${goal.distance.displayName} continuously at a sustainable pace; it is not a race or maximal benchmark."
    GoalSessionKind.GOAL_ATTEMPT -> "Added once near the end of the specific phase to practise the selected ${goal.distance.displayName} target pace."
    null -> when (type) {
      WorkoutType.LONG_RUN -> if (goal.goalType == GoalType.COMPLETE_DISTANCE) {
        "Added to progress endurance toward ${goal.distance.displayName} while remaining below the target until the dedicated completion run."
      } else {
        "Added to build endurance gradually from ${profile.longestRunLast28Days?.distanceMeters?.let { "%.1f km".format(java.util.Locale.ROOT, it / 1_000) } ?: "your current training history"}; performance-goal long runs may exceed race distance and this run is ${distanceMeters?.let { "%.1f km".format(java.util.Locale.ROOT, it / 1_000) } ?: "time based"}."
      }
      WorkoutType.RECOVERY_RUN -> "Added to maintain frequency with low fatigue and support recovery."
      WorkoutType.RUN_WALK -> "Added because current training evidence is limited; run/walk keeps the effort controlled."
      WorkoutType.STRIDES -> "Added after easy distance running to introduce relaxed speed without a full hard session."
      WorkoutType.TEMPO_RUN -> "Added as controlled sustained work after the aerobic eligibility checks were met."
      WorkoutType.THRESHOLD -> "Added as one conservative quality session because recent consistency supports it."
      WorkoutType.INTERVALS -> "Added because the stronger run-count, consistency, completion, pain, and confidence checks were met."
      WorkoutType.ASSESSMENT -> "Added to establish a usable running baseline without inventing precise pace guidance."
      else -> "Added as an explicit distance to keep most weekly running easy and build aerobic consistency."
    }
  }

  private fun cues(
    type: WorkoutType,
    goalSession: GoalSessionKind?,
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    pace: PacePrescription?,
  ): List<String> = buildList {
    when {
      goalSession == GoalSessionKind.GOAL_ATTEMPT -> add("Use the selected goal pace only for this specific session and keep the opening controlled.")
      goalSession != null -> add("Run continuously and sustainably; this is not a maximal test.")
      type in setOf(WorkoutType.EASY_RUN, WorkoutType.LONG_RUN, WorkoutType.RUN_WALK, WorkoutType.RECOVERY_RUN) -> add("Keep the effort conversational.")
      type == WorkoutType.STRIDES -> add("Run fast but relaxed; stop before form deteriorates.")
      type == WorkoutType.TEMPO_RUN || type == WorkoutType.THRESHOLD -> add("Keep the work controlled and repeatable.")
      type == WorkoutType.INTERVALS -> add("Finish each repetition with enough control for the next one.")
    }
    pace?.let { add(it.explanation) }
    if (pace == null && type != WorkoutType.RUN_WALK) add("No credible pace estimate is available yet; use the prescribed RPE range.")
    profile.typicalEasyHeartRate?.takeIf { type in setOf(WorkoutType.EASY_RUN, WorkoutType.LONG_RUN, WorkoutType.RECOVERY_RUN) }?.let {
      add("Observed easy heart rate (${it.representative.toInt()} bpm) is secondary context, not a required zone.")
    }
    if (goal.goalType == GoalType.TARGET_TIME && goalSession != GoalSessionKind.GOAL_ATTEMPT) {
      add("Target race pace is context, not the required pace for this workout.")
    }
  }

  private fun weekReasons(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    frequency: Int,
    frequencyIncreased: Boolean,
    longRun: Double?,
    recovery: Boolean,
    quality: WorkoutType?,
    eligibility: WorkoutEligibility,
    goalSession: GoalSessionKind?,
  ): List<String> = buildList {
    add("Weekly frequency is $frequency runs based on the observed ${"%.1f".format(java.util.Locale.ROOT, profile.averageRunsPerWeek)} runs per week.")
    if (frequencyIncreased) add("A fourth easy run is introduced after the recovery cycle; volume and quality progression are paused this week.")
    if (recovery) add("Prescribed distance and long-run load are reduced for recovery.")
    else add("Distance progression is bounded by ${profile.profileConfidence.name.lowercase().replace('_', ' ')} profile confidence.")
    longRun?.let {
      add(
        if (goal.goalType == GoalType.COMPLETE_DISTANCE) {
          "Long run is ${"%.1f km".format(java.util.Locale.ROOT, it / 1_000)} and remains below the goal until the completion run."
        } else {
          "Long run is ${"%.1f km".format(java.util.Locale.ROOT, it / 1_000)}; aerobic distance may exceed race distance for this goal type."
        }
      )
    }
    if (goalSession != null) add("A ${goal.distance.displayName} ${goalSession.name.lowercase().replace('_', ' ')} session deliberately connects training to the selected goal.")
    else if (quality == null) add(eligibility.reasons.first { it.contains("quality", ignoreCase = true) || it.contains("confidence", ignoreCase = true) })
    else add("One ${quality.name.lowercase().replace('_', ' ')} session is included after eligibility checks.")
  }

  private fun focusFor(type: TrainingBlockType, goal: TrainingGoal): String = when (type) {
    TrainingBlockType.FOUNDATION -> "Build consistent, comfortable running for the ${goal.distance.displayName} goal."
    TrainingBlockType.BASE -> "Develop aerobic distance while keeping most running easy."
    TrainingBlockType.BUILD -> "Progress endurance and add eligible controlled quality work."
    TrainingBlockType.SPECIFIC -> "Add goal-distance and pace emphasis while retaining easy running."
    TrainingBlockType.RECOVERY -> "Absorb recent training with reduced distance and hard-session load."
    TrainingBlockType.PEAK -> "Consolidate the strongest sustainable training week."
    TrainingBlockType.TAPER -> "Reduce fatigue while retaining routine."
  }

  private fun planExplanation(
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    frequency: Int,
    eligibility: WorkoutEligibility,
    completionMilestone: GoalCompletionMilestone?,
  ): String = buildString {
    append("A deterministic ${goal.distance.displayName} ${goal.goalType.name.lowercase().replace('_', ' ')} base plan starting at $frequency runs per week. ")
    append("Normal running is prescribed by distance, with explainable pace ranges when evidence supports them and RPE as a guardrail. ")
    append("It uses ${profile.profileConfidence.name.lowercase().replace('_', ' ')} profile confidence, bounded progression, and a ${config.recoveryWeekFrequency - 1}:1 build-to-recovery pattern. ")
    append(if (eligibility.thresholdEligible) "Controlled quality work is eligible." else "The initial emphasis remains aerobic while quality eligibility develops.")
    if (goal.goalType == GoalType.COMPLETE_DISTANCE) {
      append(" Normal workouts remain below the selected distance until the controlled Goal Completion Run.")
      append(
        when (completionMilestone?.readiness) {
          GoalCompletionReadiness.GOAL_COMPLETION_SCHEDULED -> " The athlete-selected Completion Run date is a scheduling constraint, and nearby provisional workouts are reduced or replaced."
          GoalCompletionReadiness.GOAL_COMPLETION_READY -> " The athlete is ready to choose a Completion Run date; no date is assigned automatically."
          GoalCompletionReadiness.GOAL_COMPLETION_NOT_READY, null -> " Consolidation training continues until performance evidence supports scheduling the attempt."
          GoalCompletionReadiness.GOAL_COMPLETED -> " The goal distance has been completed."
        }
      )
      if (profile.longestRunLast28Days?.distanceMeters?.let { it >= goal.distanceMeters } == true) {
        append(" You have already demonstrated ${goal.distance.displayName}; Improve Performance or Target Time may be more relevant alternatives.")
      }
    }
  }

  private fun assessmentPlan(
    planId: String,
    goal: TrainingGoal,
    profile: AthletePerformanceProfile,
    sufficiency: TrainingDataSufficiencyResult,
    startDate: LocalDate,
    createdAt: Instant,
    completionMilestone: GoalCompletionMilestone?,
  ): TrainingPlan {
    val assessmentType = requireNotNull(sufficiency.recommendedAssessment)
    val id = "$planId:assessment"
    val longest = profile.longestRunLast28Days?.distanceMeters?.takeIf { it.isFinite() && it > 0.0 }
    val distance = when (assessmentType) {
      AssessmentRunType.INITIAL_BASELINE -> null
      AssessmentRunType.EASY_PACE_ASSESSMENT -> minOf(longest ?: 3_000.0, 3_000.0).coerceAtLeast(1_500.0)
      AssessmentRunType.CONTINUOUS_RUNNING_ASSESSMENT -> minOf(longest ?: 3_000.0, goal.distanceMeters * 0.75).coerceAtLeast(1_500.0)
      AssessmentRunType.GOAL_DISTANCE_BASELINE -> goal.distanceMeters
    }
    val label = when (assessmentType) {
      AssessmentRunType.INITIAL_BASELINE -> "Baseline Run"
      AssessmentRunType.EASY_PACE_ASSESSMENT -> "Easy Pace Assessment"
      AssessmentRunType.CONTINUOUS_RUNNING_ASSESSMENT -> "Continuous Running Assessment"
      AssessmentRunType.GOAL_DISTANCE_BASELINE -> "Controlled ${goal.distance.displayName} Baseline"
    }
    val segment = WorkoutSegment(
      id = "$id:segment:0",
      order = 0,
      category = SegmentCategory.EASY,
      name = "Comfortable self-selected running",
      completion = distance?.let(::DistanceCompletion) ?: DurationCompletion(20 * 60L),
      primaryIntensity = RpeTarget(3, 4, GuidanceConfidence.HIGH),
      notes = "Use a comfortable, non-maximal effort. Walking breaks are allowed if needed.",
    )
    val workout = PlannedWorkout(
      id = id,
      date = startDate.plusDays(config.slotDayOffsets.getValue(WeeklyWorkoutSlot.EARLY_WEEK)),
      type = WorkoutType.ASSESSMENT,
      name = label,
      purpose = WorkoutPurpose.ASSESSMENT,
      blocks = listOf(SingleSegmentBlock("$id:block:0", 0, segment)),
      explanation = "Establish current sustainable running evidence before generating a normal plan.",
      cues = listOf("Choose a comfortable effort at RPE 3–4.", "This is not a maximum-effort test.", "Complete post-run feedback so the profile can be recalculated."),
      createdAt = createdAt,
      goalId = goal.id,
      origin = WorkoutOrigin.GENERATED,
    )
    val week = TrainingWeek(
      weekIndex = 0,
      startDate = startDate,
      workouts = listOf(PlannedWorkoutEntry(WeeklyWorkoutSlot.EARLY_WEEK, workout, sufficiency.reasons.last())),
      targetDistanceMeters = distance,
      targetDurationSeconds = if (distance == null) 20 * 60L else null,
      loadClassification = WeekLoadClassification.NORMAL,
      explanation = "Assessment Phase: collect a comfortable baseline before normal training prescriptions.",
      reasons = sufficiency.reasons,
    )
    return TrainingPlan(
      id = planId,
      goalId = goal.id,
      createdAt = createdAt,
      startDate = startDate,
      endDate = startDate.plusDays(6),
      weeks = listOf(week),
      blocks = listOf(TrainingBlock(TrainingBlockType.FOUNDATION, 0, 0, "Establish a safe baseline.", "Reassess data sufficiency after the result and checkout.")),
      generationVersion = TRAINING_PLAN_GENERATOR_VERSION,
      explanation = "Assessment Phase. Available data is not strong enough for a reliable normal plan, so this plan uses one comfortable assessment without a fabricated pace target.",
      confidence = profile.profileConfidence,
      status = TrainingPlanStatus.ACTIVE,
      completionMilestone = completionMilestone?.copy(planId = planId, workoutId = null),
    ).also { validator.requireValid(it, goal, requireGoalDistanceSession = false) }
  }

  private fun preGoalDistanceLimit(targetMeters: Double): Double =
    (targetMeters - minOf(100.0, targetMeters * 0.01)).coerceAtLeast(targetMeters * 0.5)

  private fun buildBlocks(types: List<TrainingBlockType>): List<TrainingBlock> {
    val blocks = mutableListOf<TrainingBlock>()
    var start = 0
    types.forEachIndexed { index, type ->
      if (index == types.lastIndex || types[index + 1] != type) {
        blocks += TrainingBlock(
          type,
          start,
          index,
          when (type) {
            TrainingBlockType.FOUNDATION -> "Establish safe running consistency."
            TrainingBlockType.BASE -> "Develop aerobic durability."
            TrainingBlockType.BUILD -> "Progress endurance and controlled quality."
            TrainingBlockType.SPECIFIC -> "Emphasize demands relevant to the selected distance."
            TrainingBlockType.RECOVERY -> "Reduce load and absorb training."
            TrainingBlockType.PEAK -> "Consolidate peak sustainable work."
            TrainingBlockType.TAPER -> "Reduce fatigue before the goal date."
          },
          if (type == TrainingBlockType.RECOVERY) "Reduce distance and hard-session load." else "Progress one primary training lever conservatively.",
        )
        start = index + 1
      }
    }
    return blocks
  }

  private fun roundTo(value: Double, increment: Double): Double = round(value / increment) * increment
}
