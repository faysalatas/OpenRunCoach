package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
  tableName = "run_routes",
  primaryKeys = ["runId"],
  foreignKeys = [ForeignKey(
    entity = RunEntity::class,
    parentColumns = ["healthConnectRecordId"],
    childColumns = ["runId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("runId")],
)
data class RunRouteEntity(
  val runId: String,
  val source: String,
  val sourceAppName: String?,
  val confidence: String,
  val rejectedPointCount: Int,
  val derivedDistanceMeters: Double?,
  val canonicalDistanceMeters: Double?,
  val distanceDifferencePercent: Double?,
  val distanceScaleFactor: Double,
  val hasLargeSamplingGap: Boolean,
)

@Entity(
  tableName = "run_route_points",
  primaryKeys = ["runId", "sequence"],
  foreignKeys = [ForeignKey(
    entity = RunRouteEntity::class,
    parentColumns = ["runId"],
    childColumns = ["runId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("runId")],
)
data class RunRoutePointEntity(
  val runId: String,
  val sequence: Int,
  val timestampEpochMillis: Long,
  val latitude: Double,
  val longitude: Double,
  val altitudeMeters: Double?,
  val horizontalAccuracyMeters: Double?,
  val verticalAccuracyMeters: Double?,
)

@Entity(
  tableName = "run_splits",
  primaryKeys = ["runId", "ordinal"],
  foreignKeys = [ForeignKey(
    entity = RunRouteEntity::class,
    parentColumns = ["runId"],
    childColumns = ["runId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("runId")],
)
data class RunSplitEntity(
  val runId: String,
  val ordinal: Int,
  val derivation: String,
  val startTimeEpochMillis: Long,
  val endTimeEpochMillis: Long,
  val distanceMeters: Double,
  val durationSeconds: Double,
  val averagePaceSecondsPerKm: Double,
  val averageHeartRate: Double?,
  val averageCadence: Double?,
  val partial: Boolean,
  val confidence: String,
)
