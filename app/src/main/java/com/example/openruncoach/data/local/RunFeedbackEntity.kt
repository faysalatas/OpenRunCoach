package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "run_feedback",
  foreignKeys = [
    ForeignKey(
      entity = RunEntity::class,
      parentColumns = ["healthConnectRecordId"],
      childColumns = ["runId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["runId"], unique = true)],
)
data class RunFeedbackEntity(
  @PrimaryKey val id: String,
  val runId: String,
  val rpe: Int,
  val cardioEffort: String,
  val muscleFatigue: String,
  val painLevel: String,
  val completionStatus: String,
  val readiness: Int?,
  val notes: String?,
  val createdAtEpochMillis: Long,
  val updatedAtEpochMillis: Long,
)
