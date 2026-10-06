package com.example.openruncoach.domain.metrics

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.floor

data class RunChartPoint(val elapsedSeconds: Double, val value: Double)

data class PreparedRunChart(
  val segments: List<List<RunChartPoint>>,
  val sourceDeviceName: String? = null,
  val scale: RunChartScale? = null,
) {
  val points: List<RunChartPoint> get() = segments.flatten()
}

data class RunChartData(
  val durationSeconds: Long,
  val pace: PreparedRunChart?,
  val heartRate: PreparedRunChart?,
  val cadence: PreparedRunChart?,
  val power: PreparedRunChart?,
  val averagePaceSecondsPerKm: Double?,
  val fastestThirtySecondPaceSecondsPerKm: Double?,
)

object RunChartPreparer {
  fun prepare(
    metrics: RunMetrics,
    targetPointCount: Int = 240,
    canonicalDurationSeconds: Long = metrics.durationSeconds.value,
    canonicalDistanceMeters: Double? = metrics.distanceMeters?.value,
    paceCalculator: PaceSeriesCalculator = PaceSeriesCalculator(),
  ): RunChartData {
    val paceSeries = paceCalculator.calculate(
      speedSamples = metrics.series.speed,
      canonicalDurationSeconds = canonicalDurationSeconds,
      canonicalDistanceMeters = canonicalDistanceMeters,
    )
    val preparedPace = prepareSegments(
      samples = paceSeries.samples,
      runStart = metrics.startTime,
      targetPointCount = targetPointCount,
      valid = { it > 0.0 && it.isFinite() },
      sourceDeviceName = metrics.series.speed.firstNotNullOfOrNull(TimedMetricSample<Double>::sourceDeviceName)
        ?: metrics.averageSpeedMetersPerSecond?.sourceDeviceName,
    )
    val pace = preparedPace?.copy(
      scale = PaceChartScaleCalculator.calculate(
        paceSeries.samples.map(TimedMetricSample<Double>::value),
        paceSeries.averagePaceSecondsPerKm,
      ),
    )
    val heartRate = prepareSegments(
      metrics.series.heartRate,
      metrics.startTime,
      targetPointCount,
      valid = { it > 0.0 && it.isFinite() },
      sourceDeviceName = metrics.averageHeartRate?.sourceDeviceName,
    )
    val movingCadence = metrics.series.cadence.map { sample ->
      val stopped = metrics.series.speed.nearestValue(sample.timestamp)?.let { it <= STOPPED_SPEED_METERS_PER_SECOND } == true
      if (sample.value <= 0.0 || stopped) sample.copy(value = Double.NaN) else sample
    }
    val cadence = prepareSegments(
      movingCadence,
      metrics.startTime,
      targetPointCount,
      valid = { it > 0.0 && it.isFinite() },
      sourceDeviceName = (metrics.movingAverageCadence ?: metrics.averageCadence)?.sourceDeviceName,
    )
    val power = prepareSegments(
      metrics.series.power,
      metrics.startTime,
      targetPointCount,
      valid = { it >= 0.0 && it.isFinite() },
      sourceDeviceName = metrics.averagePowerWatts?.sourceDeviceName,
    )
    return RunChartData(
      durationSeconds = canonicalDurationSeconds,
      pace = pace,
      heartRate = heartRate,
      cadence = cadence,
      power = power,
      averagePaceSecondsPerKm = paceSeries.averagePaceSecondsPerKm,
      fastestThirtySecondPaceSecondsPerKm = paceSeries.fastestThirtySecondPaceSecondsPerKm,
    )
  }

  fun downsample(points: List<RunChartPoint>, threshold: Int): List<RunChartPoint> {
    if (threshold < 3 || points.size <= threshold) return points
    val sampled = ArrayList<RunChartPoint>(threshold)
    val bucketSize = (points.size - 2).toDouble() / (threshold - 2)
    var selectedIndex = 0
    sampled += points.first()
    for (i in 0 until threshold - 2) {
      val averageStart = floor((i + 1) * bucketSize).toInt() + 1
      val averageEnd = (floor((i + 2) * bucketSize).toInt() + 1).coerceAtMost(points.size)
      val averageRange = points.subList(averageStart.coerceAtMost(points.lastIndex), averageEnd.coerceAtLeast(averageStart + 1).coerceAtMost(points.size))
      val averageX = averageRange.map(RunChartPoint::elapsedSeconds).average()
      val averageY = averageRange.map(RunChartPoint::value).average()
      val rangeStart = (floor(i * bucketSize).toInt() + 1).coerceAtMost(points.lastIndex)
      val rangeEnd = (floor((i + 1) * bucketSize).toInt() + 1).coerceAtMost(points.lastIndex)
      val anchor = points[selectedIndex]
      var maxArea = -1.0
      var nextIndex = rangeStart
      for (index in rangeStart..rangeEnd) {
        val point = points[index]
        val area = abs(
          (anchor.elapsedSeconds - averageX) * (point.value - anchor.value) -
            (anchor.elapsedSeconds - point.elapsedSeconds) * (averageY - anchor.value)
        )
        if (area > maxArea) {
          maxArea = area
          nextIndex = index
        }
      }
      sampled += points[nextIndex]
      selectedIndex = nextIndex
    }
    sampled += points.last()
    return sampled
  }

  private fun prepareSegments(
    samples: List<TimedMetricSample<Double>>,
    runStart: Instant,
    targetPointCount: Int,
    valid: (Double) -> Boolean,
    sourceDeviceName: String?,
  ): PreparedRunChart? {
    if (samples.isEmpty()) return null
    val rawSegments = mutableListOf<MutableList<RunChartPoint>>()
    var current = mutableListOf<RunChartPoint>()
    samples.sortedBy(TimedMetricSample<Double>::timestamp).forEach { sample ->
      if (!valid(sample.value)) {
        if (current.isNotEmpty()) rawSegments += current
        current = mutableListOf()
      } else {
        current += RunChartPoint(
          Duration.between(runStart, sample.timestamp).toMillis().coerceAtLeast(0) / 1_000.0,
          sample.value,
        )
      }
    }
    if (current.isNotEmpty()) rawSegments += current
    if (rawSegments.isEmpty()) return null
    val selected = downsample(rawSegments.flatten(), targetPointCount)
      .groupingBy { it }
      .eachCount()
      .toMutableMap()
    val prepared = rawSegments.mapNotNull { segment ->
      segment.filter { point ->
        val remaining = selected[point] ?: 0
        if (remaining == 0) false else {
          selected[point] = remaining - 1
          true
        }
      }.takeIf(List<RunChartPoint>::isNotEmpty)
    }
    return PreparedRunChart(prepared, sourceDeviceName)
      .takeIf { it.points.size >= MINIMUM_CHART_POINTS }
  }

  private fun List<TimedMetricSample<Double>>.nearestValue(timestamp: Instant): Double? =
    minByOrNull { abs(Duration.between(timestamp, it.timestamp).toMillis()) }
      ?.takeIf { abs(Duration.between(timestamp, it.timestamp).toMillis()) <= SPEED_ALIGNMENT_MILLIS }
      ?.value

  private const val STOPPED_SPEED_METERS_PER_SECOND = 0.1
  private const val SPEED_ALIGNMENT_MILLIS = 2_000L
  private const val MINIMUM_CHART_POINTS = 2
}
