package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
  tableName = "workout_adjustments",
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
data class WorkoutAdjustmentEntity(
  val plannedWorkoutId: String,
  val adjustedType: String,
  val adjustedDurationMinutes: Int,
  val adjustedTargetRpeMin: Int,
  val adjustedTargetRpeMax: Int,
  val readinessStatus: String,
  val reasons: String,
  val adjustments: String,
  val generatedAtEpochMillis: Long,
)
