package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RunFeedbackDao {
  @Query("SELECT * FROM run_feedback WHERE runId = :runId LIMIT 1")
  suspend fun getFeedbackForRun(runId: String): RunFeedbackEntity?

  @Query("SELECT * FROM run_feedback WHERE runId = :runId LIMIT 1")
  fun observeFeedbackForRun(runId: String): Flow<RunFeedbackEntity?>

  @Query("SELECT * FROM run_feedback")
  suspend fun getAllFeedback(): List<RunFeedbackEntity>

  @Insert
  suspend fun insertFeedback(feedback: RunFeedbackEntity)

  @Update
  suspend fun updateFeedback(feedback: RunFeedbackEntity)
}
