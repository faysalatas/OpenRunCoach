package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
  @Query("SELECT * FROM runs ORDER BY startTimeEpochMillis DESC LIMIT 10")
  fun observeRecentRuns(): Flow<List<RunEntity>>

  @Upsert
  suspend fun upsertAll(runs: List<RunEntity>)
}
