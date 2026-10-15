package com.example.openruncoach.ui.workout

import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.workout.*
import com.example.openruncoach.ui.common.formatDistance
import java.util.Locale

data class WorkoutListSummary(
  val distanceLabel: String?,
  val durationLabel: String?,
  val paceLabel: String?,
  val effortLabel: String?,
  val intensityBars: Int?,
  val rpeLabel: String?,
  val repetitionLabel: String?,
  val structured: Boolean,
)

/** One display projection shared by Home and Plan; segment targets are never whole-run pace. */
fun StructuredWorkout.listSummary(): WorkoutListSummary {
  val estimate = WorkoutEstimateCalculator.calculate(this)
  val targets = blocks.flatMap(WorkoutBlock::segments)
    .flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }
  val continuousSegment = (blocks.singleOrNull() as? SingleSegmentBlock)?.segment
  val pace = continuousSegment?.let { listOf(it.primaryIntensity) + it.secondaryIntensities }
    ?.filterIsInstance<PaceTarget>()?.distinct()?.singleOrNull()
  val rpe = targets.filterIsInstance<RpeTarget>()
  val workRpe = blocks.flatMap(WorkoutBlock::segments)
    .filter { it.category in setOf(SegmentCategory.WORK, SegmentCategory.STRIDE, SegmentCategory.DRILL) }
    .flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }.filterIsInstance<RpeTarget>()
  val effort = rpe.maxOfOrNull(RpeTarget::maximum)?.let(::workoutEffort)
  val repeat = blocks.sortedBy(WorkoutBlock::order).filterIsInstance<RepeatBlock>().firstOrNull()
  val work = repeat?.segments?.firstOrNull { it.category in setOf(SegmentCategory.WORK, SegmentCategory.STRIDE, SegmentCategory.DRILL) }
    ?: repeat?.segments?.firstOrNull { it.category == SegmentCategory.EASY }
  return WorkoutListSummary(
    distanceLabel = estimate.estimatedTotalDistanceMeters?.let(::formatWorkoutDistance),
    durationLabel = estimate.estimatedMinimumDurationSeconds?.let { minimum ->
      WorkoutDescriptionFormatter.durationRange(minimum, estimate.estimatedMaximumDurationSeconds ?: minimum)
    },
    paceLabel = pace?.let { WorkoutDescriptionFormatter.paceRange(it).replace(" min/km", " /km") },
    effortLabel = effort?.label,
    intensityBars = effort?.bars,
    rpeLabel = (workRpe.ifEmpty { rpe }).distinct().singleOrNull()?.let(WorkoutDescriptionFormatter::intensity),
    repetitionLabel = if (repeat != null && work != null) "${repeat.repetitions} × ${WorkoutDescriptionFormatter.completion(work.completion)}" else null,
    structured = blocks.isNotEmpty() && continuousSegment == null,
  )
}

enum class WorkoutListEffort(val label: String, val bars: Int) {
  EASY("Easy", 1), MODERATE("Moderate", 2), HARD("Hard", 3), VERY_HARD("Very hard", 4),
}

private fun workoutEffort(maximumRpe: Int): WorkoutListEffort = when (maximumRpe) {
  in 1..4 -> WorkoutListEffort.EASY
  in 5..6 -> WorkoutListEffort.MODERATE
  in 7..8 -> WorkoutListEffort.HARD
  else -> WorkoutListEffort.VERY_HARD
}

fun workoutIntensityLabel(maximumRpe: Int): String = workoutEffort(maximumRpe).label

fun formatWorkoutDistance(meters: Double): String =
  if (meters >= 1_000.0) formatDistance(meters) else "%.0f m".format(Locale.getDefault(), meters)

fun TrainingGoal.planTitle(): String = planGoalTitle(distance, customDistanceMeters, goalType)

fun TrainingGoal.planDistanceLabel(): String = goalDistanceLabel(distance, customDistanceMeters)

private fun goalDistanceLabel(distance: GoalDistance, customDistanceMeters: Double?): String =
  if (distance == GoalDistance.CUSTOM) customDistanceMeters?.let(::formatWorkoutDistance) ?: "Custom distance" else distance.displayName

fun planGoalTitle(distance: GoalDistance, customDistanceMeters: Double?, type: GoalType): String {
  val label = goalDistanceLabel(distance, customDistanceMeters)
  return when (type) {
    GoalType.COMPLETE_DISTANCE -> "Complete $label"
    GoalType.IMPROVE_PERFORMANCE -> "Improve $label performance"
    GoalType.TARGET_TIME -> "$label target time"
  }
}

fun WorkoutExecutionStatus.displayLabel(): String = when (this) {
  WorkoutExecutionStatus.COMPLETED_AS_PLANNED -> "Completed as planned"
  WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION -> "Completed with minor deviation"
  WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION -> "Completed with prescription deviation"
  WorkoutExecutionStatus.PARTIALLY_COMPLETED -> "Partially completed"
  WorkoutExecutionStatus.ABORTED -> "Stopped"
}
