package com.example.openruncoach.domain.metrics

import java.time.Duration
import java.time.Instant

data class PreparedPaceSeries(
  val samples: List<TimedMetricSample<Double>>,
  val averagePaceSecondsPerKm: Double?,
  val fastestThirtySecondPaceSecondsPerKm: Double?,
)

class PaceSeriesCalculator(
  private val smoothingWindow: Duration = Duration.ofSeconds(10),
  private val fastestWindow: Duration = Duration.ofSeconds(30),
  private val maximumSampleGap: Duration = smoothingWindow.multipliedBy(2),
) {
  fun calculate(
    speedSamples: List<TimedMetricSample<Double>>,
    canonicalDurationSeconds: Long,
    canonicalDistanceMeters: Double?,
  ): PreparedPaceSeries {
    val speeds = rejectIsolatedSpikes(coalesceTimestamps(speedSamples))
    val smoothed = smoothSpeed(speeds)
    return PreparedPaceSeries(
      samples = smoothed.map { sample ->
        sample.copy(value = speedToPaceSecondsPerKm(sample.value) ?: Double.NaN)
      },
      averagePaceSecondsPerKm = canonicalAveragePace(canonicalDurationSeconds, canonicalDistanceMeters),
      fastestThirtySecondPaceSecondsPerKm = fastestRollingPace(speeds),
    )
  }

  fun smoothSpeed(samples: List<TimedMetricSample<Double>>): List<TimedMetricSample<Double>> {
    if (samples.isEmpty()) return emptyList()
    val window = ArrayDeque<TimedMetricSample<Double>>()
    var previousValidTimestamp: Instant? = null
    return buildList {
      samples.sortedBy(TimedMetricSample<Double>::timestamp).forEach { sample ->
        if (!isValidSpeed(sample.value)) {
          window.clear()
          previousValidTimestamp = null
          add(sample.copy(value = Double.NaN))
          return@forEach
        }
        val previous = previousValidTimestamp
        if (previous != null && Duration.between(previous, sample.timestamp) > maximumSampleGap) {
          window.clear()
          add(sample.copy(timestamp = sample.timestamp.minusNanos(1), value = Double.NaN))
        }
        val earliest = sample.timestamp.minus(smoothingWindow)
        while (window.isNotEmpty() && window.first().timestamp.isBefore(earliest)) window.removeFirst()
        window.addLast(sample)
        add(sample.copy(value = window.map { it.value }.average()))
        previousValidTimestamp = sample.timestamp
      }
    }
  }

  private fun fastestRollingPace(samples: List<TimedMetricSample<Double>>): Double? {
    val window = ArrayDeque<TimedMetricSample<Double>>()
    var bestAverageSpeed: Double? = null
    var previousValidTimestamp: Instant? = null
    samples.sortedBy(TimedMetricSample<Double>::timestamp).forEach { sample ->
      if (!isValidSpeed(sample.value)) {
        window.clear()
        previousValidTimestamp = null
        return@forEach
      }
      val previous = previousValidTimestamp
      if (previous != null && Duration.between(previous, sample.timestamp) > maximumSampleGap) window.clear()
      val earliest = sample.timestamp.minus(fastestWindow)
      while (window.size > 1 && !window.elementAt(1).timestamp.isAfter(earliest)) window.removeFirst()
      window.addLast(sample)
      val span = Duration.between(window.first().timestamp, sample.timestamp)
      if (span >= fastestWindow && window.size >= MINIMUM_ROLLING_SAMPLES) {
        val averageSpeed = window.map { it.value }.average()
        if (averageSpeed > (bestAverageSpeed ?: Double.NEGATIVE_INFINITY)) bestAverageSpeed = averageSpeed
      }
      previousValidTimestamp = sample.timestamp
    }
    return bestAverageSpeed?.let(::speedToPaceSecondsPerKm)
  }

  private fun coalesceTimestamps(
    samples: List<TimedMetricSample<Double>>,
  ): List<TimedMetricSample<Double>> = samples.groupBy(TimedMetricSample<Double>::timestamp)
    .toSortedMap()
    .map { (_, sameTime) ->
      val valid = sameTime.filter { isValidSpeed(it.value) }
      if (valid.isEmpty()) sameTime.first().copy(value = Double.NaN)
      else valid.first().copy(value = valid.map { it.value }.average())
    }

  private fun rejectIsolatedSpikes(
    samples: List<TimedMetricSample<Double>>,
  ): List<TimedMetricSample<Double>> = samples.mapIndexed { index, sample ->
    if (!isValidSpeed(sample.value)) return@mapIndexed sample
    val neighbors = when (index) {
      0 -> samples.drop(1).take(2)
      samples.lastIndex -> samples.dropLast(1).takeLast(2)
      else -> listOf(samples[index - 1], samples[index + 1])
    }.filter { neighbor ->
      isValidSpeed(neighbor.value) &&
        Duration.between(sample.timestamp, neighbor.timestamp).abs() <= maximumSampleGap
    }
    if (neighbors.size < 2) return@mapIndexed sample
    val neighborMinimum = neighbors.minOf { it.value }
    val neighborMaximum = neighbors.maxOf { it.value }
    val neighborAverage = neighbors.map { it.value }.average()
    val neighborsAgree = neighborMaximum - neighborMinimum <=
      maxOf(MAXIMUM_NEIGHBOR_DIFFERENCE, neighborAverage * MAXIMUM_NEIGHBOR_DIFFERENCE_FRACTION)
    val isolatedHigh = sample.value > neighborMaximum * HIGH_SPIKE_RATIO &&
      sample.value - neighborMaximum > MINIMUM_HIGH_SPIKE_DIFFERENCE
    val isolatedLow = sample.value < neighborMinimum * LOW_SPIKE_RATIO
    if (neighborsAgree && (isolatedHigh || isolatedLow)) sample.copy(value = neighborAverage) else sample
  }

  companion object {
    private const val MINIMUM_ROLLING_SAMPLES = 3
    private const val HIGH_SPIKE_RATIO = 1.8
    private const val LOW_SPIKE_RATIO = 0.35
    private const val MINIMUM_HIGH_SPIKE_DIFFERENCE = 2.0
    private const val MAXIMUM_NEIGHBOR_DIFFERENCE = 0.5
    private const val MAXIMUM_NEIGHBOR_DIFFERENCE_FRACTION = 0.25

    fun speedToPaceSecondsPerKm(speedMetersPerSecond: Double): Double? =
      speedMetersPerSecond.takeIf(::isValidSpeed)?.let { 1_000.0 / it }

    fun canonicalAveragePace(durationSeconds: Long, distanceMeters: Double?): Double? =
      if (durationSeconds > 0 && distanceMeters != null && distanceMeters.isFinite() && distanceMeters > 0.0) {
        durationSeconds * 1_000.0 / distanceMeters
      } else null

    private fun isValidSpeed(value: Double): Boolean = value.isFinite() && value > 0.0
  }
}

data class RunChartScale(
  val minimumValue: Double,
  val maximumValue: Double,
  val tickValues: List<Double>,
  val lowerValuesAppearHigher: Boolean,
  val referenceValue: Double? = null,
) {
  init {
    require(minimumValue > 0.0)
    require(maximumValue > minimumValue)
  }

  fun verticalFraction(value: Double): Double {
    val bounded = value.coerceIn(minimumValue, maximumValue)
    val fraction = (bounded - minimumValue) / (maximumValue - minimumValue)
    return if (lowerValuesAppearHigher) fraction else 1.0 - fraction
  }
}

object PaceChartScaleCalculator {
  fun calculate(values: List<Double>, averagePaceSecondsPerKm: Double?): RunChartScale? {
    val bounded = values.asSequence()
      .filter { it.isFinite() && it > 0.0 }
      .map { it.coerceIn(MINIMUM_PACE, MAXIMUM_PACE) }
      .sorted()
      .toList()
    if (bounded.isEmpty()) return null

    val lower = percentile(bounded, if (bounded.size >= MINIMUM_TRIM_SIZE) 0.05 else 0.0)
    val upper = percentile(bounded, if (bounded.size >= MINIMUM_TRIM_SIZE) 0.95 else 1.0)
    val reference = averagePaceSecondsPerKm
      ?.takeIf { it.isFinite() && it > 0.0 }
      ?.coerceIn(MINIMUM_PACE, MAXIMUM_PACE)
    val observedMinimum = minOf(lower, reference ?: lower)
    val observedMaximum = maxOf(upper, reference ?: upper)
    val padding = ((observedMaximum - observedMinimum) * RANGE_PADDING_FRACTION).coerceAtLeast(MINIMUM_PADDING_SECONDS)
    var minimum = (observedMinimum - padding).coerceAtLeast(MINIMUM_PACE)
    var maximum = (observedMaximum + padding).coerceAtMost(MAXIMUM_PACE)
    if (maximum - minimum < MINIMUM_RANGE_SECONDS) {
      val midpoint = (minimum + maximum) / 2.0
      minimum = (midpoint - MINIMUM_RANGE_SECONDS / 2.0).coerceAtLeast(MINIMUM_PACE)
      maximum = (minimum + MINIMUM_RANGE_SECONDS).coerceAtMost(MAXIMUM_PACE)
      minimum = (maximum - MINIMUM_RANGE_SECONDS).coerceAtLeast(MINIMUM_PACE)
    }
    val ticks = (0 until TICK_COUNT).map { index ->
      minimum + (maximum - minimum) * index / (TICK_COUNT - 1)
    }
    return RunChartScale(
      minimumValue = minimum,
      maximumValue = maximum,
      tickValues = ticks,
      lowerValuesAppearHigher = true,
      referenceValue = reference,
    )
  }

  private fun percentile(sorted: List<Double>, fraction: Double): Double {
    if (sorted.size == 1) return sorted.first()
    val position = fraction * (sorted.lastIndex)
    val lowerIndex = position.toInt()
    val upperIndex = (lowerIndex + 1).coerceAtMost(sorted.lastIndex)
    val remainder = position - lowerIndex
    return sorted[lowerIndex] + (sorted[upperIndex] - sorted[lowerIndex]) * remainder
  }

  private const val MINIMUM_PACE = 180.0
  private const val MAXIMUM_PACE = 1_200.0
  private const val MINIMUM_TRIM_SIZE = 20
  private const val RANGE_PADDING_FRACTION = 0.10
  private const val MINIMUM_PADDING_SECONDS = 15.0
  private const val MINIMUM_RANGE_SECONDS = 60.0
  private const val TICK_COUNT = 5
}
