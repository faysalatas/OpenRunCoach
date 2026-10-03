package com.example.openruncoach.domain.route

import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import java.time.Instant

data class RunRoute(
  val runId: String,
  val source: MetricSource,
  val sourceAppName: String?,
  val confidence: MetricConfidence,
  val points: List<RunRoutePoint>,
  val rejectedPointCount: Int = 0,
  val derivedDistanceMeters: Double? = null,
  val canonicalDistanceMeters: Double? = null,
  val distanceDifferencePercent: Double? = null,
  val distanceScaleFactor: Double = 1.0,
  val hasLargeSamplingGap: Boolean = false,
)

data class RunRoutePoint(
  val sequence: Int,
  val timestamp: Instant,
  val latitude: Double,
  val longitude: Double,
  val altitudeMeters: Double? = null,
  val horizontalAccuracyMeters: Double? = null,
  val verticalAccuracyMeters: Double? = null,
)

enum class RunSplitDerivation {
  SOURCE_LAP,
  ROUTE_DERIVED,
  DISTANCE_SERIES_DERIVED,
  SPEED_SERIES_DERIVED,
}

val RUN_SPLIT_SOURCE_PRIORITY: List<RunSplitDerivation> = listOf(
  RunSplitDerivation.SOURCE_LAP,
  RunSplitDerivation.ROUTE_DERIVED,
  RunSplitDerivation.DISTANCE_SERIES_DERIVED,
  RunSplitDerivation.SPEED_SERIES_DERIVED,
)

data class RunSplit(
  val runId: String,
  val ordinal: Int,
  val derivation: RunSplitDerivation,
  val startTime: Instant,
  val endTime: Instant,
  val distanceMeters: Double,
  val durationSeconds: Double,
  val averagePaceSecondsPerKm: Double,
  val averageHeartRate: Double? = null,
  val averageCadence: Double? = null,
  val partial: Boolean,
  val confidence: MetricConfidence,
)

data class RunRouteData(
  val route: RunRoute,
  val splits: List<RunSplit>,
)
