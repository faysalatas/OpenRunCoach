package com.example.openruncoach.domain.workout

import java.util.Locale
import kotlin.math.roundToLong

object WorkoutDescriptionFormatter {
  fun summary(workout: StructuredWorkout): String {
    val lines = mutableListOf(workout.name)
    val repeat = workout.blocks.sortedBy(WorkoutBlock::order).filterIsInstance<RepeatBlock>().firstOrNull()
    if (repeat != null) {
      repeat.segments.firstOrNull()?.let { lines += "${repeat.repetitions} x ${completion(it.completion)}" }
      repeat.segments.drop(1).firstOrNull()?.let { lines += "${completion(it.completion)} ${it.name.lowercase()}" }
      val warmup = workout.blocks.flatMap(WorkoutBlock::segments).firstOrNull { it.category == SegmentCategory.WARMUP }
      val cooldown = workout.blocks.flatMap(WorkoutBlock::segments).firstOrNull { it.category == SegmentCategory.COOLDOWN }
      if (warmup != null || cooldown != null) {
        lines += "+ ${warmup?.let { completion(it.completion) + " warm-up" } ?: ""}" +
          if (cooldown != null) " / ${completion(cooldown.completion)} cool-down" else ""
      }
    } else {
      workout.blocks.firstOrNull()?.segments?.firstOrNull()?.let { segment ->
        lines += completion(segment.completion)
        if (segment.primaryIntensity !is NoIntensityTarget) lines += intensity(segment.primaryIntensity)
      }
    }
    return lines.joinToString("\n")
  }

  fun completion(completion: SegmentCompletion): String = when (completion) {
    is DurationCompletion -> when {
      completion.seconds % 60L == 0L -> "${completion.seconds / 60} min"
      completion.seconds < 60L -> "${completion.seconds} sec"
      else -> "${completion.seconds / 60}:${(completion.seconds % 60).toString().padStart(2, '0')} min"
    }
    is DistanceCompletion -> if (completion.meters >= 1_000) "${formatNumber(completion.meters / 1_000.0)} km" else "${completion.meters.roundToLong()} m"
    is RepetitionCompletion -> "${completion.repetitions} reps"
    OpenCompletion -> "Manual"
  }

  fun intensity(target: IntensityTarget): String = when (target) {
    NoIntensityTarget -> "No explicit intensity"
    is RpeTarget -> "RPE ${target.minimum}–${target.maximum}"
    is PaceTarget -> "${formatPace(target.minimumSecondsPerKm)}–${formatPace(target.maximumSecondsPerKm)} min/km"
    is HeartRateTarget -> "${target.minimumBpm}–${target.maximumBpm} bpm"
    is HeartRateZoneTarget -> target.zone.name.replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase) +
      if (target.confidence == GuidanceConfidence.UNCALIBRATED) " (uncalibrated)" else ""
  }

  fun purpose(purpose: WorkoutPurpose): String = purpose.name.lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)

  private fun formatPace(seconds: Double): String {
    val rounded = seconds.roundToLong()
    return "${rounded / 60}:${(rounded % 60).toString().padStart(2, '0')}"
  }

  private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(Locale.US, value)
}
