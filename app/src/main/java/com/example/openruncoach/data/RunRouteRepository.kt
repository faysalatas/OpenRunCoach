package com.example.openruncoach.data

import com.example.openruncoach.data.local.RunRouteDao
import com.example.openruncoach.data.local.RunRouteEntity
import com.example.openruncoach.data.local.RunRoutePointEntity
import com.example.openruncoach.data.local.RunRouteWithPointsAndSplits
import com.example.openruncoach.data.local.RunSplitEntity
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.route.RunRoute
import com.example.openruncoach.domain.route.RunRouteData
import com.example.openruncoach.domain.route.RunRoutePoint
import com.example.openruncoach.domain.route.RunSplit
import com.example.openruncoach.domain.route.RunSplitDerivation
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RunRouteRepository(private val dao: RunRouteDao) {
  fun observe(runId: String): Flow<RunRouteData?> = dao.observeRoute(runId).map { it?.toDomain() }

  suspend fun get(runId: String): RunRouteData? = dao.getRoute(runId)?.toDomain()

  suspend fun save(route: RunRoute, splits: List<RunSplit>) {
    require(route.runId.isNotBlank())
    require(route.points.map(RunRoutePoint::sequence).distinct().size == route.points.size)
    require(splits.all { it.runId == route.runId })
    dao.replace(
      route = route.toEntity(),
      points = route.points.map { it.toEntity(route.runId) },
      splits = splits.map(RunSplit::toEntity),
    )
  }
}

private fun RunRoute.toEntity() = RunRouteEntity(
  runId = runId,
  source = source.name,
  sourceAppName = sourceAppName,
  confidence = confidence.name,
  rejectedPointCount = rejectedPointCount,
  derivedDistanceMeters = derivedDistanceMeters,
  canonicalDistanceMeters = canonicalDistanceMeters,
  distanceDifferencePercent = distanceDifferencePercent,
  distanceScaleFactor = distanceScaleFactor,
  hasLargeSamplingGap = hasLargeSamplingGap,
)

private fun RunRoutePoint.toEntity(runId: String) = RunRoutePointEntity(
  runId = runId,
  sequence = sequence,
  timestampEpochMillis = timestamp.toEpochMilli(),
  latitude = latitude,
  longitude = longitude,
  altitudeMeters = altitudeMeters,
  horizontalAccuracyMeters = horizontalAccuracyMeters,
  verticalAccuracyMeters = verticalAccuracyMeters,
)

private fun RunSplit.toEntity() = RunSplitEntity(
  runId = runId,
  ordinal = ordinal,
  derivation = derivation.name,
  startTimeEpochMillis = startTime.toEpochMilli(),
  endTimeEpochMillis = endTime.toEpochMilli(),
  distanceMeters = distanceMeters,
  durationSeconds = durationSeconds,
  averagePaceSecondsPerKm = averagePaceSecondsPerKm,
  averageHeartRate = averageHeartRate,
  averageCadence = averageCadence,
  partial = partial,
  confidence = confidence.name,
)

private fun RunRouteWithPointsAndSplits.toDomain() = RunRouteData(
  route = RunRoute(
    runId = route.runId,
    source = MetricSource.valueOf(route.source),
    sourceAppName = route.sourceAppName,
    confidence = MetricConfidence.valueOf(route.confidence),
    points = points.sortedBy(RunRoutePointEntity::sequence).map { point ->
      RunRoutePoint(
        sequence = point.sequence,
        timestamp = Instant.ofEpochMilli(point.timestampEpochMillis),
        latitude = point.latitude,
        longitude = point.longitude,
        altitudeMeters = point.altitudeMeters,
        horizontalAccuracyMeters = point.horizontalAccuracyMeters,
        verticalAccuracyMeters = point.verticalAccuracyMeters,
      )
    },
    rejectedPointCount = route.rejectedPointCount,
    derivedDistanceMeters = route.derivedDistanceMeters,
    canonicalDistanceMeters = route.canonicalDistanceMeters,
    distanceDifferencePercent = route.distanceDifferencePercent,
    distanceScaleFactor = route.distanceScaleFactor,
    hasLargeSamplingGap = route.hasLargeSamplingGap,
  ),
  splits = splits.sortedBy(RunSplitEntity::ordinal).map { split ->
    RunSplit(
      runId = split.runId,
      ordinal = split.ordinal,
      derivation = RunSplitDerivation.valueOf(split.derivation),
      startTime = Instant.ofEpochMilli(split.startTimeEpochMillis),
      endTime = Instant.ofEpochMilli(split.endTimeEpochMillis),
      distanceMeters = split.distanceMeters,
      durationSeconds = split.durationSeconds,
      averagePaceSecondsPerKm = split.averagePaceSecondsPerKm,
      averageHeartRate = split.averageHeartRate,
      averageCadence = split.averageCadence,
      partial = split.partial,
      confidence = MetricConfidence.valueOf(split.confidence),
    )
  },
)
