package com.example.openruncoach.ui.health

import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.SingleSegmentBlock
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.metrics.PreparedRunChart
import com.example.openruncoach.domain.route.RunSplit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Display-only eligibility: never show a quality work target as a whole-run band. */
internal fun StructuredWorkout?.wholeRunPaceTarget(): PaceTarget? {
  this ?: return null
  if (type !in setOf(WorkoutType.EASY_RUN, WorkoutType.RECOVERY_RUN, WorkoutType.LONG_RUN, WorkoutType.ASSESSMENT)) return null
  if (purpose !in setOf(WorkoutPurpose.RECOVERY, WorkoutPurpose.AEROBIC_BASE,
      WorkoutPurpose.ENDURANCE, WorkoutPurpose.ASSESSMENT, WorkoutPurpose.GOAL_COMPLETION)) return null
  val segment = (blocks.singleOrNull() as? SingleSegmentBlock)?.segment ?: return null
  return (listOf(segment.primaryIntensity) + segment.secondaryIntensities)
    .filterIsInstance<PaceTarget>().singleOrNull()
}

internal fun formatPaceDelta(secondsPerKm: Double): String {
  val seconds = secondsPerKm.roundToLong()
  val sign = when { seconds < 0 -> "−"; seconds > 0 -> "+"; else -> "" }
  return "$sign${abs(seconds) / 60}:${(abs(seconds) % 60).toString().padStart(2, '0')}"
}

/** Uses the same display-only target extension as the pace renderer. */
internal fun PreparedRunChart.hasClippedPaceSamples(target: PaceTarget?): Boolean {
  val original = scale ?: return false
  val minimum = minOf(original.minimumValue, target?.minimumSecondsPerKm ?: original.minimumValue)
  val maximum = maxOf(original.maximumValue, target?.maximumSecondsPerKm ?: original.maximumValue)
  return points.any { it.value < minimum || it.value > maximum }
}

internal fun RunSplit.kilometreLabel(): String = if (partial) {
  "%.2f".format(Locale.getDefault(), distanceMeters / 1_000.0)
} else ordinal.toString()
