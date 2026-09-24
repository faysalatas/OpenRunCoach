package com.example.openruncoach.domain.inspection

import java.time.Instant

enum class RunMetricType {
  EXERCISE_SESSION,
  DISTANCE,
  HEART_RATE,
  SPEED,
  CADENCE,
  VO2_MAX,
  ELEVATION_GAINED,
  POWER,
  CALORIES,
  STEPS,
  ROUTE,
}

enum class MetricAvailability {
  AVAILABLE,
  NOT_AVAILABLE,
  PERMISSION_REQUIRED,
  UNSUPPORTED,
  ERROR,
}

enum class RouteInspectionStatus {
  AVAILABLE,
  UNAVAILABLE,
  PERMISSION_REQUIRED,
  CONSENT_REQUIRED,
  UNSUPPORTED,
  ERROR,
}

sealed interface MetricSummary

data class SessionMetricSummary(
  val exerciseType: String,
  val startTime: Instant,
  val endTime: Instant,
  val durationSeconds: Long,
  val title: String?,
  val notes: String?,
  val healthConnectRecordId: String,
  val deviceManufacturer: String?,
  val deviceModel: String?,
) : MetricSummary

data class TotalMetricSummary(
  val value: Double,
  val unit: String,
) : MetricSummary

data class CountMetricSummary(val count: Long) : MetricSummary

data class SeriesMetricSummary(
  val sampleCount: Int,
  val average: Double,
  val minimum: Double,
  val maximum: Double,
  val firstSampleTime: Instant,
  val lastSampleTime: Instant,
  val unit: String,
) : MetricSummary

data class Vo2MaxMetricSummary(
  val valueMillilitersPerMinuteKilogram: Double,
  val timestamp: Instant,
  val measurementMethod: String,
  val timing: Vo2MaxTiming,
) : MetricSummary

enum class Vo2MaxTiming { DURING_WORKOUT, NEAR_WORKOUT }

data class RouteMetricSummary(
  val status: RouteInspectionStatus,
  val pointCount: Int? = null,
) : MetricSummary

data class AlternateSourceInspection(
  val sourcePackageName: String,
  val sourceAppName: String,
  val recordCount: Int,
  val note: String = "Not used for canonical inspection",
)

data class MetricInspection(
  val metric: RunMetricType,
  val availability: MetricAvailability,
  val recordCount: Int? = null,
  val sourcePackageName: String? = null,
  val sourceAppName: String? = null,
  val summary: MetricSummary? = null,
  val notes: List<String> = emptyList(),
  val recordIds: List<String> = emptyList(),
  val alternateSources: List<AlternateSourceInspection> = emptyList(),
  val samples: List<TimedNumericSample> = emptyList(),
)

data class RunDataInspection(
  val runId: String,
  val sourcePackageName: String?,
  val sourceAppName: String?,
  val inspectedAt: Instant,
  val metrics: List<MetricInspection>,
  val missingPermissions: Set<String>,
)
