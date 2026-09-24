package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.profile.AthletePerformanceProfile

class GoalAssessmentEvaluator {
  fun evaluate(draft: TrainingGoalDraft, profile: AthletePerformanceProfile?): GoalAssessment {
    if (profile == null) return GoalAssessment(
      GoalAssessmentLevel.LIMITED_DATA,
      listOf("No performance profile is available yet.", "The goal is saved as your choice and can be reviewed as more runs are recorded."),
    )

    val benchmark = profile.goalBenchmark(draft.distance)?.personalBest
    val distanceMeters = requireGoalDistanceMeters(draft.distance, draft.customDistanceMeters)
    val longest = profile.longestRunLast28Days?.distanceMeters

    if (draft.goalType == GoalType.TARGET_TIME) {
      if (benchmark == null) return GoalAssessment(
        GoalAssessmentLevel.LIMITED_DATA,
        listOf("No reliable ${draft.distance.displayName} benchmark exists for comparison."),
      )
      val comparison = comparePerformance(benchmark.durationSeconds, requireNotNull(draft.targetTimeSeconds))
      val percent = comparison.improvementFraction * 100.0
      return when {
        percent > LARGE_TARGET_IMPROVEMENT_PERCENT -> GoalAssessment(
          GoalAssessmentLevel.LARGE_STEP,
          listOf("A verified benchmark exists.", "The target is ${formatAssessmentPercent(percent)} faster than the current best."),
        )
        percent > CLOSE_TARGET_IMPROVEMENT_PERCENT -> GoalAssessment(
          GoalAssessmentLevel.PLAUSIBLE,
          listOf("A verified benchmark exists.", "The target requires ${formatAssessmentPercent(percent)} improvement from the current best."),
        )
        else -> GoalAssessment(
          GoalAssessmentLevel.WELL_SUPPORTED,
          listOf("A verified benchmark exists.", if (percent > 0) "The target is close to current observed performance." else "The target is at or slower than current observed performance."),
        )
      }
    }

    if (draft.goalType == GoalType.IMPROVE_PERFORMANCE) {
      return if (benchmark == null) GoalAssessment(
        GoalAssessmentLevel.LIMITED_DATA,
        listOf("No reliable ${draft.distance.displayName} benchmark exists yet.", "Improvement can be measured after qualifying runs are recorded."),
      ) else GoalAssessment(
        GoalAssessmentLevel.WELL_SUPPORTED,
        listOf("A verified ${draft.distance.displayName} benchmark is available.", "Future qualifying results can be compared with this starting point."),
      )
    }

    if (longest == null) return GoalAssessment(
      GoalAssessmentLevel.LIMITED_DATA,
      listOf("No recent run distance is available to compare with this completion goal."),
    )
    val ratio = distanceMeters / longest
    return when {
      ratio > LARGE_DISTANCE_STEP_RATIO -> GoalAssessment(
        GoalAssessmentLevel.LARGE_STEP,
        listOf("The selected distance is more than twice the longest recent run.", "The longest recent run is ${formatAssessmentDistance(longest)}."),
      )
      benchmark != null || longest >= distanceMeters -> GoalAssessment(
        GoalAssessmentLevel.WELL_SUPPORTED,
        listOf("The selected distance has already been demonstrated in recent running data."),
      )
      else -> GoalAssessment(
        GoalAssessmentLevel.PLAUSIBLE,
        listOf("The distance has not yet been demonstrated.", "The longest recent run is ${formatAssessmentDistance(longest)}."),
      )
    }
  }
}

private fun formatAssessmentPercent(value: Double) = "%.1f%%".format(java.util.Locale.US, value)
private fun formatAssessmentDistance(meters: Double) = "%.2f km".format(java.util.Locale.US, meters / 1_000.0)
private const val CLOSE_TARGET_IMPROVEMENT_PERCENT = 5.0
private const val LARGE_TARGET_IMPROVEMENT_PERCENT = 15.0
private const val LARGE_DISTANCE_STEP_RATIO = 2.0
