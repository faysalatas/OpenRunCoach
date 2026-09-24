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
import com.example.openruncoach.domain.samsunghealth.SamsungWorkoutMatchStatus
import com.example.openruncoach.domain.samsunghealth.SamsungWorkoutTarget
import com.example.openruncoach.domain.samsunghealth.SamsungSourceDeviceType
import com.example.openruncoach.domain.samsunghealth.SharedMetricDifference
import com.example.openruncoach.domain.samsunghealth.investigateCadence
import com.example.openruncoach.domain.samsunghealth.matchSamsungWorkout
import com.example.openruncoach.domain.samsunghealth.summarizeExerciseLogMetric
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.device.AccessoryType
import com.samsung.android.sdk.health.data.device.Device
import com.samsung.android.sdk.health.data.device.DeviceGroup
import com.samsung.android.sdk.health.data.device.DeviceType
import com.samsung.android.sdk.health.data.request.DataType
import java.time.Duration
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
      val toleranceSeconds = 120L
      val records = dataSource.readExerciseData(
        target.startTime.minusSeconds(toleranceSeconds),
        target.endTime.plusSeconds(toleranceSeconds),
      )
      val sdkCandidates = mutableListOf<SdkCandidate>()
      for (record in records) {
        sdkCandidates += sessions(record)
      }
      val matchTarget = target.withHealthConnectMetrics(healthConnectInspection)
      val match = matchSamsungWorkout(
        matchTarget,
        sdkCandidates.map(SdkCandidate::candidate),
        manualSelectionCandidateId = manualSelectionCandidateId,
      )
      val matchedSdkSession = if (match.status == SamsungWorkoutMatchStatus.MATCHED) {
        sdkCandidates.singleOrNull { it.candidate.id == match.selected?.id }
      } else null
      val session = matchedSdkSession?.toInspection()
      val inspection = SamsungHealthInspection(
        match = match,
        session = session,
        providerMetrics = providerMetrics(healthConnectInspection, session),
        sharedDifferences = sharedDifferences(healthConnectInspection, session),
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

  private suspend fun sessions(point: HealthDataPoint): List<SdkCandidate> {
    val sessions = point.getValue(DataType.ExerciseType.SESSIONS).orEmpty()
    val source = point.dataSource
    val device = dataSource.device(source?.deviceId)
    return sessions.mapIndexedNotNull { index, session ->
      val kind = session.exerciseType.toDomainKind()
      if (kind == SamsungExerciseKind.OTHER) return@mapIndexedNotNull null
      val pointId = point.uid.takeIf(String::isNotBlank)
        ?: point.clientDataId?.takeIf(String::isNotBlank)
        ?: "${session.startTime.toEpochMilli()}-${session.endTime.toEpochMilli()}"
      val logs = session.toLogSamples()
      val cadence = investigateCadence(session.meanCadence?.toDouble(), session.maxCadence?.toDouble(), logs)
      val candidate = SamsungExerciseCandidate(
        id = if (sessions.size == 1) pointId else "$pointId#$index",
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
          exerciseLogExists = session.log != null,
          totalLogSamples = logs.orEmpty().size,
          cadenceSampleCount = cadence.cadenceSampleCount,
        ),
      )
      SdkCandidate(candidate, point, session, device)
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
    )
  }

  private data class SdkCandidate(
    val candidate: SamsungExerciseCandidate,
    val point: HealthDataPoint,
    val session: ExerciseSession,
    val device: Device?,
  )
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
