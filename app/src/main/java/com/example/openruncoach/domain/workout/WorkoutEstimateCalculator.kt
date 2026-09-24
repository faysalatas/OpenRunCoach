package com.example.openruncoach.domain.workout

data class WorkoutEstimate(
  val estimatedTotalDurationSeconds: Long?,
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
        if (estimate.durationSeconds == null) durationComplete = false else {
          val duration = estimate.durationSeconds * multiplier
          knownDuration += duration
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
      ?.let { (it.minimumSecondsPerKm + it.maximumSecondsPerKm) / 2.0 }
    return when (val completion = segment.completion) {
      is DurationCompletion -> SegmentEstimate(completion.seconds, pace?.let { completion.seconds / it * 1_000.0 })
      is DistanceCompletion -> SegmentEstimate(pace?.let { (completion.meters / 1_000.0 * it).toLong() }, completion.meters)
      is RepetitionCompletion, OpenCompletion -> SegmentEstimate(null, null)
    }
  }
}

private data class SegmentEstimate(val durationSeconds: Long?, val distanceMeters: Double?)
