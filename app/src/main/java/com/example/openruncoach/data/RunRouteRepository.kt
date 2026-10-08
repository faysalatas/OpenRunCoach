package com.example.openruncoach.data

import com.example.openruncoach.data.local.PhysicalRunRouteWithPointsAndSplits
import com.example.openruncoach.data.local.RunRouteDao
import com.example.openruncoach.data.local.RunRouteEntity
import com.example.openruncoach.data.local.RunRoutePointEntity
import com.example.openruncoach.data.local.RunRouteWithPointsAndSplits
import com.example.openruncoach.data.local.RunSplitEntity
import com.example.openruncoach.domain.RunActualResolver
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.route.RunRoute
import com.example.openruncoach.domain.route.RunRouteData
import com.example.openruncoach.domain.route.RunRoutePoint
import com.example.openruncoach.domain.route.RunSplit
import com.example.openruncoach.domain.route.RunSplitDerivation
import com.example.openruncoach.domain.route.RunSplitResolver
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RunRouteRepository(private val dao: RunRouteDao) {
  fun observe(runId: String): Flow<RunRouteData?> = combine(
    dao.observePhysicalRoute(runId), dao.observePhysicalMetrics(runId),
  ) { stored, metrics -> stored?.let { resolve(it, metrics?.toDomain()) } }.flowOn(Dispatchers.Default)

  fun observeAll(): Flow<Map<String, RunRouteData>> = dao.observePhysicalRoutes().map { records ->
    records.groupBy(PhysicalRunRouteWithPointsAndSplits::physicalRunId).mapNotNull { (id, candidates) ->
      val metrics = dao.getPhysicalMetrics(id)?.toDomain()
      candidates.firstNotNullOfOrNull { resolve(it, metrics) }?.let { id to it }
    }.toMap()
  }.flowOn(Dispatchers.Default)

  suspend fun get(runId: String): RunRouteData? = withContext(Dispatchers.Default) {
    dao.getPhysicalRoute(runId)?.let { resolve(it, dao.getPhysicalMetrics(runId)?.toDomain()) }
  }

  /** Explicit normal-refresh cache backfill. Never invoked by get/observe or diagnostics. */
  suspend fun backfillSplits(runId: String) {
    withContext(Dispatchers.Default) {
      dao.backfillPhysicalSplits(runId) { stored, metrics ->
        resolve(stored, metrics?.toDomain())?.splits.orEmpty()
          .map { it.copy(runId = stored.route.runId).toEntity() }
      }
    }
  }

  suspend fun save(route: RunRoute, splits: List<RunSplit>) {
    validate(route, splits)
    dao.replace(
      route = route.toEntity(),
      points = route.points.map { it.toEntity(route.runId) },
      splits = splits.map(RunSplit::toEntity),
    )
  }

  suspend fun saveForPhysicalRun(route: RunRoute, splits: List<RunSplit>, physicalRunId: String) {
    require(physicalRunId.isNotBlank())
    validate(route, splits)
    val storageOwner = requireNotNull(dao.routeStorageOwner(physicalRunId))
    dao.replaceForPhysicalRun(
      physicalRunId = physicalRunId,
      route = route.copy(runId = storageOwner).toEntity(),
      points = route.points.map { it.toEntity(storageOwner) },
      splits = splits.map { it.copy(runId = storageOwner).toEntity() },
    )
  }

  private fun validate(route: RunRoute, splits: List<RunSplit>) {
    require(route.runId.isNotBlank())
    require(route.points.map(RunRoutePoint::sequence).distinct().size == route.points.size)
    require(splits.all { it.runId == route.runId })
  }
}

private fun resolve(
  stored: PhysicalRunRouteWithPointsAndSplits,
  metrics: RunMetrics?,
): RunRouteData? = stored.toPhysicalDomain()?.let {
  val canonical = RunSummary(
    healthConnectRecordId = stored.canonicalRunId,
    physicalRunId = stored.physicalRunId,
    startTime = Instant.ofEpochMilli(stored.canonicalStartTimeEpochMillis),
    endTime = Instant.ofEpochMilli(stored.canonicalEndTimeEpochMillis),
    durationSeconds = stored.canonicalDurationSeconds,
    distanceMeters = stored.canonicalRunDistanceMeters,
    averagePaceSecondsPerKm = null, averageHeartRate = null, maxHeartRate = null,
  )
  val actual = RunActualResolver.resolve(canonical, metrics)
  RunSplitResolver.resolve(it, actual.distanceMeters, metrics)
}

internal fun PhysicalRunRouteWithPointsAndSplits.toPhysicalDomain(): RunRouteData? {
  val data = RunRouteWithPointsAndSplits(route, points, splits).toDomain()
  val start = Instant.ofEpochMilli(canonicalStartTimeEpochMillis)
  val end = Instant.ofEpochMilli(canonicalEndTimeEpochMillis)
  val clipped = data.route.points.filter { !it.timestamp.isBefore(start) && !it.timestamp.isAfter(end) }
    .mapIndexed { index, point -> point.copy(sequence = index) }
  if (clipped.size < 2) return null
  val windowChanged = clipped.size != data.route.points.size
  return RunRouteData(
    route = data.route.copy(
      runId = physicalRunId,
      points = clipped,
      derivedDistanceMeters = if (windowChanged) null else data.route.derivedDistanceMeters,
      canonicalDistanceMeters = if (windowChanged) null else data.route.canonicalDistanceMeters,
      distanceDifferencePercent = if (windowChanged) null else data.route.distanceDifferencePercent,
      distanceScaleFactor = if (windowChanged) 1.0 else data.route.distanceScaleFactor,
    ),
    // Never reuse boundaries from a trimmed-away interval. The resolver derives a read-only fallback.
    splits = if (windowChanged) emptyList() else data.splits
      .takeIf { values -> values.all { !it.startTime.isBefore(start) && !it.endTime.isAfter(end) } }
      .orEmpty().map { it.copy(runId = physicalRunId) },
    persistedSplitCount = data.splits.size,
  )
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
