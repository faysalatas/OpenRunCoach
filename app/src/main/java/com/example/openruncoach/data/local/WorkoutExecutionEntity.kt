package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
  tableName = "workout_executions",
  primaryKeys = ["plannedWorkoutId"],
  foreignKeys = [
    ForeignKey(
      entity = PlannedWorkoutEntity::class,
      parentColumns = ["id"],
      childColumns = ["plannedWorkoutId"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = RunEntity::class,
      parentColumns = ["healthConnectRecordId"],
      childColumns = ["actualRunId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("actualRunId", unique = true)],
)
data class WorkoutExecutionEntity(
  val plannedWorkoutId: String,
  val actualRunId: String,
  val status: String,
  val comparisonBasis: String,
  val intendedDistanceMeters: Double?,
  val intendedDurationSeconds: Long?,
  val actualDistanceMeters: Double?,
  val actualDurationSeconds: Long,
  val qualityWorkCompletionFraction: Double?,
  val explanation: String,
  val evaluatedAtEpochMillis: Long,
)
