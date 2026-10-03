package com.example.openruncoach.domain.profile

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricVerification
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.RunContinuityEvaluator
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.metrics.metricValues
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import com.example.openruncoach.domain.workout.WorkoutExecutionStatus
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max

data class BenchmarkTolerance(
  val minimumMeters: Double = 30.0,
  val fraction: Double = 0.015,
  val highConfidenceFraction: Double = 0.005,
  val mediumConfidenceFraction: Double = 0.010,
)

data class ProfileCalculationConfig(
  val benchmarkTolerance: BenchmarkTolerance = BenchmarkTolerance(),
  val vo2MaxStableTolerance: Double = 0.5,
  val easyPaceStableFraction: Double = 0.03,
  val heartRateStableBpm: Double = 3.0,
  val cadenceStableSpm: Double = 2.0,
)

class AthletePerformanceProfileCalculator(
  private val clock: Clock = Clock.systemUTC(),
  private val zoneId: ZoneId = ZoneId.systemDefault(),
  private val config: ProfileCalculationConfig = ProfileCalculationConfig(),
) {
  fun calculate(
    runs: List<RunSummary>,
    metrics: List<RunMetrics>,
    feedback: List<RunFeedback>,
    executionResults: List<WorkoutExecutionResult> = emptyList(),
  ): AthletePerformanceProfile {
    val now = clock.instant()
    val canonicalRuns = runs.filter { it.isCanonical && it.relevance == com.example.openruncoach.domain.RunRelevance.ACTIVE }
      .filter { !it.startTime.isAfter(now) }
      .distinctBy(RunSummary::healthConnectRecordId)
      .sortedBy(RunSummary::startTime)
    val metricsByRun = metrics.associateBy(RunMetrics::runId)
    val feedbackByRun = feedback.associateBy(RunFeedback::runId)
    val executionByRun = executionResults.associateBy(WorkoutExecutionResult::actualRunId)
    val recent7 = canonicalRuns.inWindow(now.minus(Duration.ofDays(7)), now)
    val recent28 = canonicalRuns.inWindow(now.minus(Duration.ofDays(28)), now)
    val currentWeekStart = now.atZone(zoneId).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val activeWeeks = activeWeekStarts(canonicalRuns, currentWeekStart.minusWeeks(7), currentWeekStart)
    val completedWeeks = (4L downTo 1L).map { weeksAgo ->
      weeklyVolume(currentWeekStart.minusWeeks(weeksAgo), canonicalRuns, metricsByRun)
    }
    val easyRuns = recent28.mapNotNull { run ->
      val runFeedback = feedbackByRun[run.healthConnectRecordId] ?: return@mapNotNull null
      val qualifies = qualifiesAsEasy(
        run,
        metricsByRun[run.healthConnectRecordId],
        runFeedback,
        executionByRun[run.healthConnectRecordId],
      )
      if (!qualifies) return@mapNotNull null
      EasyRun(run, metricsByRun[run.healthConnectRecordId])
    }
    val easyPaces = easyRuns.mapNotNull { it.paceSecondsPerKm }
    val easyHeartRates = easyRuns.mapNotNull { it.averageHeartRate }
    val easyCadences = easyRuns.mapNotNull { it.cadence }
    val recentHeartRates = recent28.mapNotNull { runValue(it, metricsByRun[it.healthConnectRecordId]).averageHeartRate }
    val vo2Values = canonicalRuns.mapNotNull { run ->
      metricsByRun[run.healthConnectRecordId]?.vo2Max?.value?.let { run.startTime to it }
    }
    val feedbackRecent = recent28.mapNotNull { feedbackByRun[it.healthConnectRecordId] }
    val consistency = consistency(activeWeeks.size)
    val confidence = confidence(canonicalRuns, recent28, activeWeeks.size, metricsByRun, feedbackByRun)

    return AthletePerformanceProfile(
      totalCanonicalRuns = canonicalRuns.size,
      runsLast7Days = recent7.size,
      runsLast28Days = recent28.size,
      activeWeeksLast8Weeks = activeWeeks.size,
      distanceLast7DaysMeters = recent7.sumOf { runValue(it, metricsByRun[it.healthConnectRecordId]).distanceMeters ?: 0.0 },
      durationLast7DaysSeconds = recent7.sumOf { runValue(it, metricsByRun[it.healthConnectRecordId]).durationSeconds },
      distanceLast28DaysMeters = recent28.sumOf { runValue(it, metricsByRun[it.healthConnectRecordId]).distanceMeters ?: 0.0 },
      durationLast28DaysSeconds = recent28.sumOf { runValue(it, metricsByRun[it.healthConnectRecordId]).durationSeconds },
      completedWeeklyVolumes = completedWeeks,
      averageWeeklyDistanceLast4WeeksMeters = completedWeeks.map { it.distanceMeters }.average(),
      averageWeeklyDurationLast4WeeksSeconds = completedWeeks.map { it.durationSeconds }.average().toLong(),
      longestRunLast28Days = longestRun(recent28, metricsByRun),
      averageRunsPerWeek = recent28.size / 4.0,
      currentRunningStreakWeeks = currentStreak(canonicalRuns, currentWeekStart),
      consistency = consistency.first,
      consistencyReasons = consistency.second,
      performanceBenchmarks = benchmarks(canonicalRuns, metricsByRun),
      recentPaceTrend = paceTrend(easyRuns),
      typicalEasyPace = baseline(easyPaces, "completed easy runs with valid pace"),
      typicalEasyHeartRate = baseline(easyHeartRates, "completed easy runs with heart-rate data"),
      recentAverageHeartRate = recentHeartRates.averageOrNull(),
      recentHeartRateTrend = valueTrend(easyRuns.mapNotNull { it.averageHeartRate }, config.heartRateStableBpm),
      vo2MaxLatest = vo2Values.lastOrNull()?.second,
      vo2MaxPrevious = vo2Values.dropLast(1).lastOrNull()?.second,
      vo2MaxTrend = fitnessTrend(vo2Values.map { it.second }),
      typicalEasyCadence = baseline(easyCadences, "completed easy runs with unified cadence data"),
      recentCadenceTrend = valueTrend(easyCadences, config.cadenceStableSpm),
      feedback = feedbackAggregates(feedbackRecent, executionByRun),
      calculatedAt = now,
      profileConfidence = confidence.first,
      confidenceReasons = confidence.second,
      dataQualityNotes = dataQualityNotes(recent28, metricsByRun, feedbackByRun),
      recentContinuity = continuitySummary(recent28, metricsByRun),
    )
  }

  private fun continuitySummary(
    runs: List<RunSummary>,
    metrics: Map<String, RunMetrics>,
  ): RecentContinuitySummary {
    val evaluator = RunContinuityEvaluator()
    val evidence = runs.map { run -> run to evaluator.evaluate(metrics[run.healthConnectRecordId]) }
    val longestContinuous = evidence.asSequence()
      .filter { it.second == RunContinuityEvidence.CONTINUOUS }
      .mapNotNull { (run, _) -> runValue(run, metrics[run.healthConnectRecordId]).distanceMeters }
      .maxOrNull()
    return RecentContinuitySummary(
      continuousRuns = evidence.count { it.second == RunContinuityEvidence.CONTINUOUS },
      interruptedRuns = evidence.count { it.second == RunContinuityEvidence.INTERRUPTED },
      unknownRuns = evidence.count { it.second == RunContinuityEvidence.UNKNOWN },
      longestContinuousDistanceMeters = longestContinuous,
    )
  }

  private fun qualifiesAsEasy(
    run: RunSummary,
    metrics: RunMetrics?,
    feedback: RunFeedback,
    execution: WorkoutExecutionResult?,
  ): Boolean {
    val values = runValue(run, metrics)
    return effectiveCompletion(feedback, execution) == CompletionStatus.COMPLETED &&
      feedback.rpe <= 4 &&
      feedback.cardioEffort in setOf(CardioEffort.EASY, CardioEffort.APPROPRIATE) &&
      feedback.painLevel != PainLevel.SIGNIFICANT &&
      values.durationSeconds > 0 && values.distanceMeters?.let { it.isFinite() && it > 0.0 } == true &&
      values.paceSecondsPerKm?.let { it.isFinite() && it > 0.0 } == true
  }

  private fun benchmarks(
    runs: List<RunSummary>,
    metrics: Map<String, RunMetrics>,
  ): List<DistanceBenchmark> = StandardRunningDistance.entries.map { target ->
    val candidates = runs.mapNotNull { run ->
      val values = runValue(run, metrics[run.healthConnectRecordId])
      val distance = values.distanceMeters ?: return@mapNotNull null
      val duration = values.durationSeconds
      if (duration <= 0 || !qualifiesForBenchmark(distance, target)) return@mapNotNull null
      PerformanceBenchmark(
        targetDistance = target,
        actualDistanceMeters = distance,
        durationSeconds = duration,
        paceSecondsPerKm = duration * 1_000.0 / distance,
        date = run.startTime,
        runId = run.healthConnectRecordId,
        confidence = benchmarkConfidence(distance, target),
      )
    }
    DistanceBenchmark(
      targetDistance = target,
      personalBest = candidates.minWithOrNull(compareBy<PerformanceBenchmark> { it.durationSeconds }.thenBy { it.date }),
      mostRecent = candidates.maxWithOrNull(compareBy<PerformanceBenchmark> { it.date }.thenBy { it.runId }),
    )
  }

  private fun qualifiesForBenchmark(distance: Double, target: StandardRunningDistance): Boolean {
    val tolerance = max(config.benchmarkTolerance.minimumMeters, target.meters * config.benchmarkTolerance.fraction)
    return distance.isFinite() && abs(distance - target.meters) <= tolerance
  }

  private fun benchmarkConfidence(distance: Double, target: StandardRunningDistance): ProfileConfidence {
    val difference = abs(distance - target.meters) / target.meters
    return when {
      difference <= config.benchmarkTolerance.highConfidenceFraction -> ProfileConfidence.HIGH
      difference <= config.benchmarkTolerance.mediumConfidenceFraction -> ProfileConfidence.MEDIUM
      else -> ProfileConfidence.LOW
    }
  }

  private fun weeklyVolume(
    start: LocalDate,
    runs: List<RunSummary>,
    metrics: Map<String, RunMetrics>,
  ): WeeklyTrainingVolume {
    val end = start.plusWeeks(1)
    val weekRuns = runs.filter {
      val date = it.startTime.atZone(zoneId).toLocalDate()
      !date.isBefore(start) && date.isBefore(end)
    }
    return WeeklyTrainingVolume(
      start,
      weekRuns.size,
      weekRuns.sumOf { runValue(it, metrics[it.healthConnectRecordId]).distanceMeters ?: 0.0 },
      weekRuns.sumOf { runValue(it, metrics[it.healthConnectRecordId]).durationSeconds },
    )
  }

  private fun activeWeekStarts(runs: List<RunSummary>, first: LocalDate, last: LocalDate): Set<LocalDate> =
    runs.map { it.startTime.atZone(zoneId).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
      .filter { !it.isBefore(first) && !it.isAfter(last) }
      .toSet()

  private fun currentStreak(runs: List<RunSummary>, currentWeekStart: LocalDate): Int {
    val active = runs.map {
      it.startTime.atZone(zoneId).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }.toSet()
    var week = currentWeekStart
    var count = 0
    while (week in active) {
      count++
      week = week.minusWeeks(1)
    }
    return count
  }

  private fun consistency(activeWeeks: Int): Pair<TrainingConsistency, List<String>> = when {
    activeWeeks == 0 -> TrainingConsistency.INACTIVE to listOf("No active weeks in the last 8 weeks")
    activeWeeks <= 2 -> TrainingConsistency.INCONSISTENT to listOf("$activeWeeks of the last 8 weeks were active")
    activeWeeks <= 5 -> TrainingConsistency.DEVELOPING to listOf("$activeWeeks of the last 8 weeks were active")
    else -> TrainingConsistency.CONSISTENT to listOf("$activeWeeks of the last 8 weeks were active")
  }

  private fun confidence(
    allRuns: List<RunSummary>,
    recentRuns: List<RunSummary>,
    activeWeeks: Int,
    metrics: Map<String, RunMetrics>,
    feedback: Map<String, RunFeedback>,
  ): Pair<ProfileConfidence, List<String>> {
    val count = recentRuns.size
    val feedbackCount = recentRuns.count { it.healthConnectRecordId in feedback }
    val hrCount = recentRuns.count { runValue(it, metrics[it.healthConnectRecordId]).averageHeartRate != null }
    val paceCount = recentRuns.count { runValue(it, metrics[it.healthConnectRecordId]).paceSecondsPerKm != null }
    val reliableCount = recentRuns.count { run ->
      val snapshot = metrics[run.healthConnectRecordId] ?: return@count true
      snapshot.metricValues().values.none {
        it.confidence == MetricConfidence.LOW || it.verification == MetricVerification.CONFLICT
      }
    }
    val feedbackFraction = feedbackCount.fractionOf(count)
    val hrFraction = hrCount.fractionOf(count)
    val paceFraction = paceCount.fractionOf(count)
    val reliableFraction = reliableCount.fractionOf(count)
    val level = when {
      count >= 8 && activeWeeks >= 6 && feedbackFraction >= 0.75 && hrFraction >= 0.75 &&
        paceFraction >= 0.9 && reliableFraction >= 0.8 -> ProfileConfidence.HIGH
      count >= 4 && activeWeeks >= 3 && paceFraction >= 0.6 &&
        (feedbackFraction >= 0.4 || hrFraction >= 0.5) -> ProfileConfidence.MEDIUM
      allRuns.isNotEmpty() -> ProfileConfidence.LOW
      else -> ProfileConfidence.VERY_LOW
    }
    return level to listOf(
      "$count runs available in the last 28 days",
      "$activeWeeks weeks represented in the last 8 weeks",
      "Feedback available for $feedbackCount of $count recent runs",
      "Heart rate available for $hrCount of $count recent runs",
      "Pace available for $paceCount of $count recent runs",
      "$reliableCount of $count recent runs have no low-confidence or conflicting metrics",
    )
  }

  private fun dataQualityNotes(
    recentRuns: List<RunSummary>,
    metrics: Map<String, RunMetrics>,
    feedback: Map<String, RunFeedback>,
  ): List<String> = buildList {
    val missingFeedback = recentRuns.count { it.healthConnectRecordId !in feedback }
    val missingHeartRate = recentRuns.count { runValue(it, metrics[it.healthConnectRecordId]).averageHeartRate == null }
    val missingPace = recentRuns.count { runValue(it, metrics[it.healthConnectRecordId]).paceSecondsPerKm == null }
    val conflicts = recentRuns.sumOf { run ->
      metrics[run.healthConnectRecordId]?.metricValues()?.values?.count {
        it.verification == MetricVerification.CONFLICT
      } ?: 0
    }
    if (recentRuns.isEmpty()) add("No canonical runs are available in the last 28 days")
    if (missingFeedback > 0) add("$missingFeedback recent runs have no post-run feedback")
    if (missingHeartRate > 0) add("$missingHeartRate recent runs have no average heart rate")
    if (missingPace > 0) add("$missingPace recent runs have no valid pace")
    if (conflicts > 0) add("$conflicts unified metrics have provider conflicts")
  }

  private fun longestRun(runs: List<RunSummary>, metrics: Map<String, RunMetrics>): LongestRecentRun? {
    val run = runs.maxWithOrNull(
      compareBy<RunSummary> { runValue(it, metrics[it.healthConnectRecordId]).distanceMeters ?: -1.0 }
        .thenBy { runValue(it, metrics[it.healthConnectRecordId]).durationSeconds }
    ) ?: return null
    val values = runValue(run, metrics[run.healthConnectRecordId])
    return LongestRecentRun(run.healthConnectRecordId, run.startTime, values.distanceMeters, values.durationSeconds)
  }

  private fun baseline(values: List<Double>, reason: String): ProfileBaseline? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    return ProfileBaseline(
      representative = sorted.median(),
      observedMinimum = sorted.first(),
      observedMaximum = sorted.last(),
      contributingRuns = sorted.size,
      confidence = baselineConfidence(sorted.size),
      reasons = listOf("Based on ${sorted.size} $reason", "Median represents the typical value"),
    )
  }

  private fun baselineConfidence(count: Int): ProfileConfidence = when {
    count >= 5 -> ProfileConfidence.HIGH
    count >= 3 -> ProfileConfidence.MEDIUM
    else -> ProfileConfidence.LOW
  }

  private fun paceTrend(easyRuns: List<EasyRun>): PaceTrend {
    val values = easyRuns.mapNotNull { it.paceSecondsPerKm }
    if (values.size < 4) return PaceTrend.INSUFFICIENT_DATA
    val (older, newer) = values.splitChronologically()
    val oldMedian = older.median()
    val newMedian = newer.median()
    val difference = (newMedian - oldMedian) / oldMedian
    return when {
      difference < -config.easyPaceStableFraction -> PaceTrend.FASTER
      difference > config.easyPaceStableFraction -> PaceTrend.SLOWER
      else -> PaceTrend.STABLE
    }
  }

  private fun valueTrend(values: List<Double>, stableTolerance: Double): ValueTrend {
    if (values.size < 3) return ValueTrend.INSUFFICIENT_DATA
    val difference = values.last() - values[values.lastIndex - 1]
    return when {
      difference > stableTolerance -> ValueTrend.INCREASING
      difference < -stableTolerance -> ValueTrend.DECREASING
      else -> ValueTrend.STABLE
    }
  }

  private fun fitnessTrend(values: List<Double>): FitnessTrend {
    if (values.size < 2) return FitnessTrend.INSUFFICIENT_DATA
    val difference = values.last() - values[values.lastIndex - 1]
    return when {
      difference > config.vo2MaxStableTolerance -> FitnessTrend.IMPROVING
      difference < -config.vo2MaxStableTolerance -> FitnessTrend.DECLINING
      else -> FitnessTrend.STABLE
    }
  }

  private fun feedbackAggregates(
    feedback: List<RunFeedback>,
    executionByRun: Map<String, WorkoutExecutionResult>,
  ): FeedbackAggregates {
    val count = feedback.size
    fun fraction(predicate: (RunFeedback) -> Boolean) = if (count == 0) null else feedback.count(predicate).toDouble() / count
    return FeedbackAggregates(
      feedbackCount = count,
      averageRpe = feedback.map { it.rpe.toDouble() }.averageOrNull(),
      completedFraction = fraction { effectiveCompletion(it, executionByRun[it.runId]) == CompletionStatus.COMPLETED },
      partialFraction = fraction { effectiveCompletion(it, executionByRun[it.runId]) == CompletionStatus.PARTIAL },
      abortedFraction = fraction { effectiveCompletion(it, executionByRun[it.runId]) == CompletionStatus.ABORTED },
      painFrequency = fraction { it.painLevel != PainLevel.NONE },
      significantPainFrequency = fraction { it.painLevel == PainLevel.SIGNIFICANT },
      averageReadiness = feedback.mapNotNull { it.readiness?.toDouble() }.averageOrNull(),
    )
  }

  private fun effectiveCompletion(
    feedback: RunFeedback,
    execution: WorkoutExecutionResult?,
  ): CompletionStatus = when (execution?.status) {
    WorkoutExecutionStatus.COMPLETED_AS_PLANNED,
    WorkoutExecutionStatus.COMPLETED_WITH_MINOR_DEVIATION,
    WorkoutExecutionStatus.COMPLETED_WITH_PRESCRIPTION_DEVIATION -> CompletionStatus.COMPLETED
    WorkoutExecutionStatus.PARTIALLY_COMPLETED -> CompletionStatus.PARTIAL
    WorkoutExecutionStatus.ABORTED -> CompletionStatus.ABORTED
    null -> feedback.completionStatus
  }

  private fun runValue(run: RunSummary, metrics: RunMetrics?) = RunValues(
    durationSeconds = metrics?.durationSeconds?.value ?: run.durationSeconds,
    distanceMeters = metrics?.distanceMeters?.value ?: run.distanceMeters,
    paceSecondsPerKm = metrics?.averagePaceSecondsPerKm?.value ?: run.averagePaceSecondsPerKm,
    averageHeartRate = metrics?.averageHeartRate?.value ?: run.averageHeartRate,
    cadence = metrics?.movingAverageCadence?.value ?: metrics?.averageCadence?.value,
  )

  private fun List<RunSummary>.inWindow(start: Instant, end: Instant) = filter {
    !it.startTime.isBefore(start) && !it.startTime.isAfter(end)
  }

  private data class RunValues(
    val durationSeconds: Long,
    val distanceMeters: Double?,
    val paceSecondsPerKm: Double?,
    val averageHeartRate: Double?,
    val cadence: Double?,
  )

  private inner class EasyRun(val run: RunSummary, metrics: RunMetrics?) {
    private val values = runValue(run, metrics)
    val paceSecondsPerKm = values.paceSecondsPerKm
    val averageHeartRate = values.averageHeartRate
    val cadence = values.cadence
  }
}

private fun Int.fractionOf(total: Int): Double = if (total == 0) 0.0 else toDouble() / total
private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
private fun List<Double>.median(): Double = if (size % 2 == 1) this[size / 2] else (this[size / 2 - 1] + this[size / 2]) / 2.0
private fun List<Double>.splitChronologically(): Pair<List<Double>, List<Double>> {
  val split = size / 2
  return take(split) to drop(split)
}
