package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
  tableName = "pre_run_check_ins",
  primaryKeys = ["plannedWorkoutId"],
  foreignKeys = [
    ForeignKey(
      entity = PlannedWorkoutEntity::class,
      parentColumns = ["id"],
      childColumns = ["plannedWorkoutId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class PreRunCheckInEntity(
  val plannedWorkoutId: String,
  val dateEpochDay: Long,
  val readiness: Int,
  val energy: Int,
  val sleepQuality: Int,
  val soreness: String,
  val sorenessAreas: String,
  val pain: String,
  val stress: Int,
  val motivation: Int,
  val illness: String,
  val notes: String?,
  val createdAtEpochMillis: Long,
)
