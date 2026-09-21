package com.example.openruncoach.domain.inspection

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricCalculationsTest {
  private val first = Instant.parse("2026-09-20T08:00:00Z")

  @Test
  fun emptySeriesHaveNoSummary() {
    assertNull(summarizeHeartRateSamples(emptyList()))
    assertNull(summarizeCadenceSamples(emptyList()))
    assertNull(summarizeSpeedSamples(emptyList()))
    assertNull(summarizePowerSamples(emptyList()))
  }

  @Test
  fun oneSampleProducesIdenticalAverageMinimumAndMaximum() {
    val summary = summarizeHeartRateSamples(listOf(sample(150.0)))!!

    assertEquals(1, summary.sampleCount)
    assertEquals(150.0, summary.average, 0.0)
    assertEquals(150.0, summary.minimum, 0.0)
    assertEquals(150.0, summary.maximum, 0.0)
    assertEquals(first, summary.firstSampleTime)
    assertEquals(first, summary.lastSampleTime)
  }

  @Test
  fun heartRateSummarySortsTimesAndCalculatesValues() {
    val summary = summarizeHeartRateSamples(
      listOf(
        TimedNumericSample(first.plusSeconds(20), 180.0),
        TimedNumericSample(first, 120.0),
        TimedNumericSample(first.plusSeconds(10), 150.0),
      )
    )!!

    assertEquals(3, summary.sampleCount)
    assertEquals(150.0, summary.average, 0.0)
    assertEquals(120.0, summary.minimum, 0.0)
    assertEquals(180.0, summary.maximum, 0.0)
    assertEquals(first, summary.firstSampleTime)
    assertEquals(first.plusSeconds(20), summary.lastSampleTime)
  }

  @Test
  fun cadenceSpeedAndPowerSummariesUseTheirUnits() {
    val values = listOf(sample(0.0), sample(10.0, 1), sample(20.0, 2))

    assertEquals("spm", summarizeCadenceSamples(values)!!.unit)
    assertEquals(10.0, summarizeCadenceSamples(values)!!.average, 0.0)
    assertEquals("m/s", summarizeSpeedSamples(values)!!.unit)
    assertEquals(20.0, summarizeSpeedSamples(values)!!.maximum, 0.0)
    assertEquals("W", summarizePowerSamples(values)!!.unit)
    assertEquals(0.0, summarizePowerSamples(values)!!.minimum, 0.0)
  }

  @Test
  fun invalidValuesAreIgnored() {
    val invalid = listOf(
      sample(Double.NaN),
      sample(Double.POSITIVE_INFINITY, 1),
      sample(-1.0, 2),
    )

    assertNull(summarizeHeartRateSamples(invalid))
    assertNull(summarizeCadenceSamples(invalid))
    assertNull(summarizeSpeedSamples(invalid))
    assertNull(summarizePowerSamples(invalid))
  }

  private fun sample(value: Double, seconds: Long = 0) =
    TimedNumericSample(first.plusSeconds(seconds), value)
}
