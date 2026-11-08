package com.example.openruncoach.domain.adaptation

import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.athlete.AthleteState
import com.example.openruncoach.domain.athlete.CurrentReadiness
import com.example.openruncoach.domain.goal.GoalStrategy
import com.example.openruncoach.domain.goal.TrainingCapability
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.workout.*
import java.time.LocalDate
import kotlin.math.roundToLong

enum class TrainingAdaptationAction { KEEP, REST, REDUCE, PROGRESS }

/** Absence of checkout is explicit. Observations include unassigned physical runs. */
data class AdaptationObservation(
  val physicalRunId: String,
  val date: LocalDate,
  val pain: PainLevel?,
  val recoveryConcern: Boolean,
  val response: WorkoutResponse?,
  val capabilityEligible: Boolean = true,
  val recoveryPriority: Boolean = false,
)

data class TrainingAdaptationDecision(
  val action: TrainingAdaptationAction,
  val capability: TrainingCapability?,
  val reasons: List<String>,
) {
  init {
    require(reasons.isNotEmpty() && reasons.none(String::isBlank))
    require(action != TrainingAdaptationAction.PROGRESS || capability != null)
  }
}

data class AdaptedPrescription(
  val original: StructuredWorkout,
  val effective: StructuredWorkout,
  val decision: TrainingAdaptationDecision,
) {
  init { require(original.id == effective.id && original.date == effective.date) }
}

/** Stateless policy: always transform the original, never yesterday's effective prescription. */
class TrainingAdaptationPlanner {
  fun adapt(
    originals: List<StructuredWorkout>,
    strategy: GoalStrategy?,
    state: AthleteState,
    observations: List<AdaptationObservation>,
    today: LocalDate,
  ): List<AdaptedPrescription> {
    val recent = observations.distinctBy { it.physicalRunId }
      .filter { !it.date.isBefore(today.minusDays(6)) && !it.date.isAfter(today) }
    val significantPain = recent.any { it.pain == PainLevel.SIGNIFICANT }
    val recovery = state.currentReadiness in setOf(CurrentReadiness.CONSERVATIVE, CurrentReadiness.RECOVERY_PRIORITY) ||
      recent.any { it.pain == PainLevel.MILD || it.recoveryConcern || it.response?.sessionCost in setOf(SessionCost.ELEVATED, SessionCost.HIGH) }
    val unknown = recent.isEmpty() || recent.any { it.pain == null } ||
      state.currentReadiness == CurrentReadiness.UNKNOWN ||
      state.capabilityConfidence in setOf(ProfileConfidence.VERY_LOW, ProfileConfidence.LOW) ||
      strategy == null || strategy.confidence in setOf(ProfileConfidence.VERY_LOW, ProfileConfidence.LOW)
    var progressed = false
    return originals.sortedWith(compareBy<StructuredWorkout> { it.date }.thenBy { it.id }).map { original ->
      val capability = strategy?.primaryCapabilities?.firstOrNull { it.supports(original.purpose) }
      val inWindow = !original.date.isBefore(today) && original.date.isBefore(today.plusDays(7))
      val hard = original.purpose in qualityPurposes || (original.type == WorkoutType.ASSESSMENT && original.targetRpeMax > 5) ||
        original.purpose == WorkoutPurpose.GOAL_COMPLETION
      val positives = observations.distinctBy { it.physicalRunId }.filter {
        !it.date.isBefore(today.minusDays(27)) && !it.date.isAfter(today) &&
          it.pain == PainLevel.NONE && !it.recoveryConcern && !it.recoveryPriority && it.response?.isPositive(original.purpose) == true &&
          it.capabilityEligible && (capability != TrainingCapability.CONTINUOUS_RUNNING ||
            it.response.continuity == com.example.openruncoach.domain.metrics.RunContinuityEvidence.CONTINUOUS)
      }
      val decision = when {
        !inWindow -> keep("The prescription is outside the next seven days; its original structure is retained.")
        original.type == WorkoutType.REST -> keep("The planned recovery day is retained.")
        significantPain || recent.any { it.recoveryPriority } || state.currentReadiness == CurrentReadiness.RECOVERY_PRIORITY -> TrainingAdaptationDecision(
          TrainingAdaptationAction.REST, null, listOf("Recent pain or repeated recovery concerns prioritize rest; reassess before running."))
        hard && (recovery || unknown) -> TrainingAdaptationDecision(
          TrainingAdaptationAction.REST, null, listOf(if (unknown)
            "Current checkout or training evidence is insufficient to authorize this demanding session."
          else "Recent session cost or fatigue requires recovery before demanding training."))
        recovery -> TrainingAdaptationDecision(TrainingAdaptationAction.REDUCE, null,
          listOf("Recent session cost or fatigue reduces the next week's work volume by 20%; intensity and recovery blocks are retained."))
        !unknown && !progressed && capability != null && positives.size >= 3 -> TrainingAdaptationDecision(
          TrainingAdaptationAction.PROGRESS, capability,
          listOf("At least three recent tolerated ${original.purpose.name.lowercase().replace('_', ' ')} sessions support 5% more work volume for ${capability.name.lowercase().replace('_', ' ')} in one workout; intensity is retained."))
        unknown -> keep("Evidence or checkout is incomplete; retain the conservative base prescription without progression. Missing feedback does not establish recovery.")
        else -> keep("Retain the original prescription: repeated purpose-specific evidence does not yet support another progression.")
      }
      val transformed = when (decision.action) {
        TrainingAdaptationAction.KEEP -> original
        TrainingAdaptationAction.REST -> original.copy(type = WorkoutType.REST, name = "Recovery day",
          purpose = WorkoutPurpose.RECOVERY, blocks = emptyList(), origin = WorkoutOrigin.ADJUSTED)
        TrainingAdaptationAction.REDUCE -> scaleWork(original, 0.8)
        TrainingAdaptationAction.PROGRESS -> scaleWork(original, 1.05)
      }
      val applied = if (transformed == original && decision.action != TrainingAdaptationAction.KEEP) {
        keep("Work-volume adjustment is unavailable for this workout's completion model or adjustment permissions; retain its original prescription.")
      } else decision
      if (applied.action == TrainingAdaptationAction.PROGRESS) progressed = true
      AdaptedPrescription(original, transformed, applied)
    }
  }

  private fun scaleWork(workout: StructuredWorkout, factor: Double): StructuredWorkout {
    if (!workout.adjustability.canChangeSegmentCompletion || !workout.adjustability.canChangeTotalDuration) return workout
    // Change one dimension only. Repeat counts, recovery, targets and block/segment identities survive.
    val categories = if (workout.purpose in qualityPurposes) workCategories - SegmentCategory.EASY else workCategories
    val segments = workout.blocks.flatMap { it.segments }.filter { it.category in categories }
    val duration = segments.firstOrNull()?.completion is DurationCompletion
    if (segments.isEmpty() || segments.any {
        if (duration) it.completion !is DurationCompletion else it.completion !is DistanceCompletion
      }) return workout
    fun transform(segment: WorkoutSegment): WorkoutSegment {
      if (segment.category !in categories) return segment
      val completion = when (val value = segment.completion) {
        is DurationCompletion -> DurationCompletion((value.seconds * factor).roundToLong().coerceAtLeast(1))
        is DistanceCompletion -> DistanceCompletion(value.meters * factor)
        else -> value
      }
      return segment.copy(completion = completion)
    }
    val blocks = workout.blocks.map {
      when (it) {
        is SingleSegmentBlock -> it.copy(segment = transform(it.segment))
        is RepeatBlock -> it.copy(segments = it.segments.map(::transform))
      }
    }
    return if (blocks == workout.blocks) workout else workout.copy(origin = WorkoutOrigin.ADJUSTED, blocks = blocks)
  }

  private fun WorkoutResponse.isPositive(purpose: WorkoutPurpose): Boolean =
    this.purpose == purpose && completion == WorkoutCompletionOutcome.COMPLETED &&
      volumeCompletion == VolumeCompletion.MET && adherence in setOf(PrescriptionAdherence.AS_PLANNED, PrescriptionAdherence.MINOR_DEVIATION) &&
      effortResponse in setOf(EffortResponse.ON_TARGET, EffortResponse.BELOW_TARGET) &&
      sessionCost in setOf(SessionCost.LOW, SessionCost.NORMAL) && confidence != WorkoutResponseConfidence.LOW &&
      pain == PainLevel.NONE && (purpose !in qualityPurposes ||
        (qualityWorkCompletionFraction ?: 0.0) >= WorkoutAdherencePolicy.AS_PLANNED_MINIMUM_FRACTION)

  private fun TrainingCapability.supports(purpose: WorkoutPurpose): Boolean = when (this) {
    TrainingCapability.AEROBIC_BASE, TrainingCapability.WEEKLY_VOLUME -> purpose == WorkoutPurpose.AEROBIC_BASE
    TrainingCapability.DISTANCE_TOLERANCE, TrainingCapability.CONTINUOUS_RUNNING -> purpose == WorkoutPurpose.ENDURANCE || purpose == WorkoutPurpose.AEROBIC_BASE
    TrainingCapability.THRESHOLD -> purpose == WorkoutPurpose.LACTATE_THRESHOLD
    TrainingCapability.SPEED -> purpose == WorkoutPurpose.SPEED || purpose == WorkoutPurpose.VO2MAX
    TrainingCapability.RUNNING_ECONOMY -> purpose == WorkoutPurpose.RUNNING_ECONOMY
    TrainingCapability.RACE_SPECIFIC_ENDURANCE -> purpose == WorkoutPurpose.RACE_SPECIFIC
    TrainingCapability.RECOVERY_TOLERANCE -> false
  }

  private fun keep(reason: String) = TrainingAdaptationDecision(TrainingAdaptationAction.KEEP, null, listOf(reason))

  private companion object {
    val workCategories = setOf(SegmentCategory.EASY, SegmentCategory.WORK, SegmentCategory.STRIDE, SegmentCategory.DRILL)
    val qualityPurposes = setOf(WorkoutPurpose.LACTATE_THRESHOLD, WorkoutPurpose.SPEED, WorkoutPurpose.VO2MAX,
      WorkoutPurpose.RUNNING_ECONOMY, WorkoutPurpose.TECHNIQUE, WorkoutPurpose.RACE_SPECIFIC)
  }
}
