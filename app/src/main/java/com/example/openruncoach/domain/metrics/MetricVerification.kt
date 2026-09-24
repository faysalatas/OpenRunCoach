package com.example.openruncoach.domain.metrics

import java.time.Duration
import kotlin.math.abs

data class MetricTolerance(
  val verifiedAbsolute: Double,
  val closeAbsolute: Double,
  val verifiedFraction: Double? = null,
  val closeFraction: Double? = null,
  val unit: String,
)

object RunMetricTolerances {
  val DISTANCE = MetricTolerance(5.0, 50.0, 0.002, 0.02, "m")
  val DURATION = MetricTolerance(2.0, 30.0, unit = "s")
  val HEART_RATE = MetricTolerance(1.0, 5.0, unit = "bpm")
  val SPEED = MetricTolerance(0.05, 0.20, unit = "m/s")
  val VO2_MAX = MetricTolerance(0.2, 1.0, unit = "ml/kg/min")
  val CADENCE = MetricTolerance(1.0, 4.0, unit = "spm")
  val ELEVATION = MetricTolerance(2.0, 10.0, unit = "m")
  val CALORIES = MetricTolerance(5.0, 30.0, unit = "kcal")
}

fun verifyMetric(
  primary: MetricValue<Double>,
  secondaryValue: Double,
  secondarySource: MetricSource,
  tolerance: MetricTolerance,
): MetricValue<Double> {
  val difference = abs(primary.value - secondaryValue)
  val reference = abs(primary.value).takeIf { it > 0.0 }
  fun within(absolute: Double, fraction: Double?): Boolean =
    difference <= absolute || (fraction != null && reference != null && difference / reference <= fraction)
  val verification = when {
    within(tolerance.verifiedAbsolute, tolerance.verifiedFraction) -> MetricVerification.VERIFIED
    within(tolerance.closeAbsolute, tolerance.closeFraction) -> MetricVerification.CLOSE_MATCH
    else -> MetricVerification.CONFLICT
  }
  val description = when (verification) {
    MetricVerification.VERIFIED -> "Providers agree within ${tolerance.verifiedAbsolute} ${tolerance.unit}"
    MetricVerification.CLOSE_MATCH -> "Provider values differ by ${formatDifference(difference)} ${tolerance.unit}"
    MetricVerification.CONFLICT -> "Provider values conflict by ${formatDifference(difference)} ${tolerance.unit}"
    MetricVerification.UNVERIFIED -> null
  }
  return primary.copy(
    verification = verification,
    secondaryValue = secondaryValue,
    secondarySource = secondarySource,
    verificationReason = description,
  )
}

fun verifyDuration(
  primary: MetricValue<Long>,
  secondarySeconds: Long,
  secondarySource: MetricSource,
): MetricValue<Long> {
  val checked = verifyMetric(
    MetricValue(primary.value.toDouble(), primary.source, primary.confidence),
    secondarySeconds.toDouble(),
    secondarySource,
    RunMetricTolerances.DURATION,
  )
  return primary.copy(
    verification = checked.verification,
    secondaryValue = secondarySeconds,
    secondarySource = secondarySource,
    verificationReason = checked.verificationReason,
  )
}

/** Keeps zero cadence samples in [cadence] but excludes them from the moving average. */
fun movingCadenceAverage(
  cadence: List<TimedMetricSample<Double>>,
  speed: List<TimedMetricSample<Double>> = emptyList(),
  alignmentTolerance: Duration = Duration.ofSeconds(2),
  stoppedSpeedMetersPerSecond: Double = 0.1,
): Double? {
  val moving = cadence.filter { sample ->
    if (!sample.value.isFinite() || sample.value <= 0.0) return@filter false
    val alignedSpeed = speed.minByOrNull { abs(Duration.between(sample.timestamp, it.timestamp).toMillis()) }
      ?.takeIf { abs(Duration.between(sample.timestamp, it.timestamp).toMillis()) <= alignmentTolerance.toMillis() }
    alignedSpeed == null || alignedSpeed.value > stoppedSpeedMetersPerSecond
  }
  return moving.map { it.value }.takeIf { it.isNotEmpty() }?.average()
}

private fun formatDifference(value: Double): String = "%.2f".format(java.util.Locale.ROOT, value)
