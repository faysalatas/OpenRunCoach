package com.example.openruncoach.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class AthletePerformanceProfileSnapshot(
  @Embedded val profile: AthletePerformanceProfileEntity,
  @Relation(parentColumn = "id", entityColumn = "profileId")
  val weeklyVolumes: List<AthleteProfileWeeklyVolumeEntity>,
  @Relation(parentColumn = "id", entityColumn = "profileId")
  val benchmarks: List<AthleteProfileBenchmarkEntity>,
  @Relation(parentColumn = "id", entityColumn = "profileId")
  val explanations: List<AthleteProfileExplanationEntity>,
)

@Dao
interface AthletePerformanceProfileDao {
  @Transaction
  @Query("SELECT * FROM athlete_performance_profile WHERE id = :id LIMIT 1")
  fun observeProfile(id: String = CURRENT_PROFILE_ID): Flow<AthletePerformanceProfileSnapshot?>

  @Upsert
  suspend fun upsertProfile(profile: AthletePerformanceProfileEntity)

  @Upsert
  suspend fun upsertWeeklyVolumes(volumes: List<AthleteProfileWeeklyVolumeEntity>)

  @Upsert
  suspend fun upsertBenchmarks(benchmarks: List<AthleteProfileBenchmarkEntity>)

  @Upsert
  suspend fun upsertExplanations(explanations: List<AthleteProfileExplanationEntity>)

  @Query("DELETE FROM athlete_profile_weekly_volume WHERE profileId = :profileId")
  suspend fun deleteWeeklyVolumes(profileId: String)

  @Query("DELETE FROM athlete_profile_benchmarks WHERE profileId = :profileId")
  suspend fun deleteBenchmarks(profileId: String)

  @Query("DELETE FROM athlete_profile_explanations WHERE profileId = :profileId")
  suspend fun deleteExplanations(profileId: String)

  @Transaction
  suspend fun replace(snapshot: AthletePerformanceProfileSnapshot) {
    upsertProfile(snapshot.profile)
    deleteWeeklyVolumes(snapshot.profile.id)
    deleteBenchmarks(snapshot.profile.id)
    deleteExplanations(snapshot.profile.id)
    upsertWeeklyVolumes(snapshot.weeklyVolumes)
    upsertBenchmarks(snapshot.benchmarks)
    upsertExplanations(snapshot.explanations)
  }
}

const val CURRENT_PROFILE_ID = "current"
