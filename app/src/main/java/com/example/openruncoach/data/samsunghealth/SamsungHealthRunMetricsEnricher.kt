package com.example.openruncoach.data.samsunghealth

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetricSeries
import com.example.openruncoach.domain.metrics.RunMetricTolerances
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.TimedMetricSample
import com.example.openruncoach.domain.metrics.movingCadenceAverage
import com.example.openruncoach.domain.metrics.verifyDuration
import com.example.openruncoach.domain.metrics.verifyMetric
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseSessionInspection
import com.example.openruncoach.domain.samsunghealth.SamsungHealthInspection
import com.example.openruncoach.domain.samsunghealth.SamsungMatchConfidence
import com.example.openruncoach.domain.samsunghealth.SamsungWorkoutMatchStatus

class SamsungHealthRunMetricsEnricher {
  fun enrich(base: RunMetrics, inspection: SamsungHealthInspection?): RunMetrics {
    val session = inspection?.session ?: return base
    val confidence = trustedConfidence(inspection) ?: return base
    val source = MetricSource.SAMSUNG_HEALTH
    fun merge(
      primary: MetricValue<Double>?,
      secondary: Double?,
      tolerance: com.example.openruncoach.domain.metrics.MetricTolerance,
    ): MetricValue<Double>? {
      val available = secondary?.takeIf { it.isFinite() } ?: return primary
      if (primary == null) return MetricValue(available, source, confidence)
      if (primary.source == source) return primary
      return verifyMetric(primary, available, source, tolerance)
    }

    val log = session.exerciseLogSamples
    val samsungHeartRate = log.mapNotNull { sample -> sample.heartRate?.let { TimedMetricSample(sample.timestamp, it) } }
    val samsungSpeed = log.mapNotNull { sample -> sample.speedMetersPerSecond?.let { TimedMetricSample(sample.timestamp, it) } }
    val samsungCadence = log.mapNotNull { sample -> sample.cadence?.let { TimedMetricSample(sample.timestamp, it) } }
    val samsungPower = log.mapNotNull { sample -> sample.powerWatts?.let { TimedMetricSample(sample.timestamp, it) } }
    val movingCadence = movingCadenceAverage(samsungCadence, samsungSpeed)
    val sessionCadence = session.meanCadence ?: samsungCadence.map { it.value }
      .filter { it.isFinite() && it >= 0.0 }
      .takeIf { it.isNotEmpty() }
      ?.average()
    val sessionMaxCadence = session.maxCadence ?: samsungCadence.maxOfOrNull { it.value }
    val candidate = session.candidate

    val enriched = base.copy(
      durationSeconds = if (base.durationSeconds.source == source) base.durationSeconds else {
        verifyDuration(base.durationSeconds, candidate.durationSeconds, source)
      },
      distanceMeters = merge(base.distanceMeters, candidate.distanceMeters, RunMetricTolerances.DISTANCE),
      averageSpeedMetersPerSecond = merge(base.averageSpeedMetersPerSecond, session.meanSpeedMetersPerSecond, RunMetricTolerances.SPEED),
      maxSpeedMetersPerSecond = merge(base.maxSpeedMetersPerSecond, session.maxSpeedMetersPerSecond, RunMetricTolerances.SPEED),
      averageHeartRate = merge(base.averageHeartRate, session.meanHeartRate, RunMetricTolerances.HEART_RATE),
      minHeartRate = merge(base.minHeartRate, session.minHeartRate, RunMetricTolerances.HEART_RATE),
      maxHeartRate = merge(base.maxHeartRate, session.maxHeartRate, RunMetricTolerances.HEART_RATE),
      vo2Max = merge(base.vo2Max, session.vo2Max, RunMetricTolerances.VO2_MAX),
      averageCadence = merge(base.averageCadence, sessionCadence, RunMetricTolerances.CADENCE),
      movingAverageCadence = merge(base.movingAverageCadence, movingCadence, RunMetricTolerances.CADENCE),
      maxCadence = merge(base.maxCadence, sessionMaxCadence, RunMetricTolerances.CADENCE),
      elevationGainMeters = merge(base.elevationGainMeters, session.altitudeGainMeters, RunMetricTolerances.ELEVATION),
      elevationLossMeters = merge(base.elevationLossMeters, session.altitudeLossMeters, RunMetricTolerances.ELEVATION),
      minAltitudeMeters = merge(base.minAltitudeMeters, session.minAltitudeMeters, RunMetricTolerances.ELEVATION),
      maxAltitudeMeters = merge(base.maxAltitudeMeters, session.maxAltitudeMeters, RunMetricTolerances.ELEVATION),
      inclineDistanceMeters = merge(base.inclineDistanceMeters, session.inclineDistanceMeters, RunMetricTolerances.DISTANCE),
      declineDistanceMeters = merge(base.declineDistanceMeters, session.declineDistanceMeters, RunMetricTolerances.DISTANCE),
      caloriesKilocalories = merge(base.caloriesKilocalories, session.caloriesKilocalories, RunMetricTolerances.CALORIES),
      recordingSource = base.recordingSource ?: candidate.source.applicationId,
      recordingDeviceManufacturer = candidate.source.manufacturer ?: base.recordingDeviceManufacturer,
      recordingDeviceModel = candidate.source.model ?: base.recordingDeviceModel,
      recordingDeviceName = candidate.source.deviceName ?: base.recordingDeviceName,
      series = RunMetricSeries(
        heartRate = base.series.heartRate.ifEmpty { samsungHeartRate },
        speed = base.series.speed.ifEmpty { samsungSpeed },
        cadence = base.series.cadence.ifEmpty { samsungCadence },
        power = base.series.power.ifEmpty { samsungPower },
      ),
    )
    if (enriched.averagePaceSecondsPerKm != null) return enriched
    val distance = enriched.distanceMeters ?: return enriched
    if (distance.value <= 0.0 || enriched.durationSeconds.value <= 0L) return enriched
    val confidenceForPace = if (
      distance.confidence == MetricConfidence.HIGH && enriched.durationSeconds.confidence == MetricConfidence.HIGH
    ) MetricConfidence.HIGH else MetricConfidence.MEDIUM
    return enriched.copy(
      averagePaceSecondsPerKm = MetricValue(
        value = enriched.durationSeconds.value * 1_000.0 / distance.value,
        source = MetricSource.DERIVED,
        confidence = confidenceForPace,
      )
    )
  }

  private fun trustedConfidence(inspection: SamsungHealthInspection): MetricConfidence? {
    val match = inspection.match
    if (match.status != SamsungWorkoutMatchStatus.MATCHED) return null
    if (match.manualSelection) return MetricConfidence.HIGH
    return when (match.confidence) {
      SamsungMatchConfidence.HIGH -> MetricConfidence.HIGH
      SamsungMatchConfidence.MEDIUM -> MetricConfidence.MEDIUM
      SamsungMatchConfidence.LOW,
      SamsungMatchConfidence.AMBIGUOUS -> null
    }
  }
}
