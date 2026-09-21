package com.example.openruncoach.domain.inspection

import java.time.Instant

data class TimedNumericSample(val timestamp: Instant, val value: Double)

fun summarizeHeartRateSamples(samples: List<TimedNumericSample>): SeriesMetricSummary? =
  summarizeSeries(samples, "bpm") { it.isFinite() && it > 0.0 }

fun summarizeCadenceSamples(samples: List<TimedNumericSample>): SeriesMetricSummary? =
  summarizeSeries(samples, "spm") { it.isFinite() && it >= 0.0 }

fun summarizeSpeedSamples(samples: List<TimedNumericSample>): SeriesMetricSummary? =
  summarizeSeries(samples, "m/s") { it.isFinite() && it >= 0.0 }

fun summarizePowerSamples(samples: List<TimedNumericSample>): SeriesMetricSummary? =
  summarizeSeries(samples, "W") { it.isFinite() && it >= 0.0 }

private fun summarizeSeries(
  samples: List<TimedNumericSample>,
  unit: String,
  isValid: (Double) -> Boolean,
): SeriesMetricSummary? {
  val valid = samples.filter { isValid(it.value) }.sortedBy(TimedNumericSample::timestamp)
  if (valid.isEmpty()) return null
  return SeriesMetricSummary(
    sampleCount = valid.size,
    average = valid.map(TimedNumericSample::value).average(),
    minimum = valid.minOf(TimedNumericSample::value),
    maximum = valid.maxOf(TimedNumericSample::value),
    firstSampleTime = valid.first().timestamp,
    lastSampleTime = valid.last().timestamp,
    unit = unit,
  )
}
