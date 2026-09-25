package com.example.openruncoach.domain.workout

data class WorkoutEstimate(
  val estimatedTotalDurationSeconds: Long?,
  val estimatedMinimumDurationSeconds: Long?,
  val estimatedMaximumDurationSeconds: Long?,
  val knownDurationSeconds: Long,
  val estimatedTotalDistanceMeters: Double?,
  val knownDistanceMeters: Double,
  val workDurationSeconds: Long,
  val recoveryDurationSeconds: Long,
  val warmupCooldownDurationSeconds: Long,
  val repeatBlockCount: Int,
  val totalRepeatedIterations: Int,
)

object WorkoutEstimateCalculator {
  fun calculate(workout: StructuredWorkout): WorkoutEstimate {
    var knownDuration = 0L
    var minimumDuration = 0L
    var maximumDuration = 0L
    var knownDistance = 0.0
    var workDuration = 0L
    var recoveryDuration = 0L
    var warmupCooldownDuration = 0L
    var durationComplete = true
    var distanceComplete = true
    var repeatBlocks = 0
    var repeatedIterations = 0
    workout.blocks.sortedBy(WorkoutBlock::order).forEach { block ->
      val multiplier = if (block is RepeatBlock) block.repetitions else 1
      if (block is RepeatBlock) {
        repeatBlocks += 1
        repeatedIterations += block.repetitions
      }
      block.segments.sortedBy(WorkoutSegment::order).forEach { segment ->
        val estimate = estimateSegment(segment)
        if (estimate.representativeDurationSeconds == null) durationComplete = false else {
          val duration = estimate.representativeDurationSeconds * multiplier
          knownDuration += duration
          minimumDuration += requireNotNull(estimate.minimumDurationSeconds) * multiplier
          maximumDuration += requireNotNull(estimate.maximumDurationSeconds) * multiplier
          when (segment.category) {
            SegmentCategory.WORK, SegmentCategory.STRIDE, SegmentCategory.DRILL -> workDuration += duration
            SegmentCategory.RECOVERY, SegmentCategory.WALK, SegmentCategory.REST -> recoveryDuration += duration
            SegmentCategory.WARMUP, SegmentCategory.COOLDOWN -> warmupCooldownDuration += duration
            SegmentCategory.EASY -> Unit
          }
        }
        if (estimate.distanceMeters == null) distanceComplete = false else knownDistance += estimate.distanceMeters * multiplier
      }
    }
    return WorkoutEstimate(
      estimatedTotalDurationSeconds = knownDuration.takeIf { durationComplete },
      estimatedMinimumDurationSeconds = minimumDuration.takeIf { durationComplete },
      estimatedMaximumDurationSeconds = maximumDuration.takeIf { durationComplete },
      knownDurationSeconds = knownDuration,
      estimatedTotalDistanceMeters = knownDistance.takeIf { distanceComplete },
      knownDistanceMeters = knownDistance,
      workDurationSeconds = workDuration,
      recoveryDurationSeconds = recoveryDuration,
      warmupCooldownDurationSeconds = warmupCooldownDuration,
      repeatBlockCount = repeatBlocks,
      totalRepeatedIterations = repeatedIterations,
    )
  }

  private fun estimateSegment(segment: WorkoutSegment): SegmentEstimate {
    val pace = (listOf(segment.primaryIntensity) + segment.secondaryIntensities)
      .filterIsInstance<PaceTarget>().firstOrNull()
    return when (val completion = segment.completion) {
      is DurationCompletion -> SegmentEstimate(
        completion.seconds,
        completion.seconds,
        completion.seconds,
        pace?.let { completion.seconds / ((it.minimumSecondsPerKm + it.maximumSecondsPerKm) / 2.0) * 1_000.0 },
      )
      is DistanceCompletion -> {
        val kilometers = completion.meters / 1_000.0
        SegmentEstimate(
          pace?.let { (kilometers * (it.minimumSecondsPerKm + it.maximumSecondsPerKm) / 2.0).toLong() },
          pace?.let { (kilometers * it.minimumSecondsPerKm).toLong() },
          pace?.let { (kilometers * it.maximumSecondsPerKm).toLong() },
          completion.meters,
        )
      }
      is RepetitionCompletion, OpenCompletion -> SegmentEstimate(null, null, null, null)
    }
  }
}

/** Returns prescribed running distance while keeping walking and rest separate. */
fun StructuredWorkout.prescribedRunningDistanceMeters(): Double {
  return blocks.sumOf { block ->
    val multiplier = if (block is RepeatBlock) block.repetitions else 1
    block.segments.sumOf { segment ->
      if (segment.category == SegmentCategory.WALK || segment.category == SegmentCategory.REST) 0.0
      else ((segment.completion as? DistanceCompletion)?.meters ?: 0.0) * multiplier
    }
  }
}

private data class SegmentEstimate(
  val representativeDurationSeconds: Long?,
  val minimumDurationSeconds: Long?,
  val maximumDurationSeconds: Long?,
  val distanceMeters: Double?,
)
