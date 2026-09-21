package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
  @Query("SELECT * FROM runs WHERE healthConnectRecordId = :runId LIMIT 1")
  fun observeRun(runId: String): Flow<RunEntity?>

  @Query("SELECT * FROM runs WHERE isCanonical = 1 ORDER BY startTimeEpochMillis DESC, healthConnectRecordId DESC LIMIT 10")
  fun observeRecentRuns(): Flow<List<RunEntity>>

  @Query("SELECT * FROM runs WHERE isCanonical = 1 ORDER BY startTimeEpochMillis DESC, healthConnectRecordId DESC")
  fun observeCanonicalRuns(): Flow<List<RunEntity>>

  @Query("SELECT * FROM runs ORDER BY startTimeEpochMillis DESC")
  fun observeAllRuns(): Flow<List<RunEntity>>

  @Query("SELECT * FROM runs")
  suspend fun getAllRuns(): List<RunEntity>

  @Query("SELECT * FROM runs WHERE sourcePackageName = '' OR sourceRecordLastModifiedTimeEpochMillis IS NULL")
  suspend fun getRunsMissingSourceMetadata(): List<RunEntity>

  @Update
  suspend fun updateAll(runs: List<RunEntity>)

  @Upsert
  suspend fun upsertAll(runs: List<RunEntity>)
}
