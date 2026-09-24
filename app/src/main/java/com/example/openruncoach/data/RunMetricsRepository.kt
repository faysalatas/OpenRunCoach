package com.example.openruncoach.data

import com.example.openruncoach.data.local.RunMetricValueEntity
import com.example.openruncoach.data.local.RunMetricsDao
import com.example.openruncoach.data.local.RunMetricsEntity
import com.example.openruncoach.data.local.RunMetricsWithValues
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.MetricVerification
import com.example.openruncoach.domain.metrics.RunMetricKey
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.metricValues
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RunMetricsRepository(private val dao: RunMetricsDao) {
  fun observeRunMetrics(runId: String): Flow<RunMetrics?> =
    dao.observeRunMetrics(runId).map { it?.toDomain() }

  suspend fun getRunMetrics(runId: String): RunMetrics? = dao.getRunMetrics(runId)?.toDomain()

  suspend fun save(metrics: RunMetrics) {
    dao.replace(
      RunMetricsEntity(
        runId = metrics.runId,
        startTimeEpochMillis = metrics.startTime.toEpochMilli(),
        endTimeEpochMillis = metrics.endTime.toEpochMilli(),
        generatedAtEpochMillis = metrics.generatedAt.toEpochMilli(),
        recordingSource = metrics.recordingSource,
        recordingDeviceManufacturer = metrics.recordingDeviceManufacturer,
        recordingDeviceModel = metrics.recordingDeviceModel,
        recordingDeviceName = metrics.recordingDeviceName,
      ),
      metrics.metricValues().map { (key, metric) -> metric.toEntity(metrics.runId, key) },
    )
  }
}

private fun MetricValue<out Number>.toEntity(runId: String, key: RunMetricKey) = RunMetricValueEntity(
  runId = runId,
  metric = key.name,
  value = value.toDouble(),
  source = source.name,
  confidence = confidence.name,
  verification = verification.name,
  secondaryValue = secondaryValue?.toDouble(),
  secondarySource = secondarySource?.name,
  verificationReason = verificationReason,
)

private fun RunMetricsWithValues.toDomain(): RunMetrics? {
  val metrics = values.associateBy { RunMetricKey.valueOf(it.metric) }
  fun double(key: RunMetricKey): MetricValue<Double>? = metrics[key]?.toDoubleMetric()
  val duration = metrics[RunMetricKey.DURATION]?.toLongMetric() ?: return null
  return RunMetrics(
    runId = snapshot.runId,
    startTime = Instant.ofEpochMilli(snapshot.startTimeEpochMillis),
    endTime = Instant.ofEpochMilli(snapshot.endTimeEpochMillis),
    durationSeconds = duration,
    distanceMeters = double(RunMetricKey.DISTANCE),
    averagePaceSecondsPerKm = double(RunMetricKey.AVERAGE_PACE),
    averageSpeedMetersPerSecond = double(RunMetricKey.AVERAGE_SPEED),
    maxSpeedMetersPerSecond = double(RunMetricKey.MAX_SPEED),
    averageHeartRate = double(RunMetricKey.AVERAGE_HEART_RATE),
    minHeartRate = double(RunMetricKey.MIN_HEART_RATE),
    maxHeartRate = double(RunMetricKey.MAX_HEART_RATE),
    vo2Max = double(RunMetricKey.VO2_MAX),
    averageCadence = double(RunMetricKey.AVERAGE_CADENCE),
    movingAverageCadence = double(RunMetricKey.MOVING_AVERAGE_CADENCE),
    maxCadence = double(RunMetricKey.MAX_CADENCE),
    elevationGainMeters = double(RunMetricKey.ELEVATION_GAIN),
    elevationLossMeters = double(RunMetricKey.ELEVATION_LOSS),
    minAltitudeMeters = double(RunMetricKey.MIN_ALTITUDE),
    maxAltitudeMeters = double(RunMetricKey.MAX_ALTITUDE),
    inclineDistanceMeters = double(RunMetricKey.INCLINE_DISTANCE),
    declineDistanceMeters = double(RunMetricKey.DECLINE_DISTANCE),
    caloriesKilocalories = double(RunMetricKey.CALORIES),
    recordingSource = snapshot.recordingSource,
    recordingDeviceManufacturer = snapshot.recordingDeviceManufacturer,
    recordingDeviceModel = snapshot.recordingDeviceModel,
    recordingDeviceName = snapshot.recordingDeviceName,
    generatedAt = Instant.ofEpochMilli(snapshot.generatedAtEpochMillis),
  )
}

private fun RunMetricValueEntity.toDoubleMetric() = MetricValue(
  value = value,
  source = MetricSource.valueOf(source),
  confidence = MetricConfidence.valueOf(confidence),
  verification = MetricVerification.valueOf(verification),
  secondaryValue = secondaryValue,
  secondarySource = secondarySource?.let(MetricSource::valueOf),
  verificationReason = verificationReason,
)

private fun RunMetricValueEntity.toLongMetric() = MetricValue(
  value = value.toLong(),
  source = MetricSource.valueOf(source),
  confidence = MetricConfidence.valueOf(confidence),
  verification = MetricVerification.valueOf(verification),
  secondaryValue = secondaryValue?.toLong(),
  secondarySource = secondarySource?.let(MetricSource::valueOf),
  verificationReason = verificationReason,
)
