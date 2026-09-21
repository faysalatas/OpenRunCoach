package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "planned_workouts")
data class PlannedWorkoutEntity(
  @PrimaryKey val id: String,
  val dateEpochDay: Long,
  val type: String,
  val durationMinutes: Int,
  val targetRpeMin: Int,
  val targetRpeMax: Int,
  val createdAtEpochMillis: Long,
  @ColumnInfo(defaultValue = "'PLANNED'") val lifecycleState: String = "PLANNED",
)
