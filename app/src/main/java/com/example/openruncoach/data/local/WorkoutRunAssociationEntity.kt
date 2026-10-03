package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A recoverable run-to-prescription link, independent of whether checkout is complete. */
@Entity(
  tableName = "workout_run_associations",
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
  indices = [Index("physicalRunId", unique = true), Index("actualRunId")],
)
data class WorkoutRunAssociationEntity(
  val plannedWorkoutId: String,
  val physicalRunId: String,
  val actualRunId: String,
  val source: String,
  val updatedAtEpochMillis: Long,
)
