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
  @ColumnInfo(defaultValue = "''") val name: String = "",
  @ColumnInfo(defaultValue = "'AEROBIC_BASE'") val purpose: String = "AEROBIC_BASE",
  @ColumnInfo(defaultValue = "''") val explanation: String = "",
  val goalId: String? = null,
  @ColumnInfo(defaultValue = "'MANUAL'") val origin: String = "MANUAL",
  @ColumnInfo(defaultValue = "0") val isPreview: Boolean = false,
  @ColumnInfo(defaultValue = "1") val canChangeType: Boolean = true,
  @ColumnInfo(defaultValue = "1") val canChangeTotalDuration: Boolean = true,
  @ColumnInfo(defaultValue = "1") val canChangeSegmentCompletion: Boolean = true,
  @ColumnInfo(defaultValue = "1") val canChangeRepetitions: Boolean = true,
  @ColumnInfo(defaultValue = "1") val canChangeIntensity: Boolean = true,
  @ColumnInfo(defaultValue = "1") val canChangeRecovery: Boolean = true,
)
