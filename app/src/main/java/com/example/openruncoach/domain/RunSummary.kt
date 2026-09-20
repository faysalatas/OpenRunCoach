package com.example.openruncoach.domain

import java.time.Instant

data class RunSummary(
  val healthConnectRecordId: String,
  val startTime: Instant,
  val endTime: Instant,
  val durationSeconds: Long,
  val distanceMeters: Double?,
  val averageHeartRate: Double?,
  val maxHeartRate: Long?,
  val averagePaceSecondsPerKm: Double?,
)

fun averagePaceSecondsPerKm(
  durationSeconds: Long,
  distanceMeters: Double?,
  averageSpeedMetersPerSecond: Double?,
): Double? {
  if (durationSeconds > 0 && distanceMeters != null && distanceMeters.isFinite() && distanceMeters > 0) {
    return durationSeconds * 1000.0 / distanceMeters
  }
  if (averageSpeedMetersPerSecond != null &&
    averageSpeedMetersPerSecond.isFinite() &&
    averageSpeedMetersPerSecond > 0
  ) {
    return 1000.0 / averageSpeedMetersPerSecond
  }
  return null
}
