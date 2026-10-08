package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class RunRouteWithPointsAndSplits(
  @Embedded val route: RunRouteEntity,
  @Relation(parentColumn = "runId", entityColumn = "runId")
  val points: List<RunRoutePointEntity>,
  @Relation(parentColumn = "runId", entityColumn = "runId")
  val splits: List<RunSplitEntity>,
)

/** Resolves legacy provider-keyed rows through the established physical-run group. */
data class PhysicalRunRouteWithPointsAndSplits(
  @Embedded val route: RunRouteEntity,
  val physicalRunId: String,
  val canonicalStartTimeEpochMillis: Long,
  val canonicalEndTimeEpochMillis: Long,
  val canonicalRunDistanceMeters: Double?,
  val canonicalRunId: String = physicalRunId,
  val canonicalDurationSeconds: Long = (canonicalEndTimeEpochMillis - canonicalStartTimeEpochMillis) / 1000L,
  @Relation(parentColumn = "runId", entityColumn = "runId")
  val points: List<RunRoutePointEntity>,
  @Relation(parentColumn = "runId", entityColumn = "runId")
  val splits: List<RunSplitEntity>,
)

@Dao
interface RunRouteDao {
  @Transaction
  suspend fun backfillPhysicalSplits(
    runId: String,
    derive: (PhysicalRunRouteWithPointsAndSplits, RunMetricsWithValues?) -> List<RunSplitEntity>,
  ) {
    val stored = getPhysicalRoute(runId) ?: return
    val resolved = derive(stored, getPhysicalMetrics(runId))
    if (stored.splits.sortedBy { it.ordinal } != resolved) replaceSplitCache(stored.route.runId, resolved)
  }

  @Transaction
  @Query("""SELECT run_metrics.* FROM run_metrics JOIN runs canonical ON canonical.healthConnectRecordId = run_metrics.runId
    WHERE canonical.isCanonical = 1 AND canonical.physicalRunId = COALESCE(
      (SELECT physicalRunId FROM runs WHERE healthConnectRecordId = :runId), :runId) LIMIT 1""")
  suspend fun getPhysicalMetrics(runId: String): RunMetricsWithValues?

  @Transaction
  @Query("""SELECT run_metrics.* FROM run_metrics JOIN runs canonical ON canonical.healthConnectRecordId = run_metrics.runId
    WHERE canonical.isCanonical = 1 AND canonical.physicalRunId = COALESCE(
      (SELECT physicalRunId FROM runs WHERE healthConnectRecordId = :runId), :runId) LIMIT 1""")
  fun observePhysicalMetrics(runId: String): Flow<RunMetricsWithValues?>

  @Query("DELETE FROM run_splits WHERE runId = :runId")
  suspend fun deleteSplits(runId: String)

  @Transaction
  suspend fun replaceSplitCache(runId: String, splits: List<RunSplitEntity>) {
    require(splits.all { it.runId == runId })
    deleteSplits(runId)
    if (splits.isNotEmpty()) insertSplits(splits)
  }

  @Transaction
  @Query("SELECT * FROM run_routes WHERE runId = :runId LIMIT 1")
  fun observeRoute(runId: String): Flow<RunRouteWithPointsAndSplits?>

  @Transaction
  @Query("SELECT * FROM run_routes WHERE runId = :runId LIMIT 1")
  suspend fun getRoute(runId: String): RunRouteWithPointsAndSplits?

  @Transaction
  @Query("""SELECT run_routes.*, owner.physicalRunId,
      canonical.startTimeEpochMillis AS canonicalStartTimeEpochMillis,
      canonical.endTimeEpochMillis AS canonicalEndTimeEpochMillis,
      canonical.distanceMeters AS canonicalRunDistanceMeters,
      canonical.healthConnectRecordId AS canonicalRunId,
      canonical.durationSeconds AS canonicalDurationSeconds
    FROM run_routes JOIN runs AS owner ON owner.healthConnectRecordId = run_routes.runId
    JOIN runs AS canonical ON canonical.physicalRunId = owner.physicalRunId AND canonical.isCanonical = 1
    WHERE owner.physicalRunId = COALESCE(
      (SELECT physicalRunId FROM runs WHERE healthConnectRecordId = :runId), :runId)
    ORDER BY CASE WHEN run_routes.runId = owner.physicalRunId THEN 0 ELSE 1 END, run_routes.runId
    LIMIT 1""")
  fun observePhysicalRoute(runId: String): Flow<PhysicalRunRouteWithPointsAndSplits?>

  @Transaction
  @Query("""SELECT run_routes.*, owner.physicalRunId,
      canonical.startTimeEpochMillis AS canonicalStartTimeEpochMillis,
      canonical.endTimeEpochMillis AS canonicalEndTimeEpochMillis,
      canonical.distanceMeters AS canonicalRunDistanceMeters,
      canonical.healthConnectRecordId AS canonicalRunId,
      canonical.durationSeconds AS canonicalDurationSeconds
    FROM run_routes JOIN runs AS owner ON owner.healthConnectRecordId = run_routes.runId
    JOIN runs AS canonical ON canonical.physicalRunId = owner.physicalRunId AND canonical.isCanonical = 1
    WHERE owner.physicalRunId = COALESCE(
      (SELECT physicalRunId FROM runs WHERE healthConnectRecordId = :runId), :runId)
    ORDER BY CASE WHEN run_routes.runId = owner.physicalRunId THEN 0 ELSE 1 END, run_routes.runId
    LIMIT 1""")
  suspend fun getPhysicalRoute(runId: String): PhysicalRunRouteWithPointsAndSplits?

  @Transaction
  @Query("""SELECT run_routes.*, owner.physicalRunId,
      canonical.startTimeEpochMillis AS canonicalStartTimeEpochMillis,
      canonical.endTimeEpochMillis AS canonicalEndTimeEpochMillis,
      canonical.distanceMeters AS canonicalRunDistanceMeters,
      canonical.healthConnectRecordId AS canonicalRunId,
      canonical.durationSeconds AS canonicalDurationSeconds
    FROM run_routes JOIN runs AS owner ON owner.healthConnectRecordId = run_routes.runId
    JOIN runs AS canonical ON canonical.physicalRunId = owner.physicalRunId AND canonical.isCanonical = 1
    ORDER BY CASE WHEN run_routes.runId = owner.physicalRunId THEN 0 ELSE 1 END, run_routes.runId""")
  fun observePhysicalRoutes(): Flow<List<PhysicalRunRouteWithPointsAndSplits>>

  // The schema retains its provider-record FK. Prefer the original identity anchor;
  // legacy custom physical IDs use a deterministic preserved member of that same group.
  @Query("""SELECT healthConnectRecordId FROM runs WHERE physicalRunId = :physicalRunId
    ORDER BY CASE WHEN healthConnectRecordId = :physicalRunId THEN 0 ELSE 1 END, healthConnectRecordId LIMIT 1""")
  suspend fun routeStorageOwner(physicalRunId: String): String?

  @Query("SELECT * FROM run_routes")
  suspend fun getAllRoutes(): List<RunRouteEntity>

  @Insert
  suspend fun insertRoute(route: RunRouteEntity)

  @Insert
  suspend fun insertPoints(points: List<RunRoutePointEntity>)

  @Insert
  suspend fun insertSplits(splits: List<RunSplitEntity>)

  @Query("DELETE FROM run_routes WHERE runId = :runId")
  suspend fun deleteRoute(runId: String)

  @Query(
    """DELETE FROM run_routes
      WHERE runId != :ownerRunId
      AND runId IN (SELECT healthConnectRecordId FROM runs WHERE physicalRunId = :physicalRunId)"""
  )
  suspend fun deleteOtherPhysicalRunRoutes(physicalRunId: String, ownerRunId: String)

  @Transaction
  suspend fun replace(
    route: RunRouteEntity,
    points: List<RunRoutePointEntity>,
    splits: List<RunSplitEntity>,
  ) {
    deleteRoute(route.runId)
    insertRoute(route)
    if (points.isNotEmpty()) insertPoints(points)
    if (splits.isNotEmpty()) insertSplits(splits)
  }

  @Transaction
  suspend fun replaceForPhysicalRun(
    physicalRunId: String,
    route: RunRouteEntity,
    points: List<RunRoutePointEntity>,
    splits: List<RunSplitEntity>,
  ) {
    require(routeStorageOwner(physicalRunId) == route.runId)
    replace(route, points, splits)
    deleteOtherPhysicalRunRoutes(physicalRunId, route.runId)
  }
}
