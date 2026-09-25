package com.example.openruncoach.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
  tableName = "training_plans",
  primaryKeys = ["id"],
  foreignKeys = [ForeignKey(
    entity = TrainingGoalEntity::class,
    parentColumns = ["id"],
    childColumns = ["goalId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("goalId"), Index("status")],
)
data class TrainingPlanEntity(
  val id: String,
  val goalId: String,
  val createdAtEpochMillis: Long,
  val startDateEpochDay: Long,
  val endDateEpochDay: Long?,
  val generationVersion: Int,
  val explanation: String,
  val confidence: String,
  val status: String,
  val plannedCompletionDateEpochDay: Long?,
  val plannedCompletionRunId: String?,
  @ColumnInfo(defaultValue = "'GOAL_COMPLETION_NOT_READY'") val completionReadiness: String,
  @ColumnInfo(defaultValue = "0") val completionPromptDismissed: Boolean,
  @ColumnInfo(defaultValue = "0") val completionUpdatedAtEpochMillis: Long,
)

@Entity(
  tableName = "training_weeks",
  primaryKeys = ["planId", "weekIndex"],
  foreignKeys = [ForeignKey(
    entity = TrainingPlanEntity::class,
    parentColumns = ["id"],
    childColumns = ["planId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("planId")],
)
data class TrainingWeekEntity(
  val planId: String,
  val weekIndex: Int,
  val startDateEpochDay: Long,
  val targetDistanceMeters: Double?,
  val targetDurationSeconds: Long?,
  val loadClassification: String,
  val explanation: String,
)

@Entity(
  tableName = "training_week_reasons",
  primaryKeys = ["planId", "weekIndex", "position"],
  foreignKeys = [ForeignKey(
    entity = TrainingWeekEntity::class,
    parentColumns = ["planId", "weekIndex"],
    childColumns = ["planId", "weekIndex"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index(value = ["planId", "weekIndex"])],
)
data class TrainingWeekReasonEntity(
  val planId: String,
  val weekIndex: Int,
  val position: Int,
  val text: String,
)

@Entity(
  tableName = "training_blocks",
  primaryKeys = ["planId", "blockIndex"],
  foreignKeys = [ForeignKey(
    entity = TrainingPlanEntity::class,
    parentColumns = ["id"],
    childColumns = ["planId"],
    onDelete = ForeignKey.CASCADE,
  )],
  indices = [Index("planId")],
)
data class TrainingBlockEntity(
  val planId: String,
  val blockIndex: Int,
  val type: String,
  val startWeek: Int,
  val endWeek: Int,
  val purpose: String,
  val progressionIntent: String,
)

@Entity(
  tableName = "training_plan_workouts",
  primaryKeys = ["planId", "weekIndex", "workoutOrder"],
  foreignKeys = [
    ForeignKey(
      entity = TrainingWeekEntity::class,
      parentColumns = ["planId", "weekIndex"],
      childColumns = ["planId", "weekIndex"],
      onDelete = ForeignKey.CASCADE,
    ),
    ForeignKey(
      entity = PlannedWorkoutEntity::class,
      parentColumns = ["id"],
      childColumns = ["workoutId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["planId", "weekIndex"]), Index(value = ["workoutId"], unique = true)],
)
data class TrainingPlanWorkoutEntity(
  val planId: String,
  val weekIndex: Int,
  val workoutOrder: Int,
  val workoutId: String,
  val slot: String,
  val generationReason: String,
)
