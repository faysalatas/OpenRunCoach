package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.RunActualResolver
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.belongsTo
import java.time.Clock
import java.time.Instant

enum class WorkoutExecutionStatus {
  COMPLETED_AS_PLANNED,
  COMPLETED_WITH_MINOR_DEVIATION,
  COMPLETED_WITH_PRESCRIPTION_DEVIATION,
  PARTIALLY_COMPLETED,
  ABORTED,
}

enum class WorkoutComparisonBasis { ORIGINAL_PRESCRIPTION, ADJUSTED_PRESCRIPTION }

data class WorkoutExecutionEvidence(
  /** Portion of the prescribed quality work or repetitions that was observed, when available. */
  val qualityWorkCompletionFraction: Double? = null,
) {
  init {
    require(qualityWorkCompletionFraction == null || qualityWorkCompletionFraction in 0.0..1.0)
  }
}

data class WorkoutExecutionResult(
  val plannedWorkoutId: String,
  val actualRunId: String,
  val status: WorkoutExecutionStatus,
  val comparisonBasis: WorkoutComparisonBasis,
  val intendedDistanceMeters: Double?,
  val intendedDurationSeconds: Long?,
  val actualDistanceMeters: Double?,
  val actualDurationSeconds: Long,
  val qualityWorkCompletionFraction: Double?,
  val explanation: String,
  val evaluatedAt: Instant,
  val physicalRunId: String = actualRunId,
) {
  init {
    require(plannedWorkoutId.isNotBlank() && actualRunId.isNotBlank() && physicalRunId.isNotBlank())
    require(intendedDistanceMeters == null || intendedDistanceMeters.isFinite() && intendedDistanceMeters > 0.0)
    require(intendedDurationSeconds == null || intendedDurationSeconds > 0L)
    require(actualDistanceMeters == null || actualDistanceMeters.isFinite() && actualDistanceMeters >= 0.0)
    require(actualDurationSeconds > 0L)
    require(qualityWorkCompletionFraction == null || qualityWorkCompletionFraction in 0.0..1.0)
    require(explanation.isNotBlank())
  }

  val isCompleted: Boolean
    get() = status == WorkoutExecutionStatus.COMPLETED_AS_PLANNED ||
      status == WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION ||
      status == WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION
}

/** Centralized, purpose-aware tolerances for normal training prescriptions. */
object WorkoutAdherencePolicy {
  const val AS_PLANNED_MINIMUM_FRACTION = 0.98
  const val AS_PLANNED_MAXIMUM_FRACTION = 1.10
  const val EASY_MINIMUM_COMPLETION_FRACTION = 0.90
  const val RECOVERY_MINIMUM_COMPLETION_FRACTION = 0.80
  const val ENDURANCE_MINIMUM_COMPLETION_FRACTION = 0.92
  const val QUALITY_MINIMUM_COMPLETION_FRACTION = 0.80
  const val GENERAL_MINIMUM_COMPLETION_FRACTION = 0.90
  const val RPE_ALLOWED_DEVIATION = 1

  fun minimumCompletionFraction(purpose: WorkoutPurpose): Double = when (purpose) {
    WorkoutPurpose.RECOVERY -> RECOVERY_MINIMUM_COMPLETION_FRACTION
    WorkoutPurpose.ENDURANCE -> ENDURANCE_MINIMUM_COMPLETION_FRACTION
    WorkoutPurpose.LACTATE_THRESHOLD,
    WorkoutPurpose.SPEED,
    WorkoutPurpose.VO2MAX,
    WorkoutPurpose.RUNNING_ECONOMY,
    WorkoutPurpose.TECHNIQUE,
    WorkoutPurpose.RACE_SPECIFIC -> QUALITY_MINIMUM_COMPLETION_FRACTION
    WorkoutPurpose.AEROBIC_BASE -> EASY_MINIMUM_COMPLETION_FRACTION
    WorkoutPurpose.GOAL_COMPLETION,
    WorkoutPurpose.ASSESSMENT -> GENERAL_MINIMUM_COMPLETION_FRACTION
  }
}

class WorkoutAdherenceEvaluator(private val clock: Clock = Clock.systemUTC()) {
  fun evaluate(
    plannedWorkout: StructuredWorkout,
    adjustedWorkout: AdjustedWorkout?,
    actualRun: RunSummary,
    feedback: RunFeedback,
    evidence: WorkoutExecutionEvidence = WorkoutExecutionEvidence(),
  ): WorkoutExecutionResult {
    val actual = RunActualResolver.resolve(actualRun)
    require(feedback.belongsTo(actualRun)) { "Feedback must belong to the actual run." }
    require(plannedWorkout.type != WorkoutType.REST) { "A rest day cannot be associated with a completed run." }

    val effective = adjustedWorkout?.structuredWorkout ?: plannedWorkout
    val estimate = WorkoutEstimateCalculator.calculate(effective)
    val intendedDistance = (if (adjustedWorkout == null || adjustedWorkout.structuredWorkout != null) estimate.estimatedTotalDistanceMeters else null)?.takeIf { it > 0 }
    val intendedDuration = (if (adjustedWorkout?.structuredWorkout != null) estimate.estimatedTotalDurationSeconds
      else adjustedWorkout?.durationMinutes?.times(60L) ?: estimate.estimatedTotalDurationSeconds)?.takeIf { it > 0 }
    val completionFraction = completionFraction(
      intendedDistance,
      intendedDuration,
      actual.distanceMeters,
      actual.durationSeconds,
    )
    val qualityPurpose = effective.purpose.requiresQualityEvidence()
    val qualityFraction = evidence.qualityWorkCompletionFraction
    val effortDeviation = effortDiffersMaterially(effective, adjustedWorkout, actual, feedback)
    val comparisonUnavailable = completionFraction == null && (intendedDistance != null || intendedDuration != null)

    val status = when {
      feedback.completionStatus == CompletionStatus.ABORTED -> WorkoutExecutionStatus.ABORTED
      feedback.completionStatus == CompletionStatus.PARTIAL -> WorkoutExecutionStatus.PARTIALLY_COMPLETED
      effective.type == WorkoutType.REST -> WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION
      qualityPurpose && qualityFraction != null && qualityFraction < WorkoutAdherencePolicy.QUALITY_MINIMUM_COMPLETION_FRACTION ->
        WorkoutExecutionStatus.PARTIALLY_COMPLETED
      completionFraction != null && completionFraction < WorkoutAdherencePolicy.minimumCompletionFraction(effective.purpose) ->
        WorkoutExecutionStatus.PARTIALLY_COMPLETED
      effortDeviation -> WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION
      qualityPurpose && qualityFraction == null -> WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION
      comparisonUnavailable -> WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION
      qualityPurpose && qualityFraction != null &&
        qualityFraction < WorkoutAdherencePolicy.AS_PLANNED_MINIMUM_FRACTION ->
        WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION
      completionFraction == null || completionFraction in
        WorkoutAdherencePolicy.AS_PLANNED_MINIMUM_FRACTION..WorkoutAdherencePolicy.AS_PLANNED_MAXIMUM_FRACTION ->
        WorkoutExecutionStatus.COMPLETED_AS_PLANNED
      else -> WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION
    }

    return WorkoutExecutionResult(
      plannedWorkoutId = plannedWorkout.id,
      actualRunId = actualRun.healthConnectRecordId,
      status = status,
      comparisonBasis = if (adjustedWorkout == null || adjustedWorkout.isUnchanged) {
        WorkoutComparisonBasis.ORIGINAL_PRESCRIPTION
      } else {
        WorkoutComparisonBasis.ADJUSTED_PRESCRIPTION
      },
      intendedDistanceMeters = intendedDistance,
      intendedDurationSeconds = intendedDuration,
      actualDistanceMeters = actual.distanceMeters,
      actualDurationSeconds = actual.durationSeconds,
      qualityWorkCompletionFraction = qualityFraction,
      explanation = if (effective.type == WorkoutType.REST) "Running was recorded against a recovery-day prescription. Actual training still counts."
        else explanation(effective, status, completionFraction, qualityFraction, comparisonUnavailable),
      evaluatedAt = clock.instant(),
      physicalRunId = actualRun.physicalRunId,
    )
  }

  private fun completionFraction(
    intendedDistanceMeters: Double?,
    intendedDurationSeconds: Long?,
    actualDistanceMeters: Double?,
    actualDurationSeconds: Long,
  ): Double? = when {
    intendedDistanceMeters != null && intendedDistanceMeters > 0.0 && actualDistanceMeters != null ->
      actualDistanceMeters / intendedDistanceMeters
    intendedDurationSeconds != null && intendedDurationSeconds > 0L ->
      actualDurationSeconds.toDouble() / intendedDurationSeconds
    else -> null
  }

  private fun effortDiffersMaterially(
    plannedWorkout: StructuredWorkout,
    adjustedWorkout: AdjustedWorkout?,
    actualRun: RunSummary,
    feedback: RunFeedback,
  ): Boolean {
    val minimum = adjustedWorkout?.targetRpeMin ?: plannedWorkout.targetRpeMin
    val maximum = adjustedWorkout?.targetRpeMax ?: plannedWorkout.targetRpeMax
    val rpeDeviation = minimum > 0 && maximum > 0 && (
      feedback.rpe < minimum - WorkoutAdherencePolicy.RPE_ALLOWED_DEVIATION ||
        feedback.rpe > maximum + WorkoutAdherencePolicy.RPE_ALLOWED_DEVIATION
      )
    // Whole-run average pace is not valid evidence for an individual quality repetition.
    val paceTarget = if (plannedWorkout.purpose.requiresQualityEvidence()) null else plannedWorkout.blocks
      .flatMap(WorkoutBlock::segments)
      .flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }
      .filterIsInstance<PaceTarget>()
      .firstOrNull { it.confidence == GuidanceConfidence.MEDIUM || it.confidence == GuidanceConfidence.HIGH }
    val paceDeviation = paceTarget?.let { target ->
      actualRun.averagePaceSecondsPerKm?.let { actualPace ->
        actualPace < target.minimumSecondsPerKm * 0.95 || actualPace > target.maximumSecondsPerKm * 1.05
      }
    } == true
    return rpeDeviation || paceDeviation
  }

  private fun explanation(
    workout: StructuredWorkout,
    status: WorkoutExecutionStatus,
    completionFraction: Double?,
    qualityFraction: Double?,
    comparisonUnavailable: Boolean,
  ): String = when (status) {
    WorkoutExecutionStatus.COMPLETED_AS_PLANNED ->
      "The intended ${workout.purpose.trainingIntent()} was completed as planned."
    WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION -> when {
      workout.purpose.requiresQualityEvidence() && qualityFraction == null ->
        "The session was completed, but quality-work adherence could not be fully verified from total distance alone."
      comparisonUnavailable ->
        "The session was completed, but the prescribed distance or duration could not be compared with the actual run."
      completionFraction != null && completionFraction !in
        WorkoutAdherencePolicy.AS_PLANNED_MINIMUM_FRACTION..WorkoutAdherencePolicy.AS_PLANNED_MAXIMUM_FRACTION ->
        "The ${workout.purpose.trainingIntent()} was completed with a small prescription deviation."
      else -> "The session was completed with a minor effort or quality-work deviation."
    }
    WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION ->
      "The workout was completed, but its intensity or effort differed meaningfully from the intended ${workout.purpose.trainingIntent()}."
    WorkoutExecutionStatus.PARTIALLY_COMPLETED ->
      "Only part of the intended ${workout.purpose.trainingIntent()} was completed. Actual training still counts toward history."
    WorkoutExecutionStatus.ABORTED ->
      "The session was stopped. Actual training still counts, and the reason remains relevant to adaptation."
  }
}

private fun WorkoutPurpose.requiresQualityEvidence(): Boolean = when (this) {
  WorkoutPurpose.LACTATE_THRESHOLD,
  WorkoutPurpose.SPEED,
  WorkoutPurpose.VO2MAX,
  WorkoutPurpose.RUNNING_ECONOMY,
  WorkoutPurpose.TECHNIQUE,
  WorkoutPurpose.RACE_SPECIFIC -> true
  WorkoutPurpose.RECOVERY,
  WorkoutPurpose.AEROBIC_BASE,
  WorkoutPurpose.ENDURANCE,
  WorkoutPurpose.GOAL_COMPLETION,
  WorkoutPurpose.ASSESSMENT -> false
}

private fun WorkoutPurpose.trainingIntent(): String = when (this) {
  WorkoutPurpose.RECOVERY -> "low-cost recovery movement"
  WorkoutPurpose.AEROBIC_BASE -> "easy aerobic stimulus"
  WorkoutPurpose.ENDURANCE -> "endurance stimulus"
  WorkoutPurpose.LACTATE_THRESHOLD -> "threshold work"
  WorkoutPurpose.SPEED, WorkoutPurpose.VO2MAX -> "quality work"
  WorkoutPurpose.RUNNING_ECONOMY -> "stride work"
  WorkoutPurpose.TECHNIQUE -> "technique work"
  WorkoutPurpose.RACE_SPECIFIC -> "race-specific work"
  WorkoutPurpose.GOAL_COMPLETION -> "controlled endurance session"
  WorkoutPurpose.ASSESSMENT -> "assessment session"
}

/** Current whole-run actuals for presentation; the persisted completion decision stays intact. */
fun WorkoutExecutionResult.withActualSummary(actual: RunSummary?): WorkoutExecutionResult {
  if (actual == null || !actual.isCanonical || actual.physicalRunId != physicalRunId) return this
  return copy(
    actualRunId = actual.healthConnectRecordId,
    actualDistanceMeters = actual.distanceMeters,
    actualDurationSeconds = actual.durationSeconds,
  )
}
