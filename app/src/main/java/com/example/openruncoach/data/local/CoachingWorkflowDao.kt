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
  /** Unfiltered rows are needed to remove obsolete or ignored interpretations during recovery. */
  @Query("SELECT * FROM workout_executions")
  suspend fun getStoredExecutions(): List<WorkoutExecutionEntity>

  @Query("SELECT * FROM workout_executions WHERE physicalRunId = :physicalRunId ORDER BY plannedWorkoutId")
  suspend fun getStoredExecutionsForPhysicalRun(physicalRunId: String): List<WorkoutExecutionEntity>

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

  @Query("SELECT * FROM workout_adjustments WHERE plannedWorkoutId = :plannedWorkoutId LIMIT 1")
  suspend fun getAdjustment(plannedWorkoutId: String): WorkoutAdjustmentEntity?

  @Query("SELECT * FROM workout_executions WHERE plannedWorkoutId = :plannedWorkoutId AND EXISTS (SELECT 1 FROM workout_run_associations WHERE workout_run_associations.plannedWorkoutId = workout_executions.plannedWorkoutId AND workout_run_associations.physicalRunId = workout_executions.physicalRunId) AND EXISTS (SELECT 1 FROM runs WHERE runs.physicalRunId = workout_executions.physicalRunId AND runs.isCanonical = 1 AND runs.relevance = 'ACTIVE') LIMIT 1")
  fun observeExecution(plannedWorkoutId: String): Flow<WorkoutExecutionEntity?>

  @Query("SELECT workout_executions.* FROM workout_executions INNER JOIN runs ON runs.physicalRunId = workout_executions.physicalRunId INNER JOIN workout_run_associations ON workout_run_associations.plannedWorkoutId = workout_executions.plannedWorkoutId AND workout_run_associations.physicalRunId = workout_executions.physicalRunId WHERE runs.healthConnectRecordId = :actualRunId AND runs.relevance = 'ACTIVE' LIMIT 1")
  fun observeExecutionForRun(actualRunId: String): Flow<WorkoutExecutionEntity?>

  @Query("SELECT workout_executions.* FROM workout_executions INNER JOIN workout_run_associations ON workout_run_associations.plannedWorkoutId = workout_executions.plannedWorkoutId AND workout_run_associations.physicalRunId = workout_executions.physicalRunId WHERE workout_executions.physicalRunId = :physicalRunId LIMIT 1")
  suspend fun getExecutionForPhysicalRun(physicalRunId: String): WorkoutExecutionEntity?

  @Query("SELECT workout_executions.* FROM workout_executions INNER JOIN runs ON runs.physicalRunId = workout_executions.physicalRunId INNER JOIN workout_run_associations ON workout_run_associations.plannedWorkoutId = workout_executions.plannedWorkoutId AND workout_run_associations.physicalRunId = workout_executions.physicalRunId WHERE runs.healthConnectRecordId = :actualRunId LIMIT 1")
  suspend fun getExecutionForRun(actualRunId: String): WorkoutExecutionEntity?

  @Query("SELECT * FROM workout_executions WHERE EXISTS (SELECT 1 FROM workout_run_associations WHERE workout_run_associations.plannedWorkoutId = workout_executions.plannedWorkoutId AND workout_run_associations.physicalRunId = workout_executions.physicalRunId) AND EXISTS (SELECT 1 FROM runs WHERE runs.physicalRunId = workout_executions.physicalRunId AND runs.isCanonical = 1 AND runs.relevance = 'ACTIVE')")
  suspend fun getAllExecutions(): List<WorkoutExecutionEntity>

  @Transaction
  @Query(
    """SELECT planned_workouts.* FROM planned_workouts
      INNER JOIN training_plan_workouts ON training_plan_workouts.workoutId = planned_workouts.id
      INNER JOIN training_plans ON training_plans.id = training_plan_workouts.planId
      INNER JOIN training_goals ON training_goals.id = training_plans.goalId
      LEFT JOIN workout_run_associations ON workout_run_associations.plannedWorkoutId = planned_workouts.id
      WHERE training_plans.status = 'ACTIVE' AND training_goals.status = 'ACTIVE' AND planned_workouts.isPreview = 0
        AND planned_workouts.dateEpochDay = :dateEpochDay AND planned_workouts.type != 'REST'
        AND workout_run_associations.plannedWorkoutId IS NULL
      ORDER BY planned_workouts.createdAtEpochMillis DESC"""
  )
  suspend fun getUnassociatedActivePlanWorkouts(dateEpochDay: Long): List<PlannedWorkoutRecord>

  @Transaction
  @Query(
    """SELECT planned_workouts.* FROM planned_workouts
      INNER JOIN training_plan_workouts ON training_plan_workouts.workoutId = planned_workouts.id
      INNER JOIN training_plans ON training_plans.id = training_plan_workouts.planId
      INNER JOIN training_goals ON training_goals.id = training_plans.goalId
      LEFT JOIN workout_run_associations ON workout_run_associations.plannedWorkoutId = planned_workouts.id
      WHERE training_plans.status = 'ACTIVE' AND training_goals.status = 'ACTIVE' AND planned_workouts.isPreview = 0
        AND workout_run_associations.plannedWorkoutId IS NULL
      ORDER BY planned_workouts.dateEpochDay, planned_workouts.createdAtEpochMillis DESC"""
  )
  suspend fun getAllUnassociatedActivePlanWorkouts(): List<PlannedWorkoutRecord>

  @Transaction
  @Query(
    """SELECT planned_workouts.* FROM planned_workouts
      INNER JOIN training_plan_workouts ON training_plan_workouts.workoutId = planned_workouts.id
      INNER JOIN training_plans ON training_plans.id = training_plan_workouts.planId
      INNER JOIN training_goals ON training_goals.id = training_plans.goalId
      WHERE training_plans.status = 'ACTIVE' AND training_goals.status = 'ACTIVE'
        AND planned_workouts.isPreview = 0 AND planned_workouts.type != 'REST'
      ORDER BY planned_workouts.dateEpochDay, planned_workouts.createdAtEpochMillis DESC"""
  )
  suspend fun getAllActivePlanWorkouts(): List<PlannedWorkoutRecord>

  @Query(
    """SELECT workout_executions.* FROM workout_executions
      INNER JOIN training_plan_workouts ON training_plan_workouts.workoutId = workout_executions.plannedWorkoutId
      WHERE training_plan_workouts.planId = :planId
        AND EXISTS (SELECT 1 FROM workout_run_associations WHERE workout_run_associations.plannedWorkoutId = workout_executions.plannedWorkoutId AND workout_run_associations.physicalRunId = workout_executions.physicalRunId)
        AND EXISTS (SELECT 1 FROM runs WHERE runs.physicalRunId = workout_executions.physicalRunId AND runs.isCanonical = 1 AND runs.relevance = 'ACTIVE')"""
  )
  suspend fun getExecutionsForPlan(planId: String): List<WorkoutExecutionEntity>

  @Query("SELECT COUNT(*) FROM planned_workouts WHERE isPreview = 0")
  suspend fun workoutCount(): Int

  @Query(
    """SELECT COUNT(*) FROM planned_workouts
      WHERE isPreview = 0 AND type = 'ASSESSMENT' AND goalId = :goalId
      AND lifecycleState IN ('COMPLETED', 'CHECKOUT_REQUIRED', 'ANALYZED', 'NEXT_WORKOUT_CREATED')"""
  )
  suspend fun completedAssessmentCount(goalId: String): Int

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
  @Upsert suspend fun upsertExecution(execution: WorkoutExecutionEntity)
  @Upsert suspend fun upsertAssociation(association: WorkoutRunAssociationEntity)

  @Query("SELECT * FROM workout_run_associations WHERE physicalRunId = :physicalRunId LIMIT 1")
  suspend fun getAssociationForPhysicalRun(physicalRunId: String): WorkoutRunAssociationEntity?

  @Query("SELECT * FROM workout_run_associations WHERE plannedWorkoutId = :plannedWorkoutId LIMIT 1")
  suspend fun getAssociationForWorkout(plannedWorkoutId: String): WorkoutRunAssociationEntity?

  @Query("SELECT * FROM workout_run_associations")
  suspend fun getAllAssociations(): List<WorkoutRunAssociationEntity>

  @Query("SELECT * FROM workout_run_associations WHERE physicalRunId = :physicalRunId LIMIT 1")
  fun observeAssociationForPhysicalRun(physicalRunId: String): Flow<WorkoutRunAssociationEntity?>

  @Query("DELETE FROM workout_run_associations WHERE physicalRunId = :physicalRunId")
  suspend fun deleteAssociationForPhysicalRun(physicalRunId: String)

  @Query("DELETE FROM workout_executions WHERE physicalRunId = :physicalRunId")
  suspend fun deleteExecutionForPhysicalRun(physicalRunId: String)

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
