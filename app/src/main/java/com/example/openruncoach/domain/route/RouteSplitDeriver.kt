package com.example.openruncoach.domain.route

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.TimedMetricSample
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteSplitDerivationResult(
  val route: RunRoute,
  val splits: List<RunSplit>,
)

class RouteSplitDeriver {
  fun derive(
    route: RunRoute,
    canonicalDistanceMeters: Double?,
    metrics: RunMetrics?,
  ): RouteSplitDerivationResult {
    val ordered = route.points
      .filter(::validPoint)
      .sortedWith(compareBy<RunRoutePoint>(RunRoutePoint::timestamp).thenBy(RunRoutePoint::sequence))
    if (ordered.size < MINIMUM_ROUTE_POINTS) {
      return emptyResult(route, canonicalDistanceMeters, route.points.size - ordered.size)
    }

    val vertices = mutableListOf(DistanceVertex(ordered.first().timestamp, 0.0))
    val acceptedPoints = mutableListOf(ordered.first())
    var previous = ordered.first()
    var rejected = route.points.size - ordered.size
    var hasLargeGap = false
    var rejectDerivation = false
    for (point in ordered.drop(1)) {
      val elapsedSeconds = Duration.between(previous.timestamp, point.timestamp).toMillis() / 1_000.0
      val distance = geodesicDistanceMeters(previous, point)
      when {
        elapsedSeconds < 0.0 -> rejected += 1
        elapsedSeconds == 0.0 && distance > DUPLICATE_DISTANCE_METERS -> rejected += 1
        elapsedSeconds > REJECT_GAP_SECONDS -> {
          hasLargeGap = true
          rejectDerivation = true
          previous = point
          acceptedPoints += point
          vertices += DistanceVertex(point.timestamp, vertices.last().distanceMeters)
        }
        elapsedSeconds > LARGE_GAP_SECONDS -> {
          hasLargeGap = true
          previous = point
          acceptedPoints += point
          vertices += DistanceVertex(point.timestamp, vertices.last().distanceMeters)
        }
        elapsedSeconds > 0.0 && distance / elapsedSeconds > MAXIMUM_RUNNING_SPEED_METERS_PER_SECOND -> {
          rejected += 1
        }
        else -> {
          val noiseFloor = noiseFloorMeters(previous, point)
          val acceptedDistance = if (distance <= noiseFloor) 0.0 else distance
          previous = point
          acceptedPoints += point
          vertices += DistanceVertex(point.timestamp, vertices.last().distanceMeters + acceptedDistance)
        }
      }
    }

    val derivedDistance = vertices.lastOrNull()?.distanceMeters ?: 0.0
    val usableCanonicalDistance = canonicalDistanceMeters?.takeIf { it.isFinite() && it > 0.0 }
    val differencePercent = usableCanonicalDistance?.let {
      abs(derivedDistance - it) / it * 100.0
    }
    val rejectedFraction = rejected.toDouble() / route.points.size.coerceAtLeast(1)
    if (
      derivedDistance < MINIMUM_DERIVABLE_DISTANCE_METERS ||
      rejectDerivation ||
      rejectedFraction > MAXIMUM_REJECTED_POINT_FRACTION ||
      (differencePercent != null && differencePercent > MAXIMUM_DISTANCE_DIFFERENCE_PERCENT)
    ) {
      return RouteSplitDerivationResult(
        route.copy(
          points = acceptedPoints.mapIndexed { index, point -> point.copy(sequence = index) },
          rejectedPointCount = route.rejectedPointCount + rejected,
          derivedDistanceMeters = derivedDistance,
          canonicalDistanceMeters = usableCanonicalDistance,
          distanceDifferencePercent = differencePercent,
          hasLargeSamplingGap = hasLargeGap,
          confidence = MetricConfidence.LOW,
        ),
        emptyList(),
      )
    }

    // A small provider-vs-GPS disagreement is calibrated uniformly. This changes only the
    // distance axis used for boundaries; timestamps and relative route shape remain unchanged.
    val scaleFactor = if (
      usableCanonicalDistance != null &&
      differencePercent != null &&
      differencePercent <= MAXIMUM_CALIBRATION_DIFFERENCE_PERCENT
    ) usableCanonicalDistance / derivedDistance else 1.0
    val calibratedVertices = vertices.map { it.copy(distanceMeters = it.distanceMeters * scaleFactor) }
    val splitDistance = calibratedVertices.last().distanceMeters
    val confidence = route.confidence
      .limitedBy(if (hasLargeGap || rejectedFraction > 0.02) MetricConfidence.LOW else MetricConfidence.HIGH)
      .limitedBy(when {
        differencePercent == null -> MetricConfidence.MEDIUM
        differencePercent <= MAXIMUM_CALIBRATION_DIFFERENCE_PERCENT -> MetricConfidence.HIGH
        differencePercent <= 10.0 -> MetricConfidence.MEDIUM
        else -> MetricConfidence.LOW
      })
    val splits = buildSplits(
      runId = route.runId,
      vertices = calibratedVertices,
      totalDistanceMeters = splitDistance,
      heartRate = metrics?.series?.heartRate.orEmpty(),
      cadence = metrics?.series?.cadence.orEmpty(),
      confidence = confidence,
    )
    return RouteSplitDerivationResult(
      route.copy(
        points = acceptedPoints.mapIndexed { index, point -> point.copy(sequence = index) },
        rejectedPointCount = route.rejectedPointCount + rejected,
        derivedDistanceMeters = derivedDistance,
        canonicalDistanceMeters = usableCanonicalDistance,
        distanceDifferencePercent = differencePercent,
        distanceScaleFactor = scaleFactor,
        hasLargeSamplingGap = hasLargeGap,
        confidence = confidence,
      ),
      splits,
    )
  }

  private fun buildSplits(
    runId: String,
    vertices: List<DistanceVertex>,
    totalDistanceMeters: Double,
    heartRate: List<TimedMetricSample<Double>>,
    cadence: List<TimedMetricSample<Double>>,
    confidence: MetricConfidence,
  ): List<RunSplit> {
    val boundaries = buildList {
      add(0.0)
      var boundary = STANDARD_SPLIT_METERS
      while (boundary < totalDistanceMeters) {
        add(boundary)
        boundary += STANDARD_SPLIT_METERS
      }
      add(totalDistanceMeters)
    }.distinct()
    return boundaries.zipWithNext().mapIndexedNotNull { index, (startDistance, endDistance) ->
      val distance = endDistance - startDistance
      if (distance < MINIMUM_PARTIAL_SPLIT_METERS) return@mapIndexedNotNull null
      val start = interpolateTime(vertices, startDistance)
      val end = interpolateTime(vertices, endDistance)
      val durationSeconds = Duration.between(start, end).toMillis() / 1_000.0
      if (durationSeconds <= 0.0) return@mapIndexedNotNull null
      RunSplit(
        runId = runId,
        ordinal = index + 1,
        derivation = RunSplitDerivation.ROUTE_DERIVED,
        startTime = start,
        endTime = end,
        distanceMeters = distance,
        durationSeconds = durationSeconds,
        averagePaceSecondsPerKm = durationSeconds * 1_000.0 / distance,
        averageHeartRate = samplesAverage(heartRate, start, end, endDistance == totalDistanceMeters),
        averageCadence = samplesAverage(cadence, start, end, endDistance == totalDistanceMeters),
        partial = distance < STANDARD_SPLIT_METERS - FULL_SPLIT_TOLERANCE_METERS,
        confidence = confidence,
      )
    }
  }

  private fun interpolateTime(vertices: List<DistanceVertex>, targetDistance: Double): Instant {
    if (targetDistance <= 0.0) return vertices.first().timestamp
    val upperIndex = vertices.indexOfFirst { it.distanceMeters >= targetDistance }
      .takeIf { it >= 0 } ?: return vertices.last().timestamp
    if (upperIndex == 0) return vertices.first().timestamp
    val before = vertices[upperIndex - 1]
    val after = vertices[upperIndex]
    val segmentDistance = after.distanceMeters - before.distanceMeters
    if (segmentDistance <= 0.0) return after.timestamp
    val fraction = ((targetDistance - before.distanceMeters) / segmentDistance).coerceIn(0.0, 1.0)
    val millis = Duration.between(before.timestamp, after.timestamp).toMillis()
    return before.timestamp.plusMillis((millis * fraction).toLong())
  }

  private fun samplesAverage(
    samples: List<TimedMetricSample<Double>>,
    start: Instant,
    end: Instant,
    includeEnd: Boolean,
  ): Double? {
    val values = samples.asSequence()
      .filter { !it.timestamp.isBefore(start) && (it.timestamp.isBefore(end) || includeEnd && it.timestamp == end) }
      .map { it.value }
      .filter { it.isFinite() && it > 0.0 }
      .toList()
    return values.takeIf { it.size >= MINIMUM_AGGREGATE_SAMPLES }?.average()
  }

  private fun emptyResult(route: RunRoute, canonicalDistanceMeters: Double?, additionallyRejected: Int) =
    RouteSplitDerivationResult(
      route.copy(
        rejectedPointCount = route.rejectedPointCount + additionallyRejected,
        canonicalDistanceMeters = canonicalDistanceMeters,
        confidence = MetricConfidence.LOW,
      ),
      emptyList(),
    )
}

private data class DistanceVertex(val timestamp: Instant, val distanceMeters: Double)

private fun validPoint(point: RunRoutePoint): Boolean =
  point.latitude.isFinite() && point.latitude in -90.0..90.0 &&
    point.longitude.isFinite() && point.longitude in -180.0..180.0 &&
    (point.altitudeMeters == null || point.altitudeMeters.isFinite()) &&
    (point.horizontalAccuracyMeters == null ||
      point.horizontalAccuracyMeters.isFinite() && point.horizontalAccuracyMeters >= 0.0) &&
    (point.verticalAccuracyMeters == null ||
      point.verticalAccuracyMeters.isFinite() && point.verticalAccuracyMeters >= 0.0)

private fun noiseFloorMeters(first: RunRoutePoint, second: RunRoutePoint): Double {
  val reportedAccuracy = listOfNotNull(first.horizontalAccuracyMeters, second.horizontalAccuracyMeters)
    .averageOrNull()
  return min(MAXIMUM_NOISE_FLOOR_METERS, (reportedAccuracy ?: 0.0) * ACCURACY_NOISE_FRACTION)
    .coerceAtLeast(DUPLICATE_DISTANCE_METERS)
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

internal fun geodesicDistanceMeters(first: RunRoutePoint, second: RunRoutePoint): Double {
  val latitude1 = Math.toRadians(first.latitude)
  val latitude2 = Math.toRadians(second.latitude)
  val latitudeDelta = latitude2 - latitude1
  val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
  val a = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
    cos(latitude1) * cos(latitude2) * sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
  return EARTH_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
}

private fun MetricConfidence.limitedBy(limit: MetricConfidence): MetricConfidence = when {
  this == MetricConfidence.LOW || limit == MetricConfidence.LOW -> MetricConfidence.LOW
  this == MetricConfidence.MEDIUM || limit == MetricConfidence.MEDIUM -> MetricConfidence.MEDIUM
  else -> MetricConfidence.HIGH
}

private const val EARTH_RADIUS_METERS = 6_371_008.8
private const val STANDARD_SPLIT_METERS = 1_000.0
private const val FULL_SPLIT_TOLERANCE_METERS = 1.0
private const val MINIMUM_PARTIAL_SPLIT_METERS = 10.0
private const val MINIMUM_DERIVABLE_DISTANCE_METERS = 100.0
private const val MINIMUM_ROUTE_POINTS = 3
private const val MINIMUM_AGGREGATE_SAMPLES = 2
private const val DUPLICATE_DISTANCE_METERS = 0.5
private const val ACCURACY_NOISE_FRACTION = 0.1
private const val MAXIMUM_NOISE_FLOOR_METERS = 2.0
private const val MAXIMUM_RUNNING_SPEED_METERS_PER_SECOND = 12.0
private const val LARGE_GAP_SECONDS = 120.0
private const val REJECT_GAP_SECONDS = 300.0
private const val MAXIMUM_REJECTED_POINT_FRACTION = 0.20
private const val MAXIMUM_CALIBRATION_DIFFERENCE_PERCENT = 5.0
private const val MAXIMUM_DISTANCE_DIFFERENCE_PERCENT = 15.0
