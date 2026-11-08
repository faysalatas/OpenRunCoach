package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One athlete-owned setting per local environment; no plan/goal/provider foreign key. */
@Entity(tableName = "training_availability", primaryKeys = ["id"])
data class TrainingAvailabilityEntity(
  val id: Int = 1,
  val availableDaysMask: Int,
  val preferredDaysMask: Int,
)

@Dao
interface TrainingAvailabilityDao {
  @Query("SELECT * FROM training_availability WHERE id = 1")
  fun observe(): Flow<TrainingAvailabilityEntity?>

  @Query("SELECT * FROM training_availability WHERE id = 1")
  suspend fun get(): TrainingAvailabilityEntity?

  @Upsert suspend fun save(value: TrainingAvailabilityEntity)

  @Query("DELETE FROM training_availability WHERE id = 1")
  suspend fun reset()
}
