package com.example.openruncoach.data.local

import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Summary fallback reads need values, not potentially large sensor series. */
data class ActualMetricSnapshot(
  @Embedded val snapshot: RunMetricsEntity,
  @Relation(parentColumn = "runId", entityColumn = "runId")
  val values: List<RunMetricValueEntity>,
)

data class RunWithActualMetrics(
  @Embedded val run: RunEntity,
  @Relation(parentColumn = "healthConnectRecordId", entityColumn = "runId", entity = RunMetricsEntity::class)
  val metrics: List<ActualMetricSnapshot>,
)

@Dao
interface RunDao {
  @Transaction
  @Query("""SELECT * FROM runs WHERE isCanonical = 1 AND physicalRunId = COALESCE(
    (SELECT physicalRunId FROM runs WHERE healthConnectRecordId = :runId), :runId) LIMIT 1""")
  fun observeActualRun(runId: String): Flow<RunWithActualMetrics?>

  @Transaction
  @Query("""SELECT * FROM runs WHERE isCanonical = 1 AND physicalRunId = COALESCE(
    (SELECT physicalRunId FROM runs WHERE healthConnectRecordId = :runId), :runId) LIMIT 1""")
  suspend fun getActualRun(runId: String): RunWithActualMetrics?

  @Transaction
  @Query("SELECT * FROM runs WHERE isCanonical = 1 AND relevance = 'ACTIVE' ORDER BY startTimeEpochMillis DESC, healthConnectRecordId DESC LIMIT 10")
  fun observeRecentActualRuns(): Flow<List<RunWithActualMetrics>>

  @Transaction
  @Query("SELECT * FROM runs WHERE isCanonical = 1 AND relevance = 'ACTIVE' ORDER BY startTimeEpochMillis DESC, healthConnectRecordId DESC")
  fun observeCanonicalActualRuns(): Flow<List<RunWithActualMetrics>>

  @Transaction
  @Query("SELECT * FROM runs WHERE isCanonical = 1 AND relevance = 'IGNORED' ORDER BY startTimeEpochMillis DESC, healthConnectRecordId DESC")
  fun observeIgnoredActualRuns(): Flow<List<RunWithActualMetrics>>

  @Query("SELECT * FROM runs WHERE healthConnectRecordId = :runId LIMIT 1")
  suspend fun getRun(runId: String): RunEntity?

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

  @Transaction
  suspend fun ignorePhysicalRun(physicalRunId: String) {
    // Retain assignment and checkout as restoration inputs. Reconciliation invalidates execution.
    updateRelevance(physicalRunId, "IGNORED")
  }

  @Query("UPDATE workout_run_associations SET actualRunId = :canonicalRunId WHERE physicalRunId = :physicalRunId")
  suspend fun updateAssociationCanonicalRecord(physicalRunId: String, canonicalRunId: String)

  @Query("UPDATE workout_executions SET actualRunId = :canonicalRunId WHERE physicalRunId = :physicalRunId")
  suspend fun updateExecutionCanonicalRecord(physicalRunId: String, canonicalRunId: String)

  @Transaction
  suspend fun repointPhysicalRunReferences(physicalRunId: String, canonicalRunId: String) {
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
