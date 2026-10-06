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
import com.example.openruncoach.domain.samsunghealth.SamsungPhysicalWorkoutRelationship
import com.example.openruncoach.domain.samsunghealth.SamsungWorkoutMatchStatus
import com.example.openruncoach.domain.samsunghealth.ExerciseLogMetricSummary
import com.example.openruncoach.domain.samsunghealth.clipSamsungSensorData
import java.time.Duration

class SamsungHealthRunMetricsEnricher {
  fun enrich(base: RunMetrics, inspection: SamsungHealthInspection?): RunMetrics {
    val session = inspection?.enrichmentSession ?: return base
    val confidence = trustedConfidence(inspection) ?: return base
    val extendsBeyondCanonicalWindow = session.candidate.startTime.isBefore(base.startTime) ||
      session.candidate.endTime.isAfter(base.endTime)
    val enriched = if (
      inspection.enrichmentMatch.relationship == SamsungPhysicalWorkoutRelationship.OVERLAPPING_SESSION ||
      extendsBeyondCanonicalWindow
    ) enrichOverlapping(base, session) else enrichExact(base, session, confidence)
    return addPaceIfMissing(enriched)
  }

  private fun enrichExact(
    base: RunMetrics,
    session: SamsungExerciseSessionInspection,
    confidence: MetricConfidence,
  ): RunMetrics {
    val source = MetricSource.SAMSUNG_HEALTH
    val sourceDevice = session.sourceDeviceName ?: listOfNotNull(
      session.sourceDeviceManufacturer,
      session.sourceDeviceModel,
    ).joinToString(" ").takeIf(String::isNotBlank)
    fun merge(
      primary: MetricValue<Double>?,
      secondary: Double?,
      tolerance: com.example.openruncoach.domain.metrics.MetricTolerance,
    ): MetricValue<Double>? {
      val available = secondary?.takeIf { it.isFinite() } ?: return primary
      if (primary == null) return MetricValue(
        available,
        source,
        confidence,
        sourceAppName = "Samsung Health",
        sourceDeviceName = sourceDevice,
      )
      if (primary.source == source) return primary
      return verifyMetric(primary, available, source, tolerance)
    }

    val log = session.exerciseLogSamples.filter {
      !it.timestamp.isBefore(base.startTime) && !it.timestamp.isAfter(base.endTime)
    }
    fun sample(timestamp: java.time.Instant, value: Double) = TimedMetricSample(
      timestamp = timestamp,
      value = value,
      source = source,
      confidence = confidence,
      sourceAppName = "Samsung Health",
      sourceDeviceName = sourceDevice,
    )
    val samsungHeartRate = log.mapNotNull { item -> item.heartRate?.let { sample(item.timestamp, it) } }
    val samsungSpeed = log.mapNotNull { item -> item.speedMetersPerSecond?.let { sample(item.timestamp, it) } }
    val samsungCadence = log.mapNotNull { item -> item.cadence?.let { sample(item.timestamp, it) } }
    val samsungPower = log.mapNotNull { item -> item.powerWatts?.let { sample(item.timestamp, it) } }
    val movingCadence = movingCadenceAverage(samsungCadence, samsungSpeed)
    val sessionCadence = session.meanCadence ?: samsungCadence.map { it.value }
      .filter { it.isFinite() && it >= 0.0 }
      .takeIf { it.isNotEmpty() }
      ?.average()
    val sessionMaxCadence = session.maxCadence ?: samsungCadence.maxOfOrNull { it.value }
    val candidate = session.candidate

    return base.copy(
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
      averagePowerWatts = merge(base.averagePowerWatts, session.meanPowerWatts, RunMetricTolerances.POWER),
      maxPowerWatts = merge(base.maxPowerWatts, session.maxPowerWatts, RunMetricTolerances.POWER),
      elevationGainMeters = merge(base.elevationGainMeters, session.altitudeGainMeters, RunMetricTolerances.ELEVATION),
      elevationLossMeters = merge(base.elevationLossMeters, session.altitudeLossMeters, RunMetricTolerances.ELEVATION),
      minAltitudeMeters = merge(base.minAltitudeMeters, session.minAltitudeMeters, RunMetricTolerances.ELEVATION),
      maxAltitudeMeters = merge(base.maxAltitudeMeters, session.maxAltitudeMeters, RunMetricTolerances.ELEVATION),
      inclineDistanceMeters = merge(base.inclineDistanceMeters, session.inclineDistanceMeters, RunMetricTolerances.DISTANCE),
      declineDistanceMeters = merge(base.declineDistanceMeters, session.declineDistanceMeters, RunMetricTolerances.DISTANCE),
      caloriesKilocalories = merge(base.caloriesKilocalories, session.caloriesKilocalories, RunMetricTolerances.CALORIES),
      recordingSource = base.recordingSource ?: candidate.source.applicationId,
      recordingDeviceManufacturer = base.recordingDeviceManufacturer ?: candidate.source.manufacturer,
      recordingDeviceModel = base.recordingDeviceModel ?: candidate.source.model,
      recordingDeviceName = base.recordingDeviceName ?: candidate.source.deviceName,
      series = RunMetricSeries(
        heartRate = base.series.heartRate.ifEmpty { samsungHeartRate },
        speed = samsungSpeed.ifEmpty { base.series.speed },
        cadence = base.series.cadence.ifEmpty { samsungCadence },
        power = base.series.power.ifEmpty { samsungPower },
      ),
    )
  }

  private fun enrichOverlapping(
    base: RunMetrics,
    session: SamsungExerciseSessionInspection,
  ): RunMetrics {
    val clipped = clipSamsungSensorData(session.exerciseLogSamples, base.startTime, base.endTime)
    val source = MetricSource.SAMSUNG_HEALTH
    val sourceDevice = session.sourceDeviceName ?: listOfNotNull(
      session.sourceDeviceManufacturer,
      session.sourceDeviceModel,
    ).joinToString(" ").takeIf(String::isNotBlank)
    fun merge(
      primary: MetricValue<Double>?,
      secondary: Double?,
      tolerance: com.example.openruncoach.domain.metrics.MetricTolerance,
      metricConfidence: MetricConfidence?,
    ): MetricValue<Double>? {
      val available = secondary?.takeIf { it.isFinite() } ?: return primary
      val acceptedConfidence = metricConfidence ?: return primary
      if (primary == null) return MetricValue(
        value = available,
        source = source,
        confidence = acceptedConfidence,
        verificationReason = OVERLAP_PROVENANCE,
        sourceAppName = "Samsung Health",
        sourceDeviceName = sourceDevice,
      )
      if (primary.source == source) return primary
      val verified = verifyMetric(primary, available, source, tolerance)
      return verified.copy(
        verificationReason = listOfNotNull(verified.verificationReason, OVERLAP_PROVENANCE).joinToString(". "),
      )
    }

    val heartRateConfidence = clipped.heartRate.confidenceFor(base)
    val speedConfidence = clipped.speed.confidenceFor(base)
    val cadenceConfidence = clipped.cadence.confidenceFor(base)
    val powerConfidence = clipped.power.confidenceFor(base)
    fun sample(timestamp: java.time.Instant, value: Double, confidence: MetricConfidence?) = TimedMetricSample(
      timestamp = timestamp,
      value = value,
      source = source,
      confidence = confidence,
      sourceAppName = "Samsung Health",
      sourceDeviceName = sourceDevice,
    )
    val heartRate = clipped.samples.mapNotNull { item ->
      item.heartRate?.let { sample(item.timestamp, it, heartRateConfidence) }
    }
    val speed = clipped.samples.mapNotNull { item ->
      item.speedMetersPerSecond?.let { sample(item.timestamp, it, speedConfidence) }
    }
    val cadence = clipped.samples.mapNotNull { item ->
      item.cadence?.let { sample(item.timestamp, it, cadenceConfidence) }
    }
    val power = clipped.samples.mapNotNull { item ->
      item.powerWatts?.let { sample(item.timestamp, it, powerConfidence) }
    }
    // Full-session elevation/VO₂max cannot be attributed to a trimmed interval.
    return base.copy(
      durationSeconds = base.durationSeconds,
      distanceMeters = base.distanceMeters,
      averageSpeedMetersPerSecond = merge(
        base.averageSpeedMetersPerSecond, clipped.speed?.average, RunMetricTolerances.SPEED, speedConfidence,
      ),
      maxSpeedMetersPerSecond = merge(
        base.maxSpeedMetersPerSecond, clipped.speed?.maximum, RunMetricTolerances.SPEED, speedConfidence,
      ),
      averageHeartRate = merge(
        base.averageHeartRate, clipped.heartRate?.average, RunMetricTolerances.HEART_RATE, heartRateConfidence,
      ),
      minHeartRate = merge(
        base.minHeartRate, clipped.heartRate?.minimum, RunMetricTolerances.HEART_RATE, heartRateConfidence,
      ),
      maxHeartRate = merge(
        base.maxHeartRate, clipped.heartRate?.maximum, RunMetricTolerances.HEART_RATE, heartRateConfidence,
      ),
      averageCadence = merge(
        base.averageCadence, clipped.cadence?.average, RunMetricTolerances.CADENCE, cadenceConfidence,
      ),
      movingAverageCadence = merge(
        base.movingAverageCadence, clipped.movingAverageCadence, RunMetricTolerances.CADENCE, cadenceConfidence,
      ),
      maxCadence = merge(
        base.maxCadence, clipped.cadence?.maximum, RunMetricTolerances.CADENCE, cadenceConfidence,
      ),
      averagePowerWatts = merge(
        base.averagePowerWatts, clipped.power?.average, RunMetricTolerances.POWER, powerConfidence,
      ),
      maxPowerWatts = merge(
        base.maxPowerWatts, clipped.power?.maximum, RunMetricTolerances.POWER, powerConfidence,
      ),
      recordingSource = base.recordingSource,
      recordingDeviceManufacturer = base.recordingDeviceManufacturer,
      recordingDeviceModel = base.recordingDeviceModel,
      recordingDeviceName = base.recordingDeviceName,
      series = RunMetricSeries(
        heartRate = base.series.heartRate.ifEmpty { if (heartRateConfidence != null) heartRate else emptyList() },
        speed = if (speedConfidence != null && speed.isNotEmpty()) speed else base.series.speed,
        cadence = base.series.cadence.ifEmpty { if (cadenceConfidence != null) cadence else emptyList() },
        power = base.series.power.ifEmpty { if (powerConfidence != null) power else emptyList() },
      ),
    )
  }

  private fun addPaceIfMissing(enriched: RunMetrics): RunMetrics {
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

  private fun ExerciseLogMetricSummary?.confidenceFor(base: RunMetrics): MetricConfidence? {
    this ?: return null
    val canonicalDurationMillis = Duration.between(base.startTime, base.endTime).toMillis().coerceAtLeast(1)
    val sampleSpanMillis = Duration.between(firstTimestamp, lastTimestamp).toMillis().coerceAtLeast(0)
    val coverage = sampleSpanMillis.toDouble() / canonicalDurationMillis
    return when {
      sampleCount >= HIGH_CONFIDENCE_MINIMUM_SAMPLES && coverage >= HIGH_CONFIDENCE_SAMPLE_COVERAGE ->
        MetricConfidence.HIGH
      sampleCount >= MEDIUM_CONFIDENCE_MINIMUM_SAMPLES && coverage >= MEDIUM_CONFIDENCE_SAMPLE_COVERAGE ->
        MetricConfidence.MEDIUM
      else -> null
    }
  }

  private fun trustedConfidence(inspection: SamsungHealthInspection): MetricConfidence? {
    val match = inspection.enrichmentMatch
    if (match.status != SamsungWorkoutMatchStatus.MATCHED) return null
    if (match.manualSelection) return MetricConfidence.HIGH
    return when (match.confidence) {
      SamsungMatchConfidence.HIGH -> MetricConfidence.HIGH
      SamsungMatchConfidence.MEDIUM -> MetricConfidence.MEDIUM
      SamsungMatchConfidence.LOW,
      SamsungMatchConfidence.AMBIGUOUS -> null
    }
  }

  private companion object {
    const val HIGH_CONFIDENCE_MINIMUM_SAMPLES = 30
    const val HIGH_CONFIDENCE_SAMPLE_COVERAGE = 0.90
    const val MEDIUM_CONFIDENCE_MINIMUM_SAMPLES = 5
    const val MEDIUM_CONFIDENCE_SAMPLE_COVERAGE = 0.50
    const val OVERLAP_PROVENANCE = "Derived from Samsung sensor samples clipped to the canonical activity window"
  }
}
