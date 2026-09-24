package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class WorkoutSegmentRecord(
  @Embedded val segment: WorkoutSegmentEntity,
  @Relation(parentColumn = "id", entityColumn = "segmentId")
  val intensityTargets: List<WorkoutIntensityTargetEntity>,
)

data class WorkoutBlockRecord(
  @Embedded val block: WorkoutBlockEntity,
  @Relation(entity = WorkoutSegmentEntity::class, parentColumn = "id", entityColumn = "blockId")
  val segments: List<WorkoutSegmentRecord>,
)

data class PlannedWorkoutRecord(
  @Embedded val workout: PlannedWorkoutEntity,
  @Relation(entity = WorkoutBlockEntity::class, parentColumn = "id", entityColumn = "workoutId")
  val blocks: List<WorkoutBlockRecord>,
  @Relation(parentColumn = "id", entityColumn = "workoutId")
  val cues: List<WorkoutCueEntity>,
)

@Dao
interface CoachingWorkflowDao {
  @Transaction
  @Query("SELECT * FROM planned_workouts WHERE isPreview = 0 ORDER BY dateEpochDay DESC, createdAtEpochMillis DESC LIMIT 1")
  fun observeLatestPlannedWorkout(): Flow<PlannedWorkoutRecord?>

  @Transaction
  @Query("SELECT * FROM planned_workouts WHERE id = :workoutId LIMIT 1")
  fun observeWorkout(workoutId: String): Flow<PlannedWorkoutRecord?>

  @Transaction
  @Query("SELECT * FROM planned_workouts WHERE id = :workoutId LIMIT 1")
  suspend fun getWorkout(workoutId: String): PlannedWorkoutRecord?

  @Query("SELECT * FROM pre_run_check_ins WHERE plannedWorkoutId = :plannedWorkoutId LIMIT 1")
  fun observeCheckIn(plannedWorkoutId: String): Flow<PreRunCheckInEntity?>

  @Query("SELECT * FROM workout_adjustments WHERE plannedWorkoutId = :plannedWorkoutId LIMIT 1")
  fun observeAdjustment(plannedWorkoutId: String): Flow<WorkoutAdjustmentEntity?>

  @Query("SELECT COUNT(*) FROM planned_workouts WHERE isPreview = 0")
  suspend fun workoutCount(): Int

  @Query("SELECT lifecycleState FROM planned_workouts WHERE id = :plannedWorkoutId LIMIT 1")
  suspend fun getLifecycleState(plannedWorkoutId: String): String?

  @Query("UPDATE planned_workouts SET lifecycleState = :state WHERE id = :plannedWorkoutId")
  suspend fun updateLifecycleState(plannedWorkoutId: String, state: String)

  @Upsert suspend fun upsertPlannedWorkout(workout: PlannedWorkoutEntity)
  @Upsert suspend fun upsertBlocks(blocks: List<WorkoutBlockEntity>)
  @Upsert suspend fun upsertSegments(segments: List<WorkoutSegmentEntity>)
  @Upsert suspend fun upsertIntensityTargets(targets: List<WorkoutIntensityTargetEntity>)
  @Upsert suspend fun upsertCues(cues: List<WorkoutCueEntity>)
  @Upsert suspend fun upsertCheckIn(checkIn: PreRunCheckInEntity)
  @Upsert suspend fun upsertAdjustment(adjustment: WorkoutAdjustmentEntity)

  @Query("DELETE FROM workout_blocks WHERE workoutId = :workoutId")
  suspend fun deleteBlocks(workoutId: String)

  @Query("DELETE FROM workout_cues WHERE workoutId = :workoutId")
  suspend fun deleteCues(workoutId: String)

  @Transaction
  suspend fun replaceWorkout(record: PlannedWorkoutRecord) {
    upsertPlannedWorkout(record.workout)
    deleteBlocks(record.workout.id)
    deleteCues(record.workout.id)
    upsertBlocks(record.blocks.map { it.block })
    upsertSegments(record.blocks.flatMap { block -> block.segments.map { it.segment } })
    upsertIntensityTargets(record.blocks.flatMap { block -> block.segments.flatMap { it.intensityTargets } })
    upsertCues(record.cues)
  }
}
