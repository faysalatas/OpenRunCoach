package com.example.openruncoach.domain.workout

import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import java.time.Clock
import kotlin.math.roundToInt

class CheckInEvaluator(private val clock: Clock = Clock.systemUTC()) {
  fun evaluate(
    baseline: PlannedWorkout,
    checkIn: PreRunCheckIn,
    recentCanonicalRunHistory: List<RunSummary>,
    recentTrainingVolume: RecentTrainingVolume,
    previousFeedback: RunFeedback? = null,
  ): WorkoutAdjustmentDecision {
    checkIn.validationError()?.let { throw IllegalArgumentException(it) }
    val history = recentCanonicalRunHistory.filter(RunSummary::isCanonical)
    require(history.size == recentCanonicalRunHistory.size) { "Check-in evaluation requires canonical run history." }

    safetyOverride(baseline, checkIn, previousFeedback)?.let { return it }

    val signals = recoverySignals(checkIn)
    val status = when {
      signals.size >= RED_SIGNAL_COUNT -> ReadinessStatus.RED
      signals.isNotEmpty() -> ReadinessStatus.YELLOW
      else -> ReadinessStatus.GREEN
    }
    val volumeReason = if (recentTrainingVolume.runCount > 0 && status != ReadinessStatus.GREEN) {
      listOf("Recent training volume considered: ${recentTrainingVolume.runCount} runs")
    } else emptyList()

    return when (status) {
      ReadinessStatus.GREEN -> decision(
        baseline,
        status,
        baseline.asAdjusted(),
        reasons = listOf("Recovery signals support the planned workout"),
        adjustments = listOf("Baseline workout kept unchanged"),
      )
      ReadinessStatus.YELLOW -> {
        val reduction = when {
          signals.size >= 2 || checkIn.soreness == SorenessLevel.MODERATE || checkIn.illness == IllnessLevel.MILD -> 20
          else -> 10
        }
        val adjusted = reduceWorkout(baseline, reduction, checkIn, forceRecovery = false)
        decision(
          baseline,
          status,
          adjusted,
          reasons = signals + volumeReason,
          adjustments = describeAdjustments(baseline, adjusted),
        )
      }
      ReadinessStatus.RED -> {
        val adjusted = recoveryWorkout(baseline)
        decision(
          baseline,
          status,
          adjusted,
          reasons = signals + volumeReason + "Multiple strong recovery concerns",
          adjustments = describeAdjustments(baseline, adjusted),
        )
      }
    }
  }

  private fun safetyOverride(
    baseline: PlannedWorkout,
    checkIn: PreRunCheckIn,
    previousFeedback: RunFeedback?,
  ): WorkoutAdjustmentDecision? {
    if (checkIn.pain == PainLevel.SIGNIFICANT) {
      return restDecision(baseline, "Significant pain reported")
    }
    if (checkIn.illness == IllnessLevel.MODERATE || checkIn.illness == IllnessLevel.SEVERE) {
      return restDecision(baseline, "${checkIn.illness.displayName()} illness reported")
    }
    if (previousFeedback?.completionStatus == CompletionStatus.ABORTED && checkIn.pain != PainLevel.NONE) {
      return restDecision(baseline, "Previous workout was aborted and pain is present")
    }
    if (checkIn.soreness == SorenessLevel.SEVERE) {
      val otherConcern = checkIn.readiness <= 2 || checkIn.energy <= 2 || checkIn.sleepQuality <= 2 ||
        checkIn.stress >= 4 || checkIn.illness != IllnessLevel.NONE || checkIn.pain != PainLevel.NONE
      val status = if (otherConcern) ReadinessStatus.RED else ReadinessStatus.YELLOW
      val adjusted = recoveryWorkout(baseline)
      return decision(
        baseline,
        status,
        adjusted,
        reasons = listOf("Severe muscle soreness") + if (otherConcern) listOf("Additional recovery concern present") else emptyList(),
        adjustments = describeAdjustments(baseline, adjusted),
      )
    }
    return null
  }

  private fun recoverySignals(checkIn: PreRunCheckIn): List<String> = buildList {
    if (checkIn.readiness <= 2) add("Readiness below normal")
    if (checkIn.energy <= 2) add("Low energy")
    if (checkIn.sleepQuality <= 2) add("Sleep quality below normal")
    if (checkIn.soreness == SorenessLevel.MODERATE) add("Moderate muscle soreness")
    if (checkIn.stress >= 4) add("High stress")
    if (checkIn.motivation <= 2) add("Low motivation")
    if (checkIn.illness == IllnessLevel.MILD) add("Mild illness symptoms")
    if (checkIn.pain == PainLevel.MILD) add("Mild pain reported")
  }

  private fun reduceWorkout(
    baseline: PlannedWorkout,
    reductionPercent: Int,
    checkIn: PreRunCheckIn,
    forceRecovery: Boolean,
  ): AdjustedWorkout {
    if (baseline.type == WorkoutType.REST) return baseline.asAdjusted()
    if (baseline.durationMinutes < MINIMUM_RUN_MINUTES) {
      return AdjustedWorkout(baseline.id, WorkoutType.REST, 0, 0, 0)
    }
    val reducedDuration = (baseline.durationMinutes * (100 - reductionPercent) / 100.0)
      .roundToInt().coerceAtLeast(MINIMUM_RUN_MINUTES).coerceAtMost(baseline.durationMinutes)
    val demanding = baseline.type in setOf(WorkoutType.TEMPO_RUN, WorkoutType.INTERVALS, WorkoutType.LONG_RUN)
    val needsRecovery = forceRecovery || checkIn.illness == IllnessLevel.MILD
    val type = when {
      needsRecovery -> WorkoutType.RECOVERY_RUN
      demanding && (checkIn.soreness == SorenessLevel.MODERATE || reductionPercent >= 20) -> WorkoutType.EASY_RUN
      else -> baseline.type
    }
    val rpeMax = minOf(baseline.targetRpeMax, if (type == WorkoutType.RECOVERY_RUN) 3 else 4)
    return AdjustedWorkout(baseline.id, type, reducedDuration, minOf(baseline.targetRpeMin, rpeMax), rpeMax)
  }

  private fun recoveryWorkout(baseline: PlannedWorkout): AdjustedWorkout {
    if (baseline.type == WorkoutType.REST) return baseline.asAdjusted()
    if (baseline.durationMinutes < MINIMUM_RUN_MINUTES) {
      return AdjustedWorkout(baseline.id, WorkoutType.REST, 0, 0, 0)
    }
    val duration = MINIMUM_RUN_MINUTES
    return AdjustedWorkout(baseline.id, WorkoutType.RECOVERY_RUN, duration, 2, 3)
  }

  private fun restDecision(baseline: PlannedWorkout, reason: String): WorkoutAdjustmentDecision {
    val rest = AdjustedWorkout(baseline.id, WorkoutType.REST, 0, 0, 0)
    return decision(
      baseline,
      ReadinessStatus.RED,
      rest,
      reasons = listOf(reason),
      adjustments = describeAdjustments(baseline, rest),
    )
  }

  private fun decision(
    baseline: PlannedWorkout,
    status: ReadinessStatus,
    adjusted: AdjustedWorkout,
    reasons: List<String>,
    adjustments: List<String>,
  ) = WorkoutAdjustmentDecision(baseline, adjusted, status, reasons, adjustments, clock.instant())
}

private const val MINIMUM_RUN_MINUTES = 15
private const val RED_SIGNAL_COUNT = 3

private fun PlannedWorkout.asAdjusted() =
  AdjustedWorkout(id, type, durationMinutes, targetRpeMin, targetRpeMax)

private fun describeAdjustments(baseline: PlannedWorkout, adjusted: AdjustedWorkout): List<String> = buildList {
  if (baseline.type != adjusted.type) add("Workout changed from ${baseline.type.displayName()} to ${adjusted.type.displayName()}")
  if (baseline.durationMinutes != adjusted.durationMinutes) {
    add(if (adjusted.type == WorkoutType.REST) "Running workout replaced with rest" else "Duration reduced from ${baseline.durationMinutes} to ${adjusted.durationMinutes} minutes")
  }
  if (baseline.targetRpeMax != adjusted.targetRpeMax) add("Target effort reduced")
  if (isEmpty()) add("Baseline workout kept unchanged")
}

private fun IllnessLevel.displayName() = name.lowercase().replaceFirstChar { it.titlecase() }
