package com.example.openruncoach.domain.samsunghealth

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CadenceInvestigationTest {
  private val start = Instant.parse("2026-09-20T08:00:00Z")

  @Test
  fun emptyExerciseLogHasNoCadenceSummary() {
    val result = investigateCadence(null, null, emptyList())

    assertTrue(result.logExists)
    assertEquals(0, result.totalLogSamples)
    assertEquals(0, result.cadenceSampleCount)
    assertNull(result.averageCadence)
    assertNull(result.averageSampleIntervalSeconds)
    assertFalse(result.detailedCadenceAvailable)
  }

  @Test
  fun missingExerciseLogIsReportedSeparately() {
    val result = investigateCadence(164.0, 176.0, null)

    assertFalse(result.logExists)
    assertEquals(164.0, result.sessionMeanCadence!!, 0.0)
    assertEquals(176.0, result.sessionMaxCadence!!, 0.0)
    assertFalse(result.detailedCadenceAvailable)
  }

  @Test
  fun cadenceAggregationAndSampleIntervalAreCalculated() {
    val result = investigateCadence(null, null, listOf(
      sample(0, cadence = 160.0),
      sample(1, cadence = 164.0),
      sample(3, cadence = 170.0),
    ))

    assertEquals(3, result.cadenceSampleCount)
    assertEquals(164.666, result.averageCadence!!, 0.001)
    assertEquals(160.0, result.minimumCadence!!, 0.0)
    assertEquals(170.0, result.maximumCadence!!, 0.0)
    assertEquals(1.5, result.averageSampleIntervalSeconds!!, 0.0)
    assertEquals(start, result.firstCadenceTimestamp)
    assertEquals(start.plusSeconds(3), result.lastCadenceTimestamp)
  }

  @Test
  fun partiallyPopulatedLogIgnoresMissingAndInvalidCadence() {
    val result = investigateCadence(null, null, listOf(
      sample(0, heartRate = 140.0),
      sample(1, cadence = 162.0, heartRate = 141.0),
      sample(2, cadence = Double.NaN, speed = 3.0),
      sample(3, cadence = 166.0, speed = 3.1),
    ))

    assertEquals(4, result.totalLogSamples)
    assertEquals(2, result.cadenceSampleCount)
    assertEquals(164.0, result.averageCadence!!, 0.0)
  }

  @Test
  fun cadenceAlignmentUsesNearbyHeartRateAndSpeedTimestamps() {
    val result = investigateCadence(null, null, listOf(
      sample(0, cadence = 160.0, heartRate = 140.0, speed = 3.0),
      sample(1, cadence = 162.0, heartRate = 141.0, speed = 3.1),
      sample(2, cadence = 164.0, heartRate = 142.0, speed = 3.2),
      sample(3, cadence = 166.0, heartRate = 143.0),
      sample(4, cadence = 168.0, heartRate = 144.0),
    ), alignmentToleranceSeconds = 0)

    assertEquals(SampleAlignmentStatus.ALIGNED, result.heartRateAlignment.status)
    assertEquals(SampleAlignmentStatus.PARTIALLY_ALIGNED, result.speedAlignment.status)
    assertEquals(3, result.speedAlignment.alignedSamples)
  }

  @Test
  fun genericExerciseLogSummaryHandlesPartialMetrics() {
    val log = listOf(
      sample(0, heartRate = 140.0),
      sample(1),
      sample(2, heartRate = 144.0),
    )

    val result = summarizeExerciseLogMetric(log) { it.heartRate }

    assertEquals(2, result?.sampleCount)
    assertEquals(142.0, result!!.average, 0.0)
    assertEquals(140.0, result.minimum, 0.0)
    assertEquals(144.0, result.maximum, 0.0)
    assertEquals(2.0, result.averageSampleIntervalSeconds!!, 0.0)
  }

  private fun sample(
    offsetSeconds: Long,
    heartRate: Double? = null,
    cadence: Double? = null,
    speed: Double? = null,
  ) = SamsungExerciseLogSample(
    timestamp = start.plusSeconds(offsetSeconds),
    heartRate = heartRate,
    cadence = cadence,
    speedMetersPerSecond = speed,
    powerWatts = null,
    count = null,
  )
}
