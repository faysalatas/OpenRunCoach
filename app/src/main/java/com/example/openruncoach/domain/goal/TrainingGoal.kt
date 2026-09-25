package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.profile.ProfileConfidence
import java.time.Instant

enum class GoalDistance(val meters: Double?, val displayName: String) {
  FIVE_K(5_000.0, "5K"),
  TEN_K(10_000.0, "10K"),
  HALF_MARATHON(21_097.5, "Half Marathon"),
  MARATHON(42_195.0, "Marathon"),
  CUSTOM(null, "Custom"),
}

enum class GoalType {
  COMPLETE_DISTANCE,
  IMPROVE_PERFORMANCE,
  TARGET_TIME,
}

enum class GoalStatus { ACTIVE, COMPLETED, PAUSED, ARCHIVED }

enum class GoalAssessmentLevel { WELL_SUPPORTED, PLAUSIBLE, LIMITED_DATA, LARGE_STEP }

data class GoalBenchmarkSnapshot(
  val durationSeconds: Long,
  val runId: String,
  val observedAt: Instant,
)

data class StartingPerformanceSnapshot(
  val bestBenchmark: GoalBenchmarkSnapshot?,
  val mostRecentBenchmark: GoalBenchmarkSnapshot?,
  val averageWeeklyDistanceMeters: Double,
  val averageWeeklyDurationSeconds: Long,
  val longestRecentRunDistanceMeters: Double?,
  val longestRecentRunDurationSeconds: Long?,
  val averageRunsPerWeek: Double,
  val typicalEasyPaceSecondsPerKm: Double?,
  val typicalEasyHeartRate: Double?,
  val profileConfidence: ProfileConfidence,
  val capturedAt: Instant,
)

data class GoalAssessment(
  val level: GoalAssessmentLevel,
  val reasons: List<String>,
)

data class TrainingGoal(
  val id: String,
  val distance: GoalDistance,
  val customDistanceMeters: Double?,
  val goalType: GoalType,
  val targetTimeSeconds: Long?,
  val createdAt: Instant,
  val status: GoalStatus,
  val startingPerformanceSnapshot: StartingPerformanceSnapshot,
  val confidenceAtCreation: ProfileConfidence,
  val assessmentAtCreation: GoalAssessment,
) {
  val distanceMeters: Double get() = requireGoalDistanceMeters(distance, customDistanceMeters)
}

data class TrainingGoalDraft(
  val distance: GoalDistance,
  val customDistanceMeters: Double? = null,
  val goalType: GoalType,
  val targetTimeSeconds: Long? = null,
)

data class GoalValidationResult(val isValid: Boolean, val errors: List<String>)

fun validateGoalDraft(draft: TrainingGoalDraft): GoalValidationResult {
  val errors = buildList {
    if (draft.distance == GoalDistance.CUSTOM) {
      val meters = draft.customDistanceMeters
      if (meters == null || !meters.isFinite() || meters < MIN_CUSTOM_DISTANCE_METERS || meters > MAX_CUSTOM_DISTANCE_METERS) {
        add("Custom distance must be between 0.1 km and 200 km.")
      }
    } else if (draft.customDistanceMeters != null) {
      add("Custom distance is only valid for a custom goal.")
    }
    if (draft.goalType == GoalType.TARGET_TIME && (draft.targetTimeSeconds == null || draft.targetTimeSeconds <= 0)) {
      add("Enter a target time greater than zero.")
    }
    if (draft.goalType != GoalType.TARGET_TIME && draft.targetTimeSeconds != null) {
      add("Target time is only valid for a target-time goal.")
    }
  }
  return GoalValidationResult(errors.isEmpty(), errors)
}

fun requireGoalDistanceMeters(distance: GoalDistance, customDistanceMeters: Double?): Double =
  distance.meters ?: requireNotNull(customDistanceMeters) { "A custom distance is required." }.also {
    require(it.isFinite() && it in MIN_CUSTOM_DISTANCE_METERS..MAX_CUSTOM_DISTANCE_METERS) {
      "Custom distance must be between 0.1 km and 200 km."
    }
  }

fun targetPaceSecondsPerKm(targetTimeSeconds: Long, distanceMeters: Double): Double {
  require(targetTimeSeconds > 0) { "Target time must be positive." }
  require(distanceMeters > 0 && distanceMeters.isFinite()) { "Distance must be positive." }
  return targetTimeSeconds / (distanceMeters / 1_000.0)
}

data class PerformanceComparison(
  val differenceSeconds: Long,
  val improvementFraction: Double,
)

fun comparePerformance(currentDurationSeconds: Long, targetDurationSeconds: Long): PerformanceComparison {
  require(currentDurationSeconds > 0 && targetDurationSeconds > 0)
  return PerformanceComparison(
    differenceSeconds = currentDurationSeconds - targetDurationSeconds,
    improvementFraction = (currentDurationSeconds - targetDurationSeconds).toDouble() / currentDurationSeconds,
  )
}

fun parseGoalDuration(value: String): Long? {
  val parts = value.trim().split(':')
  if (parts.size !in 2..3 || parts.any { it.isEmpty() || it.any { char -> !char.isDigit() } }) return null
  val numbers = parts.map { it.toLongOrNull() ?: return null }
  if (numbers.drop(1).any { it !in 0..59 }) return null
  val seconds = if (numbers.size == 2) numbers[0] * 60 + numbers[1]
  else numbers[0] * 3_600 + numbers[1] * 60 + numbers[2]
  return seconds.takeIf { it > 0 }
}

/** Returns a new list where the new goal is active and every previous active goal is archived. */
fun activateGoal(existingGoals: List<TrainingGoal>, newGoal: TrainingGoal): List<TrainingGoal> =
  existingGoals.map { if (it.status == GoalStatus.ACTIVE) it.copy(status = GoalStatus.ARCHIVED) else it } +
    newGoal.copy(status = GoalStatus.ACTIVE)

const val MIN_CUSTOM_DISTANCE_METERS = 100.0
const val MAX_CUSTOM_DISTANCE_METERS = 200_000.0
