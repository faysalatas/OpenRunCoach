package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.coaching.CoachingEngine

class PostRunEvaluator(private val coachingEngine: CoachingEngine = CoachingEngine()) {
  fun evaluate(
    prescribedWorkout: AdjustedWorkout,
    actualRun: RunSummary,
    feedback: RunFeedback,
    recentCanonicalRunHistory: List<RunSummary>,
  ): CoachingDecision? {
    val baseDecision = coachingEngine.analyze(actualRun, recentCanonicalRunHistory, feedback) ?: return null
    val prescribedSeconds = prescribedWorkout.durationMinutes * 60L
    val completionReason = when {
      prescribedWorkout.type == WorkoutType.REST -> "Rest was prescribed before checkout"
      actualRun.durationSeconds >= prescribedSeconds -> "Completed the prescribed duration"
      else -> "Completed less than the prescribed duration"
    }
    return baseDecision.copy(reasons = baseDecision.reasons + completionReason)
  }
}
