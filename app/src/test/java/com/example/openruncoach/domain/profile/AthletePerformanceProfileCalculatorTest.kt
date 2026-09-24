package com.example.openruncoach.domain.profile

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetrics
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AthletePerformanceProfileCalculatorTest {
  private val now = Instant.parse("2026-09-24T12:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val calculator = AthletePerformanceProfileCalculator(clock, ZoneOffset.UTC)

  @Test
  fun noRunsProducesEmptyVeryLowConfidenceProfile() {
    val profile = calculate()
    assertEquals(0, profile.totalCanonicalRuns)
    assertEquals(ProfileConfidence.VERY_LOW, profile.profileConfidence)
    assertNull(profile.longestRunLast28Days)
    assertTrue(profile.performanceBenchmarks.all { it.personalBest == null })
  }

  @Test
  fun oneRunProducesLowConfidenceProfile() {
    val run = run("one", 1)
    val profile = calculate(listOf(run), listOf(metrics(run)))
    assertEquals(1, profile.totalCanonicalRuns)
    assertEquals(ProfileConfidence.LOW, profile.profileConfidence)
  }

  @Test
  fun nonCanonicalDuplicateIsExcluded() {
    val canonical = run("canonical", 1, distance = 5_000.0)
    val duplicate = run("duplicate", 1, distance = 5_000.0, canonical = false)
    val profile = calculate(listOf(canonical, duplicate))
    assertEquals(1, profile.totalCanonicalRuns)
    assertEquals(5_000.0, profile.distanceLast7DaysMeters, 0.01)
  }

  @Test
  fun sevenDayVolumeUsesOnlyRunsInsideWindow() {
    val profile = calculate(listOf(
      run("today", 0, 4_000.0, 1_800),
      run("six", 6, 6_000.0, 2_400),
      run("eight", 8, 8_000.0, 3_600),
    ))
    assertEquals(2, profile.runsLast7Days)
    assertEquals(10_000.0, profile.distanceLast7DaysMeters, 0.01)
    assertEquals(4_200L, profile.durationLast7DaysSeconds)
  }

  @Test
  fun twentyEightDayVolumeUsesOnlyRecentCanonicalRuns() {
    val profile = calculate(listOf(
      run("recent", 27, 5_000.0, 2_000),
      run("old", 29, 7_000.0, 3_000),
    ))
    assertEquals(1, profile.runsLast28Days)
    assertEquals(5_000.0, profile.distanceLast28DaysMeters, 0.01)
  }

  @Test
  fun longestRecentRunUsesDistanceAndRetainsDurationAndDate() {
    val longer = run("long", 4, 9_000.0, 4_000)
    val profile = calculate(listOf(run("short", 2, 4_000.0), longer))
    val longest = requireNotNull(profile.longestRunLast28Days)
    assertEquals("long", longest.runId)
    assertEquals(9_000.0, longest.distanceMeters!!, 0.01)
    assertEquals(4_000L, longest.durationSeconds)
  }

  @Test
  fun frequencyUsesRecentFourWeekWindow() {
    val runs = (1..6).map { run("r$it", it.toLong()) }
    val profile = calculate(runs)
    assertEquals(1.5, profile.averageRunsPerWeek, 0.001)
  }

  @Test
  fun consistencyReflectsActiveWeeks() {
    val developing = calculate(listOf(run("a", 1), run("b", 9), run("c", 16)))
    assertEquals(TrainingConsistency.DEVELOPING, developing.consistency)
    assertEquals(3, developing.activeWeeksLast8Weeks)
    val inactive = calculate(listOf(run("old", 80)))
    assertEquals(TrainingConsistency.INACTIVE, inactive.consistency)
  }

  @Test
  fun qualifyingEasyRunBuildsPaceAndHeartRateBaseline() {
    val run = run("easy", 1)
    val profile = calculate(listOf(run), listOf(metrics(run, pace = 420.0, heartRate = 145.0)), listOf(feedback(run)))
    val pace = requireNotNull(profile.typicalEasyPace)
    assertEquals(420.0, pace.representative, 0.01)
    assertEquals(145.0, profile.typicalEasyHeartRate!!.representative, 0.01)
    assertEquals(1, pace.contributingRuns)
  }

  @Test
  fun highRpeRunIsExcludedFromEasyBaseline() {
    val run = run("hard", 1)
    val profile = calculate(listOf(run), listOf(metrics(run)), listOf(feedback(run, rpe = 8)))
    assertNull(profile.typicalEasyPace)
  }

  @Test
  fun missingFeedbackIsExcludedFromEasyBaseline() {
    val run = run("unknown", 1)
    val profile = calculate(listOf(run), listOf(metrics(run)))
    assertNull(profile.typicalEasyPace)
    assertTrue(profile.dataQualityNotes.any { it.contains("no post-run feedback") })
  }

  @Test
  fun cadenceBaselineUsesMedianMovingCadenceAndObservedRange() {
    val runs = listOf(run("a", 3), run("b", 2), run("c", 1))
    val cadences = listOf(158.0, 162.0, 160.0)
    val profile = calculate(
      runs,
      runs.zip(cadences).map { (run, cadence) -> metrics(run, cadence = cadence) },
      runs.map(::feedback),
    )
    val cadence = requireNotNull(profile.typicalEasyCadence)
    assertEquals(160.0, cadence.representative, 0.01)
    assertEquals(158.0, cadence.observedMinimum, 0.01)
    assertEquals(162.0, cadence.observedMaximum, 0.01)
  }

  @Test
  fun missingCadenceDoesNotPreventOtherEasyBaselines() {
    val run = run("easy", 1)
    val profile = calculate(listOf(run), listOf(metrics(run, cadence = null)), listOf(feedback(run)))
    assertNull(profile.typicalEasyCadence)
    assertTrue(profile.typicalEasyPace != null)
  }

  @Test
  fun latestVo2MaxComesFromMostRecentRunWithValue() {
    val older = run("older", 10)
    val latest = run("latest", 1)
    val profile = calculate(listOf(older, latest), listOf(metrics(older, vo2 = 38.0), metrics(latest, vo2 = 39.7)))
    assertEquals(39.7, profile.vo2MaxLatest!!, 0.01)
    assertEquals(38.0, profile.vo2MaxPrevious!!, 0.01)
  }

  @Test
  fun vo2MaxTrendDoesNotOverinterpretSmallChanges() {
    val older = run("older", 10)
    val latest = run("latest", 1)
    val stable = calculate(listOf(older, latest), listOf(metrics(older, vo2 = 39.5), metrics(latest, vo2 = 39.7)))
    assertEquals(FitnessTrend.STABLE, stable.vo2MaxTrend)
    val improving = calculate(listOf(older, latest), listOf(metrics(older, vo2 = 38.5), metrics(latest, vo2 = 39.7)))
    assertEquals(FitnessTrend.IMPROVING, improving.vo2MaxTrend)
  }

  @Test
  fun qualifyingFiveKilometerSessionCreatesObservedBenchmark() {
    val run = run("five-k", 1, 5_030.0, 1_920)
    val profile = calculate(listOf(run))
    val benchmark = profile.benchmark(StandardRunningDistance.FIVE_K)
    assertEquals("five-k", benchmark.personalBest?.runId)
    assertEquals(BenchmarkSource.OBSERVED_SESSION, benchmark.personalBest?.source)
  }

  @Test
  fun fourPointEightKilometersDoesNotQualifyAsFiveK() {
    val profile = calculate(listOf(run("not-five-k", 1, 4_800.0, 1_920)))
    assertNull(profile.benchmark(StandardRunningDistance.FIVE_K).personalBest)
    assertEquals(4_800.0, profile.longestRunLast28Days!!.distanceMeters!!, 0.01)
  }

  @Test
  fun benchmarkKeepsPersonalBestAndMostRecentSeparately() {
    val personalBest = run("pb", 20, 5_000.0, 1_700)
    val recent = run("recent", 1, 5_010.0, 1_900)
    val benchmark = calculate(listOf(personalBest, recent)).benchmark(StandardRunningDistance.FIVE_K)
    assertEquals("pb", benchmark.personalBest?.runId)
    assertEquals("recent", benchmark.mostRecent?.runId)
  }

  @Test
  fun sparseHistoryHasLowProfileConfidenceWithReasons() {
    val run = run("one", 1)
    val profile = calculate(listOf(run))
    assertEquals(ProfileConfidence.LOW, profile.profileConfidence)
    assertTrue(profile.confidenceReasons.any { it.contains("1 runs") })
  }

  @Test
  fun sufficientRecentReliableHistoryHasHighProfileConfidence() {
    val recentRuns = (0..7).map { run("recent-$it", (it * 3).toLong()) }
    val olderWeeks = listOf(run("week-five", 35), run("week-six", 42))
    val allRuns = recentRuns + olderWeeks
    val profile = calculate(
      allRuns,
      allRuns.map { metrics(it) },
      recentRuns.map(::feedback),
    )
    assertEquals(ProfileConfidence.HIGH, profile.profileConfidence)
  }

  @Test
  fun missingMetricsUsesSafeRunSummaryFallbacks() {
    val run = run("fallback", 1, distance = null, duration = 1_800)
    val profile = calculate(listOf(run))
    assertEquals(1_800L, profile.durationLast7DaysSeconds)
    assertEquals(0.0, profile.distanceLast7DaysMeters, 0.01)
    assertNull(profile.typicalEasyPace)
  }

  @Test
  fun fixedClockMakesWindowsAndCalculatedAtDeterministic() {
    val inside = run("inside", 6)
    val outside = run("outside", 8)
    val profile = calculate(listOf(inside, outside))
    assertEquals(1, profile.runsLast7Days)
    assertEquals(now, profile.calculatedAt)
  }

  @Test
  fun recentFeedbackAggregatesCompletionPainAndReadiness() {
    val completed = run("completed", 2)
    val aborted = run("aborted", 1)
    val profile = calculate(
      listOf(completed, aborted),
      feedback = listOf(
        feedback(completed, readiness = 4),
        feedback(aborted, rpe = 8, pain = PainLevel.SIGNIFICANT, completion = CompletionStatus.ABORTED, readiness = 2),
      ),
    )
    assertEquals(5.5, profile.feedback.averageRpe!!, 0.01)
    assertEquals(0.5, profile.feedback.completedFraction!!, 0.01)
    assertEquals(0.5, profile.feedback.abortedFraction!!, 0.01)
    assertEquals(0.5, profile.feedback.significantPainFrequency!!, 0.01)
    assertEquals(3.0, profile.feedback.averageReadiness!!, 0.01)
  }

  @Test
  fun easyPaceTrendUsesOnlyQualifiedEasyRuns() {
    val runs = listOf(run("a", 12), run("b", 9), run("c", 5), run("d", 1))
    val paces = listOf(450.0, 450.0, 400.0, 400.0)
    val profile = calculate(runs, runs.zip(paces).map { metrics(it.first, pace = it.second) }, runs.map(::feedback))
    assertEquals(PaceTrend.FASTER, profile.recentPaceTrend)
  }

  private fun calculate(
    runs: List<RunSummary> = emptyList(),
    metrics: List<RunMetrics> = emptyList(),
    feedback: List<RunFeedback> = emptyList(),
  ) = calculator.calculate(runs, metrics, feedback)

  private fun run(
    id: String,
    daysAgo: Long,
    distance: Double? = 5_000.0,
    duration: Long = 2_100,
    canonical: Boolean = true,
  ): RunSummary {
    val start = now.minusSeconds(daysAgo * 86_400).minusSeconds(3_600)
    return RunSummary(
      healthConnectRecordId = id,
      startTime = start,
      endTime = start.plusSeconds(duration),
      durationSeconds = duration,
      distanceMeters = distance,
      averageHeartRate = 145.0,
      maxHeartRate = 165,
      averagePaceSecondsPerKm = distance?.takeIf { it > 0.0 }?.let { duration * 1_000.0 / it },
      isCanonical = canonical,
    )
  }

  private fun metrics(
    run: RunSummary,
    pace: Double? = run.averagePaceSecondsPerKm,
    heartRate: Double? = 145.0,
    cadence: Double? = 160.0,
    vo2: Double? = null,
  ) = RunMetrics(
    runId = run.healthConnectRecordId,
    startTime = run.startTime,
    endTime = run.endTime,
    durationSeconds = metric(run.durationSeconds),
    distanceMeters = run.distanceMeters?.let(::metric),
    averagePaceSecondsPerKm = pace?.let(::metric),
    averageHeartRate = heartRate?.let(::metric),
    maxHeartRate = metric(165.0),
    vo2Max = vo2?.let(::metric),
    movingAverageCadence = cadence?.let(::metric),
    generatedAt = now,
  )

  private fun feedback(
    run: RunSummary,
    rpe: Int = 3,
    pain: PainLevel = PainLevel.NONE,
    completion: CompletionStatus = CompletionStatus.COMPLETED,
    readiness: Int? = 4,
  ) = RunFeedback(
    id = "feedback-${run.healthConnectRecordId}",
    runId = run.healthConnectRecordId,
    rpe = rpe,
    cardioEffort = CardioEffort.APPROPRIATE,
    muscleFatigue = MuscleFatigue.MILD,
    painLevel = pain,
    completionStatus = completion,
    readiness = readiness,
    notes = null,
    createdAt = run.endTime,
    updatedAt = run.endTime,
  )

  private fun metric(value: Double) = MetricValue(value, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH)
  private fun metric(value: Long) = MetricValue(value, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH)
  private fun AthletePerformanceProfile.benchmark(target: StandardRunningDistance) =
    performanceBenchmarks.first { it.targetDistance == target }
}
