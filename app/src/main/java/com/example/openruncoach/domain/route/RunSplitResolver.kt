package com.example.openruncoach.domain.route

import com.example.openruncoach.domain.metrics.RunMetrics
import kotlin.math.abs

/** Persisted splits are a cache. This resolver has no storage or provider side effects. */
object RunSplitResolver {
  fun resolve(data: RunRouteData, canonicalDistanceMeters: Double?, metrics: RunMetrics?, forceDerivation: Boolean = false): RunRouteData {
    val splits = data.splits
    val basisChanged = canonicalDistanceMeters?.takeIf { it.isFinite() && it > 0 }?.let { actual ->
      abs(splits.sumOf { split -> split.distanceMeters } - actual) > 1.0 &&
        (data.route.canonicalDistanceMeters?.let { abs(it - actual) > 1.0 } ?: true)
    } == true
    val validCache = splits.isNotEmpty() && !basisChanged && splits.map { it.ordinal } == (1..splits.size).toList() &&
      splits.all { split ->
        split.runId == data.route.runId && split.distanceMeters.isFinite() && split.distanceMeters > 0 &&
          split.durationSeconds.isFinite() && split.durationSeconds > 0 &&
          split.averagePaceSecondsPerKm.isFinite() && split.averagePaceSecondsPerKm > 0 &&
          split.startTime < split.endTime
      } && splits.zipWithNext().all { (a, b) -> a.endTime == b.startTime }
    if (validCache && !forceDerivation) return data.copy(splitDerivationSource = SplitDerivationSource.PERSISTED_CACHE)
    val result = RouteSplitDeriver().derive(data.route, canonicalDistanceMeters, metrics)
    // Keep the authoritative route untouched; validation/calibration metadata belongs to derivation.
    return data.copy(splits = result.splits, splitDerivationSource =
      if (result.splits.isEmpty()) SplitDerivationSource.UNAVAILABLE else SplitDerivationSource.ROUTE_DERIVED_ON_READ)
  }
}
