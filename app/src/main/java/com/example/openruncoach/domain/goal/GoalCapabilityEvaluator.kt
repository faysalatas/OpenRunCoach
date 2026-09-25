package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.DistanceBenchmark
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.profile.StandardRunningDistance

data class GoalTypeCapability(
  val goalType: GoalType,
  val supportedByCurrentData: Boolean,
  val explanation: String,
)

data class DistanceGoalCapability(
  val distance: GoalDistance,
  val hasCompletedDistance: Boolean,
  val benchmark: DistanceBenchmark?,
  val longestRecentRunDistanceMeters: Double?,
  val options: List<GoalTypeCapability>,
) {
  val hasVerifiedBenchmark: Boolean get() = benchmark?.personalBest != null
}

data class GoalCapabilities(
  val profileAvailable: Boolean,
  val confidence: ProfileConfidence,
  val longestRecentRunDistanceMeters: Double?,
  val byDistance: Map<GoalDistance, DistanceGoalCapability>,
)

class GoalCapabilityEvaluator {
  fun evaluate(profile: AthletePerformanceProfile?): GoalCapabilities {
    val confidence = profile?.profileConfidence ?: ProfileConfidence.VERY_LOW
    val longest = profile?.longestRunLast28Days?.distanceMeters
    val capabilities = GoalDistance.entries.associateWith { distance ->
      val targetMeters = distance.meters
      val benchmark = profile?.benchmarkFor(distance)
      val hasCompleted = benchmark?.personalBest != null || (targetMeters != null && longest != null && longest >= targetMeters)
      val benchmarkIsReliable = benchmark?.personalBest != null &&
        benchmark.personalBest.confidence >= ProfileConfidence.MEDIUM &&
        confidence >= ProfileConfidence.MEDIUM
      DistanceGoalCapability(
        distance = distance,
        hasCompletedDistance = hasCompleted,
        benchmark = benchmark,
        longestRecentRunDistanceMeters = longest,
        options = listOf(
          GoalTypeCapability(
            GoalType.COMPLETE_DISTANCE,
            supportedByCurrentData = profile != null,
            explanation = if (hasCompleted) "You have already demonstrated ${distance.displayName}. Improve Performance or Target Time may be more relevant, but Complete Distance remains available."
            else "Build toward completing this distance; a previous result is not required.",
          ),
          GoalTypeCapability(
            GoalType.IMPROVE_PERFORMANCE,
            supportedByCurrentData = benchmark?.personalBest != null,
            explanation = if (benchmark?.personalBest != null) "A verified benchmark is available for comparison."
            else "Complete a few qualifying runs at this distance for better guidance.",
          ),
          GoalTypeCapability(
            GoalType.TARGET_TIME,
            supportedByCurrentData = benchmarkIsReliable,
            explanation = when {
              benchmark?.personalBest == null -> "No reliable benchmark is available for this distance yet."
              !benchmarkIsReliable -> "A benchmark exists, but current data confidence is limited."
              else -> "A verified benchmark is available to compare with your target."
            },
          ),
        ),
      )
    }
    return GoalCapabilities(profile != null, confidence, longest, capabilities)
  }
}

private fun AthletePerformanceProfile.benchmarkFor(distance: GoalDistance): DistanceBenchmark? {
  val standard = when (distance) {
    GoalDistance.FIVE_K -> StandardRunningDistance.FIVE_K
    GoalDistance.TEN_K -> StandardRunningDistance.TEN_K
    GoalDistance.HALF_MARATHON -> StandardRunningDistance.HALF_MARATHON
    GoalDistance.MARATHON -> StandardRunningDistance.MARATHON
    GoalDistance.CUSTOM -> null
  }
  return standard?.let { target -> performanceBenchmarks.firstOrNull { it.targetDistance == target } }
}

internal fun AthletePerformanceProfile.goalBenchmark(distance: GoalDistance): DistanceBenchmark? = benchmarkFor(distance)
