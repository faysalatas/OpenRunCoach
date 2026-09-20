package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runs")
data class RunEntity(
  @PrimaryKey val healthConnectRecordId: String,
  val startTimeEpochMillis: Long,
  val endTimeEpochMillis: Long,
  val durationSeconds: Long,
  val distanceMeters: Double?,
  val averageHeartRate: Double?,
  val maxHeartRate: Long?,
  val averagePaceSecondsPerKm: Double?,
  val importedAtEpochMillis: Long,
)
