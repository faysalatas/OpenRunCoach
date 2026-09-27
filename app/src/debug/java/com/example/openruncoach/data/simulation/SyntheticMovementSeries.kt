package com.example.openruncoach.data.simulation

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.RunTimeSeries
import com.example.openruncoach.domain.metrics.TimedMetricSample
import java.time.Instant

/** Debug-only raw movement inputs. Production continuity remains derived by its normal evaluator. */
internal object SyntheticMovementSeries {
  private const val SAMPLE_INTERVAL_SECONDS = 10L
  private const val WALKING_SPEED_METERS_PER_SECOND = 1.1
  private const val WALK_START_FRACTION = 0.40
  private const val WALK_END_FRACTION = 0.60

  fun missing() = RunTimeSeries()

  fun steadyRunning(
    startTime: Instant,
    durationSeconds: Long,
    distanceMeters: Double,
  ): RunTimeSeries {
    require(durationSeconds > 0)
    require(distanceMeters > 0.0)
    val steadySpeed = distanceMeters / durationSeconds
    return RunTimeSeries(speed = samples(startTime, durationSeconds) { steadySpeed })
  }

  fun runWalkRun(
    startTime: Instant,
    durationSeconds: Long,
    distanceMeters: Double,
  ): RunTimeSeries {
    require(durationSeconds > 0)
    require(distanceMeters > 0.0)
    val walkingSeconds = durationSeconds * (WALK_END_FRACTION - WALK_START_FRACTION)
    val runningSeconds = durationSeconds - walkingSeconds
    val runningSpeed = (distanceMeters - WALKING_SPEED_METERS_PER_SECOND * walkingSeconds) / runningSeconds
    require(runningSpeed > WALKING_SPEED_METERS_PER_SECOND)
    return RunTimeSeries(speed = samples(startTime, durationSeconds) { fraction ->
      if (fraction in WALK_START_FRACTION..WALK_END_FRACTION) {
        WALKING_SPEED_METERS_PER_SECOND
      } else {
        runningSpeed
      }
    })
  }

  private fun samples(
    startTime: Instant,
    durationSeconds: Long,
    value: (fraction: Double) -> Double,
  ): List<TimedMetricSample<Double>> {
    val regularOffsets = (0L..durationSeconds).step(SAMPLE_INTERVAL_SECONDS).toMutableList()
    if (regularOffsets.last() != durationSeconds) regularOffsets += durationSeconds
    return regularOffsets.map { second ->
      TimedMetricSample(
        timestamp = startTime.plusSeconds(second),
        value = value(second.toDouble() / durationSeconds),
        source = MetricSource.DERIVED,
        confidence = MetricConfidence.HIGH,
        sourceAppName = "OpenRunCoach Simulation",
        sourceDeviceName = "Deterministic simulator",
      )
    }
  }
}
