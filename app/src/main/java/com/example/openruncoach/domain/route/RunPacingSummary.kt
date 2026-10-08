package com.example.openruncoach.domain.route

import com.example.openruncoach.domain.RunActualResolver
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.metrics.MetricConfidence
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

data class RunDistanceSample(val timestamp: Instant, val distanceMeters: Double)
enum class HalfSplitDirection { NEGATIVE, POSITIVE, EVEN }

data class EqualDistanceHalves(
  val halfDistanceMeters: Double,
  val midpoint: Instant,
  val firstHalfDurationSeconds: Double,
  val secondHalfDurationSeconds: Double,
) {
  val firstHalfPaceSecondsPerKm: Double get() = firstHalfDurationSeconds * 1_000.0 / halfDistanceMeters
  val secondHalfPaceSecondsPerKm: Double get() = secondHalfDurationSeconds * 1_000.0 / halfDistanceMeters
  val deltaSecondsPerKm: Double get() = secondHalfPaceSecondsPerKm - firstHalfPaceSecondsPerKm
  val direction: HalfSplitDirection get() = when {
    abs(deltaSecondsPerKm) <= EVEN_SPLIT_TOLERANCE_SECONDS_PER_KM -> HalfSplitDirection.EVEN
    deltaSecondsPerKm < 0 -> HalfSplitDirection.NEGATIVE
    else -> HalfSplitDirection.POSITIVE
  }
}

const val EVEN_SPLIT_TOLERANCE_SECONDS_PER_KM = 3.0

data class RunSplitSummary(
  val actualDistanceMeters: Double?,
  val wholeRunAveragePaceSecondsPerKm: Double?,
  val baselineSource: String,
  val deltasSecondsPerKm: Map<Int, Double>,
  val equalDistanceHalves: EqualDistanceHalves?,
)

/** Factual display values only; does not change splits, response or coaching evidence. */
object RunPacingSummaryCalculator {
  /** Shared by presentation and dumps: route geometry stays independent of summary provenance. */
  fun summarize(run: RunSummary, data: RunRouteData?): RunSplitSummary {
    val actual = RunActualResolver.resolve(run)
    return RunSplitSummary(
      actualDistanceMeters = actual.distanceMeters,
      wholeRunAveragePaceSecondsPerKm = actual.averagePaceSecondsPerKm,
      baselineSource = "RESOLVED_CANONICAL_ACTUAL_DURATION_OVER_DISTANCE",
      deltasSecondsPerKm = data?.splits.orEmpty().mapNotNull { split ->
        splitDeltaSecondsPerKm(split.averagePaceSecondsPerKm, actual.averagePaceSecondsPerKm)?.let { split.ordinal to it }
      }.toMap(),
      equalDistanceHalves = data?.route?.let { fromRoute(it, actual.distanceMeters, actual.startTime, actual.endTime) },
    )
  }

  fun splitDeltaSecondsPerKm(splitPace: Double, wholeRunPace: Double?): Double? =
    wholeRunPace?.takeIf { it.isFinite() && it > 0.0 }
      ?.let { average -> splitPace.takeIf { it.isFinite() && it > 0.0 }?.minus(average) }

  fun fromRoute(route: RunRoute, actualDistanceMeters: Double?, start: Instant, end: Instant): EqualDistanceHalves? {
    val distance = actualDistanceMeters?.takeIf { it.isFinite() && it >= MINIMUM_RUN_DISTANCE_METERS } ?: return null
    if (route.confidence == MetricConfidence.LOW || route.hasLargeSamplingGap) return null
    // Reuse exactly the existing route validation/noise/jump/calibration pipeline.
    // No new kilometre boundaries, sensor aggregation, or persisted values are written.
    val evidence = RouteSplitDeriver().derive(route, distance, null, includeDistanceSamples = true)
    if (evidence.route.confidence == MetricConfidence.LOW || evidence.route.hasLargeSamplingGap) return null
    val samples = evidence.distanceSamples
    if (samples.isEmpty() || samples.first().timestamp < start || samples.last().timestamp > end) return null
    if (Duration.between(start, samples.first().timestamp).toMillis() > MAXIMUM_EDGE_GAP_MILLIS ||
      Duration.between(samples.last().timestamp, end).toMillis() > MAXIMUM_EDGE_GAP_MILLIS) return null
    // Require the existing bounded calibration to cover the actual run distance.
    if (abs(samples.last().distanceMeters - distance) > 0.01) return null
    val covered = buildList {
      if (samples.first().timestamp > start) add(RunDistanceSample(start, 0.0))
      addAll(samples)
      if (samples.last().timestamp < end) add(RunDistanceSample(end, distance))
    }
    return calculate(covered, distance)
  }

  /** Interpolate the first crossing of half distance; stationary time remains in elapsed time. */
  fun calculate(samples: List<RunDistanceSample>, actualDistanceMeters: Double): EqualDistanceHalves? {
    if (!actualDistanceMeters.isFinite() || actualDistanceMeters < MINIMUM_RUN_DISTANCE_METERS || samples.size < 3) return null
    if (samples.any { !it.distanceMeters.isFinite() || it.distanceMeters < 0.0 } ||
      abs(samples.first().distanceMeters) > 0.01 || abs(samples.last().distanceMeters - actualDistanceMeters) > 0.01) return null
    if (samples.zipWithNext().any { (a, b) ->
        b.distanceMeters < a.distanceMeters || b.timestamp <= a.timestamp ||
          Duration.between(a.timestamp, b.timestamp).toMillis() > MAXIMUM_SAMPLE_GAP_MILLIS
      }) return null
    val half = actualDistanceMeters / 2.0
    val upperIndex = samples.indexOfFirst { it.distanceMeters >= half }
    if (upperIndex <= 0) return null
    val before = samples[upperIndex - 1]
    val after = samples[upperIndex]
    val intervalDistance = after.distanceMeters - before.distanceMeters
    if (intervalDistance <= 0.0) return null
    val fraction = (half - before.distanceMeters) / intervalDistance
    val midpoint = before.timestamp.plusNanos((Duration.between(before.timestamp, after.timestamp).toNanos() * fraction).toLong())
    val firstDuration = Duration.between(samples.first().timestamp, midpoint).toNanos() / 1_000_000_000.0
    val secondDuration = Duration.between(midpoint, samples.last().timestamp).toNanos() / 1_000_000_000.0
    if (firstDuration <= 0.0 || secondDuration <= 0.0) return null
    return EqualDistanceHalves(half, midpoint, firstDuration, secondDuration)
  }
}

private const val MINIMUM_RUN_DISTANCE_METERS = 1_000.0 // At least 500 m per half.
private const val MAXIMUM_SAMPLE_GAP_MILLIS = 120_000L
private const val MAXIMUM_EDGE_GAP_MILLIS = 15_000L
