package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
  @Query("SELECT * FROM runs WHERE healthConnectRecordId = :runId LIMIT 1")
  fun observeRun(runId: String): Flow<RunEntity?>

  @Query("SELECT * FROM runs WHERE isCanonical = 1 AND relevance = 'ACTIVE' ORDER BY startTimeEpochMillis DESC, healthConnectRecordId DESC LIMIT 10")
  fun observeRecentRuns(): Flow<List<RunEntity>>

  @Query("SELECT * FROM runs WHERE isCanonical = 1 AND relevance = 'ACTIVE' ORDER BY startTimeEpochMillis DESC, healthConnectRecordId DESC")
  fun observeCanonicalRuns(): Flow<List<RunEntity>>

  @Query("SELECT * FROM runs WHERE isCanonical = 1 AND relevance = 'IGNORED' ORDER BY startTimeEpochMillis DESC, healthConnectRecordId DESC")
  fun observeIgnoredCanonicalRuns(): Flow<List<RunEntity>>

  @Query("SELECT * FROM runs ORDER BY startTimeEpochMillis DESC")
  fun observeAllRuns(): Flow<List<RunEntity>>

  @Query("SELECT * FROM runs")
  suspend fun getAllRuns(): List<RunEntity>

  @Query("SELECT * FROM runs WHERE isCanonical = 1 AND relevance = 'ACTIVE' ORDER BY startTimeEpochMillis ASC, healthConnectRecordId ASC")
  suspend fun getCanonicalRuns(): List<RunEntity>

  @Query("SELECT * FROM runs WHERE physicalRunId = :physicalRunId ORDER BY healthConnectRecordId")
  suspend fun getProviderRecords(physicalRunId: String): List<RunEntity>

  @Query("SELECT * FROM runs WHERE physicalRunId = :physicalRunId AND isCanonical = 1 LIMIT 1")
  suspend fun getCanonicalRunByPhysicalId(physicalRunId: String): RunEntity?

  @Query("UPDATE runs SET relevance = :relevance WHERE physicalRunId = :physicalRunId")
  suspend fun updateRelevance(physicalRunId: String, relevance: String)

  @Query("UPDATE planned_workouts SET lifecycleState = 'PLANNED' WHERE id IN (SELECT plannedWorkoutId FROM workout_run_associations WHERE physicalRunId = :physicalRunId)")
  suspend fun resetAssociatedWorkout(physicalRunId: String)

  @Query("DELETE FROM workout_executions WHERE physicalRunId = :physicalRunId")
  suspend fun deletePhysicalRunExecution(physicalRunId: String)

  @Query("DELETE FROM workout_run_associations WHERE physicalRunId = :physicalRunId")
  suspend fun deletePhysicalRunAssociation(physicalRunId: String)

  @Transaction
  suspend fun ignorePhysicalRun(physicalRunId: String) {
    resetAssociatedWorkout(physicalRunId)
    deletePhysicalRunExecution(physicalRunId)
    deletePhysicalRunAssociation(physicalRunId)
    updateRelevance(physicalRunId, "IGNORED")
  }

  @Query(
    """UPDATE run_feedback SET runId = :canonicalRunId
      WHERE runId = (SELECT run_feedback.runId FROM run_feedback
        INNER JOIN runs ON runs.healthConnectRecordId = run_feedback.runId
        WHERE runs.physicalRunId = :physicalRunId AND run_feedback.runId != :canonicalRunId LIMIT 1)
      AND NOT EXISTS (SELECT 1 FROM run_feedback WHERE runId = :canonicalRunId)"""
  )
  suspend fun moveFeedbackToCanonical(physicalRunId: String, canonicalRunId: String)

  @Query("UPDATE workout_run_associations SET actualRunId = :canonicalRunId WHERE physicalRunId = :physicalRunId")
  suspend fun updateAssociationCanonicalRecord(physicalRunId: String, canonicalRunId: String)

  @Query("UPDATE workout_executions SET actualRunId = :canonicalRunId WHERE physicalRunId = :physicalRunId")
  suspend fun updateExecutionCanonicalRecord(physicalRunId: String, canonicalRunId: String)

  @Transaction
  suspend fun repointPhysicalRunReferences(physicalRunId: String, canonicalRunId: String) {
    moveFeedbackToCanonical(physicalRunId, canonicalRunId)
    updateAssociationCanonicalRecord(physicalRunId, canonicalRunId)
    updateExecutionCanonicalRecord(physicalRunId, canonicalRunId)
  }

  @Query("SELECT * FROM runs WHERE sourcePackageName = '' OR sourceRecordLastModifiedTimeEpochMillis IS NULL")
  suspend fun getRunsMissingSourceMetadata(): List<RunEntity>

  @Update
  suspend fun updateAll(runs: List<RunEntity>)

  @Upsert
  suspend fun upsertAll(runs: List<RunEntity>)
}
