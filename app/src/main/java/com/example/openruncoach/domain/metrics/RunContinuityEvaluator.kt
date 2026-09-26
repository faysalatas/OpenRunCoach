package com.example.openruncoach.domain.metrics

import java.time.Duration
import java.time.Instant

enum class RunContinuityEvidence { CONTINUOUS, INTERRUPTED, UNKNOWN }

/** Detects only clear sustained non-running intervals; missing samples never invent continuity evidence. */
class RunContinuityEvaluator(
  private val maximumClearWalkingSpeedMetersPerSecond: Double = 1.25,
  private val maximumClearWalkingCadenceStepsPerMinute: Double = 125.0,
  private val minimumInterruptionSeconds: Long = 30,
  private val maximumSampleGapSeconds: Long = 15,
  private val maximumContinuousSampleGapSeconds: Long = 60,
  private val minimumCoverageFraction: Double = 0.80,
) {
  fun evaluate(metrics: RunMetrics?): RunContinuityEvidence {
    metrics ?: return RunContinuityEvidence.UNKNOWN
    val speed = metrics.series.speed.filter { it.value.isFinite() && it.value >= 0.0 }
    val cadence = metrics.series.cadence.filter { it.value.isFinite() && it.value >= 0.0 }
    val interrupted = hasSustainedLowValues(speed, maximumClearWalkingSpeedMetersPerSecond) ||
      hasSustainedLowValues(cadence, maximumClearWalkingCadenceStepsPerMinute)
    return when {
      interrupted -> RunContinuityEvidence.INTERRUPTED
      hasContinuousCoverage(speed, metrics.startTime, metrics.endTime) ||
        hasContinuousCoverage(cadence, metrics.startTime, metrics.endTime) -> RunContinuityEvidence.CONTINUOUS
      else -> RunContinuityEvidence.UNKNOWN
    }
  }

  private fun hasContinuousCoverage(
    samples: List<TimedMetricSample<Double>>,
    startTime: Instant,
    endTime: Instant,
  ): Boolean {
    if (samples.size < 3 || !endTime.isAfter(startTime)) return false
    val ordered = samples.sortedBy { it.timestamp }
    val sessionSeconds = Duration.between(startTime, endTime).seconds
    val coveredSeconds = Duration.between(ordered.first().timestamp, ordered.last().timestamp).seconds
    if (coveredSeconds < sessionSeconds * minimumCoverageFraction) return false
    return ordered.zipWithNext().all { (first, second) ->
      Duration.between(first.timestamp, second.timestamp).seconds in 0..maximumContinuousSampleGapSeconds
    }
  }

  private fun hasSustainedLowValues(
    samples: List<TimedMetricSample<Double>>,
    maximumValue: Double,
  ): Boolean {
    var intervalStart: Instant? = null
    var previous: Instant? = null
    for (sample in samples.sortedBy { it.timestamp }) {
      val gap = previous?.let { Duration.between(it, sample.timestamp).seconds }
      if (sample.value <= maximumValue) {
        if (intervalStart == null || gap == null || gap > maximumSampleGapSeconds) intervalStart = sample.timestamp
        if (Duration.between(requireNotNull(intervalStart), sample.timestamp).seconds >= minimumInterruptionSeconds) return true
      } else {
        intervalStart = null
      }
      previous = sample.timestamp
    }
    return false
  }
}
