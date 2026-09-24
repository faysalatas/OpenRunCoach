package com.example.openruncoach.domain.metrics

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceSeriesCalculatorTest {
  private val start = Instant.parse("2026-09-23T15:55:30Z")
  private val calculator = PaceSeriesCalculator()

  @Test fun speedConvertsToExpectedPace() {
    assertEquals(360.0, PaceSeriesCalculator.speedToPaceSecondsPerKm(2.7777777778)!!, 0.01)
    assertEquals(500.0, PaceSeriesCalculator.speedToPaceSecondsPerKm(2.0)!!, 0.01)
    assertEquals(1_000.0, PaceSeriesCalculator.speedToPaceSecondsPerKm(1.0)!!, 0.01)
  }

  @Test fun invalidSpeedsAreMissing() {
    assertNull(PaceSeriesCalculator.speedToPaceSecondsPerKm(0.0))
    assertNull(PaceSeriesCalculator.speedToPaceSecondsPerKm(-1.0))
    assertNull(PaceSeriesCalculator.speedToPaceSecondsPerKm(Double.NaN))
    assertNull(PaceSeriesCalculator.speedToPaceSecondsPerKm(Double.POSITIVE_INFINITY))
  }

  @Test fun tenSecondRollingSpeedIsDeterministic() {
    val input = (0L..20L).map { second -> sample(second, second.toDouble() + 1.0) }
    val first = calculator.smoothSpeed(input)
    val second = calculator.smoothSpeed(input)

    assertEquals(first, second)
    assertEquals(6.0, first[10].value, 0.01)
    assertEquals(16.0, first[20].value, 0.01)
  }

  @Test fun walkingSectionRemainsVisiblySlowerAfterSmoothing() {
    val input = (0L..80L).map { second ->
      sample(second, if (second in 25L..50L) 1.0 else 2.2)
    }
    val paces = calculator.calculate(input, 80, 12_000.0).samples.filter { it.value.isFinite() }

    val runningPace = paces.first { it.timestamp == start.plusSeconds(20) }.value
    val walkingPace = paces.first { it.timestamp == start.plusSeconds(45) }.value
    assertTrue(walkingPace - runningPace > 400.0)
  }

  @Test fun oneSpeedSpikeDoesNotDominateSmoothedChart() {
    val input = (0L..60L).map { second -> sample(second, if (second == 20L) 10.0 else 2.5) }
    val result = calculator.calculate(input, 60, 150.0)

    assertTrue(result.samples.filter { it.value.isFinite() }.minOf { it.value } > 300.0)
    assertEquals(400.0, result.fastestThirtySecondPaceSecondsPerKm!!, 0.01)
  }

  @Test fun isolatedStartupSpeedArtifactDoesNotCreateAPaceSpike() {
    val input = listOf(sample(0, 0.1), sample(1, 2.2), sample(2, 2.2), sample(3, 2.2))
    val chart = RunChartPreparer.prepare(metrics(input)).pace!!

    assertTrue(chart.points.first().value < 600.0)
    assertEquals(1, chart.points.groupBy { it.elapsedSeconds }.values.maxOf { it.size })
  }

  @Test fun fastestThirtySecondsUsesRollingAverageSpeed() {
    val input = (0L..90L).map { second ->
      sample(second, when (second) {
        in 30L..60L -> 3.0
        in 61L..90L -> 1.0
        else -> 2.0
      })
    }
    val result = calculator.calculate(input, 90, 300.0)

    assertEquals(1_000.0 / 3.0, result.fastestThirtySecondPaceSecondsPerKm!!, 0.01)
  }

  @Test fun fastestThirtySecondsIsOmittedWhenSeriesIsTooShort() {
    val input = (0L..20L).map { second -> sample(second, 2.5) }
    assertNull(calculator.calculate(input, 20, 1_000.0).fastestThirtySecondPaceSecondsPerKm)
  }

  @Test fun canonicalAverageUsesDistanceAndDuration() {
    assertEquals(455.0, PaceSeriesCalculator.canonicalAveragePace(1_092, 2_400.0)!!, 0.01)
    assertNull(PaceSeriesCalculator.canonicalAveragePace(1_092, null))
  }

  @Test fun paceAxisPlacesFasterValuesHigherAndNeverIncludesZero() {
    val scale = PaceChartScaleCalculator.calculate(listOf(330.0, 450.0, 600.0), 455.0)!!

    assertTrue(scale.verticalFraction(330.0) < scale.verticalFraction(600.0))
    assertTrue(scale.minimumValue > 0.0)
    assertFalse(scale.tickValues.any { it == 0.0 })
  }

  @Test fun oneExtremePointDoesNotSetThePaceScale() {
    val values = List(100) { 450.0 } + 180.0
    val scale = PaceChartScaleCalculator.calculate(values, 455.0)!!

    assertTrue(scale.minimumValue > 300.0)
  }

  @Test fun invalidSamplesSplitPaceSegmentsWithoutArtificialConnection() {
    val metrics = metrics(listOf(sample(0, 2.0), sample(1, 0.0), sample(2, 2.0)))
    val chart = RunChartPreparer.prepare(metrics).pace!!

    assertEquals(2, chart.segments.size)
    assertEquals(listOf(0.0), chart.segments.first().map { it.elapsedSeconds })
    assertEquals(listOf(2.0), chart.segments.last().map { it.elapsedSeconds })
  }

  @Test fun realisticRunWalkSeriesPreservesSlowSections() {
    val input = (0L..150L).map { second ->
      val speed = when (second) {
        in 31L..55L, in 91L..115L -> 1.0
        else -> 2.2
      }
      sample(second, speed)
    }
    val chart = RunChartPreparer.prepare(metrics(input)).pace!!

    assertTrue(chart.points.minOf { it.value } < 500.0)
    assertTrue(chart.points.maxOf { it.value } > 900.0)
    assertTrue(chart.scale!!.maximumValue - chart.scale.minimumValue > 400.0)
  }

  @Test fun sensorTimeGapSplitsThePaceLine() {
    val calculator = PaceSeriesCalculator(maximumSampleGap = Duration.ofSeconds(10))
    val metrics = metrics(listOf(sample(0, 2.0), sample(1, 2.0), sample(30, 2.0), sample(31, 2.0)))
    val chart = RunChartPreparer.prepare(metrics, paceCalculator = calculator).pace!!

    assertEquals(2, chart.segments.size)
  }

  private fun metrics(speed: List<TimedMetricSample<Double>>) = RunMetrics(
    runId = "run",
    startTime = start,
    endTime = start.plusSeconds(180),
    durationSeconds = MetricValue(180, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    distanceMeters = MetricValue(400.0, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    series = RunTimeSeries(speed = speed),
    generatedAt = start.plusSeconds(200),
  )

  private fun sample(seconds: Long, speed: Double) = TimedMetricSample(start.plusSeconds(seconds), speed)
}
