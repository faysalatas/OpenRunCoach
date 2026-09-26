package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class TrainingGoalRecord(
  @Embedded val goal: TrainingGoalEntity,
  @Relation(parentColumn = "id", entityColumn = "goalId")
  val assessmentReasons: List<TrainingGoalAssessmentReasonEntity>,
)

@Dao
interface TrainingGoalDao {
  @Transaction
  @Query("SELECT * FROM training_goals WHERE status = 'ACTIVE' ORDER BY createdAtEpochMillis DESC LIMIT 1")
  fun observeActiveGoal(): Flow<TrainingGoalRecord?>

  @Transaction
  @Query("SELECT * FROM training_goals WHERE id = :goalId LIMIT 1")
  fun observeGoal(goalId: String): Flow<TrainingGoalRecord?>

  @Transaction
  @Query("SELECT * FROM training_goals WHERE status = 'ACTIVE' ORDER BY createdAtEpochMillis DESC LIMIT 1")
  suspend fun getActiveGoal(): TrainingGoalRecord?

  @Transaction
  @Query("SELECT * FROM training_goals ORDER BY createdAtEpochMillis DESC")
  suspend fun getAllGoals(): List<TrainingGoalRecord>

  @Upsert
  suspend fun upsertGoal(goal: TrainingGoalEntity)

  @Upsert
  suspend fun upsertReasons(reasons: List<TrainingGoalAssessmentReasonEntity>)

  @Query("DELETE FROM training_goal_assessment_reasons WHERE goalId = :goalId")
  suspend fun deleteReasons(goalId: String)

  @Query("UPDATE training_goals SET status = 'ARCHIVED' WHERE status = 'ACTIVE'")
  suspend fun archiveActiveGoals()

  @Query("UPDATE training_goals SET status = :status WHERE id = :goalId")
  suspend fun updateStatus(goalId: String, status: String)

  @Transaction
  suspend fun activate(record: TrainingGoalRecord) {
    archiveActiveGoals()
    upsertGoal(record.goal)
    deleteReasons(record.goal.id)
    upsertReasons(record.assessmentReasons)
  }
}
