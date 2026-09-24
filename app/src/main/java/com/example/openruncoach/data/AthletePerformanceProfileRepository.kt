package com.example.openruncoach.data

import com.example.openruncoach.data.local.AthletePerformanceProfileDao
import com.example.openruncoach.data.local.AthletePerformanceProfileEntity
import com.example.openruncoach.data.local.AthletePerformanceProfileSnapshot
import com.example.openruncoach.data.local.AthleteProfileBenchmarkEntity
import com.example.openruncoach.data.local.AthleteProfileExplanationEntity
import com.example.openruncoach.data.local.AthleteProfileWeeklyVolumeEntity
import com.example.openruncoach.data.local.CURRENT_PROFILE_ID
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import com.example.openruncoach.domain.profile.BenchmarkSource
import com.example.openruncoach.domain.profile.DistanceBenchmark
import com.example.openruncoach.domain.profile.FeedbackAggregates
import com.example.openruncoach.domain.profile.FitnessTrend
import com.example.openruncoach.domain.profile.LongestRecentRun
import com.example.openruncoach.domain.profile.PaceTrend
import com.example.openruncoach.domain.profile.PerformanceBenchmark
import com.example.openruncoach.domain.profile.ProfileBaseline
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.profile.StandardRunningDistance
import com.example.openruncoach.domain.profile.TrainingConsistency
import com.example.openruncoach.domain.profile.ValueTrend
import com.example.openruncoach.domain.profile.WeeklyTrainingVolume
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AthletePerformanceProfileRepository(
  private val database: OpenRunCoachDatabase,
  private val calculator: AthletePerformanceProfileCalculator = AthletePerformanceProfileCalculator(),
) {
  private val dao: AthletePerformanceProfileDao = database.athletePerformanceProfileDao()
  private val recalculationMutex = Mutex()

  fun observeProfile(): Flow<AthletePerformanceProfile?> =
    dao.observeProfile().map { it?.toDomain() }

  suspend fun getProfile(): AthletePerformanceProfile? = dao.getProfile()?.toDomain()

  suspend fun recalculate(): AthletePerformanceProfile = recalculationMutex.withLock {
    val profile = calculator.calculate(
      runs = database.runDao().getCanonicalRuns().map { it.toRunSummary() },
      metrics = database.runMetricsDao().getAllRunMetrics().mapNotNull { it.toDomain() },
      feedback = database.runFeedbackDao().getAllFeedback().map { it.toDomain() },
    )
    dao.replace(profile.toSnapshot())
    profile
  }
}

private enum class ExplanationCategory { CONSISTENCY, CONFIDENCE, DATA_QUALITY, EASY_PACE, EASY_HEART_RATE, EASY_CADENCE }
private enum class BenchmarkResultType { PERSONAL_BEST, MOST_RECENT }

private fun AthletePerformanceProfile.toSnapshot(): AthletePerformanceProfileSnapshot {
  fun baselineValues(baseline: ProfileBaseline?) = listOf(
    baseline?.representative,
    baseline?.observedMinimum,
    baseline?.observedMaximum,
  )
  val pace = baselineValues(typicalEasyPace)
  val heartRate = baselineValues(typicalEasyHeartRate)
  val cadence = baselineValues(typicalEasyCadence)
  val profileEntity = AthletePerformanceProfileEntity(
    id = CURRENT_PROFILE_ID,
    version = PROFILE_VERSION,
    calculatedAtEpochMillis = calculatedAt.toEpochMilli(),
    totalCanonicalRuns = totalCanonicalRuns,
    runsLast7Days = runsLast7Days,
    runsLast28Days = runsLast28Days,
    activeWeeksLast8Weeks = activeWeeksLast8Weeks,
    distanceLast7DaysMeters = distanceLast7DaysMeters,
    durationLast7DaysSeconds = durationLast7DaysSeconds,
    distanceLast28DaysMeters = distanceLast28DaysMeters,
    durationLast28DaysSeconds = durationLast28DaysSeconds,
    averageWeeklyDistanceLast4WeeksMeters = averageWeeklyDistanceLast4WeeksMeters,
    averageWeeklyDurationLast4WeeksSeconds = averageWeeklyDurationLast4WeeksSeconds,
    longestRunId = longestRunLast28Days?.runId,
    longestRunDateEpochMillis = longestRunLast28Days?.date?.toEpochMilli(),
    longestRunDistanceMeters = longestRunLast28Days?.distanceMeters,
    longestRunDurationSeconds = longestRunLast28Days?.durationSeconds,
    averageRunsPerWeek = averageRunsPerWeek,
    currentRunningStreakWeeks = currentRunningStreakWeeks,
    consistency = consistency.name,
    recentPaceTrend = recentPaceTrend.name,
    easyPaceRepresentative = pace[0],
    easyPaceMinimum = pace[1],
    easyPaceMaximum = pace[2],
    easyPaceRunCount = typicalEasyPace?.contributingRuns ?: 0,
    easyPaceConfidence = typicalEasyPace?.confidence?.name,
    easyHeartRateRepresentative = heartRate[0],
    easyHeartRateMinimum = heartRate[1],
    easyHeartRateMaximum = heartRate[2],
    easyHeartRateRunCount = typicalEasyHeartRate?.contributingRuns ?: 0,
    easyHeartRateConfidence = typicalEasyHeartRate?.confidence?.name,
    recentAverageHeartRate = recentAverageHeartRate,
    recentHeartRateTrend = recentHeartRateTrend.name,
    vo2MaxLatest = vo2MaxLatest,
    vo2MaxPrevious = vo2MaxPrevious,
    vo2MaxTrend = vo2MaxTrend.name,
    easyCadenceRepresentative = cadence[0],
    easyCadenceMinimum = cadence[1],
    easyCadenceMaximum = cadence[2],
    easyCadenceRunCount = typicalEasyCadence?.contributingRuns ?: 0,
    easyCadenceConfidence = typicalEasyCadence?.confidence?.name,
    recentCadenceTrend = recentCadenceTrend.name,
    feedbackCount = feedback.feedbackCount,
    averageRpe = feedback.averageRpe,
    completedFraction = feedback.completedFraction,
    partialFraction = feedback.partialFraction,
    abortedFraction = feedback.abortedFraction,
    painFrequency = feedback.painFrequency,
    significantPainFrequency = feedback.significantPainFrequency,
    averageReadiness = feedback.averageReadiness,
    profileConfidence = profileConfidence.name,
  )
  val benchmarkEntities = performanceBenchmarks.flatMap { benchmark ->
    listOfNotNull(
      benchmark.personalBest?.toEntity(BenchmarkResultType.PERSONAL_BEST),
      benchmark.mostRecent?.toEntity(BenchmarkResultType.MOST_RECENT),
    )
  }
  val explanations = buildList {
    addExplanations(ExplanationCategory.CONSISTENCY, consistencyReasons)
    addExplanations(ExplanationCategory.CONFIDENCE, confidenceReasons)
    addExplanations(ExplanationCategory.DATA_QUALITY, dataQualityNotes)
    addExplanations(ExplanationCategory.EASY_PACE, typicalEasyPace?.reasons.orEmpty())
    addExplanations(ExplanationCategory.EASY_HEART_RATE, typicalEasyHeartRate?.reasons.orEmpty())
    addExplanations(ExplanationCategory.EASY_CADENCE, typicalEasyCadence?.reasons.orEmpty())
  }
  return AthletePerformanceProfileSnapshot(
    profileEntity,
    completedWeeklyVolumes.map {
      AthleteProfileWeeklyVolumeEntity(CURRENT_PROFILE_ID, it.weekStart.toEpochDay(), it.runCount, it.distanceMeters, it.durationSeconds)
    },
    benchmarkEntities,
    explanations,
  )
}

private fun MutableList<AthleteProfileExplanationEntity>.addExplanations(
  category: ExplanationCategory,
  reasons: List<String>,
) {
  reasons.forEachIndexed { index, reason ->
    add(AthleteProfileExplanationEntity(CURRENT_PROFILE_ID, category.name, index, reason))
  }
}

private fun PerformanceBenchmark.toEntity(type: BenchmarkResultType) = AthleteProfileBenchmarkEntity(
  CURRENT_PROFILE_ID,
  targetDistance.name,
  type.name,
  actualDistanceMeters,
  durationSeconds,
  paceSecondsPerKm,
  date.toEpochMilli(),
  runId,
  confidence.name,
  source.name,
)

private fun AthletePerformanceProfileSnapshot.toDomain(): AthletePerformanceProfile {
  val explanationsByCategory = explanations.sortedBy { it.position }.groupBy { it.category }
  fun reasons(category: ExplanationCategory) = explanationsByCategory[category.name].orEmpty().map { it.text }
  fun baseline(
    representative: Double?,
    minimum: Double?,
    maximum: Double?,
    count: Int,
    confidence: String?,
    category: ExplanationCategory,
  ): ProfileBaseline? {
    if (representative == null || minimum == null || maximum == null || confidence == null) return null
    return ProfileBaseline(representative, minimum, maximum, count, ProfileConfidence.valueOf(confidence), reasons(category))
  }
  val benchmarkGroups = benchmarks.groupBy { it.targetDistance }
  val longest = if (
    profile.longestRunId != null && profile.longestRunDateEpochMillis != null && profile.longestRunDurationSeconds != null
  ) {
    LongestRecentRun(
      profile.longestRunId,
      Instant.ofEpochMilli(profile.longestRunDateEpochMillis),
      profile.longestRunDistanceMeters,
      profile.longestRunDurationSeconds,
    )
  } else null
  return AthletePerformanceProfile(
    totalCanonicalRuns = profile.totalCanonicalRuns,
    runsLast7Days = profile.runsLast7Days,
    runsLast28Days = profile.runsLast28Days,
    activeWeeksLast8Weeks = profile.activeWeeksLast8Weeks,
    distanceLast7DaysMeters = profile.distanceLast7DaysMeters,
    durationLast7DaysSeconds = profile.durationLast7DaysSeconds,
    distanceLast28DaysMeters = profile.distanceLast28DaysMeters,
    durationLast28DaysSeconds = profile.durationLast28DaysSeconds,
    completedWeeklyVolumes = weeklyVolumes.sortedBy { it.weekStartEpochDay }.map {
      WeeklyTrainingVolume(LocalDate.ofEpochDay(it.weekStartEpochDay), it.runCount, it.distanceMeters, it.durationSeconds)
    },
    averageWeeklyDistanceLast4WeeksMeters = profile.averageWeeklyDistanceLast4WeeksMeters,
    averageWeeklyDurationLast4WeeksSeconds = profile.averageWeeklyDurationLast4WeeksSeconds,
    longestRunLast28Days = longest,
    averageRunsPerWeek = profile.averageRunsPerWeek,
    currentRunningStreakWeeks = profile.currentRunningStreakWeeks,
    consistency = TrainingConsistency.valueOf(profile.consistency),
    consistencyReasons = reasons(ExplanationCategory.CONSISTENCY),
    performanceBenchmarks = StandardRunningDistance.entries.map { target ->
      val results = benchmarkGroups[target.name].orEmpty().associateBy { it.resultType }
      DistanceBenchmark(
        target,
        results[BenchmarkResultType.PERSONAL_BEST.name]?.toDomain(),
        results[BenchmarkResultType.MOST_RECENT.name]?.toDomain(),
      )
    },
    recentPaceTrend = PaceTrend.valueOf(profile.recentPaceTrend),
    typicalEasyPace = baseline(profile.easyPaceRepresentative, profile.easyPaceMinimum, profile.easyPaceMaximum, profile.easyPaceRunCount, profile.easyPaceConfidence, ExplanationCategory.EASY_PACE),
    typicalEasyHeartRate = baseline(profile.easyHeartRateRepresentative, profile.easyHeartRateMinimum, profile.easyHeartRateMaximum, profile.easyHeartRateRunCount, profile.easyHeartRateConfidence, ExplanationCategory.EASY_HEART_RATE),
    recentAverageHeartRate = profile.recentAverageHeartRate,
    recentHeartRateTrend = ValueTrend.valueOf(profile.recentHeartRateTrend),
    vo2MaxLatest = profile.vo2MaxLatest,
    vo2MaxPrevious = profile.vo2MaxPrevious,
    vo2MaxTrend = FitnessTrend.valueOf(profile.vo2MaxTrend),
    typicalEasyCadence = baseline(profile.easyCadenceRepresentative, profile.easyCadenceMinimum, profile.easyCadenceMaximum, profile.easyCadenceRunCount, profile.easyCadenceConfidence, ExplanationCategory.EASY_CADENCE),
    recentCadenceTrend = ValueTrend.valueOf(profile.recentCadenceTrend),
    feedback = FeedbackAggregates(
      profile.feedbackCount,
      profile.averageRpe,
      profile.completedFraction,
      profile.partialFraction,
      profile.abortedFraction,
      profile.painFrequency,
      profile.significantPainFrequency,
      profile.averageReadiness,
    ),
    calculatedAt = Instant.ofEpochMilli(profile.calculatedAtEpochMillis),
    profileConfidence = ProfileConfidence.valueOf(profile.profileConfidence),
    confidenceReasons = reasons(ExplanationCategory.CONFIDENCE),
    dataQualityNotes = reasons(ExplanationCategory.DATA_QUALITY),
  )
}

private fun AthleteProfileBenchmarkEntity.toDomain() = PerformanceBenchmark(
  targetDistance = StandardRunningDistance.valueOf(targetDistance),
  actualDistanceMeters = actualDistanceMeters,
  durationSeconds = durationSeconds,
  paceSecondsPerKm = paceSecondsPerKm,
  date = Instant.ofEpochMilli(dateEpochMillis),
  runId = runId,
  confidence = ProfileConfidence.valueOf(confidence),
  source = BenchmarkSource.valueOf(source),
)

private const val PROFILE_VERSION = 1
