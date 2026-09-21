package com.example.openruncoach.data.healthconnect.inspection

import java.time.Instant

/** Identifies one Health Connect workout for future raw-record inspection queries. */
data class HealthConnectInspectionTarget(
  val exerciseSessionRecordId: String,
  val startTime: Instant,
  val endTime: Instant,
)
