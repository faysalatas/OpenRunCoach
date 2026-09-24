package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
  tableName = "run_metrics",
  primaryKeys = ["runId"],
  foreignKeys = [ForeignKey(
    entity = RunEntity::class,
    parentColumns = ["healthConnectRecordId"],
    childColumns = ["runId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("runId")],
)
data class RunMetricsEntity(
  val runId: String,
  val startTimeEpochMillis: Long,
  val endTimeEpochMillis: Long,
  val generatedAtEpochMillis: Long,
  val recordingSource: String?,
  val recordingDeviceManufacturer: String?,
  val recordingDeviceModel: String?,
  val recordingDeviceName: String?,
)

@Entity(
  tableName = "run_metric_values",
  primaryKeys = ["runId", "metric"],
  foreignKeys = [ForeignKey(
    entity = RunMetricsEntity::class,
    parentColumns = ["runId"],
    childColumns = ["runId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("runId")],
)
data class RunMetricValueEntity(
  val runId: String,
  val metric: String,
  val value: Double,
  val source: String,
  val confidence: String,
  val verification: String,
  val secondaryValue: Double?,
  val secondarySource: String?,
  val verificationReason: String?,
)
