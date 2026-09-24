package com.example.openruncoach.data.healthconnect

import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.averagePaceSecondsPerKm
import com.example.openruncoach.domain.inspection.RunDataInspection
import com.example.openruncoach.domain.inspection.RunMetricType
import com.example.openruncoach.domain.inspection.SeriesMetricSummary
import com.example.openruncoach.domain.inspection.SessionMetricSummary
import com.example.openruncoach.domain.inspection.TotalMetricSummary
import com.example.openruncoach.domain.inspection.Vo2MaxMetricSummary
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetricSeries
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.TimedMetricSample
import com.example.openruncoach.domain.metrics.movingCadenceAverage
import java.time.Clock

class HealthConnectRunMetricsProvider(
  private val clock: Clock = Clock.systemUTC(),
) {
  fun build(run: RunSummary, inspection: RunDataInspection?): RunMetrics {
    fun metric(type: RunMetricType) = inspection?.metrics?.firstOrNull { it.metric == type }
    fun seriesSummary(type: RunMetricType) = metric(type)?.summary as? SeriesMetricSummary
    fun total(type: RunMetricType) = (metric(type)?.summary as? TotalMetricSummary)?.value
    fun samples(type: RunMetricType) = metric(type)?.samples.orEmpty().map {
      TimedMetricSample(it.timestamp, it.value)
    }
    fun value(value: Double?) = value?.takeIf { it.isFinite() }?.let {
      MetricValue(it, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH)
    }

    val session = metric(RunMetricType.EXERCISE_SESSION)?.summary as? SessionMetricSummary
    val duration = session?.durationSeconds ?: run.durationSeconds
    val distance = total(RunMetricType.DISTANCE) ?: run.distanceMeters
    val heartRate = seriesSummary(RunMetricType.HEART_RATE)
    val speed = seriesSummary(RunMetricType.SPEED)
    val cadence = seriesSummary(RunMetricType.CADENCE)
    val heartRateSamples = samples(RunMetricType.HEART_RATE)
    val speedSamples = samples(RunMetricType.SPEED)
    val cadenceSamples = samples(RunMetricType.CADENCE)
    val averagePace = averagePaceSecondsPerKm(duration, distance, speed?.average) ?: run.averagePaceSecondsPerKm

    return RunMetrics(
      runId = run.healthConnectRecordId,
      startTime = session?.startTime ?: run.startTime,
      endTime = session?.endTime ?: run.endTime,
      durationSeconds = MetricValue(duration, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
      distanceMeters = value(distance),
      averagePaceSecondsPerKm = averagePace?.let {
        MetricValue(it, MetricSource.DERIVED, MetricConfidence.HIGH)
      },
      averageSpeedMetersPerSecond = value(speed?.average),
      maxSpeedMetersPerSecond = value(speed?.maximum),
      averageHeartRate = value(heartRate?.average ?: run.averageHeartRate),
      minHeartRate = value(heartRate?.minimum),
      maxHeartRate = value(heartRate?.maximum ?: run.maxHeartRate?.toDouble()),
      vo2Max = value((metric(RunMetricType.VO2_MAX)?.summary as? Vo2MaxMetricSummary)?.valueMillilitersPerMinuteKilogram),
      averageCadence = value(cadence?.average),
      movingAverageCadence = value(movingCadenceAverage(cadenceSamples, speedSamples)),
      maxCadence = value(cadence?.maximum),
      elevationGainMeters = value(total(RunMetricType.ELEVATION_GAINED)),
      caloriesKilocalories = value(total(RunMetricType.CALORIES)),
      recordingSource = run.sourceAppName ?: run.sourcePackageName.takeIf(String::isNotBlank),
      recordingDeviceManufacturer = run.sourceDeviceManufacturer,
      recordingDeviceModel = run.sourceDeviceModel,
      series = RunMetricSeries(
        heartRate = heartRateSamples,
        speed = speedSamples,
        cadence = cadenceSamples,
        power = samples(RunMetricType.POWER),
      ),
      generatedAt = clock.instant(),
    )
  }
}
