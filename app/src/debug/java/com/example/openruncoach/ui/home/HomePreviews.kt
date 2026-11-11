package com.example.openruncoach.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.openruncoach.domain.*
import com.example.openruncoach.domain.coaching.CoachingAction
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.goal.*
import com.example.openruncoach.domain.plan.*
import com.example.openruncoach.domain.profile.*
import com.example.openruncoach.domain.route.*
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.workout.*
import com.example.openruncoach.theme.OpenRunCoachTheme
import com.example.openruncoach.ui.components.OpenRunBottomBar
import com.example.openruncoach.ui.workout.listSummary
import java.time.*

/** Synthetic, debug-only design fixtures. Never used by production HomeScreen or repositories. */
internal object HomePreviewData {
  private val zone = ZoneId.of("Europe/Berlin")
  fun clock(kind: HomeDayState): Clock = Clock.fixed(Instant.parse(when (kind) {
    HomeDayState.RestDay -> "2026-11-07T08:00:00Z"
    HomeDayState.BeforeRun -> "2026-11-08T08:00:00Z"
    HomeDayState.AfterRun -> "2026-11-08T11:00:00Z"
  }), zone)

  fun workout(id: String, date: LocalDate, type: WorkoutType, meters: Double = 5_000.0,
    pace: PaceTarget? = PaceTarget(405.0, 450.0), rpe: RpeTarget? = RpeTarget(3, 4)): StructuredWorkout = PlannedWorkout(
    id, date, type, type.displayName().replace(" Run", " run"), type.defaultPurpose(),
    if (type == WorkoutType.REST) emptyList() else listOf(SingleSegmentBlock("$id-block", 0,
      WorkoutSegment("$id-segment", 0, SegmentCategory.EASY, "Run", DistanceCompletion(meters),
        pace ?: NoIntensityTarget, listOfNotNull(rpe)))),
    "Keep the effort comfortable and leave room to recover.", emptyList(), Instant.parse("2026-10-25T08:00:00Z"),
  )

  fun run(id: String, date: String, meters: Double? = 5_000.0, duration: Long = 1_925): RunSummary {
    val start = Instant.parse("${date}T09:46:12Z")
    return RunSummary(id, start, start.plusSeconds(duration), duration, meters, 146.0, 158,
      meters?.takeIf { it > 0 }?.let { duration * 1_000.0 / it })
  }

  fun state(kind: HomeDayState): HomeUiState {
    val clock = clock(kind)
    val today = LocalDate.now(clock)
    val oldLong = run("fixture-long-previous", "2026-11-01", 7_000.0, 2_940)
    val monday = run("fixture-mon", "2026-11-02")
    val wednesday = run("fixture-wed", "2026-11-04", 3_000.0, 1_215)
    val friday = run("fixture-fri", "2026-11-06")
    val todayRun = run("fixture-today", "2026-11-08", 8_020.0, 3_348).takeIf { kind == HomeDayState.AfterRun }
    val runs = listOfNotNull(todayRun, friday, wednesday, monday, oldLong)
    val long = workout("fixture-long", LocalDate.parse("2026-11-08"), WorkoutType.LONG_RUN, 8_000.0)
    fun entry(workout: StructuredWorkout, run: RunSummary? = null) = PlannedWorkoutEntry(WeeklyWorkoutSlot.WEEKEND,
      workout, workout.explanation, executionResult = run?.let {
        WorkoutExecutionResult(workout.id, it.healthConnectRecordId, WorkoutExecutionStatus.COMPLETED_AS_PLANNED,
          WorkoutComparisonBasis.ORIGINAL_PRESCRIPTION, workout.let(WorkoutEstimateCalculator::calculate).estimatedTotalDistanceMeters,
          null, it.distanceMeters, it.durationSeconds, null, "Fixture: completed as prescribed.", it.endTime)
      })
    val week = TrainingWeek(0, LocalDate.parse("2026-11-02"), listOf(
      entry(workout("fixture-mon-plan", LocalDate.parse("2026-11-02"), WorkoutType.EASY_RUN), monday),
      entry(workout("fixture-wed-plan", LocalDate.parse("2026-11-04"), WorkoutType.RECOVERY_RUN, 3_000.0), wednesday),
      entry(workout("fixture-fri-plan", LocalDate.parse("2026-11-06"), WorkoutType.EASY_RUN), friday),
      entry(long, todayRun),
    ), 21_000.0, null, WeekLoadClassification.NORMAL, "Fixture training week.", listOf("Fixture progression."))
    val following = TrainingWeek(1, LocalDate.parse("2026-11-09"), listOf(
      entry(workout("fixture-next-easy", LocalDate.parse("2026-11-09"), WorkoutType.EASY_RUN)),
      entry(workout("fixture-next-recovery", LocalDate.parse("2026-11-11"), WorkoutType.RECOVERY_RUN, 3_000.0)),
    ), 8_000.0, null, WeekLoadClassification.NORMAL, "Fixture following week.", listOf("Fixture recovery."))
    val profile = AthletePerformanceProfileCalculator(clock, zone).calculate(runs, emptyList(), emptyList()).copy(
      typicalEasyPace = baseline(387.0), typicalEasyHeartRate = baseline(146.0), typicalEasyCadence = baseline(168.0),
      recentPaceTrend = PaceTrend.FASTER,
      feedback = FeedbackAggregates(3, 3.0, 1.0, 0.0, 0.0, 0.0, 0.0, if (kind == HomeDayState.AfterRun) 3.0 else 4.0),
    )
    val goal = TrainingGoal("fixture-goal", GoalDistance.TEN_K, null, GoalType.COMPLETE_DISTANCE, null, clock.instant(),
      GoalStatus.ACTIVE, StartingPerformanceSnapshot(null, null, 13_000.0, 5_000, 7_000.0, 2_940,
        3.0, 400.0, 146.0, ProfileConfidence.MEDIUM, clock.instant()), ProfileConfidence.MEDIUM,
      GoalAssessment(GoalAssessmentLevel.PLAUSIBLE, listOf("Fixture goal assessment.")))
    val plan = TrainingPlan("fixture-plan", goal.id, clock.instant(), week.startDate, null, listOf(week, following),
      emptyList(), 5, "Fixture plan.", ProfileConfidence.MEDIUM, TrainingPlanStatus.ACTIVE)
    val upcoming = plan.weeks.flatMap { it.workouts }.filter { it.executionResult == null && it.workout.date >= today }
      .map { e -> val summary = e.workout.listSummary(); HomeWorkoutSummary(e.workout.name, e.workout.type,
        e.workout.date, e.workout.date == today, listOfNotNull(summary.distanceLabel, summary.paceLabel),
        summary.distanceLabel, summary.paceLabel, null, workoutId = e.workout.id) }
    val types = mapOf(oldLong.healthConnectRecordId to WorkoutType.LONG_RUN,
      monday.healthConnectRecordId to WorkoutType.EASY_RUN, wednesday.healthConnectRecordId to WorkoutType.RECOVERY_RUN,
      friday.healthConnectRecordId to WorkoutType.EASY_RUN) + if (todayRun != null) mapOf(todayRun.healthConnectRecordId to WorkoutType.LONG_RUN) else emptyMap()
    val latest = runs.first()
    val points = listOf(0.0 to 0.0, .0001 to .0005, .0008 to .0008, .0010 to .0002, .0003 to -.0001, 0.0 to 0.0)
      .mapIndexed { i, p -> RunRoutePoint(i, latest.startTime.plusSeconds(i * 60L), p.first, p.second) }
    val sources = HomeTodaySources(runs, plan, goal, null, null, types, profile,
      RunRouteData(RunRoute(latest.healthConnectRecordId, MetricSource.HEALTH_CONNECT, null, MetricConfidence.HIGH, points), emptyList()),
      RunMetrics(latest.healthConnectRecordId, latest.startTime, latest.endTime,
        MetricValue(latest.durationSeconds, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
        averageCadence = MetricValue(168.0, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
        elevationGainMeters = MetricValue(42.0, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH), generatedAt = clock.instant()))
    return HomeUiState(isLoading = false, latestRun = latest, nextWorkout = upcoming.firstOrNull(), upcomingWorkouts = upcoming,
      hasActivePlan = true, activeGoal = HomeGoalSummary("Complete 10K"), todaySources = sources,
      coachingDecision = CoachingDecision(CoachingAction.MAINTAIN, 0,
        listOf("${runs.count { it.startTime.atZone(zone).toLocalDate() >= week.startDate }} runs are recorded this week.", "Keep your next run comfortable and leave room to recover."), clock.instant()),
      progress = HomeProgressSummary(listOf(412.0, 400.0, 393.0, 387.0).mapIndexed { i, pace ->
        HomeProgressWeek(week.startDate.minusWeeks(3L - i), pace, 13_000.0)
      }, 412.0, 387.0, 25),
    )
  }

  private fun baseline(value: Double) = ProfileBaseline(value, value, value, 3, ProfileConfidence.MEDIUM, listOf("Synthetic preview fixture."))
}

@Composable
internal fun TodayPreview(kind: HomeDayState, scroll: ScrollState = rememberScrollState(), actions: HomeActions = HomeActions()) {
  Column(Modifier.fillMaxSize()) {
    HomeContent(homePresentation(HomePreviewData.state(kind), HomePreviewData.clock(kind)), actions,
      Modifier.weight(1f), scroll)
    OpenRunBottomBar("home") {}
  }
}

@Preview(name = "Today before · light", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable private fun TodayBeforeLight() { OpenRunCoachTheme { TodayPreview(HomeDayState.BeforeRun) } }
@Preview(name = "Today before · dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun TodayBeforeDark() { OpenRunCoachTheme { TodayPreview(HomeDayState.BeforeRun) } }
@Preview(name = "Today after · light", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable private fun TodayAfterLight() { OpenRunCoachTheme { TodayPreview(HomeDayState.AfterRun) } }
@Preview(name = "Today after · dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun TodayAfterDark() { OpenRunCoachTheme { TodayPreview(HomeDayState.AfterRun) } }
@Preview(name = "Today rest · light", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable private fun TodayRestLight() { OpenRunCoachTheme { TodayPreview(HomeDayState.RestDay) } }
@Preview(name = "Today rest · dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun TodayRestDark() { OpenRunCoachTheme { TodayPreview(HomeDayState.RestDay) } }
@Preview(name = "Today before · 130%", widthDp = 390, heightDp = 844, fontScale = 1.3f)
@Composable private fun TodayBefore130() { OpenRunCoachTheme { TodayPreview(HomeDayState.BeforeRun) } }
@Preview(name = "Today after · 200%", widthDp = 390, heightDp = 844, fontScale = 2f)
@Composable private fun TodayAfter200() { OpenRunCoachTheme { TodayPreview(HomeDayState.AfterRun) } }
