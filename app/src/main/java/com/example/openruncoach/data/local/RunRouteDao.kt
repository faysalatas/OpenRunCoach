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

@Dao
interface RunRouteDao {
  @Transaction
  @Query("SELECT * FROM run_routes WHERE runId = :runId LIMIT 1")
  fun observeRoute(runId: String): Flow<RunRouteWithPointsAndSplits?>

  @Transaction
  @Query("SELECT * FROM run_routes WHERE runId = :runId LIMIT 1")
  suspend fun getRoute(runId: String): RunRouteWithPointsAndSplits?

  @Insert
  suspend fun insertRoute(route: RunRouteEntity)

  @Insert
  suspend fun insertPoints(points: List<RunRoutePointEntity>)

  @Insert
  suspend fun insertSplits(splits: List<RunSplitEntity>)

  @Query("DELETE FROM run_routes WHERE runId = :runId")
  suspend fun deleteRoute(runId: String)

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
}
