package com.example.openruncoach.data

import com.example.openruncoach.data.local.RunMetricValueEntity
import com.example.openruncoach.data.local.RunMetricSampleEntity
import com.example.openruncoach.data.local.RunMetricsDao
import com.example.openruncoach.data.local.RunMetricsEntity
import com.example.openruncoach.data.local.RunMetricsWithValues
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.MetricVerification
import com.example.openruncoach.domain.metrics.RunMetricKey
import com.example.openruncoach.domain.metrics.RunSampleMetricType
import com.example.openruncoach.domain.metrics.RunTimeSeries
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.TimedMetricSample
import com.example.openruncoach.domain.metrics.metricValues
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RunMetricsRepository(private val dao: RunMetricsDao) {
  fun observeActualRunMetrics(runId: String): Flow<RunMetrics?> =
    dao.observeActualRunMetrics(runId).map { it?.toDomain() }

  fun observeRunMetrics(runId: String): Flow<RunMetrics?> =
    dao.observeRunMetrics(runId).map { it?.toDomain() }

  suspend fun getRunMetrics(runId: String): RunMetrics? = dao.getRunMetrics(runId)?.toDomain()

  suspend fun getAllRunMetrics(): List<RunMetrics> = dao.getAllRunMetricIds().mapNotNull { runId ->
    getRunMetrics(runId)
  }

  suspend fun getCanonicalRunMetrics(): List<RunMetrics> = dao.getCanonicalRunMetricIds().mapNotNull { runId ->
    getRunMetrics(runId)
  }

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
        dataVersion = metrics.dataVersion,
      ),
      metrics.metricValues().map { (key, metric) -> metric.toEntity(metrics.runId, key) },
      metrics.toSampleEntities(),
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
  sourceAppName = sourceAppName,
  sourceDeviceName = sourceDeviceName,
)

internal fun RunMetricsWithValues.toDomain(): RunMetrics? {
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
    averagePowerWatts = double(RunMetricKey.AVERAGE_POWER),
    maxPowerWatts = double(RunMetricKey.MAX_POWER),
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
    series = samples.toTimeSeries(),
    generatedAt = Instant.ofEpochMilli(snapshot.generatedAtEpochMillis),
    dataVersion = snapshot.dataVersion,
  )
}

internal fun RunMetrics.toSampleEntities(): List<RunMetricSampleEntity> {
  fun entities(
    type: RunSampleMetricType,
    samples: List<TimedMetricSample<Double>>,
    metric: MetricValue<Double>?,
  ) = samples.asSequence()
    .filter { !it.timestamp.isBefore(startTime) && !it.timestamp.isAfter(endTime) }
    .filter { it.value.isFinite() }
    .map { sample ->
      RunMetricSampleEntity(
        runId = runId,
        metricType = type.name,
        timestampEpochMillis = sample.timestamp.toEpochMilli(),
        value = sample.value,
        source = (sample.source ?: metric?.source ?: MetricSource.HEALTH_CONNECT).name,
        confidence = (sample.confidence ?: metric?.confidence ?: MetricConfidence.LOW).name,
        sourceAppName = sample.sourceAppName ?: metric?.sourceAppName,
        sourceDeviceName = sample.sourceDeviceName ?: metric?.sourceDeviceName,
      )
    }
  return sequenceOf(
    entities(RunSampleMetricType.HEART_RATE, series.heartRate, averageHeartRate),
    entities(RunSampleMetricType.SPEED, series.speed, averageSpeedMetersPerSecond),
    entities(RunSampleMetricType.CADENCE, series.cadence, movingAverageCadence ?: averageCadence),
    entities(RunSampleMetricType.POWER, series.power, averagePowerWatts),
  ).flatten().distinctBy { listOf(it.runId, it.metricType, it.timestampEpochMillis, it.source) }.toList()
}

internal fun List<RunMetricSampleEntity>.toTimeSeries(): RunTimeSeries {
  fun samples(type: RunSampleMetricType) = asSequence()
    .filter { it.metricType == type.name }
    .sortedBy(RunMetricSampleEntity::timestampEpochMillis)
    .map {
      TimedMetricSample(
        timestamp = Instant.ofEpochMilli(it.timestampEpochMillis),
        value = it.value,
        source = MetricSource.valueOf(it.source),
        confidence = MetricConfidence.valueOf(it.confidence),
        sourceAppName = it.sourceAppName,
        sourceDeviceName = it.sourceDeviceName,
      )
    }
    .toList()
  return RunTimeSeries(
    heartRate = samples(RunSampleMetricType.HEART_RATE),
    speed = samples(RunSampleMetricType.SPEED),
    cadence = samples(RunSampleMetricType.CADENCE),
    power = samples(RunSampleMetricType.POWER),
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
  sourceAppName = sourceAppName,
  sourceDeviceName = sourceDeviceName,
)

private fun RunMetricValueEntity.toLongMetric() = MetricValue(
  value = value.toLong(),
  source = MetricSource.valueOf(source),
  confidence = MetricConfidence.valueOf(confidence),
  verification = MetricVerification.valueOf(verification),
  secondaryValue = secondaryValue?.toLong(),
  secondarySource = secondarySource?.let(MetricSource::valueOf),
  verificationReason = verificationReason,
  sourceAppName = sourceAppName,
  sourceDeviceName = sourceDeviceName,
)
