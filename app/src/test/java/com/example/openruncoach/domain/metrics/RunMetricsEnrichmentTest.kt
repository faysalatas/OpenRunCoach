package com.example.openruncoach.domain.metrics

import com.example.openruncoach.data.healthconnect.HealthConnectRunMetricsProvider
import com.example.openruncoach.data.samsunghealth.SamsungHealthRunMetricsEnricher
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.inspection.MetricAvailability
import com.example.openruncoach.domain.inspection.MetricInspection
import com.example.openruncoach.domain.inspection.RunDataInspection
import com.example.openruncoach.domain.inspection.RunMetricType
import com.example.openruncoach.domain.inspection.SeriesMetricSummary
import com.example.openruncoach.domain.inspection.SessionMetricSummary
import com.example.openruncoach.domain.inspection.TimedNumericSample
import com.example.openruncoach.domain.inspection.TotalMetricSummary
import com.example.openruncoach.domain.inspection.Vo2MaxMetricSummary
import com.example.openruncoach.domain.inspection.Vo2MaxTiming
import com.example.openruncoach.domain.samsunghealth.CadenceInvestigation
import com.example.openruncoach.domain.samsunghealth.SampleAlignment
import com.example.openruncoach.domain.samsunghealth.SampleAlignmentStatus
import com.example.openruncoach.domain.samsunghealth.SamsungCandidateSource
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseCandidate
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseKind
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseLogSample
import com.example.openruncoach.domain.samsunghealth.SamsungExerciseSessionInspection
import com.example.openruncoach.domain.samsunghealth.SamsungHealthInspection
import com.example.openruncoach.domain.samsunghealth.SamsungMatchConfidence
import com.example.openruncoach.domain.samsunghealth.SamsungSourceDeviceType
import com.example.openruncoach.domain.samsunghealth.SamsungWorkoutMatchResult
import com.example.openruncoach.domain.samsunghealth.SamsungWorkoutMatchStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RunMetricsEnrichmentTest {
  private val start = Instant.parse("2026-09-20T10:35:04Z")
  private val clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC)
  private val healthProvider = HealthConnectRunMetricsProvider(clock)
  private val enricher = SamsungHealthRunMetricsEnricher()

  @Test
  fun healthConnectOnlyBuildsProviderIndependentMetrics() {
    val result = healthProvider.build(run(), healthInspection())

    assertEquals(MetricSource.HEALTH_CONNECT, result.distanceMeters?.source)
    assertEquals(4_802.8, result.distanceMeters!!.value, 0.01)
    assertEquals(145.2, result.averageHeartRate!!.value, 0.01)
    assertEquals(167.0, result.maxHeartRate!!.value, 0.01)
    assertEquals(2, result.series.heartRate.size)
    assertEquals(MetricSource.DERIVED, result.averagePaceSecondsPerKm?.source)
  }

  @Test
  fun samsungUnavailableReturnsExistingMetricsUnchanged() {
    val base = healthProvider.build(run(), healthInspection())
    assertSame(base, enricher.enrich(base, null))
  }

  @Test
  fun highMatchAddsSamsungCadenceAndElevation() {
    val result = enricher.enrich(base(), samsungInspection(SamsungMatchConfidence.HIGH))

    assertEquals(MetricSource.SAMSUNG_HEALTH, result.averageCadence?.source)
    assertEquals(MetricConfidence.HIGH, result.averageCadence?.confidence)
    assertEquals(158.9, result.averageCadence!!.value, 0.01)
    assertEquals(160.0, result.movingAverageCadence!!.value, 0.01)
    assertEquals(16.5, result.elevationGainMeters!!.value, 0.01)
    assertEquals("Galaxy Watch8", result.recordingDeviceName)
    assertEquals(listOf(0.0, 158.0, 162.0), result.series.cadence.map { it.value })
  }

  @Test
  fun elevationEnrichmentUsesSamsungWhenHealthConnectIsMissingIt() {
    val result = enricher.enrich(base(), samsungInspection(SamsungMatchConfidence.HIGH))

    assertEquals(MetricSource.SAMSUNG_HEALTH, result.elevationGainMeters?.source)
    assertEquals(16.5, result.elevationGainMeters!!.value, 0.01)
    assertEquals(14.0, result.elevationLossMeters!!.value, 0.01)
    assertEquals(35.0, result.minAltitudeMeters!!.value, 0.01)
    assertEquals(48.0, result.maxAltitudeMeters!!.value, 0.01)
  }

  @Test
  fun mediumMatchEnrichesWithMediumConfidence() {
    val result = enricher.enrich(base(), samsungInspection(SamsungMatchConfidence.MEDIUM))
    assertEquals(MetricConfidence.MEDIUM, result.averageCadence?.confidence)
  }

  @Test
  fun ambiguousMatchDoesNotEnrich() {
    val base = base()
    val result = enricher.enrich(base, samsungInspection(
      confidence = SamsungMatchConfidence.AMBIGUOUS,
      status = SamsungWorkoutMatchStatus.AMBIGUOUS,
    ))
    assertSame(base, result)
  }

  @Test
  fun manualSelectionIsTrustedEvenWithLowAutomaticConfidence() {
    val result = enricher.enrich(base(), samsungInspection(SamsungMatchConfidence.LOW, manual = true))
    assertEquals(MetricConfidence.HIGH, result.averageCadence?.confidence)
  }

  @Test
  fun missingSamsungCadenceRemainsAbsent() {
    val result = enricher.enrich(base(), samsungInspection(
      SamsungMatchConfidence.HIGH,
      session = samsungSession(meanCadence = null, maxCadence = null, logs = emptyList()),
    ))
    assertNull(result.averageCadence)
    assertNull(result.movingAverageCadence)
  }

  @Test
  fun movingCadenceExcludesZerosAndAlignedStoppedSamplesButPreservesRawSeries() {
    val session = samsungSession(logs = listOf(
      log(0, cadence = 0.0, speed = 0.0),
      log(1, cadence = 150.0, speed = 0.0),
      log(2, cadence = 158.0, speed = 2.4),
      log(3, cadence = 162.0, speed = 2.5),
    ))
    val result = enricher.enrich(base(), samsungInspection(SamsungMatchConfidence.HIGH, session = session))

    assertEquals(160.0, result.movingAverageCadence!!.value, 0.01)
    assertEquals(listOf(0.0, 150.0, 158.0, 162.0), result.series.cadence.map { it.value })
  }

  @Test
  fun overlappingHeartRateIsVerifiedWithoutReplacingHealthConnect() {
    val result = enricher.enrich(base(), samsungInspection(SamsungMatchConfidence.HIGH))
    assertEquals(MetricSource.HEALTH_CONNECT, result.averageHeartRate?.source)
    assertEquals(MetricVerification.VERIFIED, result.averageHeartRate?.verification)
    assertEquals(MetricSource.SAMSUNG_HEALTH, result.averageHeartRate?.secondarySource)
  }

  @Test
  fun overlappingDistanceIsVerified() {
    val result = enricher.enrich(base(), samsungInspection(SamsungMatchConfidence.HIGH))
    assertEquals(MetricVerification.VERIFIED, result.distanceMeters?.verification)
    assertEquals(4_802.8, result.distanceMeters!!.value, 0.01)
  }

  @Test
  fun elevenSecondDurationDifferenceIsCloseMatch() {
    val result = enricher.enrich(base(), samsungInspection(SamsungMatchConfidence.HIGH))
    assertEquals(MetricVerification.CLOSE_MATCH, result.durationSeconds.verification)
    assertEquals(1_974L, result.durationSeconds.value)
  }

  @Test
  fun materialProviderDifferenceIsConflict() {
    val session = samsungSession(meanHeartRate = 170.0)
    val result = enricher.enrich(base(), samsungInspection(SamsungMatchConfidence.HIGH, session = session))
    assertEquals(MetricVerification.CONFLICT, result.averageHeartRate?.verification)
    assertEquals(145.2, result.averageHeartRate!!.value, 0.01)
  }

  @Test
  fun enrichmentIsIdempotent() {
    val first = enricher.enrich(base(), samsungInspection(SamsungMatchConfidence.HIGH))
    val second = enricher.enrich(first, samsungInspection(SamsungMatchConfidence.HIGH))
    assertEquals(first, second)
  }

  @Test
  fun nullableMetricsRemainSafeWhenProvidersDoNotSupplyThem() {
    val sparseRun = run().copy(distanceMeters = null, averageHeartRate = null, maxHeartRate = null, averagePaceSecondsPerKm = null)
    val result = healthProvider.build(sparseRun, null)
    assertNull(result.distanceMeters)
    assertNull(result.averageHeartRate)
    assertNull(result.averageCadence)
    assertTrue(result.series.cadence.isEmpty())
  }

  private fun run() = RunSummary(
    healthConnectRecordId = "hc-run",
    startTime = start,
    endTime = start.plusSeconds(1_974),
    durationSeconds = 1_974,
    distanceMeters = 4_802.8,
    averageHeartRate = 145.2,
    maxHeartRate = 167,
    averagePaceSecondsPerKm = 411.0,
    sourcePackageName = "com.sec.android.app.shealth",
    sourceAppName = "Samsung Health",
    sourceDeviceManufacturer = "Samsung",
    sourceDeviceModel = "SM-L330",
  )

  private fun healthInspection(): RunDataInspection = RunDataInspection(
    runId = "hc-run",
    sourcePackageName = "com.sec.android.app.shealth",
    sourceAppName = "Samsung Health",
    inspectedAt = clock.instant(),
    missingPermissions = emptySet(),
    metrics = listOf(
      metric(RunMetricType.EXERCISE_SESSION, SessionMetricSummary(
        "Running", start, start.plusSeconds(1_974), 1_974, null, null, "hc-run", "Samsung", "SM-L330",
      )),
      metric(RunMetricType.DISTANCE, TotalMetricSummary(4_802.8, "m")),
      metric(
        RunMetricType.HEART_RATE,
        SeriesMetricSummary(2, 145.2, 120.0, 167.0, start, start.plusSeconds(1), "bpm"),
        listOf(TimedNumericSample(start, 120.0), TimedNumericSample(start.plusSeconds(1), 167.0)),
      ),
      metric(RunMetricType.SPEED, SeriesMetricSummary(2, 2.4, 2.3, 2.5, start, start.plusSeconds(1), "m/s")),
      metric(RunMetricType.VO2_MAX, Vo2MaxMetricSummary(39.7, start, "Other", Vo2MaxTiming.DURING_WORKOUT)),
    ),
  )

  private fun metric(
    type: RunMetricType,
    summary: com.example.openruncoach.domain.inspection.MetricSummary,
    samples: List<TimedNumericSample> = emptyList(),
  ) = MetricInspection(type, MetricAvailability.AVAILABLE, summary = summary, samples = samples)

  private fun base() = healthProvider.build(run(), healthInspection())

  private fun samsungInspection(
    confidence: SamsungMatchConfidence,
    manual: Boolean = false,
    status: SamsungWorkoutMatchStatus = SamsungWorkoutMatchStatus.MATCHED,
    session: SamsungExerciseSessionInspection = samsungSession(),
  ): SamsungHealthInspection {
    val match = SamsungWorkoutMatchResult(
      status = status,
      selected = if (status == SamsungWorkoutMatchStatus.MATCHED) session.candidate else null,
      plausibleCandidates = listOf(session.candidate),
      confidence = confidence,
      manualSelection = manual,
    )
    return SamsungHealthInspection(match, if (status == SamsungWorkoutMatchStatus.MATCHED) session else null, emptyList(), emptyList())
  }

  private fun samsungSession(
    meanCadence: Double? = 158.9,
    maxCadence: Double? = 172.0,
    meanHeartRate: Double? = 145.0,
    logs: List<SamsungExerciseLogSample> = listOf(
      log(0, cadence = 0.0, speed = 0.0),
      log(1, cadence = 158.0, speed = 2.4),
      log(2, cadence = 162.0, speed = 2.5),
    ),
  ): SamsungExerciseSessionInspection {
    val candidate = SamsungExerciseCandidate(
      id = "samsung-uid",
      startTime = start,
      endTime = start.plusSeconds(1_963),
      durationSeconds = 1_963,
      distanceMeters = 4_802.8,
      exerciseKind = SamsungExerciseKind.RUNNING,
      source = SamsungCandidateSource(
        uid = "samsung-uid",
        dataPointStartTime = start,
        dataPointEndTime = start.plusSeconds(1_963),
        updateTime = start.plusSeconds(2_000),
        applicationId = "com.sec.android.app.shealth",
        deviceId = "watch-8",
        deviceType = SamsungSourceDeviceType.WATCH,
        manufacturer = "Samsung",
        model = "SM-L330",
        deviceName = "Galaxy Watch8",
      ),
    )
    val noAlignment = SampleAlignment(SampleAlignmentStatus.NO_COMPARISON_DATA, 0, 0)
    return SamsungExerciseSessionInspection(
      candidate = candidate,
      caloriesKilocalories = 350.0,
      meanHeartRate = meanHeartRate,
      minHeartRate = 110.0,
      maxHeartRate = 167.0,
      meanSpeedMetersPerSecond = 2.4,
      maxSpeedMetersPerSecond = 3.2,
      meanCadence = meanCadence,
      maxCadence = maxCadence,
      meanPowerWatts = null,
      maxPowerWatts = null,
      vo2Max = 39.7,
      altitudeGainMeters = 16.5,
      altitudeLossMeters = 14.0,
      minAltitudeMeters = 35.0,
      maxAltitudeMeters = 48.0,
      inclineDistanceMeters = 800.0,
      declineDistanceMeters = 700.0,
      customTitle = null,
      comment = null,
      sourceAppId = candidate.source.applicationId,
      sourceDeviceId = candidate.source.deviceId,
      sourceDeviceManufacturer = candidate.source.manufacturer,
      sourceDeviceModel = candidate.source.model,
      sourceDeviceName = candidate.source.deviceName,
      cadence = CadenceInvestigation(
        meanCadence, maxCadence, logs.isNotEmpty(), logs.size, logs.count { it.cadence != null },
        null, null, null, null, null, null, noAlignment, noAlignment,
      ),
      heartRateLogSummary = null,
      speedLogSummary = null,
      powerLogSummary = null,
      countLogSummary = null,
      exerciseLogSamples = logs,
    )
  }

  private fun log(offset: Long, cadence: Double?, speed: Double?) = SamsungExerciseLogSample(
    timestamp = start.plusSeconds(offset),
    heartRate = 145.0,
    cadence = cadence,
    speedMetersPerSecond = speed,
    powerWatts = null,
    count = null,
  )
}
