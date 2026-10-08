package com.example.openruncoach.domain.evidence

import com.example.openruncoach.domain.*
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.workout.*

/** Supplies canonical actuals to the unchanged eligibility policy. No measurements are persisted. */
object CanonicalTrainingEvidence {
  fun evaluate(
    run: RunSummary,
    metrics: RunMetrics? = null,
    feedback: RunFeedback? = null,
    execution: WorkoutExecutionResult? = null,
    associatedWorkout: StructuredWorkout? = null,
    associatedWorkoutId: String? = null,
    observedCompletion: CompletionStatus? = null,
  ): Map<TrainingEvidencePurpose, EvidenceDecision> {
    val actual = RunActualResolver.resolve(run, metrics)
    // Private policy input only: remove enrichment distance/pace precedence while retaining HR/cadence.
    // This projection is neither a provider measurement nor an exported/persisted RunMetrics snapshot.
    val policyInput = metrics?.copy(
      durationSeconds = metrics.durationSeconds.copy(value = actual.durationSeconds),
      distanceMeters = null,
      averagePaceSecondsPerKm = null,
    )
    return TrainingEvidencePolicy.evaluate(actual, policyInput, feedback, execution, associatedWorkout,
      associatedWorkoutId, observedCompletion)
  }
}
