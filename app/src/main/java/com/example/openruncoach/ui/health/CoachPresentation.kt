package com.example.openruncoach.ui.health

import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.coaching.CoachingAction
import com.example.openruncoach.domain.workout.PaceStability
import com.example.openruncoach.domain.workout.WorkoutCompletionOutcome
import com.example.openruncoach.domain.workout.SessionCost
import com.example.openruncoach.ui.common.formatDistance

internal data class CoachPresentation(val observations: List<String>, val implication: String?)

/** Wording of existing evidence/action only; never chooses a recommendation. */
internal fun coachPresentation(state: RunDetailUiState): CoachPresentation {
  val response = state.workoutResponse
  val observations = buildList {
    if (response != null) {
      val completion = when (response.completion) {
        WorkoutCompletionOutcome.ABORTED -> "Stopped"
        WorkoutCompletionOutcome.PARTIAL -> "Partially completed"
        WorkoutCompletionOutcome.COMPLETED -> "Completed"
      }
      val actual = response.actualDistanceMeters
      val planned = response.plannedDistanceMeters
      add(if (actual != null && planned != null) "$completion after ${formatDistance(actual)} of ${formatDistance(planned)}"
        else if (actual != null) "$completion · ${formatDistance(actual)}" else completion)
      if (response.continuity == RunContinuityEvidence.INTERRUPTED) add("Sustained walking/stopped time detected")
      if (response.pain == PainLevel.SIGNIFICANT) add("Significant pain reported")
      else if (response.pain == PainLevel.MILD) add("Mild pain reported")
      if (response.sessionCost == SessionCost.HIGH) add("High session cost")
      else if (response.sessionCost == SessionCost.ELEVATED) add("Elevated session cost")
      if (response.paceStability == PaceStability.STRONG_FADE) add("Strong pace fade detected")
      if (size < 4 && response.continuity == RunContinuityEvidence.CONTINUOUS) add("Continuous running demonstrated")
    } else {
      state.feedback?.let { feedback ->
        add("${feedback.completionStatus.name.lowercase().replaceFirstChar(Char::titlecase)} run")
        if (feedback.painLevel == PainLevel.SIGNIFICANT) add("Significant pain reported")
        else if (feedback.painLevel == PainLevel.MILD) add("Mild pain reported")
      }
    }
  }.take(4)
  val implication = when (state.coachingDecision?.action) {
    CoachingAction.RECOVERY -> "Prioritize recovery before progressing the next session."
    CoachingAction.REDUCE -> "Reduce the next training load."
    CoachingAction.MAINTAIN -> "Maintain the current training load."
    CoachingAction.SMALL_PROGRESS -> "Keep the next progression small."
    CoachingAction.PROGRESS -> "Progress the next training load."
    null -> null
  }
  return CoachPresentation(observations, implication)
}
