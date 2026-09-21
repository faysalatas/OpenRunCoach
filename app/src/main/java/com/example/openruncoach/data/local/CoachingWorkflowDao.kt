package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachingWorkflowDao {
  @Query("SELECT * FROM planned_workouts ORDER BY dateEpochDay DESC, createdAtEpochMillis DESC LIMIT 1")
  fun observeLatestPlannedWorkout(): Flow<PlannedWorkoutEntity?>

  @Query("SELECT * FROM pre_run_check_ins WHERE plannedWorkoutId = :plannedWorkoutId LIMIT 1")
  fun observeCheckIn(plannedWorkoutId: String): Flow<PreRunCheckInEntity?>

  @Query("SELECT * FROM workout_adjustments WHERE plannedWorkoutId = :plannedWorkoutId LIMIT 1")
  fun observeAdjustment(plannedWorkoutId: String): Flow<WorkoutAdjustmentEntity?>

  @Query("SELECT COUNT(*) FROM planned_workouts")
  suspend fun workoutCount(): Int

  @Query("SELECT lifecycleState FROM planned_workouts WHERE id = :plannedWorkoutId LIMIT 1")
  suspend fun getLifecycleState(plannedWorkoutId: String): String?

  @Query("UPDATE planned_workouts SET lifecycleState = :state WHERE id = :plannedWorkoutId")
  suspend fun updateLifecycleState(plannedWorkoutId: String, state: String)

  @Upsert
  suspend fun upsertPlannedWorkout(workout: PlannedWorkoutEntity)

  @Upsert
  suspend fun upsertCheckIn(checkIn: PreRunCheckInEntity)

  @Upsert
  suspend fun upsertAdjustment(adjustment: WorkoutAdjustmentEntity)
}
