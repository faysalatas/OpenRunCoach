package com.example.openruncoach.domain.profile

import java.time.Instant
import java.time.LocalDate

enum class ProfileConfidence { VERY_LOW, LOW, MEDIUM, HIGH }

enum class TrainingConsistency { INACTIVE, INCONSISTENT, DEVELOPING, CONSISTENT }

enum class PaceTrend { FASTER, STABLE, SLOWER, INSUFFICIENT_DATA }

enum class ValueTrend { INCREASING, STABLE, DECREASING, INSUFFICIENT_DATA }

enum class FitnessTrend { IMPROVING, STABLE, DECLINING, INSUFFICIENT_DATA }

enum class BenchmarkSource { OBSERVED_SESSION, OBSERVED_SEGMENT, RACE_RESULT, MANUAL_RESULT }

enum class StandardRunningDistance(val meters: Double, val displayName: String) {
  ONE_K(1_000.0, "1K"),
  FIVE_K(5_000.0, "5K"),
  TEN_K(10_000.0, "10K"),
  HALF_MARATHON(21_097.5, "Half Marathon"),
  MARATHON(42_195.0, "Marathon"),
}

data class ProfileBaseline(
  val representative: Double,
  val observedMinimum: Double,
  val observedMaximum: Double,
  val contributingRuns: Int,
  val confidence: ProfileConfidence,
  val reasons: List<String>,
)

data class PerformanceBenchmark(
  val targetDistance: StandardRunningDistance,
  val actualDistanceMeters: Double,
  val durationSeconds: Long,
  val paceSecondsPerKm: Double,
  val date: Instant,
  val runId: String,
  val confidence: ProfileConfidence,
  val source: BenchmarkSource = BenchmarkSource.OBSERVED_SESSION,
)

data class DistanceBenchmark(
  val targetDistance: StandardRunningDistance,
  val personalBest: PerformanceBenchmark?,
  val mostRecent: PerformanceBenchmark?,
)

data class WeeklyTrainingVolume(
  val weekStart: LocalDate,
  val runCount: Int,
  val distanceMeters: Double,
  val durationSeconds: Long,
)

data class LongestRecentRun(
  val runId: String,
  val date: Instant,
  val distanceMeters: Double?,
  val durationSeconds: Long,
)

data class FeedbackAggregates(
  val feedbackCount: Int,
  val averageRpe: Double?,
  val completedFraction: Double?,
  val partialFraction: Double?,
  val abortedFraction: Double?,
  val painFrequency: Double?,
  val significantPainFrequency: Double?,
  val averageReadiness: Double?,
)

data class AthletePerformanceProfile(
  val totalCanonicalRuns: Int,
  val runsLast7Days: Int,
  val runsLast28Days: Int,
  val activeWeeksLast8Weeks: Int,
  val distanceLast7DaysMeters: Double,
  val durationLast7DaysSeconds: Long,
  val distanceLast28DaysMeters: Double,
  val durationLast28DaysSeconds: Long,
  val completedWeeklyVolumes: List<WeeklyTrainingVolume>,
  val averageWeeklyDistanceLast4WeeksMeters: Double,
  val averageWeeklyDurationLast4WeeksSeconds: Long,
  val longestRunLast28Days: LongestRecentRun?,
  val averageRunsPerWeek: Double,
  val currentRunningStreakWeeks: Int,
  val consistency: TrainingConsistency,
  val consistencyReasons: List<String>,
  val performanceBenchmarks: List<DistanceBenchmark>,
  val recentPaceTrend: PaceTrend,
  val typicalEasyPace: ProfileBaseline?,
  val typicalEasyHeartRate: ProfileBaseline?,
  val recentAverageHeartRate: Double?,
  val recentHeartRateTrend: ValueTrend,
  val vo2MaxLatest: Double?,
  val vo2MaxPrevious: Double?,
  val vo2MaxTrend: FitnessTrend,
  val typicalEasyCadence: ProfileBaseline?,
  val recentCadenceTrend: ValueTrend,
  val feedback: FeedbackAggregates,
  val calculatedAt: Instant,
  val profileConfidence: ProfileConfidence,
  val confidenceReasons: List<String>,
  val dataQualityNotes: List<String>,
)
