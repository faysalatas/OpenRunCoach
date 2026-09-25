package com.example.openruncoach.domain.plan

import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.profile.StandardRunningDistance

enum class TrainingDataSufficiency { SUFFICIENT, PARTIALLY_SUFFICIENT, ASSESSMENT_REQUIRED }

enum class AssessmentRunType {
  INITIAL_BASELINE,
  EASY_PACE_ASSESSMENT,
  CONTINUOUS_RUNNING_ASSESSMENT,
  GOAL_DISTANCE_BASELINE,
}

data class TrainingDataSufficiencyResult(
  val level: TrainingDataSufficiency,
  val canPrescribeEasyPace: Boolean,
  val canPrescribeLongRunPace: Boolean,
  val canPrescribeQualityPace: Boolean,
  val hasGoalDistanceBenchmark: Boolean,
  val canGenerateNormalPlan: Boolean,
  val recommendedAssessment: AssessmentRunType?,
  val reasons: List<String>,
)

class TrainingDataSufficiencyEvaluator(
  private val maximumAssessmentAttemptsBeforeConservativePlan: Int = 2,
) {
  fun evaluate(
    profile: AthletePerformanceProfile,
    goal: TrainingGoal,
    runMetricsHistory: List<RunMetrics>,
    feedbackAvailable: Boolean,
    profileConfidence: ProfileConfidence = profile.profileConfidence,
    completedAssessmentAttempts: Int = 0,
  ): TrainingDataSufficiencyResult {
    val validMetricRuns = runMetricsHistory.count { metrics ->
      metrics.durationSeconds.value > 0 &&
        metrics.distanceMeters?.value?.let { it.isFinite() && it > 0.0 } == true &&
        metrics.averagePaceSecondsPerKm?.value?.let { it.isFinite() && it > 0.0 } == true
    }
    val hasEasyEvidence = profile.typicalEasyPace != null || (validMetricRuns >= 2 && feedbackAvailable)
    val hasDistanceEvidence = profile.longestRunLast28Days?.distanceMeters?.let { it.isFinite() && it > 0.0 } == true
    val hasLongRunEvidence = hasDistanceEvidence && profile.totalCanonicalRuns >= 2
    val hasGoalBenchmark = profile.performanceBenchmarks.any {
      it.targetDistance == goal.distance.standardDistance() && it.personalBest != null
    }
    val hasQualityEvidence = profileConfidence >= ProfileConfidence.MEDIUM &&
      profile.runsLast28Days >= 6 && feedbackAvailable &&
      (goal.goalType == GoalType.COMPLETE_DISTANCE || hasGoalBenchmark)
    val normalEvidence = profile.totalCanonicalRuns >= 3 && hasDistanceEvidence &&
      (hasEasyEvidence || profile.distanceLast28DaysMeters > 0.0 && profile.durationLast28DaysSeconds > 0L)
    val loopFallback = completedAssessmentAttempts >= maximumAssessmentAttemptsBeforeConservativePlan &&
      feedbackAvailable && (validMetricRuns > 0 || normalEvidence)
    val canGenerateNormalPlan = normalEvidence || loopFallback
    val level = when {
      !canGenerateNormalPlan -> TrainingDataSufficiency.ASSESSMENT_REQUIRED
      hasEasyEvidence && hasLongRunEvidence &&
        (goal.goalType == GoalType.COMPLETE_DISTANCE || hasQualityEvidence) -> TrainingDataSufficiency.SUFFICIENT
      else -> TrainingDataSufficiency.PARTIALLY_SUFFICIENT
    }
    val assessment = if (level != TrainingDataSufficiency.ASSESSMENT_REQUIRED) null else when {
      profile.totalCanonicalRuns == 0 || !hasDistanceEvidence -> AssessmentRunType.INITIAL_BASELINE
      !hasEasyEvidence -> AssessmentRunType.EASY_PACE_ASSESSMENT
      goal.goalType != GoalType.COMPLETE_DISTANCE && !hasGoalBenchmark &&
        requireNotNull(profile.longestRunLast28Days).distanceMeters?.let { it >= goal.distanceMeters * 0.9 } == true ->
        AssessmentRunType.GOAL_DISTANCE_BASELINE
      else -> AssessmentRunType.CONTINUOUS_RUNNING_ASSESSMENT
    }
    val reasons = buildList {
      add(if (hasEasyEvidence) "Usable easy-pace evidence is available." else "A reliable easy-pace baseline is not available yet.")
      add(if (hasLongRunEvidence) "Recent distance history supports a conservative long run." else "More continuous-distance evidence is needed for long-run guidance.")
      add(if (hasGoalBenchmark) "A verified benchmark exists for the selected distance." else "No verified benchmark exists for the selected distance.")
      add(if (feedbackAvailable) "Post-run effort feedback is available." else "Post-run effort feedback is not available yet.")
      if (loopFallback) add("Completed assessments plus basic distance, duration, pace, and feedback allow conservative planning without heart-rate or cadence data.")
      if (level == TrainingDataSufficiency.ASSESSMENT_REQUIRED) add("An assessment run is safer than inventing a pace prescription from the current evidence.")
    }
    return TrainingDataSufficiencyResult(
      level,
      hasEasyEvidence,
      hasEasyEvidence && hasLongRunEvidence,
      hasQualityEvidence,
      hasGoalBenchmark,
      canGenerateNormalPlan,
      assessment,
      reasons,
    )
  }
}

private fun GoalDistance.standardDistance(): StandardRunningDistance? = when (this) {
  GoalDistance.FIVE_K -> StandardRunningDistance.FIVE_K
  GoalDistance.TEN_K -> StandardRunningDistance.TEN_K
  GoalDistance.HALF_MARATHON -> StandardRunningDistance.HALF_MARATHON
  GoalDistance.MARATHON -> StandardRunningDistance.MARATHON
  GoalDistance.CUSTOM -> null
}
