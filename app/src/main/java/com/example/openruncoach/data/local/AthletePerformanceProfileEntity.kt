package com.example.openruncoach.data.local

import androidx.room.Entity

@Entity(tableName = "athlete_performance_profile", primaryKeys = ["id"])
data class AthletePerformanceProfileEntity(
  val id: String,
  val version: Int,
  val calculatedAtEpochMillis: Long,
  val totalCanonicalRuns: Int,
  val runsLast7Days: Int,
  val runsLast28Days: Int,
  val activeWeeksLast8Weeks: Int,
  val distanceLast7DaysMeters: Double,
  val durationLast7DaysSeconds: Long,
  val distanceLast28DaysMeters: Double,
  val durationLast28DaysSeconds: Long,
  val averageWeeklyDistanceLast4WeeksMeters: Double,
  val averageWeeklyDurationLast4WeeksSeconds: Long,
  val longestRunId: String?,
  val longestRunDateEpochMillis: Long?,
  val longestRunDistanceMeters: Double?,
  val longestRunDurationSeconds: Long?,
  val averageRunsPerWeek: Double,
  val currentRunningStreakWeeks: Int,
  val consistency: String,
  val recentPaceTrend: String,
  val easyPaceRepresentative: Double?,
  val easyPaceMinimum: Double?,
  val easyPaceMaximum: Double?,
  val easyPaceRunCount: Int,
  val easyPaceConfidence: String?,
  val easyHeartRateRepresentative: Double?,
  val easyHeartRateMinimum: Double?,
  val easyHeartRateMaximum: Double?,
  val easyHeartRateRunCount: Int,
  val easyHeartRateConfidence: String?,
  val recentAverageHeartRate: Double?,
  val recentHeartRateTrend: String,
  val vo2MaxLatest: Double?,
  val vo2MaxPrevious: Double?,
  val vo2MaxTrend: String,
  val easyCadenceRepresentative: Double?,
  val easyCadenceMinimum: Double?,
  val easyCadenceMaximum: Double?,
  val easyCadenceRunCount: Int,
  val easyCadenceConfidence: String?,
  val recentCadenceTrend: String,
  val feedbackCount: Int,
  val averageRpe: Double?,
  val completedFraction: Double?,
  val partialFraction: Double?,
  val abortedFraction: Double?,
  val painFrequency: Double?,
  val significantPainFrequency: Double?,
  val averageReadiness: Double?,
  val profileConfidence: String,
)

@Entity(tableName = "athlete_profile_weekly_volume", primaryKeys = ["profileId", "weekStartEpochDay"])
data class AthleteProfileWeeklyVolumeEntity(
  val profileId: String,
  val weekStartEpochDay: Long,
  val runCount: Int,
  val distanceMeters: Double,
  val durationSeconds: Long,
)

@Entity(tableName = "athlete_profile_benchmarks", primaryKeys = ["profileId", "targetDistance", "resultType"])
data class AthleteProfileBenchmarkEntity(
  val profileId: String,
  val targetDistance: String,
  val resultType: String,
  val actualDistanceMeters: Double,
  val durationSeconds: Long,
  val paceSecondsPerKm: Double,
  val dateEpochMillis: Long,
  val runId: String,
  val confidence: String,
  val source: String,
)

@Entity(tableName = "athlete_profile_explanations", primaryKeys = ["profileId", "category", "position"])
data class AthleteProfileExplanationEntity(
  val profileId: String,
  val category: String,
  val position: Int,
  val text: String,
)
