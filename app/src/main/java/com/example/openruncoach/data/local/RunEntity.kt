package com.example.openruncoach.data.local

import androidx.room.ColumnInfo
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
  @ColumnInfo(defaultValue = "''") val sourcePackageName: String = "",
  val sourceAppName: String? = null,
  val sourceDeviceManufacturer: String? = null,
  val sourceDeviceModel: String? = null,
  val sourceDeviceType: String? = null,
  val sourceRecordLastModifiedTimeEpochMillis: Long? = null,
  val duplicateGroupId: String? = null,
  @ColumnInfo(defaultValue = "1") val isCanonical: Boolean = true,
  val canonicalSelectionReason: String? = null,
)
