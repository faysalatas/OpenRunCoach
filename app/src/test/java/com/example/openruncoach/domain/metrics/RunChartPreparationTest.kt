package com.example.openruncoach.domain.metrics

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunChartPreparationTest {
  private val start = Instant.parse("2026-09-23T15:55:30Z")

  @Test fun speedConvertsToPaceWithoutChangingRawSamples() {
    val metrics = metrics(speed = listOf(sample(0, 2.0), sample(10, 2.0)))
    val chart = RunChartPreparer.prepare(metrics)

    assertEquals(500.0, chart.pace!!.points.first().value, 0.01)
    assertEquals(listOf(2.0, 2.0), metrics.series.speed.map { it.value })
  }

  @Test fun zeroAndNegativeSpeedCreateGapsInsteadOfInvalidPace() {
    val chart = RunChartPreparer.prepare(metrics(speed = listOf(
      sample(0, 2.0), sample(1, 0.0), sample(2, -1.0), sample(3, 2.5),
    ))).pace!!

    assertEquals(2, chart.segments.size)
    assertTrue(chart.points.all { it.value.isFinite() && it.value < 10_000.0 })
  }

  @Test fun speedSmoothingIsDeterministic() {
    val input = listOf(sample(0, 2.0), sample(2, 4.0), sample(7, 6.0))
    val calculator = PaceSeriesCalculator()
    val first = calculator.smoothSpeed(input)
    val second = calculator.smoothSpeed(input)

    assertEquals(first, second)
    assertEquals(3.0, first[1].value, 0.01)
    assertEquals(4.0, first[2].value, 0.01)
  }

  @Test fun cadenceZerosRemainRawButAreExcludedFromDisplayLine() {
    val metrics = metrics(cadence = listOf(sample(0, 0.0), sample(1, 160.0), sample(2, 162.0)))
    val chart = RunChartPreparer.prepare(metrics)

    assertEquals(0.0, metrics.series.cadence.first().value, 0.0)
    assertEquals(listOf(160.0, 162.0), chart.cadence!!.points.map { it.value })
  }

  @Test fun unavailableHeartRateAndCadenceProduceNoCharts() {
    val chart = RunChartPreparer.prepare(metrics())
    assertNull(chart.heartRate)
    assertNull(chart.cadence)
  }

  @Test fun aSingleSampleDoesNotCreateAnEmptyLookingChartTab() {
    val chart = RunChartPreparer.prepare(metrics(cadence = listOf(sample(0, 160.0))))

    assertNull(chart.cadence)
  }

  @Test fun smallSeriesIsUnchangedByDownsampling() {
    val input = (0 until 20).map { RunChartPoint(it.toDouble(), it.toDouble()) }
    assertEquals(input, RunChartPreparer.downsample(input, 100))
  }

  @Test fun largeSeriesIsReducedAndPreservesEndpoints() {
    val input = (0 until 3_000).map { RunChartPoint(it.toDouble(), (it % 97).toDouble()) }
    val output = RunChartPreparer.downsample(input, 240)

    assertEquals(240, output.size)
    assertEquals(input.first(), output.first())
    assertEquals(input.last(), output.last())
    assertEquals(output, RunChartPreparer.downsample(input, 240))
  }

  @Test fun fragmentedSeriesStillHonorsTheDisplayPointLimit() {
    val speed = (0 until 1_000).map { index -> sample(index.toLong(), if (index % 2 == 0) 2.5 else 0.0) }
    val chart = RunChartPreparer.prepare(metrics(speed = speed), targetPointCount = 240).pace!!

    assertTrue(chart.points.size <= 240)
    assertTrue(chart.segments.size > 1)
  }

  private fun metrics(
    speed: List<TimedMetricSample<Double>> = emptyList(),
    cadence: List<TimedMetricSample<Double>> = emptyList(),
  ) = RunMetrics(
    runId = "run",
    startTime = start,
    endTime = start.plusSeconds(600),
    durationSeconds = MetricValue(600, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    series = RunTimeSeries(speed = speed, cadence = cadence),
    generatedAt = start.plusSeconds(700),
  )

  private fun sample(seconds: Long, value: Double) = TimedMetricSample(start.plusSeconds(seconds), value)
}
