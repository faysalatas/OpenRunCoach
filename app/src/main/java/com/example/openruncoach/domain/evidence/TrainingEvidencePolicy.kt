package com.example.openruncoach.domain.evidence

import com.example.openruncoach.domain.*
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.workout.*

enum class TrainingEvidencePurpose {
  SESSION_FREQUENCY, EASY_PACE, EASY_HEART_RATE, EASY_CADENCE,
  CAPABILITY, ACTUAL_VOLUME, SAFETY_RECOVERY, ASSOCIATED_WORKOUT,
}

data class EvidenceDecision(val eligible: Boolean, val reason: String)

/** Recording-fragment guard, not a universal physiological minimum or a History filter. */
object TrainingEvidencePolicy {
  const val MINIMUM_STANDALONE_SESSION_SECONDS = 300L

  fun evaluate(
    run: RunSummary,
    metrics: RunMetrics? = null,
    feedback: RunFeedback? = null,
    execution: WorkoutExecutionResult? = null,
    associatedWorkout: StructuredWorkout? = null,
    associatedWorkoutId: String? = null,
    observedCompletion: CompletionStatus? = null,
  ): Map<TrainingEvidencePurpose, EvidenceDecision> {
    val active = run.isCanonical && run.relevance == RunRelevance.ACTIVE
    val duration = metrics?.durationSeconds?.value ?: run.durationSeconds
    val distance = metrics?.distanceMeters?.value ?: run.distanceMeters
    val pace = metrics?.averagePaceSecondsPerKm?.value ?: run.averagePaceSecondsPerKm
    val hr = metrics?.averageHeartRate?.value ?: run.averageHeartRate
    val cadence = metrics?.movingAverageCadence?.value ?: metrics?.averageCadence?.value
    val associated = !associatedWorkoutId.isNullOrBlank() || associatedWorkout?.type?.let { it != WorkoutType.REST } == true ||
      execution?.physicalRunId == run.physicalRunId
    val recorded = duration > 0 && (distance == null || distance.isFinite() && distance >= 0)
    val session = recorded && (associated || duration >= MINIMUM_STANDALONE_SESSION_SECONDS)
    val completion = observedCompletion ?: feedback?.completionStatus
    val completed = execution?.isCompleted ?: (completion != CompletionStatus.ABORTED && completion != CompletionStatus.PARTIAL)
    val easyCompleted = execution?.isCompleted ?: (completion == CompletionStatus.COMPLETED)
    val aerobicContext = associatedWorkout == null || associatedWorkout.purpose in setOf(
      WorkoutPurpose.AEROBIC_BASE, WorkoutPurpose.RECOVERY, WorkoutPurpose.ENDURANCE, WorkoutPurpose.ASSESSMENT,
    )
    val easy = session && easyCompleted && aerobicContext && feedback != null && feedback.rpe <= 4 &&
      feedback.cardioEffort in setOf(CardioEffort.EASY, CardioEffort.APPROPRIATE) &&
      feedback.painLevel != PainLevel.SIGNIFICANT && positive(distance) && positive(pace)
    fun decision(eligible: Boolean, reason: String) = if (!active) {
      EvidenceDecision(false, "Not an active canonical physical run.")
    } else EvidenceDecision(eligible, reason)
    val sessionReason = when {
      !recorded -> "No usable recorded duration/volume."
      associated -> "Authoritative associated workout context supports this session, including short prescriptions."
      session -> "Standalone recording meets the conservative 5-minute session evidence guard."
      else -> "Standalone recording is shorter than the 5-minute session evidence guard."
    }
    fun baseline(value: Double?) = decision(easy && positive(value), when {
      !session -> sessionReason
      !easy -> "Requires completed, low-effort aerobic evidence without significant pain and with valid distance/pace."
      !positive(value) -> "Required baseline metric is unavailable or invalid."
      else -> "Completed easy session with the required observed metric."
    })
    return linkedMapOf(
      TrainingEvidencePurpose.SESSION_FREQUENCY to decision(session, sessionReason),
      TrainingEvidencePurpose.EASY_PACE to baseline(pace),
      TrainingEvidencePurpose.EASY_HEART_RATE to baseline(hr),
      TrainingEvidencePurpose.EASY_CADENCE to baseline(cadence),
      TrainingEvidencePurpose.CAPABILITY to decision(session && completed && positive(distance),
        if (!session) sessionReason else "Capability requires completed session evidence and positive observed distance."),
      TrainingEvidencePurpose.ACTUAL_VOLUME to decision(recorded,
        "Actual recorded volume is retained independently of session length, effort, or completion."),
      TrainingEvidencePurpose.SAFETY_RECOVERY to decision(recorded || feedback != null,
        "Observed effort, fatigue and pain remain evidence even for short or aborted activities."),
      TrainingEvidencePurpose.ASSOCIATED_WORKOUT to decision(associated && recorded,
        "Requires authoritative workout association/execution and recorded activity."),
    )
  }

  private fun positive(value: Double?) = value != null && value.isFinite() && value > 0
}
