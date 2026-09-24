package com.example.openruncoach.data

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.RunTimeSeries
import com.example.openruncoach.domain.metrics.TimedMetricSample
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class RunMetricSampleMappingTest {
  private val start = Instant.parse("2026-09-23T15:55:30Z")

  @Test fun rawSamplesRoundTripWithoutProviderObjects() {
    val metrics = metrics(listOf(sample(0, 140.0), sample(1, 141.0)))
    val entities = metrics.toSampleEntities()
    val roundTrip = entities.toTimeSeries()

    assertEquals(metrics.series.heartRate, roundTrip.heartRate)
    assertEquals("SAMSUNG_HEALTH", entities.first().source)
  }

  @Test fun duplicateSamplesHaveDeterministicUniqueness() {
    val duplicate = sample(1, 141.0)
    val entities = metrics(listOf(duplicate, duplicate)).toSampleEntities()
    assertEquals(1, entities.size)
  }

  @Test fun samplesOutsideCanonicalWindowAreExcluded() {
    val entities = metrics(listOf(
      TimedMetricSample(start.minusSeconds(1), 130.0),
      sample(0, 140.0),
      sample(600, 150.0),
      sample(601, 160.0),
    )).toSampleEntities()
    assertEquals(listOf(start, start.plusSeconds(600)), entities.map { Instant.ofEpochMilli(it.timestampEpochMillis) })
  }

  private fun metrics(samples: List<TimedMetricSample<Double>>) = RunMetrics(
    runId = "run",
    startTime = start,
    endTime = start.plusSeconds(600),
    durationSeconds = MetricValue(600, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    averageHeartRate = MetricValue(
      140.0,
      MetricSource.SAMSUNG_HEALTH,
      MetricConfidence.HIGH,
      sourceAppName = "Samsung Health",
      sourceDeviceName = "Galaxy Watch8",
    ),
    series = RunTimeSeries(heartRate = samples),
    generatedAt = start.plusSeconds(700),
  )

  private fun sample(seconds: Long, value: Double) = TimedMetricSample(
    timestamp = start.plusSeconds(seconds),
    value = value,
    source = MetricSource.SAMSUNG_HEALTH,
    confidence = MetricConfidence.HIGH,
    sourceAppName = "Samsung Health",
    sourceDeviceName = "Galaxy Watch8",
  )
}
