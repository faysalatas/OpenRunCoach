package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
  tableName = "workout_blocks",
  foreignKeys = [ForeignKey(
    entity = PlannedWorkoutEntity::class,
    parentColumns = ["id"],
    childColumns = ["workoutId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("workoutId")],
)
data class WorkoutBlockEntity(
  @androidx.room.PrimaryKey val id: String,
  val workoutId: String,
  val blockOrder: Int,
  val blockType: String,
  val repetitions: Int,
  val label: String?,
)

@Entity(
  tableName = "workout_segments",
  foreignKeys = [ForeignKey(
    entity = WorkoutBlockEntity::class,
    parentColumns = ["id"],
    childColumns = ["blockId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("blockId")],
)
data class WorkoutSegmentEntity(
  @androidx.room.PrimaryKey val id: String,
  val blockId: String,
  val segmentOrder: Int,
  val category: String,
  val name: String,
  val completionType: String,
  val durationSeconds: Long?,
  val distanceMeters: Double?,
  val completionRepetitions: Int?,
  val notes: String?,
)

@Entity(
  tableName = "workout_intensity_targets",
  primaryKeys = ["segmentId", "position"],
  foreignKeys = [ForeignKey(
    entity = WorkoutSegmentEntity::class,
    parentColumns = ["id"],
    childColumns = ["segmentId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("segmentId")],
)
data class WorkoutIntensityTargetEntity(
  val segmentId: String,
  val position: Int,
  val targetType: String,
  val minimumValue: Double?,
  val maximumValue: Double?,
  val heartRateZone: String?,
  val confidence: String,
)

@Entity(
  tableName = "workout_cues",
  primaryKeys = ["workoutId", "position"],
  foreignKeys = [ForeignKey(
    entity = PlannedWorkoutEntity::class,
    parentColumns = ["id"],
    childColumns = ["workoutId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("workoutId")],
)
data class WorkoutCueEntity(val workoutId: String, val position: Int, val text: String)
