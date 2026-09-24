package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class RunMetricsWithValues(
  @Embedded val snapshot: RunMetricsEntity,
  @Relation(parentColumn = "runId", entityColumn = "runId")
  val values: List<RunMetricValueEntity>,
)

@Dao
interface RunMetricsDao {
  @Transaction
  @Query("SELECT * FROM run_metrics WHERE runId = :runId LIMIT 1")
  fun observeRunMetrics(runId: String): Flow<RunMetricsWithValues?>

  @Transaction
  @Query("SELECT * FROM run_metrics WHERE runId = :runId LIMIT 1")
  suspend fun getRunMetrics(runId: String): RunMetricsWithValues?

  @Transaction
  @Query("SELECT * FROM run_metrics ORDER BY startTimeEpochMillis ASC")
  suspend fun getAllRunMetrics(): List<RunMetricsWithValues>

  @Upsert
  suspend fun upsertSnapshot(snapshot: RunMetricsEntity)

  @Upsert
  suspend fun upsertValues(values: List<RunMetricValueEntity>)

  @Query("DELETE FROM run_metric_values WHERE runId = :runId")
  suspend fun deleteValues(runId: String)

  @Transaction
  suspend fun replace(snapshot: RunMetricsEntity, values: List<RunMetricValueEntity>) {
    upsertSnapshot(snapshot)
    deleteValues(snapshot.runId)
    upsertValues(values)
  }
}
