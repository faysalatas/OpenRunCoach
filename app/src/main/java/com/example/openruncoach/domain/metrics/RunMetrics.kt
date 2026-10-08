package com.example.openruncoach.domain.metrics

import java.time.Instant

enum class MetricSource { HEALTH_CONNECT, SAMSUNG_HEALTH, DERIVED }

enum class MetricConfidence { HIGH, MEDIUM, LOW }

enum class MetricVerification { VERIFIED, CLOSE_MATCH, UNVERIFIED, CONFLICT }

data class MetricValue<T>(
  val value: T,
  val source: MetricSource,
  val confidence: MetricConfidence,
  val verification: MetricVerification = MetricVerification.UNVERIFIED,
  val secondaryValue: T? = null,
  val secondarySource: MetricSource? = null,
  val verificationReason: String? = null,
  val sourceAppName: String? = null,
  val sourceDeviceName: String? = null,
)

data class TimedMetricSample<T>(
  val timestamp: Instant,
  val value: T,
  val source: MetricSource? = null,
  val confidence: MetricConfidence? = null,
  val sourceAppName: String? = null,
  val sourceDeviceName: String? = null,
)

data class RunTimeSeries(
  val heartRate: List<TimedMetricSample<Double>> = emptyList(),
  val speed: List<TimedMetricSample<Double>> = emptyList(),
  val cadence: List<TimedMetricSample<Double>> = emptyList(),
  val power: List<TimedMetricSample<Double>> = emptyList(),
)

typealias RunMetricSeries = RunTimeSeries

/**
 * Provider-independent measurements/enrichment, not the authoritative whole-run activity summary.
 * Distance may be a time-window aggregate or a provider measurement that differs from an edited
 * canonical session. Whole-run actual consumers resolve RunSummary through RunActualResolver.
 */
data class RunMetrics(
  val runId: String,
  val startTime: Instant,
  val endTime: Instant,
  val durationSeconds: MetricValue<Long>,
  val distanceMeters: MetricValue<Double>? = null,
  val averagePaceSecondsPerKm: MetricValue<Double>? = null,
  val averageSpeedMetersPerSecond: MetricValue<Double>? = null,
  val maxSpeedMetersPerSecond: MetricValue<Double>? = null,
  val averageHeartRate: MetricValue<Double>? = null,
  val minHeartRate: MetricValue<Double>? = null,
  val maxHeartRate: MetricValue<Double>? = null,
  val vo2Max: MetricValue<Double>? = null,
  val averageCadence: MetricValue<Double>? = null,
  val movingAverageCadence: MetricValue<Double>? = null,
  val maxCadence: MetricValue<Double>? = null,
  val averagePowerWatts: MetricValue<Double>? = null,
  val maxPowerWatts: MetricValue<Double>? = null,
  val elevationGainMeters: MetricValue<Double>? = null,
  val elevationLossMeters: MetricValue<Double>? = null,
  val minAltitudeMeters: MetricValue<Double>? = null,
  val maxAltitudeMeters: MetricValue<Double>? = null,
  val inclineDistanceMeters: MetricValue<Double>? = null,
  val declineDistanceMeters: MetricValue<Double>? = null,
  val caloriesKilocalories: MetricValue<Double>? = null,
  val recordingSource: String? = null,
  val recordingDeviceManufacturer: String? = null,
  val recordingDeviceModel: String? = null,
  val recordingDeviceName: String? = null,
  val series: RunTimeSeries = RunTimeSeries(),
  val generatedAt: Instant,
  val dataVersion: Int = CURRENT_RUN_METRICS_DATA_VERSION,
)

const val CURRENT_RUN_METRICS_DATA_VERSION = 5

enum class RunSampleMetricType { HEART_RATE, SPEED, CADENCE, POWER }

enum class RunMetricKey {
  DURATION,
  DISTANCE,
  AVERAGE_PACE,
  AVERAGE_SPEED,
  MAX_SPEED,
  AVERAGE_HEART_RATE,
  MIN_HEART_RATE,
  MAX_HEART_RATE,
  VO2_MAX,
  AVERAGE_CADENCE,
  MOVING_AVERAGE_CADENCE,
  MAX_CADENCE,
  AVERAGE_POWER,
  MAX_POWER,
  ELEVATION_GAIN,
  ELEVATION_LOSS,
  MIN_ALTITUDE,
  MAX_ALTITUDE,
  INCLINE_DISTANCE,
  DECLINE_DISTANCE,
  CALORIES,
}

fun RunMetrics.metricValues(): Map<RunMetricKey, MetricValue<out Number>> = buildMap {
  put(RunMetricKey.DURATION, durationSeconds)
  distanceMeters?.let { put(RunMetricKey.DISTANCE, it) }
  averagePaceSecondsPerKm?.let { put(RunMetricKey.AVERAGE_PACE, it) }
  averageSpeedMetersPerSecond?.let { put(RunMetricKey.AVERAGE_SPEED, it) }
  maxSpeedMetersPerSecond?.let { put(RunMetricKey.MAX_SPEED, it) }
  averageHeartRate?.let { put(RunMetricKey.AVERAGE_HEART_RATE, it) }
  minHeartRate?.let { put(RunMetricKey.MIN_HEART_RATE, it) }
  maxHeartRate?.let { put(RunMetricKey.MAX_HEART_RATE, it) }
  vo2Max?.let { put(RunMetricKey.VO2_MAX, it) }
  averageCadence?.let { put(RunMetricKey.AVERAGE_CADENCE, it) }
  movingAverageCadence?.let { put(RunMetricKey.MOVING_AVERAGE_CADENCE, it) }
  maxCadence?.let { put(RunMetricKey.MAX_CADENCE, it) }
  averagePowerWatts?.let { put(RunMetricKey.AVERAGE_POWER, it) }
  maxPowerWatts?.let { put(RunMetricKey.MAX_POWER, it) }
  elevationGainMeters?.let { put(RunMetricKey.ELEVATION_GAIN, it) }
  elevationLossMeters?.let { put(RunMetricKey.ELEVATION_LOSS, it) }
  minAltitudeMeters?.let { put(RunMetricKey.MIN_ALTITUDE, it) }
  maxAltitudeMeters?.let { put(RunMetricKey.MAX_ALTITUDE, it) }
  inclineDistanceMeters?.let { put(RunMetricKey.INCLINE_DISTANCE, it) }
  declineDistanceMeters?.let { put(RunMetricKey.DECLINE_DISTANCE, it) }
  caloriesKilocalories?.let { put(RunMetricKey.CALORIES, it) }
}
