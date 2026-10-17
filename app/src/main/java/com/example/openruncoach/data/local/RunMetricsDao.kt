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
  @Relation(parentColumn = "runId", entityColumn = "runId")
  val samples: List<RunMetricSampleEntity>,
)

@Dao
interface RunMetricsDao {
  /** Detail follows the current canonical summary even when opened via an older provider ID. */
  @Transaction
  @Query("""SELECT run_metrics.* FROM run_metrics JOIN runs canonical ON canonical.healthConnectRecordId = run_metrics.runId
    WHERE canonical.isCanonical = 1 AND canonical.physicalRunId = COALESCE(
      (SELECT physicalRunId FROM runs WHERE healthConnectRecordId = :runId), :runId) LIMIT 1""")
  fun observeActualRunMetrics(runId: String): Flow<RunMetricsWithValues?>

  @Transaction
  @Query("SELECT * FROM run_metrics WHERE runId = :runId LIMIT 1")
  fun observeRunMetrics(runId: String): Flow<RunMetricsWithValues?>

  @Transaction
  @Query("SELECT * FROM run_metrics WHERE runId = :runId LIMIT 1")
  suspend fun getRunMetrics(runId: String): RunMetricsWithValues?

  @Transaction
  @Query("SELECT * FROM run_metrics ORDER BY startTimeEpochMillis ASC")
  suspend fun getAllRunMetrics(): List<RunMetricsWithValues>

  @Query("SELECT runId FROM run_metrics ORDER BY startTimeEpochMillis ASC")
  suspend fun getAllRunMetricIds(): List<String>

  @Transaction
  @Query(
    """SELECT run_metrics.* FROM run_metrics
      INNER JOIN runs ON runs.healthConnectRecordId = run_metrics.runId
      WHERE runs.isCanonical = 1 AND runs.relevance = 'ACTIVE'
        AND run_metrics.startTimeEpochMillis = runs.startTimeEpochMillis
        AND run_metrics.endTimeEpochMillis = runs.endTimeEpochMillis
      ORDER BY run_metrics.startTimeEpochMillis ASC"""
  )
  suspend fun getCanonicalRunMetrics(): List<RunMetricsWithValues>

  @Query(
    """SELECT run_metrics.runId FROM run_metrics
      INNER JOIN runs ON runs.healthConnectRecordId = run_metrics.runId
      WHERE runs.isCanonical = 1 AND runs.relevance = 'ACTIVE'
        AND run_metrics.startTimeEpochMillis = runs.startTimeEpochMillis
        AND run_metrics.endTimeEpochMillis = runs.endTimeEpochMillis
      ORDER BY run_metrics.startTimeEpochMillis ASC"""
  )
  suspend fun getCanonicalRunMetricIds(): List<String>

  @Upsert
  suspend fun upsertSnapshot(snapshot: RunMetricsEntity)

  @Upsert
  suspend fun upsertValues(values: List<RunMetricValueEntity>)

  @Upsert
  suspend fun upsertSamples(samples: List<RunMetricSampleEntity>)

  @Query("DELETE FROM run_metric_values WHERE runId = :runId")
  suspend fun deleteValues(runId: String)

  @Query("DELETE FROM run_metric_samples WHERE runId = :runId")
  suspend fun deleteSamples(runId: String)

  @Query(
    """SELECT COUNT(*) FROM run_metric_samples
      WHERE runId = :runId AND metricType = :metricType"""
  )
  suspend fun countSamples(runId: String, metricType: String): Int

  @Transaction
  suspend fun replace(
    snapshot: RunMetricsEntity,
    values: List<RunMetricValueEntity>,
    samples: List<RunMetricSampleEntity>,
  ) {
    upsertSnapshot(snapshot)
    deleteValues(snapshot.runId)
    deleteSamples(snapshot.runId)
    if (values.isNotEmpty()) upsertValues(values)
    if (samples.isNotEmpty()) upsertSamples(samples)
  }
}
