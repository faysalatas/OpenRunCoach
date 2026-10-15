package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Read-only fields for Plan presentation; no additional persisted state. */
data class PlanWorkoutMetadata(
  val workoutId: String,
  val lifecycleState: String,
  val adjusted: Boolean,
  val physicalRunId: String?,
)

data class PreviousPlanRecord(
  val id: String,
  val startDateEpochDay: Long,
  val endDateEpochDay: Long?,
  val status: String,
  val weekCount: Int,
  val distance: String,
  val customDistanceMeters: Double?,
  val goalType: String,
)

@Dao
interface TrainingPlanDao {
  @Query("""SELECT planned_workouts.id AS workoutId, planned_workouts.lifecycleState,
    EXISTS(SELECT 1 FROM workout_adjustments WHERE plannedWorkoutId = planned_workouts.id) AS adjusted,
    workout_run_associations.physicalRunId
    FROM training_plan_workouts
    INNER JOIN planned_workouts ON planned_workouts.id = training_plan_workouts.workoutId
    LEFT JOIN workout_run_associations ON workout_run_associations.plannedWorkoutId = planned_workouts.id
    WHERE training_plan_workouts.planId = :planId""")
  fun observeWorkoutMetadata(planId: String): Flow<List<PlanWorkoutMetadata>>

  @Query("""SELECT training_plans.id, training_plans.startDateEpochDay, training_plans.endDateEpochDay,
    training_plans.status, training_goals.distance, training_goals.customDistanceMeters, training_goals.goalType,
    (SELECT COUNT(*) FROM training_weeks WHERE planId = training_plans.id) AS weekCount
    FROM training_plans INNER JOIN training_goals ON training_goals.id = training_plans.goalId
    WHERE training_plans.status != 'ACTIVE' OR training_goals.status != 'ACTIVE'
    ORDER BY training_plans.createdAtEpochMillis DESC, training_plans.id""")
  fun observePreviousPlans(): Flow<List<PreviousPlanRecord>>

  @Query(
    """SELECT training_plans.* FROM training_plans
      INNER JOIN training_goals ON training_goals.id = training_plans.goalId
      WHERE training_plans.status = 'ACTIVE' AND training_goals.status = 'ACTIVE'
      ORDER BY training_plans.createdAtEpochMillis DESC LIMIT 1"""
  )
  fun observeActivePlan(): Flow<TrainingPlanEntity?>

  @Query(
    """SELECT COUNT(workout_executions.plannedWorkoutId) FROM workout_executions
      INNER JOIN training_plan_workouts ON training_plan_workouts.workoutId = workout_executions.plannedWorkoutId
      INNER JOIN training_plans ON training_plans.id = training_plan_workouts.planId
      INNER JOIN training_goals ON training_goals.id = training_plans.goalId
      WHERE training_plans.status = 'ACTIVE' AND training_goals.status = 'ACTIVE'
        AND EXISTS (SELECT 1 FROM workout_run_associations WHERE workout_run_associations.plannedWorkoutId = workout_executions.plannedWorkoutId AND workout_run_associations.physicalRunId = workout_executions.physicalRunId)
        AND EXISTS (SELECT 1 FROM runs WHERE runs.physicalRunId = workout_executions.physicalRunId AND runs.isCanonical = 1 AND runs.relevance = 'ACTIVE')"""
  )
  fun observeActivePlanExecutionRevision(): Flow<Int>

  @Query(
    """SELECT training_plans.* FROM training_plans
      INNER JOIN training_goals ON training_goals.id = training_plans.goalId
      WHERE training_plans.status = 'ACTIVE' AND training_goals.status = 'ACTIVE'
      ORDER BY training_plans.createdAtEpochMillis DESC LIMIT 1"""
  )
  suspend fun getActivePlan(): TrainingPlanEntity?

  @Query("SELECT * FROM training_plans WHERE goalId = :goalId AND status = 'ACTIVE' ORDER BY createdAtEpochMillis DESC LIMIT 1")
  fun observeActivePlanForGoal(goalId: String): Flow<TrainingPlanEntity?>

  @Query("SELECT * FROM training_plans WHERE goalId = :goalId AND status = 'ACTIVE' ORDER BY createdAtEpochMillis DESC LIMIT 1")
  suspend fun getActivePlanForGoal(goalId: String): TrainingPlanEntity?

  @Query("SELECT * FROM training_plans WHERE id = :planId LIMIT 1")
  suspend fun getPlan(planId: String): TrainingPlanEntity?

  @Query("SELECT * FROM training_weeks WHERE planId = :planId ORDER BY weekIndex")
  suspend fun getWeeks(planId: String): List<TrainingWeekEntity>

  @Query("SELECT * FROM training_week_reasons WHERE planId = :planId ORDER BY weekIndex, position")
  suspend fun getReasons(planId: String): List<TrainingWeekReasonEntity>

  @Query("SELECT * FROM training_blocks WHERE planId = :planId ORDER BY blockIndex")
  suspend fun getBlocks(planId: String): List<TrainingBlockEntity>

  @Query("SELECT * FROM training_plan_workouts WHERE planId = :planId ORDER BY weekIndex, workoutOrder")
  suspend fun getWorkoutReferences(planId: String): List<TrainingPlanWorkoutEntity>

  @Query("SELECT * FROM training_plans ORDER BY createdAtEpochMillis")
  suspend fun getAllPlans(): List<TrainingPlanEntity>

  @Query(
    """SELECT planned_workouts.id FROM planned_workouts
      INNER JOIN training_plan_workouts ON training_plan_workouts.workoutId = planned_workouts.id
      INNER JOIN training_plans ON training_plans.id = training_plan_workouts.planId
      WHERE training_plans.goalId = :goalId
        AND training_plans.status = 'ACTIVE'
        AND planned_workouts.purpose = 'GOAL_COMPLETION'
      ORDER BY planned_workouts.dateEpochDay"""
  )
  suspend fun getActiveCompletionWorkoutIds(goalId: String): List<String>

  @Query("UPDATE training_plans SET status = 'SUPERSEDED' WHERE status = 'ACTIVE'")
  suspend fun supersedeActivePlans()

  @Query("UPDATE training_plans SET status = :status WHERE id = :planId")
  suspend fun updateStatus(planId: String, status: String)

  @Query(
    """UPDATE training_plans SET
      plannedCompletionDateEpochDay = :dateEpochDay,
      plannedCompletionRunId = :workoutId,
      completionReadiness = :readiness,
      completionPromptDismissed = :promptDismissed,
      completionUpdatedAtEpochMillis = :updatedAtEpochMillis
      WHERE id = :planId AND goalId = :goalId AND status = 'ACTIVE'"""
  )
  suspend fun updateCompletionMilestone(
    planId: String,
    goalId: String,
    dateEpochDay: Long?,
    workoutId: String?,
    readiness: String,
    promptDismissed: Boolean,
    updatedAtEpochMillis: Long,
  ): Int

  @Upsert suspend fun upsertPlan(plan: TrainingPlanEntity)
  @Upsert suspend fun upsertWeeks(weeks: List<TrainingWeekEntity>)
  @Upsert suspend fun upsertReasons(reasons: List<TrainingWeekReasonEntity>)
  @Upsert suspend fun upsertBlocks(blocks: List<TrainingBlockEntity>)
  @Upsert suspend fun upsertWorkoutReferences(workouts: List<TrainingPlanWorkoutEntity>)
}
