package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "training_goals", primaryKeys = ["id"])
data class TrainingGoalEntity(
  val id: String,
  val distance: String,
  val customDistanceMeters: Double?,
  val goalType: String,
  val targetTimeSeconds: Long?,
  val createdAtEpochMillis: Long,
  val status: String,
  val confidenceAtCreation: String,
  val startingBestDurationSeconds: Long?,
  val startingBestRunId: String?,
  val startingBestObservedAtEpochMillis: Long?,
  val startingRecentDurationSeconds: Long?,
  val startingRecentRunId: String?,
  val startingRecentObservedAtEpochMillis: Long?,
  val startingAverageWeeklyDistanceMeters: Double,
  val startingAverageWeeklyDurationSeconds: Long,
  val startingLongestRunDistanceMeters: Double?,
  val startingLongestRunDurationSeconds: Long?,
  val startingAverageRunsPerWeek: Double,
  val startingTypicalEasyPaceSecondsPerKm: Double?,
  val startingTypicalEasyHeartRate: Double?,
  val startingProfileConfidence: String,
  val snapshotCapturedAtEpochMillis: Long,
  val assessmentLevel: String,
)

@Entity(
  tableName = "training_goal_assessment_reasons",
  primaryKeys = ["goalId", "position"],
  foreignKeys = [ForeignKey(
    entity = TrainingGoalEntity::class,
    parentColumns = ["id"],
    childColumns = ["goalId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("goalId")],
)
data class TrainingGoalAssessmentReasonEntity(
  val goalId: String,
  val position: Int,
  val text: String,
)
