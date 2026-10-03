package com.example.openruncoach.domain.coaching

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import com.example.openruncoach.domain.workout.WorkoutExecutionStatus
import com.example.openruncoach.domain.workout.PaceStability
import com.example.openruncoach.domain.workout.PrescriptionAdherence
import com.example.openruncoach.domain.workout.SessionCost
import com.example.openruncoach.domain.workout.WorkoutCompletionOutcome
import com.example.openruncoach.domain.workout.WorkoutResponse
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale

enum class CoachingAction { PROGRESS, SMALL_PROGRESS, MAINTAIN, REDUCE, RECOVERY }

data class CoachingDecision(
  val action: CoachingAction,
  val score: Int,
  val reasons: List<String>,
  val generatedAt: Instant,
  val workoutResponse: WorkoutResponse? = null,
)

class CoachingEngine(private val clock: Clock = Clock.systemUTC()) {
  fun analyze(
    latestRun: RunSummary,
    recentRunHistory: List<RunSummary>,
    feedback: RunFeedback,
    executionResult: WorkoutExecutionResult? = null,
    workoutResponse: WorkoutResponse? = null,
  ): CoachingDecision? {
    val canonicalHistory = recentRunHistory.filter { it.isCanonical && it.relevance == RunRelevance.ACTIVE }
    val newestCanonicalRun = canonicalHistory.maxWithOrNull(
      compareBy<RunSummary>({ it.startTime }, { it.healthConnectRecordId })
    )
    if (!latestRun.isCanonical || latestRun.relevance != RunRelevance.ACTIVE ||
      feedback.runId != latestRun.healthConnectRecordId ||
      newestCanonicalRun?.healthConnectRecordId != latestRun.healthConnectRecordId
    ) return null
    val score = completionScore(feedback.completionStatus, executionResult) +
      rpeScore(feedback.rpe) +
      painScore(feedback.painLevel) +
      muscleScore(feedback.muscleFatigue) +
      cardioScore(feedback.cardioEffort)
    val baseAction = when {
      feedback.painLevel == PainLevel.SIGNIFICANT -> CoachingAction.RECOVERY
      isAborted(feedback, executionResult) && feedback.painLevel != PainLevel.NONE -> CoachingAction.RECOVERY
      isAborted(feedback, executionResult) -> CoachingAction.REDUCE
      executionResult?.status == WorkoutExecutionStatus.PARTIALLY_COMPLETED && score >= 0 -> CoachingAction.MAINTAIN
      score >= 5 -> CoachingAction.PROGRESS
      score >= 3 -> CoachingAction.SMALL_PROGRESS
      score >= 0 -> CoachingAction.MAINTAIN
      score >= -3 -> CoachingAction.REDUCE
      else -> CoachingAction.RECOVERY
    }
    val action = applyResponseConstraints(baseAction, workoutResponse)
    return CoachingDecision(
      action = action,
      score = score,
      reasons = feedbackReasons(feedback, executionResult) +
        listOfNotNull(executionResult?.explanation) +
        workoutResponse?.reasons.orEmpty() +
        recentVolumeReason(latestRun, canonicalHistory),
      generatedAt = clock.instant(),
      workoutResponse = workoutResponse,
    )
  }
}

private fun applyResponseConstraints(
  action: CoachingAction,
  response: WorkoutResponse?,
): CoachingAction {
  response ?: return action
  if (response.pain == PainLevel.SIGNIFICANT) return CoachingAction.RECOVERY
  if (response.completion == WorkoutCompletionOutcome.ABORTED) return action
  if (response.sessionCost == SessionCost.HIGH && (
      response.continuity == RunContinuityEvidence.INTERRUPTED ||
        response.paceStability == PaceStability.STRONG_FADE ||
        response.pain != PainLevel.NONE
      )) return if (action == CoachingAction.RECOVERY) CoachingAction.RECOVERY else CoachingAction.REDUCE
  val avoidProgression = response.sessionCost == SessionCost.HIGH ||
    response.sessionCost == SessionCost.ELEVATED ||
    response.adherence == PrescriptionAdherence.MEANINGFUL_DEVIATION ||
    response.pain == PainLevel.MILD
  return if (avoidProgression && (action == CoachingAction.PROGRESS || action == CoachingAction.SMALL_PROGRESS)) {
    CoachingAction.MAINTAIN
  } else action
}

private fun isAborted(feedback: RunFeedback, execution: WorkoutExecutionResult?): Boolean =
  feedback.completionStatus == CompletionStatus.ABORTED || execution?.status == WorkoutExecutionStatus.ABORTED

private const val COMPLETED_SCORE = 2
private const val PARTIAL_SCORE = 0
private const val ABORTED_SCORE = -2

private fun completionScore(status: CompletionStatus, execution: WorkoutExecutionResult?): Int = when (execution?.status) {
  WorkoutExecutionStatus.COMPLETED_AS_PLANNED,
  WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION,
  WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION -> COMPLETED_SCORE
  WorkoutExecutionStatus.PARTIALLY_COMPLETED -> PARTIAL_SCORE
  WorkoutExecutionStatus.ABORTED -> ABORTED_SCORE
  null -> when (status) {
    CompletionStatus.COMPLETED -> COMPLETED_SCORE
    CompletionStatus.PARTIAL -> PARTIAL_SCORE
    CompletionStatus.ABORTED -> ABORTED_SCORE
  }
}

private fun rpeScore(rpe: Int): Int = when (rpe) {
  in 1..2 -> 2
  in 3..5 -> 1
  in 6..7 -> 0
  8 -> -1
  else -> -2
}

private fun painScore(pain: PainLevel): Int = when (pain) {
  PainLevel.NONE -> 1
  PainLevel.MILD -> 0
  PainLevel.SIGNIFICANT -> -4
}

private fun muscleScore(fatigue: MuscleFatigue): Int = when (fatigue) {
  MuscleFatigue.NONE -> 1
  MuscleFatigue.MILD -> 0
  MuscleFatigue.MODERATE -> -1
  MuscleFatigue.SEVERE -> -2
}

private fun cardioScore(effort: CardioEffort): Int = when (effort) {
  CardioEffort.EASY, CardioEffort.APPROPRIATE -> 1
  CardioEffort.HARD -> -1
  CardioEffort.LIMIT -> -2
}

private fun feedbackReasons(feedback: RunFeedback, execution: WorkoutExecutionResult?): List<String> = listOf(
  when (execution?.status) {
    WorkoutExecutionStatus.COMPLETED_AS_PLANNED -> "Workout completed as planned"
    WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION -> "Workout completed with a minor prescription deviation"
    WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION -> "Workout completed with a meaningful prescription deviation"
    WorkoutExecutionStatus.PARTIALLY_COMPLETED -> "Workout partially completed"
    WorkoutExecutionStatus.ABORTED -> "Workout aborted"
    null -> when (feedback.completionStatus) {
      CompletionStatus.COMPLETED -> "Workout completed"
      CompletionStatus.PARTIAL -> "Workout partially completed"
      CompletionStatus.ABORTED -> "Workout aborted"
    }
  },
  "RPE ${feedback.rpe}/10",
  when (feedback.painLevel) {
    PainLevel.NONE -> "No pain reported"
    PainLevel.MILD -> "Mild pain reported"
    PainLevel.SIGNIFICANT -> "Significant pain reported"
  },
  when (feedback.muscleFatigue) {
    MuscleFatigue.NONE -> "No muscle fatigue"
    MuscleFatigue.MILD -> "Mild muscle fatigue"
    MuscleFatigue.MODERATE -> "Moderate muscle fatigue"
    MuscleFatigue.SEVERE -> "Severe muscle fatigue"
  },
  when (feedback.cardioEffort) {
    CardioEffort.EASY -> "Cardio effort easy"
    CardioEffort.APPROPRIATE -> "Cardio effort appropriate"
    CardioEffort.HARD -> "Cardio effort hard"
    CardioEffort.LIMIT -> "Cardio effort at the limit"
  },
)

private fun recentVolumeReason(latestRun: RunSummary, history: List<RunSummary>): String {
  val windowStart = latestRun.startTime.minus(Duration.ofDays(7))
  val recent = history.filter { it.startTime >= windowStart && it.startTime <= latestRun.startTime }
    .distinctBy(RunSummary::healthConnectRecordId)
  val kilometers = recent.mapNotNull(RunSummary::distanceMeters).sum() / 1000.0
  return "Recent canonical volume: ${"%.1f".format(Locale.ROOT, kilometers)} km across ${recent.size} runs"
}
