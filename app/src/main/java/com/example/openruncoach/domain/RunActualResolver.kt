package com.example.openruncoach.domain

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.MetricVerification
import com.example.openruncoach.domain.metrics.RunMetrics
import java.time.Duration

enum class ActualValueBasis { CANONICAL_SUMMARY, CANONICAL_INTERVAL, MATCHED_METRIC_FALLBACK, UNAVAILABLE }

data class ActualValueOrigin(val basis: ActualValueBasis, val source: String?, val reason: String)
data class RunActualProvenance(
  val distance: ActualValueOrigin,
  val duration: ActualValueOrigin,
  val pace: String = "Resolved duration / resolved distance; no sensor-speed or stored-pace substitution.",
)

/** One whole-run semantic. Provider records and enrichment measurements remain untouched. */
object RunActualResolver {
  fun resolve(run: RunSummary, metrics: RunMetrics? = null): RunSummary {
    // Already-resolved summaries retain the explicit fallback origin through downstream consumers.
    if (run.actualProvenance != null) return run.copy(
      averagePaceSecondsPerKm = averagePaceSecondsPerKm(run.durationSeconds, run.distanceMeters, null)
        ?.takeIf { it.isFinite() },
    )
    val matched = metrics?.takeIf {
      it.runId == run.healthConnectRecordId && it.startTime == run.startTime && it.endTime == run.endTime &&
        it.endTime > it.startTime
    }
    fun origin(basis: ActualValueBasis, source: String?, reason: String) = ActualValueOrigin(basis, source, reason)
    val canonicalSource = run.sourcePackageName.ifBlank { run.sourceAppName ?: "Unknown summary source" }
    val distanceMetric = matched?.distanceMeters?.takeIf { trusted(it) && positive(it.value) }
    val distance = run.distanceMeters?.takeIf(::positive) ?: distanceMetric?.value
    val distanceOrigin = when {
      positive(run.distanceMeters) -> origin(ActualValueBasis.CANONICAL_SUMMARY, canonicalSource, "Valid canonical summary distance.")
      distanceMetric != null -> origin(ActualValueBasis.MATCHED_METRIC_FALLBACK,
        distanceMetric.sourceAppName ?: distanceMetric.source.name,
        "Canonical distance missing/invalid; HIGH-confidence, non-conflicting metric with identical record and interval.")
      else -> origin(ActualValueBasis.UNAVAILABLE, null, "No valid canonical distance or trusted interval-matched fallback.")
    }
    val intervalDuration = Duration.between(run.startTime, run.endTime).seconds.takeIf { it > 0 }
    val durationMetric = matched?.durationSeconds?.takeIf { trusted(it) && it.value > 0 }
    val duration = run.durationSeconds.takeIf { it > 0 } ?: intervalDuration ?: durationMetric?.value ?: 0L
    val durationOrigin = when {
      run.durationSeconds > 0 -> origin(ActualValueBasis.CANONICAL_SUMMARY, canonicalSource, "Valid canonical summary duration.")
      intervalDuration != null -> origin(ActualValueBasis.CANONICAL_INTERVAL, canonicalSource, "Duration recovered from canonical start/end.")
      durationMetric != null -> origin(ActualValueBasis.MATCHED_METRIC_FALLBACK,
        durationMetric.sourceAppName ?: durationMetric.source.name, "Missing canonical duration; trusted interval-matched metric.")
      else -> origin(ActualValueBasis.UNAVAILABLE, null, "No valid canonical duration, interval, or trusted fallback; zero denotes unavailable.")
    }
    return run.copy(
      distanceMeters = distance,
      durationSeconds = duration,
      averagePaceSecondsPerKm = averagePaceSecondsPerKm(duration, distance, null)?.takeIf { it.isFinite() },
      actualProvenance = RunActualProvenance(distanceOrigin, durationOrigin),
    )
  }

  private fun positive(value: Double?) = value != null && value.isFinite() && value > 0.0
  private fun trusted(value: MetricValue<*>) = value.confidence == MetricConfidence.HIGH &&
    value.verification != MetricVerification.CONFLICT
}

/** Distance-weighted pace; partial/missing actuals must not masquerade as a complete week. */
fun aggregateActualPaceSecondsPerKm(runs: List<RunSummary>): Double? {
  if (runs.isEmpty() || runs.any { it.durationSeconds <= 0 ||
      it.distanceMeters?.let { distance -> distance.isFinite() && distance > 0 } != true }) return null
  val pace = runs.sumOf { it.durationSeconds.toDouble() } * 1000.0 / runs.sumOf { requireNotNull(it.distanceMeters) }
  return pace.takeIf { it.isFinite() && it > 0.0 }
}
