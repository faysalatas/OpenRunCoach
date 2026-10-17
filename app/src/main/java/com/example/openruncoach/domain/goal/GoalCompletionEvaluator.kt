package com.example.openruncoach.domain.goal

import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.RunActualResolver
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.belongsTo
import com.example.openruncoach.domain.metrics.RunContinuityEvaluator
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.workout.WorkoutLifecycleState

enum class GoalCompletionStatus {
  NOT_APPLICABLE,
  INVALID_RUN,
  WORKOUT_NOT_COMPLETED,
  DISTANCE_NOT_AVAILABLE,
  TARGET_NOT_REACHED,
  CONTINUITY_NOT_DEMONSTRATED,
  GOAL_DISTANCE_DEMONSTRATED,
}

data class GoalCompletionResult(
  val status: GoalCompletionStatus,
  val completed: Boolean,
  val targetDistanceMeters: Double,
  val observedDistanceMeters: Double?,
  val reasons: List<String>,
)

/** Provider-independent, explainable evaluation of a completed canonical run. */
class GoalCompletionEvaluator {
  fun evaluate(
    goal: TrainingGoal,
    canonicalRun: RunSummary,
    metrics: RunMetrics?,
    workoutState: WorkoutLifecycleState,
    feedback: RunFeedback? = null,
  ): GoalCompletionResult {
    val target = goal.distanceMeters
    if (goal.goalType != GoalType.COMPLETE_DISTANCE) {
      return result(GoalCompletionStatus.NOT_APPLICABLE, target, null, "Goal completion evaluation applies to Complete Distance goals.")
    }
    if (!canonicalRun.isCanonical || canonicalRun.relevance != RunRelevance.ACTIVE ||
      metrics?.runId?.let { it != canonicalRun.healthConnectRecordId } == true ||
      feedback?.let { !it.belongsTo(canonicalRun) } == true
    ) {
      return result(GoalCompletionStatus.INVALID_RUN, target, null, "The result must use the matching canonical run and metrics.")
    }
    if (workoutState !in completedStates || feedback?.completionStatus in setOf(CompletionStatus.PARTIAL, CompletionStatus.ABORTED)) {
      return result(GoalCompletionStatus.WORKOUT_NOT_COMPLETED, target, observedDistance(canonicalRun, metrics), "The workout was not recorded as completed.")
    }
    val observed = observedDistance(canonicalRun, metrics)
      ?: return result(GoalCompletionStatus.DISTANCE_NOT_AVAILABLE, target, null, "No valid distance is available for the completed run.")
    if (observed < target) {
      return result(
        GoalCompletionStatus.TARGET_NOT_REACHED,
        target,
        observed,
        "Observed distance was ${formatKm(observed)}, below the ${formatKm(target)} target.",
      )
    }
    when (RunContinuityEvaluator().evaluate(metrics)) {
      RunContinuityEvidence.INTERRUPTED -> return result(
        GoalCompletionStatus.CONTINUITY_NOT_DEMONSTRATED,
        target,
        observed,
        "The distance included a sustained walking or stopped interval, so continuous completion was not demonstrated.",
      )
      RunContinuityEvidence.UNKNOWN -> return result(
        GoalCompletionStatus.CONTINUITY_NOT_DEMONSTRATED,
        target,
        observed,
        "Continuous completion could not be verified because speed or cadence evidence was insufficient.",
      )
      RunContinuityEvidence.CONTINUOUS -> Unit
    }
    return GoalCompletionResult(
      GoalCompletionStatus.GOAL_DISTANCE_DEMONSTRATED,
      true,
      target,
      observed,
      listOf(
        "The canonical workout was completed.",
        "Observed distance ${formatKm(observed)} met or exceeded the ${formatKm(target)} target.",
        "Goal distance demonstrated.",
      ),
    )
  }

  private fun result(status: GoalCompletionStatus, target: Double, observed: Double?, reason: String) =
    GoalCompletionResult(status, false, target, observed, listOf(reason))

  private fun observedDistance(run: RunSummary, metrics: RunMetrics?): Double? =
    RunActualResolver.resolve(run, metrics).distanceMeters

  private fun formatKm(meters: Double) = "%.2f km".format(java.util.Locale.ROOT, meters / 1_000.0)

  private companion object {
    val completedStates = setOf(
      WorkoutLifecycleState.COMPLETED,
      WorkoutLifecycleState.CHECKOUT_REQUIRED,
      WorkoutLifecycleState.ANALYZED,
      WorkoutLifecycleState.NEXT_WORKOUT_CREATED,
    )
  }
}
