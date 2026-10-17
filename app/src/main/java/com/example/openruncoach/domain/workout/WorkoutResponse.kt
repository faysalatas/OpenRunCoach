package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunActualResolver
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.belongsTo
import com.example.openruncoach.domain.metrics.RunContinuityEvaluator
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.metrics.RunMetrics
import java.util.Locale

enum class WorkoutCompletionOutcome { COMPLETED, PARTIAL, ABORTED }

enum class PrescriptionAdherence {
  AS_PLANNED,
  MINOR_DEVIATION,
  MEANINGFUL_DEVIATION,
  INSUFFICIENT_EVIDENCE,
}

enum class VolumeCompletion { MET, SUBSTANTIALLY_COMPLETED, PARTIAL, UNKNOWN }

enum class EffortResponse { BELOW_TARGET, ON_TARGET, ABOVE_TARGET, UNKNOWN }

enum class PaceResponse { FASTER_THAN_TARGET, ON_TARGET, SLOWER_THAN_TARGET, UNKNOWN }

enum class PaceStability { STABLE, STRONG_FADE, UNKNOWN }

enum class SessionCost { LOW, NORMAL, ELEVATED, HIGH }

enum class WorkoutResponseConfidence { LOW, MEDIUM, HIGH }

/**
 * Session-level evidence only. It describes what happened and how this workout appears to have
 * been tolerated; it does not replace longitudinal AthleteState or strict Goal completion.
 */
data class WorkoutResponse(
  val runId: String,
  val plannedWorkoutId: String,
  val purpose: WorkoutPurpose,
  val completion: WorkoutCompletionOutcome,
  val adherence: PrescriptionAdherence,
  val volumeCompletion: VolumeCompletion,
  val effortResponse: EffortResponse,
  val paceResponse: PaceResponse,
  val continuity: RunContinuityEvidence,
  val paceStability: PaceStability,
  val sessionCost: SessionCost,
  val pain: PainLevel,
  val confidence: WorkoutResponseConfidence,
  val actualDistanceMeters: Double?,
  val actualDurationSeconds: Long,
  val actualPaceSecondsPerKm: Double?,
  val plannedDistanceMeters: Double?,
  val plannedDurationSeconds: Long?,
  val plannedPaceMinimumSecondsPerKm: Double?,
  val plannedPaceMaximumSecondsPerKm: Double?,
  val plannedRpeMinimum: Int?,
  val plannedRpeMaximum: Int?,
  val reportedRpe: Int,
  val qualityWorkCompletionFraction: Double?,
  val reasons: List<String>,
  /** Stable training-session identity; runId identifies the current canonical evidence record. */
  val physicalRunId: String = runId,
) {
  init {
    require(runId.isNotBlank() && plannedWorkoutId.isNotBlank() && physicalRunId.isNotBlank())
    require(actualDistanceMeters == null || actualDistanceMeters.isFinite() && actualDistanceMeters >= 0.0)
    require(actualDurationSeconds > 0L)
    require(actualPaceSecondsPerKm == null || actualPaceSecondsPerKm.isFinite() && actualPaceSecondsPerKm > 0.0)
    require(reportedRpe in 1..10)
    require(reasons.isNotEmpty())
  }
}

class WorkoutResponseEvaluator(
  private val continuityEvaluator: RunContinuityEvaluator = RunContinuityEvaluator(),
) {
  fun evaluate(
    plannedWorkout: StructuredWorkout,
    adjustedWorkout: AdjustedWorkout?,
    execution: WorkoutExecutionResult,
    actualRun: RunSummary,
    feedback: RunFeedback,
    metrics: RunMetrics? = null,
  ): WorkoutResponse {
    require(execution.plannedWorkoutId == plannedWorkout.id)
    require(execution.actualRunId == actualRun.healthConnectRecordId)
    require(feedback.belongsTo(actualRun))
    require(metrics == null || metrics.runId == actualRun.healthConnectRecordId)

    val targetSegments = plannedWorkout.responseTargetSegments()
    val targets = targetSegments
      .flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }
    val plannedPace = targets.filterIsInstance<PaceTarget>().firstOrNull()
      ?.takeIf { adjustedWorkout == null }
    val plannedRpeTargets = targets.filterIsInstance<RpeTarget>()
    val plannedRpeMinimum = adjustedWorkout?.targetRpeMin?.takeIf { it > 0 }
      ?: plannedRpeTargets.minOfOrNull(RpeTarget::minimum)
    val plannedRpeMaximum = adjustedWorkout?.targetRpeMax?.takeIf { it > 0 }
      ?: plannedRpeTargets.maxOfOrNull(RpeTarget::maximum)
    val actual = RunActualResolver.resolve(actualRun, metrics)
    val actualDistance = actual.distanceMeters
    val actualDuration = actual.durationSeconds
    val actualPace = actual.averagePaceSecondsPerKm
    // The execution records the exact original or adjusted prescription that was compared.
    val plannedDistance = execution.intendedDistanceMeters
    val plannedDuration = execution.intendedDurationSeconds
    val completion = execution.status.toCompletionOutcome()
    val volumeCompletion = volumeCompletion(
      plannedWorkout.purpose,
      actualDistance,
      plannedDistance,
      actualDuration,
      plannedDuration,
    )
    val effortResponse = effortResponse(feedback.rpe, plannedRpeMinimum, plannedRpeMaximum)
    val paceResponse = paceResponse(plannedWorkout.purpose, actualPace, plannedPace, adjustedWorkout)
    val continuity = continuityEvaluator.evaluate(metrics)
    val paceStability = paceStability(metrics)
    val adherence = adherence(
      plannedWorkout.purpose,
      execution,
      feedback.rpe,
      plannedRpeMinimum,
      plannedRpeMaximum,
      paceResponse,
    )
    val sessionCost = sessionCost(
      plannedWorkout.purpose,
      feedback,
      effortResponse,
      continuity,
      paceStability,
    )
    val confidence = confidence(actualDistance, actualDuration, plannedPace, continuity, execution)
    val reasons = reasons(
      plannedWorkout,
      completion,
      volumeCompletion,
      effortResponse,
      paceResponse,
      continuity,
      paceStability,
      sessionCost,
      feedback,
      actualDistance,
      plannedDistance,
      actualPace,
      plannedPace,
      plannedRpeMinimum,
      plannedRpeMaximum,
      execution.qualityWorkCompletionFraction,
    )

    return WorkoutResponse(
      runId = actualRun.healthConnectRecordId,
      plannedWorkoutId = plannedWorkout.id,
      purpose = plannedWorkout.purpose,
      completion = completion,
      adherence = adherence,
      volumeCompletion = volumeCompletion,
      effortResponse = effortResponse,
      paceResponse = paceResponse,
      continuity = continuity,
      paceStability = paceStability,
      sessionCost = sessionCost,
      pain = feedback.painLevel,
      confidence = confidence,
      actualDistanceMeters = actualDistance,
      actualDurationSeconds = actualDuration,
      actualPaceSecondsPerKm = actualPace,
      plannedDistanceMeters = plannedDistance,
      plannedDurationSeconds = plannedDuration,
      plannedPaceMinimumSecondsPerKm = plannedPace?.minimumSecondsPerKm,
      plannedPaceMaximumSecondsPerKm = plannedPace?.maximumSecondsPerKm,
      plannedRpeMinimum = plannedRpeMinimum,
      plannedRpeMaximum = plannedRpeMaximum,
      reportedRpe = feedback.rpe,
      qualityWorkCompletionFraction = execution.qualityWorkCompletionFraction,
      reasons = reasons,
      physicalRunId = actualRun.physicalRunId,
    )
  }

  private fun volumeCompletion(
    purpose: WorkoutPurpose,
    actualDistance: Double?,
    plannedDistance: Double?,
    actualDuration: Long,
    plannedDuration: Long?,
  ): VolumeCompletion {
    val fraction = when {
      actualDistance != null && plannedDistance != null && plannedDistance > 0.0 -> actualDistance / plannedDistance
      plannedDuration != null && plannedDuration > 0L -> actualDuration.toDouble() / plannedDuration
      else -> null
    } ?: return VolumeCompletion.UNKNOWN
    return when {
      fraction >= WorkoutAdherencePolicy.AS_PLANNED_MINIMUM_FRACTION -> VolumeCompletion.MET
      fraction >= WorkoutAdherencePolicy.minimumCompletionFraction(purpose) -> VolumeCompletion.SUBSTANTIALLY_COMPLETED
      else -> VolumeCompletion.PARTIAL
    }
  }

  private fun effortResponse(actual: Int, minimum: Int?, maximum: Int?): EffortResponse = when {
    minimum == null || maximum == null -> EffortResponse.UNKNOWN
    actual < minimum -> EffortResponse.BELOW_TARGET
    actual > maximum -> EffortResponse.ABOVE_TARGET
    else -> EffortResponse.ON_TARGET
  }

  private fun paceResponse(
    purpose: WorkoutPurpose,
    actualPace: Double?,
    target: PaceTarget?,
    adjustedWorkout: AdjustedWorkout?,
  ): PaceResponse {
    // A whole-run average cannot establish adherence to individual quality repetitions.
    if (purpose.requiresStructuredQualityEvidence() || actualPace == null || target == null || adjustedWorkout != null) {
      return PaceResponse.UNKNOWN
    }
    if (target.confidence != GuidanceConfidence.MEDIUM && target.confidence != GuidanceConfidence.HIGH) {
      return PaceResponse.UNKNOWN
    }
    return when {
      actualPace < target.minimumSecondsPerKm * 0.95 -> PaceResponse.FASTER_THAN_TARGET
      actualPace > target.maximumSecondsPerKm * 1.05 -> PaceResponse.SLOWER_THAN_TARGET
      else -> PaceResponse.ON_TARGET
    }
  }

  private fun adherence(
    purpose: WorkoutPurpose,
    execution: WorkoutExecutionResult,
    actualRpe: Int,
    plannedRpeMinimum: Int?,
    plannedRpeMaximum: Int?,
    paceResponse: PaceResponse,
  ): PrescriptionAdherence {
    val meaningfulRpeDeviation = plannedRpeMinimum != null && plannedRpeMaximum != null &&
      (actualRpe < plannedRpeMinimum - WorkoutAdherencePolicy.RPE_ALLOWED_DEVIATION ||
        actualRpe > plannedRpeMaximum + WorkoutAdherencePolicy.RPE_ALLOWED_DEVIATION)
    return when {
      execution.status == WorkoutExecutionStatus.PARTIALLY_COMPLETED ||
        execution.status == WorkoutExecutionStatus.ABORTED -> PrescriptionAdherence.MEANINGFUL_DEVIATION
      execution.status == WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION ||
        meaningfulRpeDeviation ||
        paceResponse == PaceResponse.FASTER_THAN_TARGET ||
        paceResponse == PaceResponse.SLOWER_THAN_TARGET -> PrescriptionAdherence.MEANINGFUL_DEVIATION
      purpose.requiresStructuredQualityEvidence() && execution.qualityWorkCompletionFraction == null ->
        PrescriptionAdherence.INSUFFICIENT_EVIDENCE
      execution.status == WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION ->
        PrescriptionAdherence.MINOR_DEVIATION
      else -> PrescriptionAdherence.AS_PLANNED
    }
  }

  private fun paceStability(metrics: RunMetrics?): PaceStability {
    val speed = metrics?.series?.speed.orEmpty()
      .filter { it.value.isFinite() && it.value > 0.0 }
      .sortedBy { it.timestamp }
    if (speed.size < 6) return PaceStability.UNKNOWN
    val third = speed.size / 3
    val early = speed.take(third).map { it.value }.median() ?: return PaceStability.UNKNOWN
    val late = speed.takeLast(third).map { it.value }.median() ?: return PaceStability.UNKNOWN
    return if (late < early * 0.85) PaceStability.STRONG_FADE else PaceStability.STABLE
  }

  private fun sessionCost(
    purpose: WorkoutPurpose,
    feedback: RunFeedback,
    effort: EffortResponse,
    continuity: RunContinuityEvidence,
    paceStability: PaceStability,
  ): SessionCost = when {
    feedback.painLevel == PainLevel.SIGNIFICANT -> SessionCost.HIGH
    feedback.rpe >= 9 -> SessionCost.HIGH
    feedback.cardioEffort == CardioEffort.LIMIT || feedback.muscleFatigue == MuscleFatigue.SEVERE -> SessionCost.HIGH
    feedback.painLevel == PainLevel.MILD ||
      feedback.cardioEffort == CardioEffort.HARD ||
      feedback.muscleFatigue == MuscleFatigue.MODERATE ||
      feedback.readiness?.let { it <= 2 } == true ||
      effort == EffortResponse.ABOVE_TARGET ||
      continuity == RunContinuityEvidence.INTERRUPTED ||
      paceStability == PaceStability.STRONG_FADE -> SessionCost.ELEVATED
    purpose == WorkoutPurpose.RECOVERY && feedback.rpe <= 3 -> SessionCost.LOW
    else -> SessionCost.NORMAL
  }

  private fun confidence(
    actualDistance: Double?,
    actualDuration: Long,
    paceTarget: PaceTarget?,
    continuity: RunContinuityEvidence,
    execution: WorkoutExecutionResult,
  ): WorkoutResponseConfidence = when {
    actualDistance != null && actualDuration > 0L &&
      (continuity != RunContinuityEvidence.UNKNOWN ||
        execution.qualityWorkCompletionFraction != null ||
        paceTarget?.confidence == GuidanceConfidence.HIGH) -> WorkoutResponseConfidence.HIGH
    actualDistance != null && actualDuration > 0L -> WorkoutResponseConfidence.MEDIUM
    else -> WorkoutResponseConfidence.LOW
  }

  private fun reasons(
    workout: StructuredWorkout,
    completion: WorkoutCompletionOutcome,
    volume: VolumeCompletion,
    effort: EffortResponse,
    paceResponse: PaceResponse,
    continuity: RunContinuityEvidence,
    paceStability: PaceStability,
    cost: SessionCost,
    feedback: RunFeedback,
    actualDistance: Double?,
    plannedDistance: Double?,
    actualPace: Double?,
    plannedPace: PaceTarget?,
    plannedRpeMinimum: Int?,
    plannedRpeMaximum: Int?,
    qualityWorkCompletionFraction: Double?,
  ): List<String> = buildList {
    add(when (completion) {
      WorkoutCompletionOutcome.COMPLETED -> "The ${workout.name} was completed."
      WorkoutCompletionOutcome.PARTIAL -> "The ${workout.name} was partially completed; actual training still counts."
      WorkoutCompletionOutcome.ABORTED -> "The ${workout.name} was stopped; actual training still counts."
    })
    if (actualDistance != null && plannedDistance != null) {
      add("${distance(actualDistance)} of the planned ${distance(plannedDistance)} were completed" +
        if (volume == VolumeCompletion.SUBSTANTIALLY_COMPLETED) "; the distance deviation was minor." else ".")
    }
    if (effort == EffortResponse.ABOVE_TARGET && plannedRpeMinimum != null && plannedRpeMaximum != null) {
      add("Reported RPE ${feedback.rpe} was above the prescribed RPE $plannedRpeMinimum–$plannedRpeMaximum.")
    }
    if (paceResponse == PaceResponse.FASTER_THAN_TARGET && actualPace != null && plannedPace != null) {
      add("Average pace ${pace(actualPace)} was faster than the prescribed ${pace(plannedPace.minimumSecondsPerKm)}–${pace(plannedPace.maximumSecondsPerKm)} range.")
    } else if (paceResponse == PaceResponse.SLOWER_THAN_TARGET && actualPace != null && plannedPace != null) {
      add("Average pace ${pace(actualPace)} was slower than the prescribed range.")
    }
    if (workout.purpose.requiresStructuredQualityEvidence() && qualityWorkCompletionFraction == null) {
      add("Total session data cannot verify the prescribed quality work or repetitions.")
    }
    when (continuity) {
      RunContinuityEvidence.CONTINUOUS -> add("Available movement samples support continuous running.")
      RunContinuityEvidence.INTERRUPTED -> add("Movement samples include sustained walking or stopped time.")
      RunContinuityEvidence.UNKNOWN -> Unit
    }
    if (paceStability == PaceStability.STRONG_FADE) add("Available speed samples show a strong late-session fade.")
    when (feedback.painLevel) {
      PainLevel.NONE -> Unit
      PainLevel.MILD -> add("Mild pain was reported, so progression should remain conservative.")
      PainLevel.SIGNIFICANT -> add("Significant pain was reported; recovery and safety take priority.")
    }
    if (cost == SessionCost.HIGH && feedback.rpe >= 9) {
      add("This was a successful but high-cost effort with little reserve in this session.")
    } else if (cost == SessionCost.ELEVATED) {
      add("This session appears more costly than the intended ${workout.purpose.trainingIntentLabel()} stimulus.")
    }
  }

  private fun distance(meters: Double) = "%.2f km".format(Locale.ROOT, meters / 1000.0)

  private fun pace(secondsPerKm: Double): String {
    val rounded = secondsPerKm.toLong()
    return "%d:%02d min/km".format(Locale.ROOT, rounded / 60, rounded % 60)
  }
}

private fun WorkoutExecutionStatus.toCompletionOutcome() = when (this) {
  WorkoutExecutionStatus.COMPLETED_AS_PLANNED,
  WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION,
  WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION -> WorkoutCompletionOutcome.COMPLETED
  WorkoutExecutionStatus.PARTIALLY_COMPLETED -> WorkoutCompletionOutcome.PARTIAL
  WorkoutExecutionStatus.ABORTED -> WorkoutCompletionOutcome.ABORTED
}

private fun WorkoutPurpose.requiresStructuredQualityEvidence() = when (this) {
  WorkoutPurpose.LACTATE_THRESHOLD,
  WorkoutPurpose.SPEED,
  WorkoutPurpose.VO2MAX,
  WorkoutPurpose.RUNNING_ECONOMY,
  WorkoutPurpose.TECHNIQUE,
  WorkoutPurpose.RACE_SPECIFIC -> true
  else -> false
}

private fun StructuredWorkout.responseTargetSegments(): List<WorkoutSegment> {
  val segments = blocks.flatMap(WorkoutBlock::segments)
  val purposeSegments = when (purpose) {
    WorkoutPurpose.LACTATE_THRESHOLD,
    WorkoutPurpose.SPEED,
    WorkoutPurpose.VO2MAX,
    WorkoutPurpose.RUNNING_ECONOMY,
    WorkoutPurpose.TECHNIQUE,
    WorkoutPurpose.RACE_SPECIFIC -> segments.filter {
      it.category == SegmentCategory.WORK ||
        it.category == SegmentCategory.STRIDE ||
        it.category == SegmentCategory.DRILL
    }
    else -> segments.filter {
      it.category == SegmentCategory.EASY || it.category == SegmentCategory.WORK
    }
  }
  return purposeSegments.ifEmpty { segments }
}

private fun WorkoutPurpose.trainingIntentLabel() = when (this) {
  WorkoutPurpose.RECOVERY -> "recovery"
  WorkoutPurpose.AEROBIC_BASE -> "easy aerobic"
  WorkoutPurpose.ENDURANCE -> "endurance"
  WorkoutPurpose.LACTATE_THRESHOLD -> "threshold"
  WorkoutPurpose.SPEED, WorkoutPurpose.VO2MAX -> "quality"
  WorkoutPurpose.RUNNING_ECONOMY -> "running-economy"
  WorkoutPurpose.TECHNIQUE -> "technique"
  WorkoutPurpose.RACE_SPECIFIC -> "race-specific"
  WorkoutPurpose.GOAL_COMPLETION -> "goal-completion"
  WorkoutPurpose.ASSESSMENT -> "assessment"
}

private fun List<Double>.median(): Double? {
  if (isEmpty()) return null
  val sorted = sorted()
  val middle = sorted.size / 2
  return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
}
