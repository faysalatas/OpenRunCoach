package com.example.openruncoach.data.simulation

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.route.RunRoute
import com.example.openruncoach.domain.route.RunRoutePoint
import com.example.openruncoach.domain.simulation.SimulatedRouteProfile
import com.example.openruncoach.domain.simulation.SimulatedRunInput
import kotlin.math.cos

internal object SyntheticRunRoute {
  private const val SAMPLE_INTERVAL_SECONDS = 5L
  private const val BASE_LATITUDE = 52.52
  private const val BASE_LONGITUDE = 13.405

  fun create(run: SimulatedRunInput): RunRoute? {
    if (run.routeProfile == SimulatedRouteProfile.NONE) return null
    val offsets = (0L..run.durationSeconds step SAMPLE_INTERVAL_SECONDS).toMutableList()
    if (offsets.last() != run.durationSeconds) offsets += run.durationSeconds
    val filteredOffsets = if (run.routeProfile == SimulatedRouteProfile.LARGE_GAP) {
      offsets.filterNot { it in (run.durationSeconds * 0.35).toLong()..(run.durationSeconds * 0.65).toLong() }
    } else offsets
    val points = filteredOffsets.mapIndexed { index, second ->
      val fraction = second.toDouble() / run.durationSeconds
      val distance = run.distanceMeters * distanceFraction(run.routeProfile, fraction)
      val longitude = BASE_LONGITUDE + metersToLongitudeDegrees(distance)
      RunRoutePoint(
        sequence = index,
        timestamp = run.startTime.plusSeconds(second),
        latitude = if (run.routeProfile == SimulatedRouteProfile.GPS_OUTLIER && index == filteredOffsets.size / 2) {
          BASE_LATITUDE + 1.0
        } else BASE_LATITUDE,
        longitude = longitude,
        altitudeMeters = 15.0 + fraction * 3.0,
        horizontalAccuracyMeters = 4.0,
      )
    }
    return RunRoute(
      runId = run.id,
      source = MetricSource.DERIVED,
      sourceAppName = "OpenRunCoach Simulation",
      confidence = MetricConfidence.HIGH,
      points = points,
    )
  }

  private fun distanceFraction(profile: SimulatedRouteProfile, timeFraction: Double): Double = when (profile) {
    SimulatedRouteProfile.PROGRESSIVE -> 0.65 * timeFraction + 0.35 * timeFraction * timeFraction
    SimulatedRouteProfile.WALKING_INTERRUPTION -> when {
      timeFraction <= 0.4 -> timeFraction * 1.15
      timeFraction <= 0.6 -> 0.46 + (timeFraction - 0.4) * 0.4
      else -> 0.54 + (timeFraction - 0.6) * 1.15
    }
    else -> timeFraction
  }.coerceIn(0.0, 1.0)

  private fun metersToLongitudeDegrees(meters: Double): Double =
    meters / (111_320.0 * cos(Math.toRadians(BASE_LATITUDE)))
}
