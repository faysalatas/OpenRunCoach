package com.example.openruncoach.data.samsunghealth

import com.example.openruncoach.domain.inspection.MetricAvailability
import com.example.openruncoach.domain.inspection.RunDataInspection
import com.example.openruncoach.domain.inspection.RunMetricType
import com.example.openruncoach.domain.inspection.SeriesMetricSummary
import com.example.openruncoach.domain.inspection.SessionMetricSummary
import com.example.openruncoach.domain.inspection.TotalMetricSummary
import com.example.openruncoach.domain.inspection.Vo2MaxMetricSummary
import com.example.openruncoach.domain.samsunghealth.ProviderMetric
import com.example.openruncoach.domain.samsunghealth.ProviderMetricPresence
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseCandidate
import com.example.openruncoach.domain.samsunghealth.SamsungCandidateMetrics
import com.example.openruncoach.domain.samsunghealth.SamsungCandidateSource
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseKind
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseLogSample
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseSessionInspection
import com.example.openruncoach.domain.samsunghealth.SamsungHealthInspection
import com.example.openruncoach.domain.samsunghealth.SamsungWorkoutTarget
import com.example.openruncoach.domain.samsunghealth.SamsungSourceDeviceType
import com.example.openruncoach.domain.samsunghealth.SamsungPhysicalWorkoutRelationship
import com.example.openruncoach.domain.samsunghealth.SharedMetricDifference
import com.example.openruncoach.domain.samsunghealth.clipSamsungSensorData
import com.example.openruncoach.domain.samsunghealth.investigateCadence
import com.example.openruncoach.domain.samsunghealth.matchSamsungEnrichment
import com.example.openruncoach.domain.samsunghealth.matchSamsungIdentity
import com.example.openruncoach.domain.samsunghealth.summarizeExerciseLogMetric
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.route.RunRoute
import com.example.openruncoach.domain.route.RunRoutePoint
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.device.AccessoryType
import com.samsung.android.sdk.health.data.device.Device
import com.samsung.android.sdk.health.data.device.DeviceGroup
import com.samsung.android.sdk.health.data.device.DeviceType
import com.samsung.android.sdk.health.data.request.DataType
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

data class SamsungHealthInspectionResult(
  val availability: SamsungHealthAvailabilityState,
  val inspection: SamsungHealthInspection? = null,
)

class SamsungHealthExerciseInspector(
  private val dataSource: SamsungHealthDataSource,
  private val permissionManager: SamsungHealthPermissionManager,
) {
  suspend fun inspect(
    target: SamsungWorkoutTarget,
    healthConnectInspection: RunDataInspection?,
    manualSelectionCandidateId: String? = null,
  ): SamsungHealthInspectionResult {
    val availability = permissionManager.availability()
    if (availability.status != SamsungHealthAvailability.AVAILABLE) {
      return SamsungHealthInspectionResult(availability)
    }
    return try {
      val records = dataSource.readExerciseData(
        target.startTime.minusSeconds(QUERY_START_PADDING_SECONDS),
        target.endTime.plusSeconds(QUERY_END_PADDING_SECONDS),
      )
      val sdkCandidates = mutableListOf<SdkCandidate>()
      for (record in records) {
        sdkCandidates += sessions(
          point = record,
          target = target,
          includeRoute = availability.exerciseLocationReadPermission == SamsungExercisePermissionState.GRANTED,
        )
      }
      val matchTarget = target.withHealthConnectMetrics(healthConnectInspection)
      val candidates = sdkCandidates.map(SdkCandidate::candidate)
      val identityMatch = matchSamsungIdentity(matchTarget, candidates)
      val enrichmentMatch = matchSamsungEnrichment(
        target = matchTarget,
        candidates = candidates,
        identityCandidateId = identityMatch.selected?.id,
        manualSelectionCandidateId = manualSelectionCandidateId,
      )
      val plausibleIds = (identityMatch.plausibleCandidates + enrichmentMatch.plausibleCandidates)
        .mapTo(mutableSetOf(), SamsungExerciseCandidate::id)
      val plausibleSessions = sdkCandidates
        .filter { it.candidate.id in plausibleIds }
        .map { it.toInspection() }
      val identitySession = plausibleSessions.singleOrNull { it.candidate.id == identityMatch.selected?.id }
      val enrichmentSession = plausibleSessions.singleOrNull { it.candidate.id == enrichmentMatch.selected?.id }
      val clippedEnrichment = enrichmentSession?.takeIf {
        enrichmentMatch.relationship == SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION
      }?.let { clipSamsungSensorData(it.exerciseLogSamples, target.startTime, target.endTime) }
      val inspection = SamsungHealthInspection(
        match = identityMatch,
        session = identitySession,
        providerMetrics = providerMetrics(healthConnectInspection, enrichmentSession),
        sharedDifferences = sharedDifferences(healthConnectInspection, enrichmentSession),
        identityMatch = identityMatch,
        identitySession = identitySession,
        enrichmentMatch = enrichmentMatch,
        enrichmentSession = enrichmentSession,
        plausibleSessions = plausibleSessions,
        clippedEnrichment = clippedEnrichment,
      )
      SamsungHealthInspectionResult(availability, inspection)
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      SamsungHealthInspectionResult(
        permissionManager.failureState(error, SamsungHealthOperations.READ_EXERCISE)
      )
    }
  }

  private suspend fun sessions(
    point: HealthDataPoint,
    target: SamsungWorkoutTarget,
    includeRoute: Boolean,
  ): List<SdkCandidate> {
    val sessions = point.getValue(DataType.ExerciseType.SESSIONS).orEmpty()
    val source = point.dataSource
    val device = dataSource.device(source?.deviceId)
    return sessions.mapIndexedNotNull { index, session ->
      val kind = session.exerciseType.toDomainKind()
      if (kind == SamsungExerciseKind.OTHER) return@mapIndexedNotNull null
      val pointId = point.uid.takeIf(String::isNotBlank)
        ?: point.clientDataId?.takeIf(String::isNotBlank)
        ?: "${session.startTime.toEpochMilli()}-${session.endTime.toEpochMilli()}"
      val candidateId = if (sessions.size == 1) pointId else "$pointId#$index"
      val logs = session.toLogSamples()
      val routeDiagnostics = if (includeRoute) session.routeDiagnostics(target) else null
      val cadence = investigateCadence(session.meanCadence?.toDouble(), session.maxCadence?.toDouble(), logs)
      val candidate = SamsungExerciseCandidate(
        id = candidateId,
        startTime = session.startTime,
        endTime = session.endTime,
        durationSeconds = session.duration.seconds.coerceAtLeast(0),
        distanceMeters = session.distance.validMetric(),
        exerciseKind = kind,
        source = SamsungCandidateSource(
          uid = pointId,
          dataPointStartTime = point.startTime,
          dataPointEndTime = point.endTime,
          updateTime = point.updateTime,
          applicationId = source?.appId,
          deviceId = source?.deviceId,
          deviceType = device?.deviceType.toDomainType(),
          manufacturer = device?.manufacturer,
          model = device?.model,
          deviceName = device?.name,
        ),
        metrics = SamsungCandidateMetrics(
          averageHeartRate = session.meanHeartRate.validMetric(),
          maxHeartRate = session.maxHeartRate.validMetric(),
          averageSpeedMetersPerSecond = session.meanSpeed.validMetric(),
          vo2Max = session.vo2Max.validMetric(),
          meanCadence = session.meanCadence.validMetric(),
          maxCadence = session.maxCadence.validMetric(),
          routePointCount = routeDiagnostics?.pointCount,
          routeAltitudePointCount = routeDiagnostics?.altitudePointCount,
          routeAccuracyPointCount = routeDiagnostics?.accuracyPointCount,
          routeRejectedPointCount = routeDiagnostics?.rejectedPointCount,
          routeFirstTimestamp = routeDiagnostics?.firstTimestamp,
          routeLastTimestamp = routeDiagnostics?.lastTimestamp,
          exerciseLogExists = session.log != null,
          totalLogSamples = logs.orEmpty().size,
          heartRateSampleCount = logs.orEmpty().count { it.heartRate != null },
          speedSampleCount = logs.orEmpty().count { it.speedMetersPerSecond != null },
          cadenceSampleCount = cadence.cadenceSampleCount,
          powerSampleCount = logs.orEmpty().count { it.powerWatts != null },
          powerAvailable = session.meanPower.validMetric() != null || session.maxPower.validMetric() != null,
          elevationAvailable = listOf(
            session.altitudeGain,
            session.altitudeLoss,
            session.minAltitude,
            session.maxAltitude,
          ).any { it.validMetric() != null },
        ),
      )
      val route = routeDiagnostics?.let { diagnostics ->
        RunRoute(
          runId = target.runId ?: candidateId,
          source = MetricSource.SAMSUNG_HEALTH,
          sourceAppName = "Samsung Health",
          confidence = MetricConfidence.LOW,
          points = diagnostics.points,
          rejectedPointCount = diagnostics.rejectedPointCount,
        )
      }
      SdkCandidate(candidate, point, session, device, route)
    }
  }

  private suspend fun SdkCandidate.toInspection(): SamsungExerciseSessionInspection {
    val logs = session.toLogSamples()
    val source = point.dataSource
    return SamsungExerciseSessionInspection(
      candidate = candidate,
      caloriesKilocalories = session.calories.toDouble().takeIf { it.isFinite() && it >= 0.0 },
      meanHeartRate = session.meanHeartRate.validMetric(),
      minHeartRate = session.minHeartRate.validMetric(),
      maxHeartRate = session.maxHeartRate.validMetric(),
      meanSpeedMetersPerSecond = session.meanSpeed.validMetric(),
      maxSpeedMetersPerSecond = session.maxSpeed.validMetric(),
      meanCadence = session.meanCadence.validMetric(),
      maxCadence = session.maxCadence.validMetric(),
      meanPowerWatts = session.meanPower.validMetric(),
      maxPowerWatts = session.maxPower.validMetric(),
      vo2Max = session.vo2Max.validMetric(),
      altitudeGainMeters = session.altitudeGain.validMetric(),
      altitudeLossMeters = session.altitudeLoss.validMetric(),
      minAltitudeMeters = session.minAltitude.validMetric(),
      maxAltitudeMeters = session.maxAltitude.validMetric(),
      inclineDistanceMeters = session.inclineDistance.validMetric(),
      declineDistanceMeters = session.declineDistance.validMetric(),
      customTitle = session.customTitle,
      comment = session.comment,
      sourceAppId = source?.appId,
      sourceDeviceId = source?.deviceId,
      sourceDeviceManufacturer = device?.manufacturer,
      sourceDeviceModel = device?.model,
      sourceDeviceName = device?.name,
      cadence = investigateCadence(session.meanCadence?.toDouble(), session.maxCadence?.toDouble(), logs),
      heartRateLogSummary = summarizeExerciseLogMetric(logs) { it.heartRate },
      speedLogSummary = summarizeExerciseLogMetric(logs) { it.speedMetersPerSecond },
      powerLogSummary = summarizeExerciseLogMetric(logs) { it.powerWatts },
      countLogSummary = summarizeExerciseLogMetric(logs) { it.count?.toDouble() },
      exerciseLogSamples = logs.orEmpty(),
      route = route,
    )
  }

  private data class SdkCandidate(
    val candidate: SamsungExerciseCandidate,
    val point: HealthDataPoint,
    val session: ExerciseSession,
    val device: Device?,
    val route: RunRoute?,
  )

  private companion object {
    const val QUERY_START_PADDING_SECONDS = 120L
    const val QUERY_END_PADDING_SECONDS = 30L * 60L
  }
}

private fun ExerciseSession.toLogSamples(): List<SamsungExerciseLogSample>? = log?.map { entry ->
  SamsungExerciseLogSample(
    timestamp = entry.timestamp,
    heartRate = entry.heartRate.validMetric(),
    cadence = entry.cadence.validMetric(),
    speedMetersPerSecond = entry.speed.validMetric(),
    powerWatts = entry.power.validMetric(),
    count = entry.count,
  )
}

private data class SamsungRouteDiagnostics(
  val pointCount: Int,
  val altitudePointCount: Int,
  val accuracyPointCount: Int,
  val rejectedPointCount: Int,
  val firstTimestamp: Instant?,
  val lastTimestamp: Instant?,
  val points: List<RunRoutePoint>,
)

private fun ExerciseSession.routeDiagnostics(target: SamsungWorkoutTarget): SamsungRouteDiagnostics? {
  val sourcePoints = route ?: return null
  val earliestAccepted = target.startTime.minusSeconds(ROUTE_TIME_TOLERANCE_SECONDS)
  val latestAccepted = target.endTime.plusSeconds(ROUTE_TIME_TOLERANCE_SECONDS)
  val accepted = sourcePoints.mapIndexedNotNull { sourceSequence, point ->
    val latitude = point.latitude.toDouble()
    val longitude = point.longitude.toDouble()
    val altitude = point.altitude?.toDouble()
    val accuracy = point.accuracy?.toDouble()
    val timestamp = point.timestamp
    if (
      !latitude.isFinite() || latitude !in -90.0..90.0 ||
      !longitude.isFinite() || longitude !in -180.0..180.0 ||
      (altitude != null && !altitude.isFinite()) ||
      (accuracy != null && (!accuracy.isFinite() || accuracy < 0.0)) ||
      timestamp.isBefore(earliestAccepted) || timestamp.isAfter(latestAccepted)
    ) {
      null
    } else {
      ValidSamsungRoutePoint(
        sourceSequence = sourceSequence,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitude,
        accuracyMeters = accuracy,
      )
    }
  }.sortedWith(compareBy<ValidSamsungRoutePoint>(ValidSamsungRoutePoint::timestamp)
    .thenBy(ValidSamsungRoutePoint::sourceSequence))
  return SamsungRouteDiagnostics(
    pointCount = accepted.size,
    altitudePointCount = accepted.count { it.altitudeMeters != null },
    accuracyPointCount = accepted.count { it.accuracyMeters != null },
    rejectedPointCount = sourcePoints.size - accepted.size,
    firstTimestamp = accepted.firstOrNull()?.timestamp,
    lastTimestamp = accepted.lastOrNull()?.timestamp,
    points = accepted.mapIndexed { index, point ->
      RunRoutePoint(
        sequence = index,
        timestamp = point.timestamp,
        latitude = point.latitude,
        longitude = point.longitude,
        altitudeMeters = point.altitudeMeters,
        horizontalAccuracyMeters = point.accuracyMeters,
      )
    },
  )
}

private data class ValidSamsungRoutePoint(
  val sourceSequence: Int,
  val timestamp: Instant,
  val latitude: Double,
  val longitude: Double,
  val altitudeMeters: Double?,
  val accuracyMeters: Double?,
)

private const val ROUTE_TIME_TOLERANCE_SECONDS = 120L

private fun DeviceType?.toDomainType(): SamsungSourceDeviceType = when (this) {
  DeviceGroup.WATCH -> SamsungSourceDeviceType.WATCH
  DeviceGroup.MOBILE -> SamsungSourceDeviceType.MOBILE
  DeviceGroup.RING -> SamsungSourceDeviceType.RING
  DeviceGroup.BAND -> SamsungSourceDeviceType.BAND
  DeviceGroup.ACCESSORY -> SamsungSourceDeviceType.ACCESSORY
  DeviceGroup.OTHER -> SamsungSourceDeviceType.OTHER
  is AccessoryType -> SamsungSourceDeviceType.ACCESSORY
  else -> SamsungSourceDeviceType.UNKNOWN
}

private fun SamsungWorkoutTarget.withHealthConnectMetrics(inspection: RunDataInspection?): SamsungWorkoutTarget {
  fun summary(type: RunMetricType) = inspection?.metrics?.firstOrNull { it.metric == type }?.summary
  return copy(
    averageHeartRate = (summary(RunMetricType.HEART_RATE) as? SeriesMetricSummary)?.average ?: averageHeartRate,
    maxHeartRate = (summary(RunMetricType.HEART_RATE) as? SeriesMetricSummary)?.maximum ?: maxHeartRate,
    averageSpeedMetersPerSecond = (summary(RunMetricType.SPEED) as? SeriesMetricSummary)?.average
      ?: averageSpeedMetersPerSecond,
    vo2Max = (summary(RunMetricType.VO2_MAX) as? Vo2MaxMetricSummary)?.valueMillilitersPerMinuteKilogram ?: vo2Max,
  )
}

private fun DataType.ExerciseType.PredefinedExerciseType.toDomainKind(): SamsungExerciseKind = when (this) {
  DataType.ExerciseType.PredefinedExerciseType.RUNNING -> SamsungExerciseKind.RUNNING
  DataType.ExerciseType.PredefinedExerciseType.TRACK_RUNNING -> SamsungExerciseKind.TRACK_RUNNING
  else -> SamsungExerciseKind.OTHER
}

private fun Float?.validMetric(): Double? = this?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 }

private fun providerMetrics(
  healthConnect: RunDataInspection?,
  samsung: SamsungExerciseSessionInspection?,
): List<ProviderMetricPresence> {
  fun healthConnectAvailable(type: RunMetricType) =
    healthConnect?.metrics?.firstOrNull { it.metric == type }?.availability == MetricAvailability.AVAILABLE
  return listOf(
    ProviderMetricPresence(ProviderMetric.DISTANCE, healthConnectAvailable(RunMetricType.DISTANCE), samsung?.candidate?.distanceMeters != null),
    ProviderMetricPresence(ProviderMetric.DURATION, healthConnectAvailable(RunMetricType.EXERCISE_SESSION), samsung != null),
    ProviderMetricPresence(ProviderMetric.AVERAGE_HEART_RATE, healthConnectAvailable(RunMetricType.HEART_RATE), samsung?.meanHeartRate != null),
    ProviderMetricPresence(ProviderMetric.MAX_HEART_RATE, healthConnectAvailable(RunMetricType.HEART_RATE), samsung?.maxHeartRate != null),
    ProviderMetricPresence(ProviderMetric.SPEED, healthConnectAvailable(RunMetricType.SPEED), samsung?.meanSpeedMetersPerSecond != null),
    ProviderMetricPresence(ProviderMetric.VO2_MAX, healthConnectAvailable(RunMetricType.VO2_MAX), samsung?.vo2Max != null),
    ProviderMetricPresence(ProviderMetric.CADENCE, healthConnectAvailable(RunMetricType.CADENCE), samsung?.cadence?.detailedCadenceAvailable == true || samsung?.meanCadence != null),
    ProviderMetricPresence(ProviderMetric.POWER, healthConnectAvailable(RunMetricType.POWER), samsung?.meanPowerWatts != null),
    ProviderMetricPresence(ProviderMetric.ELEVATION, healthConnectAvailable(RunMetricType.ELEVATION_GAINED), samsung?.altitudeGainMeters != null),
  )
}

private fun sharedDifferences(
  healthConnect: RunDataInspection?,
  samsung: SamsungExerciseSessionInspection?,
): List<SharedMetricDifference> {
  if (healthConnect == null || samsung == null) return emptyList()
  fun summary(type: RunMetricType) = healthConnect.metrics.firstOrNull { it.metric == type }?.summary
  val result = mutableListOf<SharedMetricDifference>()
  fun add(metric: ProviderMetric, hc: Double?, sh: Double?, unit: String) {
    if (hc != null && sh != null && hc.isFinite() && sh.isFinite()) {
      result += SharedMetricDifference(metric, hc, sh, unit)
    }
  }
  add(
    ProviderMetric.DISTANCE,
    (summary(RunMetricType.DISTANCE) as? TotalMetricSummary)?.value,
    samsung.candidate.distanceMeters,
    "m",
  )
  add(
    ProviderMetric.DURATION,
    (summary(RunMetricType.EXERCISE_SESSION) as? SessionMetricSummary)?.durationSeconds?.toDouble(),
    samsung.candidate.durationSeconds.toDouble(),
    "s",
  )
  add(
    ProviderMetric.AVERAGE_HEART_RATE,
    (summary(RunMetricType.HEART_RATE) as? SeriesMetricSummary)?.average,
    samsung.meanHeartRate,
    "bpm",
  )
  add(
    ProviderMetric.MAX_HEART_RATE,
    (summary(RunMetricType.HEART_RATE) as? SeriesMetricSummary)?.maximum,
    samsung.maxHeartRate,
    "bpm",
  )
  add(
    ProviderMetric.SPEED,
    (summary(RunMetricType.SPEED) as? SeriesMetricSummary)?.average,
    samsung.meanSpeedMetersPerSecond,
    "m/s",
  )
  add(
    ProviderMetric.VO2_MAX,
    (summary(RunMetricType.VO2_MAX) as? Vo2MaxMetricSummary)?.valueMillilitersPerMinuteKilogram,
    samsung.vo2Max,
    "ml/kg/min",
  )
  return result
}
