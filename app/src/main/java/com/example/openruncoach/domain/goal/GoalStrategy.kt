package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.athlete.AthleteState
import com.example.openruncoach.domain.profile.ProfileConfidence
import java.util.Locale

/** Development dimensions, not workout types, prescriptions, or eligibility to train hard. */
enum class TrainingCapability {
  AEROBIC_BASE,
  DISTANCE_TOLERANCE,
  CONTINUOUS_RUNNING,
  WEEKLY_VOLUME,
  THRESHOLD,
  SPEED,
  RUNNING_ECONOMY,
  RACE_SPECIFIC_ENDURANCE,
  RECOVERY_TOLERANCE,
}

/** Confidence describes the interpretation of evidence, never the probability of goal success. */
data class GoalStrategy(
  val primaryCapabilities: List<TrainingCapability>,
  val secondaryCapabilities: List<TrainingCapability>,
  val supportingCapabilities: List<TrainingCapability>,
  val confidence: ProfileConfidence,
  val reasons: List<String>,
) {
  init {
    val capabilities = primaryCapabilities + secondaryCapabilities + supportingCapabilities
    require(primaryCapabilities.isNotEmpty())
    require(capabilities.distinct().size == capabilities.size)
    require(reasons.isNotEmpty() && reasons.none(String::isBlank))
  }
}

/**
 * WHAT matters for the goal, using accumulated capability only. No clock, plan milestone,
 * current readiness, latest cost, pace prescription, or goal-qualification decision belongs here.
 */
class GoalStrategyResolver {
  fun resolve(goal: TrainingGoal, state: AthleteState): GoalStrategy? {
    if (goal.status != GoalStatus.ACTIVE) return null
    val target = goal.distanceMeters
    val continuous = state.longestDemonstratedContinuousDistanceMeters
    val tolerated = state.longestToleratedDistanceMeters
    val repeatedAerobicEvidence = state.toleratedSessionCount >= 3
    val distanceEstablished = continuous != null && continuous >= target &&
      tolerated != null && tolerated >= target && repeatedAerobicEvidence
    // A broad consolidation band, not a completion tolerance or readiness threshold.
    val closeWithTolerance = continuous != null && continuous >= target * 0.9 &&
      tolerated != null && tolerated >= target * 0.9 && repeatedAerobicEvidence
    val shortDistance = target <= 5_000.0
    val longDistance = target >= requireNotNull(GoalDistance.HALF_MARATHON.meters)
    val marathonOrLonger = target >= requireNotNull(GoalDistance.MARATHON.meters)
    val performanceDirection = goal.goalType != GoalType.COMPLETE_DISTANCE && distanceEstablished
    val primary: List<TrainingCapability>
    val secondary: List<TrainingCapability>
    val supporting: List<TrainingCapability>

    if (performanceDirection) {
      primary = if (longDistance) listOf(
        TrainingCapability.AEROBIC_BASE, TrainingCapability.RACE_SPECIFIC_ENDURANCE,
      ) else listOf(TrainingCapability.THRESHOLD, TrainingCapability.RACE_SPECIFIC_ENDURANCE)
      secondary = if (longDistance) listOf(
        TrainingCapability.DISTANCE_TOLERANCE, TrainingCapability.WEEKLY_VOLUME, TrainingCapability.THRESHOLD,
      ) else listOf(TrainingCapability.AEROBIC_BASE, TrainingCapability.RUNNING_ECONOMY)
      supporting = if (longDistance) listOf(
        TrainingCapability.RECOVERY_TOLERANCE, TrainingCapability.RUNNING_ECONOMY,
      ) else if (shortDistance) listOf(TrainingCapability.WEEKLY_VOLUME, TrainingCapability.SPEED)
      else listOf(TrainingCapability.WEEKLY_VOLUME)
    } else if (shortDistance && !closeWithTolerance) {
      primary = listOf(TrainingCapability.CONTINUOUS_RUNNING, TrainingCapability.DISTANCE_TOLERANCE)
      secondary = listOf(TrainingCapability.AEROBIC_BASE, TrainingCapability.WEEKLY_VOLUME)
      supporting = emptyList()
    } else {
      primary = listOf(TrainingCapability.DISTANCE_TOLERANCE, TrainingCapability.AEROBIC_BASE) +
        if (marathonOrLonger) listOf(TrainingCapability.WEEKLY_VOLUME) else emptyList()
      secondary = if (marathonOrLonger) listOf(
        TrainingCapability.RECOVERY_TOLERANCE, TrainingCapability.RACE_SPECIFIC_ENDURANCE,
      ) else if (longDistance) listOf(TrainingCapability.WEEKLY_VOLUME, TrainingCapability.RECOVERY_TOLERANCE)
      else listOf(TrainingCapability.CONTINUOUS_RUNNING, TrainingCapability.WEEKLY_VOLUME)
      supporting = if (longDistance) listOf(TrainingCapability.CONTINUOUS_RUNNING) +
        if (marathonOrLonger) emptyList() else listOf(TrainingCapability.RACE_SPECIFIC_ENDURANCE)
      else emptyList()
    }

    val confidence = when {
      continuous == null || tolerated == null || state.toleratedSessionCount < 3 ||
        state.recentContinuity.continuousRuns < 3 ||
        state.capabilityConfidence in setOf(ProfileConfidence.VERY_LOW, ProfileConfidence.LOW) -> ProfileConfidence.LOW
      state.capabilityConfidence == ProfileConfidence.HIGH && state.toleratedSessionCount >= 5 &&
        state.recentContinuity.continuousRuns >= 5 -> ProfileConfidence.HIGH
      else -> ProfileConfidence.MEDIUM
    }
    val reasons = buildList {
      add(when (goal.goalType) {
        GoalType.COMPLETE_DISTANCE -> "The goal is continuous ${distance(target)} completion; distance tolerance and aerobic development take precedence over speed."
        GoalType.IMPROVE_PERFORMANCE -> "The goal is faster ${distance(target)} performance; threshold, running economy, and race-specific endurance are relevant development capabilities."
        GoalType.TARGET_TIME -> "The ${distance(target)} target time is goal context only; it does not establish current easy, threshold, or sustainable race pace."
      })
      if (continuous == null) {
        add("Continuous-running capability is insufficiently observed for the ${distance(target)} goal; missing evidence does not establish inability.")
      } else {
        add("Continuous running has been demonstrated to ${distance(continuous)} for the ${distance(target)} goal.")
      }
      state.longestCompletedDistanceMeters?.let { completed ->
        if (continuous == null || completed > continuous) {
          add("Completed distance of ${distance(completed)} is separate from demonstrated continuous distance.")
        }
      }
      if (tolerated == null) {
        add("Comfortable aerobic distance tolerance is insufficiently observed; priorities remain provisional.")
      } else {
        val sessions = if (state.toleratedSessionCount == 1) "session provides" else "sessions provide"
        add("${state.toleratedSessionCount} tolerated aerobic $sessions distance evidence up to ${distance(tolerated)}.")
      }
      if (performanceDirection) {
        add("Repeated aerobic tolerance and continuous distance support moving beyond first-distance development toward ${distance(target)} performance.")
        add("Athlete State does not establish current threshold or speed capability; these priorities describe goal demands, not measured pace deficits or permission for hard workouts.")
      } else if (goal.goalType != GoalType.COMPLETE_DISTANCE) {
        add("Repeated tolerance and continuous evidence at the goal distance are not yet jointly established; foundational distance development remains important even with a performance goal.")
      } else if (shortDistance && closeWithTolerance) {
        add("Observed continuity and repeated aerobic tolerance are close to or beyond the goal distance; consolidate aerobic consistency while retaining completion-directed distance and continuity priorities.")
      }
      if (longDistance) {
        add(if (marathonOrLonger)
          "Marathon-length or longer demands make sustained weekly volume, recovery tolerance, and race-specific aerobic durability important."
        else "Half-marathon-length demands add weekly volume tolerance, recovery tolerance, and sustained aerobic durability to distance development.")
      }
      add("Recent training totals ${distance(state.recentActualVolume28DaysMeters)} in 28 days at ${String.format(Locale.ROOT, "%.1f", state.recentRunFrequencyPerWeek)} runs/week.")
      if (state.recentRunFrequencyPerWeek < 2.0 || state.recentActualVolume28DaysMeters / 4 < target * 2) {
        add("The observed volume or frequency provides limited repeated exposure for this distance; aerobic consistency and weekly volume remain relevant, without prescribing an increase.")
      }
      if (confidence == ProfileConfidence.LOW) {
        add("Recent relevant observations are limited, so these capability priorities have low interpretation confidence.")
      }
    }
    return GoalStrategy(primary, secondary, supporting, confidence, reasons)
  }

  private fun distance(meters: Double) = String.format(Locale.ROOT, "%.2f km", meters / 1_000.0)
}
