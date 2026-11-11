package com.example.openruncoach.ui.home

import com.example.openruncoach.domain.*
import com.example.openruncoach.domain.coaching.CoachingAction
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.plan.PlannedWorkoutEntry
import com.example.openruncoach.domain.plan.TrainingPlan
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import com.example.openruncoach.domain.profile.PaceTrend
import com.example.openruncoach.domain.route.RunRouteData
import com.example.openruncoach.domain.route.RunRoutePoint
import com.example.openruncoach.domain.workout.*
import com.example.openruncoach.ui.common.formatDuration
import com.example.openruncoach.ui.components.BarState
import com.example.openruncoach.ui.components.ChartBar
import com.example.openruncoach.ui.components.ChartPoint
import com.example.openruncoach.ui.workout.listSummary
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Read-only sources already owned by the app. This projection never writes/reconciles training state. */
data class HomeTodaySources(
  val canonicalRuns: List<RunSummary> = emptyList(),
  val plan: TrainingPlan? = null,
  val goal: TrainingGoal? = null,
  val latestFeedback: RunFeedback? = null,
  val latestResponse: WorkoutResponse? = null,
  val completedWorkoutTypesByRunId: Map<String, WorkoutType> = emptyMap(),
  val profile: AthletePerformanceProfile? = null,
  val route: RunRouteData? = null,
  val latestMetrics: RunMetrics? = null,
)

internal enum class HomeDayState { BeforeRun, AfterRun, RestDay }
internal data class HomeFact(val value: String, val label: String)
internal data class HomeChip(val label: String, val good: Boolean = false)
internal data class HomeGoalBar(val label: String, val value: String, val fraction: Float)
internal data class HomeGoalPresentation(val title: String, val subtitle: String?, val progress: Float?, val bars: List<HomeGoalBar>)
internal data class HomeWeekPresentation(
  val dateRange: String, val distance: String?, val plannedDistance: String?,
  val bars: List<ChartBar>, val description: String, val chips: List<HomeChip>,
)
internal data class HomeStat(
  val title: String, val value: String, val unit: String, val delta: String? = null,
  val good: Boolean = false, val points: List<ChartPoint> = emptyList(), val readiness: Float? = null,
)
internal data class HomeRunPresentation(
  val id: String, val title: String, val date: String, val distance: String?, val pace: String?,
  val asPlanned: Boolean, val route: List<RunRoutePoint>,
)
internal data class HomeNextUp(val id: String?, val weekday: String, val day: String, val name: String, val meta: String?)
internal data class HomePresentation(
  val dayState: HomeDayState, val date: String, val eyebrow: String?, val title: String,
  val subtitle: String?, val reason: String?, val heroMetrics: List<HomeFact>,
  val workoutId: String?, val runId: String?, val quickFacts: List<HomeFact>,
  val chips: List<HomeChip>, val checkoutRunId: String?, val coach: String?,
  val goal: HomeGoalPresentation?, val week: HomeWeekPresentation?, val form: List<HomeStat>,
  val lastRun: HomeRunPresentation?, val nextUp: List<HomeNextUp>,
  val loading: Boolean, val error: String?,
)

/** State selection and missing-data rules only; never chooses or adjusts a prescription. */
internal fun homePresentation(state: HomeUiState, clock: Clock, locale: Locale = Locale.getDefault()): HomePresentation {
  val today = LocalDate.now(clock)
  val zone = clock.zone
  val sources = state.todaySources
  val runs = sources?.canonicalRuns.orEmpty().filter {
    it.isCanonical && it.relevance == RunRelevance.ACTIVE && !it.endTime.isAfter(clock.instant())
  }
  val latest = state.latestRun?.takeIf {
    it.isCanonical && it.relevance == RunRelevance.ACTIVE && !it.endTime.isAfter(clock.instant())
  }
  val todayRun = latest?.takeIf { it.endTime.atZone(zone).toLocalDate() == today }
  val workout = state.nextWorkout?.takeIf { it.date == today && it.type != WorkoutType.REST }
  val dayState = when {
    todayRun != null -> HomeDayState.AfterRun
    workout != null -> HomeDayState.BeforeRun
    else -> HomeDayState.RestDay
  }
  val entries = sources?.plan?.weeks.orEmpty().flatMap { it.workouts }
  val planned = workout?.workoutId?.let { id -> entries.firstOrNull { it.workout.id == id } }
  val completedEntry = latest?.let { run -> entries.firstOrNull { it.executionResult?.actualRunId == run.healthConnectRecordId } }
  // An execution records which prescription was compared. Do not label an original target as adjusted.
  val completedWorkout = completedEntry?.let {
    if (it.executionResult?.comparisonBasis == WorkoutComparisonBasis.ORIGINAL_PRESCRIPTION) it.originalWorkout else it.workout
  }
  val feedback = sources?.latestFeedback?.takeIf { latest != null && it.belongsTo(latest) }
  val response = sources?.latestResponse?.takeIf {
    latest != null && (it.runId == latest.healthConnectRecordId || it.physicalRunId == latest.physicalRunId)
  }
  val asPlanned = feedback != null && response?.adherence == PrescriptionAdherence.AS_PLANNED
  val runName = completedWorkout?.name ?: latest?.let {
    sources?.completedWorkoutTypesByRunId?.get(it.healthConnectRecordId)?.displayName()
  } ?: "Run"
  val next = state.upcomingWorkouts.filter { it.date > today && it.type != WorkoutType.REST }.take(2)
  val ready = !state.isLoading && state.errorMessage == null
  val reason = when (dayState) {
    HomeDayState.BeforeRun -> workout?.adaptationReason.clean() ?: planned?.generationReason.clean() ?: planned?.workout?.explanation.clean()
    HomeDayState.RestDay -> entries.firstOrNull { it.workout.date == today && it.workout.type == WorkoutType.REST }
      ?.workout?.explanation.clean() ?: "No workout is scheduled today."
    HomeDayState.AfterRun -> null
  }
  val facts = when (dayState) {
    HomeDayState.BeforeRun -> quickFacts(workout!!, planned, runs, sources, today, zone, locale)
    HomeDayState.AfterRun -> afterRunFacts(todayRun!!, sources?.latestMetrics, response.takeIf { feedback != null }, locale)
    HomeDayState.RestDay -> restDayFacts(sources, runs, today, zone, locale)
  }
  val previousRuns = todayRun?.let { run -> runs.filter { it.physicalRunId != run.physicalRunId && it.startTime < run.startTime } }.orEmpty()
  val newLongest = todayRun?.distanceMeters.validPositive()?.let { distance ->
    previousRuns.isNotEmpty() && previousRuns.all { it.distanceMeters.validPositive() != null } &&
      distance > previousRuns.maxOf { requireNotNull(it.distanceMeters) }
  } == true
  val chips = if (dayState == HomeDayState.AfterRun) buildList {
    if (asPlanned) add(HomeChip("As planned", true))
    completedWorkout?.listSummary()?.paceLabel.clean()?.let { add(HomeChip(it)) }
    if (newLongest) add(HomeChip("New longest run", true))
  } else emptyList()
  val timeOfDay = when (clock.instant().atZone(zone).hour) { in 5..11 -> "Morning"; in 12..17 -> "Afternoon"; else -> "Evening" }
  val date = today.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) + if (ready) " · " +
    when (dayState) { HomeDayState.AfterRun -> "Done"; HomeDayState.RestDay -> "Rest day"; else -> timeOfDay } else ""
  return HomePresentation(
    dayState = dayState,
    date = date.uppercase(locale),
    eyebrow = if (!ready) null else when (dayState) {
      HomeDayState.BeforeRun -> "TODAY'S RUN · ${workout!!.type.displayName().removeSuffix(" Run").uppercase(locale)}"
      HomeDayState.AfterRun -> "DONE · ${todayRun!!.endTime.atZone(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))}"
      HomeDayState.RestDay -> "TODAY"
    },
    title = if (!ready) "Today" else when (dayState) {
      HomeDayState.BeforeRun -> workout!!.name
      HomeDayState.AfterRun -> runName
      HomeDayState.RestDay -> "Rest day"
    },
    subtitle = if (ready && dayState == HomeDayState.BeforeRun) listOfNotNull(
      planned?.workout?.listSummary()?.repetitionLabel.clean(),
      planned?.workout?.let(WorkoutEstimateCalculator::calculate)?.estimatedTotalDistanceMeters.validPositive()
        ?.let { compactDistance(it, locale) } ?: workout?.distanceLabel.clean(), workout?.paceLabel.clean(),
    ).joinToString(" · ").clean() else null,
    reason = reason.takeIf { ready },
    heroMetrics = if (ready && todayRun != null) buildList {
      todayRun.distanceMeters.validPositive()?.let { add(HomeFact(km(it, locale, 2), "km")) }
      todayRun.durationSeconds.takeIf { it > 0 }?.let { add(HomeFact(formatDuration(it), "time")) }
      todayRun.averagePaceSecondsPerKm.validPositive()?.let { add(HomeFact(pace(it), "/km")) }
    } else emptyList(),
    workoutId = (if (dayState == HomeDayState.BeforeRun) workout?.workoutId else next.firstOrNull()?.workoutId).takeIf { ready },
    runId = todayRun?.healthConnectRecordId.takeIf { ready },
    quickFacts = facts.takeIf { ready }.orEmpty(),
    chips = chips.takeIf { ready }.orEmpty(),
    checkoutRunId = todayRun?.healthConnectRecordId?.takeIf { ready && sources != null && feedback == null },
    coach = homeCoachCopy(state.coachingDecision, feedback).takeIf { ready },
    goal = if (ready) goalPresentation(state, sources, runs, today, zone, locale) else null,
    week = if (ready) weekPresentation(sources, runs, today, zone, locale) else null,
    form = if (ready) formPresentation(state, locale) else emptyList(),
    lastRun = latest?.takeIf { ready }?.let { run ->
      val route = sources?.route?.route?.takeIf { it.runId == run.healthConnectRecordId || it.runId == run.physicalRunId }
        ?.points.orEmpty().filter { it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
      HomeRunPresentation(run.healthConnectRecordId, runName,
        run.startTime.atZone(zone).format(DateTimeFormatter.ofPattern("EEE d MMM", locale)),
        run.distanceMeters.validPositive()?.let { "${km(it, locale, 2)} km" },
        run.averagePaceSecondsPerKm.validPositive()?.let { "${pace(it)} /km" }, asPlanned,
        route.takeIf { it.size >= 2 }.orEmpty())
    },
    nextUp = if (ready) next.map { HomeNextUp(it.workoutId,
      it.date.format(DateTimeFormatter.ofPattern("EEE", locale)).uppercase(locale),
      DateTimeFormatter.ofPattern("d", locale).format(it.date), it.name, it.details.filter(String::isNotBlank).joinToString(" · ").clean()) } else emptyList(),
    loading = state.isLoading, error = state.errorMessage,
  )
}

/** Human copy of an existing decision; the engine remains the only recommendation selector. */
private fun homeCoachCopy(decision: CoachingDecision?, feedback: RunFeedback?): String? {
  decision ?: return null
  val observation = decision.reasons.firstOrNull(String::isNotBlank)?.let { reason ->
    when (reason) {
      "Workout completed" -> "You completed your run."
      "Workout completed as planned" -> "You completed your workout as planned."
      "Workout partially completed" -> "You completed part of your workout."
      "Workout aborted" -> "You stopped your workout."
      else -> reason.trim().let { if (it.last() in ".!?" ) it else "$it." }
    }
  }
  val pain = when (feedback?.painLevel) {
    PainLevel.MILD -> "Mild pain reported."
    PainLevel.SIGNIFICANT -> "Significant pain reported."
    else -> null
  }
  // These implications preserve the existing Coach presentation's action wording.
  val implication = when (decision.action) {
    CoachingAction.RECOVERY -> "Prioritize recovery before progressing the next session."
    CoachingAction.REDUCE -> "Reduce the next training load."
    CoachingAction.MAINTAIN -> "Maintain the current training load."
    CoachingAction.SMALL_PROGRESS -> "Keep the next progression small."
    CoachingAction.PROGRESS -> "Progress the next training load."
  }
  return listOfNotNull(observation, pain, implication).distinct().joinToString(" ")
}

private fun quickFacts(workout: HomeWorkoutSummary, entry: PlannedWorkoutEntry?, runs: List<RunSummary>,
  sources: HomeTodaySources?, today: LocalDate, zone: java.time.ZoneId, locale: Locale): List<HomeFact> = buildList {
  val estimatedDistance = entry?.workout?.let(WorkoutEstimateCalculator::calculate)?.estimatedTotalDistanceMeters.validPositive()
  (estimatedDistance?.let { compactDistance(it, locale) } ?: workout.distanceLabel.clean())?.let { add(HomeFact(it, "distance")) }
  val prescription = entry?.workout?.listSummary()
  entry?.workout?.let(WorkoutEstimateCalculator::calculate)?.let { estimate ->
    val minimum = estimate.estimatedMinimumDurationSeconds?.takeIf { it > 0 }
    val maximum = estimate.estimatedMaximumDurationSeconds?.takeIf { it > 0 }
    if (minimum != null && maximum != null) {
      val value = if (minimum % 60L == 0L && maximum % 60L == 0L) {
        if (minimum == maximum) "${minimum / 60}′" else "${minimum / 60}–${maximum / 60}′"
      } else prescription?.durationLabel
      value.clean()?.let { add(HomeFact(it, "est. time")) }
    }
  }
  prescription?.rpeLabel.clean()?.let { add(HomeFact(it.removePrefix("RPE "), "effort /10")) }
  val distance = entry?.workout?.let(WorkoutEstimateCalculator::calculate)?.estimatedTotalDistanceMeters.validPositive()
  val previous = runs.filter { workout.type == WorkoutType.LONG_RUN && it.startTime.atZone(zone).toLocalDate() < today &&
    sources?.completedWorkoutTypesByRunId?.get(it.healthConnectRecordId) == WorkoutType.LONG_RUN }.maxByOrNull { it.startTime }
  previous?.distanceMeters.validPositive()?.let { baseline -> distance?.let { current ->
    val difference = current - baseline
    val value = (if (difference > 0) "+" else if (difference < 0) "−" else "") + number(abs(difference) / 1_000, locale)
    add(HomeFact("$value km", "vs last ${workout.type.displayName().removeSuffix(" Run").lowercase(locale)}"))
  } }
}

/** Only complementary facts: hero distance/time/pace never appear in this card. */
private fun afterRunFacts(run: RunSummary, metrics: RunMetrics?, response: WorkoutResponse?, locale: Locale): List<HomeFact> {
  val matched = metrics?.takeIf { it.runId == run.healthConnectRecordId && it.startTime == run.startTime && it.endTime == run.endTime }
  return buildList {
    (run.averageHeartRate.validPositive() ?: matched?.averageHeartRate?.value.validPositive())?.let {
      add(HomeFact("${number(it, locale)} bpm", "avg heart rate"))
    }
    val movingCadence = matched?.movingAverageCadence?.value.validPositive()
    (movingCadence ?: matched?.averageCadence?.value.validPositive())?.let {
      add(HomeFact("${number(it, locale)} spm", if (movingCadence != null) "moving cadence" else "cadence"))
    }
    matched?.elevationGainMeters?.value?.takeIf { it.isFinite() && it >= 0 }?.let {
      add(HomeFact("${number(it, locale)} m", "elevation gain"))
    }
    when (response?.effortResponse) {
      EffortResponse.BELOW_TARGET -> "Below target"
      EffortResponse.ON_TARGET -> "On target"
      EffortResponse.ABOVE_TARGET -> "Above target"
      else -> null
    }?.let { add(HomeFact(it, "effort vs plan")) }
  }.takeIf { it.size >= 2 }.orEmpty()
}

private fun restDayFacts(sources: HomeTodaySources?, runs: List<RunSummary>, today: LocalDate,
  zone: java.time.ZoneId, locale: Locale): List<HomeFact> {
  sources ?: return emptyList()
  val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  val sunday = monday.plusDays(6)
  val weekRuns = runs.filter { it.startTime.atZone(zone).toLocalDate() in monday..sunday }
  val actualDistance = completeDistance(weekRuns)
  val plannedDistances = sources.plan?.weeks.orEmpty().flatMap { it.workouts }
    .filter { it.workout.date in monday..sunday && it.workout.type != WorkoutType.REST }
    .map { WorkoutEstimateCalculator.calculate(it.workout).estimatedTotalDistanceMeters }
  val plannedDistance = plannedDistances.takeIf { it.isNotEmpty() && it.all { d -> d.validPositive() != null } }
    ?.sumOf { requireNotNull(it) }
  return buildList {
    actualDistance?.let { add(HomeFact(km(it, locale), "km done")) }
    add(HomeFact(String.format(locale, "%d", weekRuns.size), "runs"))
    if (actualDistance != null && plannedDistance != null) {
      add(HomeFact("${number((plannedDistance - actualDistance).coerceAtLeast(0.0) / 1_000, locale)} km", "to go"))
    }
    sources.profile?.feedback?.takeIf { it.feedbackCount > 0 }?.averageReadiness
      ?.takeIf { it.isFinite() && it in 1.0..5.0 }?.let { add(HomeFact("${number(it, locale)} / 5", "readiness")) }
  }.takeIf { it.size >= 2 }.orEmpty()
}

private fun weekPresentation(sources: HomeTodaySources?, runs: List<RunSummary>, today: LocalDate,
  zone: java.time.ZoneId, locale: Locale): HomeWeekPresentation? {
  sources ?: return null
  val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  val sunday = monday.plusDays(6)
  val weekRuns = runs.filter { it.startTime.atZone(zone).toLocalDate() in monday..sunday }
  val weekEntries = sources.plan?.weeks.orEmpty().flatMap { it.workouts }
    .filter { it.workout.date in monday..sunday && it.workout.type != WorkoutType.REST }
  if (weekRuns.isEmpty() && weekEntries.isEmpty()) return null
  val actual = completeDistance(weekRuns)
  val planned = weekEntries.map { WorkoutEstimateCalculator.calculate(it.workout).estimatedTotalDistanceMeters }
    .takeIf { it.isNotEmpty() && it.all { distance -> distance.validPositive() != null } }?.sumOf { requireNotNull(it) }
  val descriptions = mutableListOf<String>()
  val bars = (0L..6L).map { offset ->
    val date = monday.plusDays(offset)
    val dailyRuns = weekRuns.filter { it.startTime.atZone(zone).toLocalDate() == date }
    val dailyEntries = weekEntries.filter { it.workout.date == date }
    val plannedDistances = dailyEntries.map { WorkoutEstimateCalculator.calculate(it.workout).estimatedTotalDistanceMeters }
    val distance = if (dailyRuns.isNotEmpty()) completeDistance(dailyRuns)
      else if (dailyEntries.isNotEmpty()) plannedDistances.takeIf { it.all { d -> d.validPositive() != null } }?.sumOf { requireNotNull(it) }
      else 0.0
    val state = when {
      dailyRuns.isNotEmpty() -> if (date == today) BarState.Current else BarState.Done
      dailyEntries.isNotEmpty() -> if (date == today) BarState.TodayPlanned else BarState.Planned
      else -> BarState.Rest
    }
    val day = date.format(DateTimeFormatter.ofPattern("EEE", locale))
    descriptions += "$day: " + if (distance == null) "distance unavailable" else
      "${km(distance, locale)} km ${if (dailyRuns.isNotEmpty()) "recorded" else if (dailyEntries.isNotEmpty()) "planned" else "scheduled"}"
    ChartBar(day.take(1), distance?.div(1_000)?.toFloat() ?: Float.NaN, state = state)
  }
  val chips = buildList {
    if (weekEntries.isNotEmpty()) {
      val done = weekEntries.count { it.executionResult?.isCompleted == true }
      add(HomeChip("$done of ${weekEntries.size} runs done", done > 0))
    } else add(HomeChip("${weekRuns.size} ${if (weekRuns.size == 1) "run" else "runs"} recorded"))
    aggregateActualPaceSecondsPerKm(weekRuns).validPositive()?.let { add(HomeChip("avg ${pace(it)} /km")) }
  }
  return HomeWeekPresentation(
    "${monday.format(DateTimeFormatter.ofPattern("MMM d", locale))}–${sunday.format(DateTimeFormatter.ofPattern(if (monday.month == sunday.month) "d" else "MMM d", locale))}",
    actual?.let { km(it, locale) }, planned?.let { km(it, locale) }, bars,
    "This week. ${descriptions.joinToString(". ")}", chips,
  )
}

private fun goalPresentation(state: HomeUiState, sources: HomeTodaySources?, runs: List<RunSummary>,
  today: LocalDate, zone: java.time.ZoneId, locale: Locale): HomeGoalPresentation? {
  val title = state.activeGoal?.label.clean() ?: return null
  val goal = sources?.goal
  val plan = sources?.plan?.takeIf { it.goalId == goal?.id }
  val longest = sources?.profile?.longestRunLast28Days?.distanceMeters.validPositive()
  val goalDistance = goal?.distanceMeters.validPositive()
  val bars = buildList {
    if (longest != null && goalDistance != null) add(HomeGoalBar("Longest run", "${km(longest, locale)} of ${km(goalDistance, locale)} km", (longest / goalDistance).toFloat().coerceIn(0f, 1f)))
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val actualRuns = runs.filter { it.startTime.atZone(zone).toLocalDate() in monday..monday.plusDays(6) }
    val actualMeters = completeDistance(actualRuns)
    val plannedValues = plan?.weeks.orEmpty().flatMap { it.workouts }.filter { it.workout.date in monday..monday.plusDays(6) && it.workout.type != WorkoutType.REST }
      .map { WorkoutEstimateCalculator.calculate(it.workout).estimatedTotalDistanceMeters }
    val plannedMeters = plannedValues.takeIf { it.isNotEmpty() && it.all { d -> d.validPositive() != null } }?.sumOf { requireNotNull(it) }
    if (actualMeters != null && plannedMeters != null) add(HomeGoalBar("Weekly distance", "${km(actualMeters, locale)} of ${km(plannedMeters, locale)} km planned", (actualMeters / plannedMeters).toFloat().coerceIn(0f, 1f)))
  }
  val subtitle = listOfNotNull(
    plan?.completionMilestone?.scheduledDate?.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)),
    plan?.completionMilestone?.scheduledDate?.let { date -> ChronoUnit.DAYS.between(today, date) }
      ?.takeIf { it > 0 }?.let { days -> ceil(days / 7.0).toInt().let { "$it ${if (it == 1) "week" else "weeks"} left" } },
    plan?.weeks?.indexOfFirst { today in it.startDate..it.startDate.plusDays(6) }?.takeIf { it >= 0 }?.let { "Week ${it + 1} of ${plan.weeks.size}" },
  ).joinToString(" · ").clean()
  return HomeGoalPresentation(title, subtitle,
    if (longest != null && goalDistance != null) (longest / goalDistance).toFloat().coerceIn(0f, 1f) else null, bars)
}

private fun formPresentation(state: HomeUiState, locale: Locale): List<HomeStat> = buildList {
  val profile = state.todaySources?.profile
  val weeklyEasyPace = state.progress?.latestEasyPaceSecondsPerKm.validPositive()
  val easyPace = weeklyEasyPace ?: profile?.typicalEasyPace?.takeIf { it.contributingRuns > 0 }?.representative.validPositive()
  easyPace?.let {
    val change = state.progress?.paceChangeSecondsPerKm.takeIf { weeklyEasyPace != null }
    val delta = if (weeklyEasyPace != null) change?.let { seconds ->
      when { seconds > 0 -> "$seconds s/km faster"; seconds < 0 -> "${abs(seconds)} s/km slower"; else -> "Steady" }
    } else when (profile?.recentPaceTrend) { PaceTrend.FASTER -> "Getting faster"; PaceTrend.STABLE -> "Steady"; PaceTrend.SLOWER -> "Getting slower"; else -> null }
    add(HomeStat("Easy pace", pace(it), "/km", delta,
      if (weeklyEasyPace != null) change?.let { seconds -> seconds > 0 } == true else profile?.recentPaceTrend == PaceTrend.FASTER,
      state.progress?.weeks.orEmpty().map { week -> ChartPoint(week.easyPaceSecondsPerKm.validPositive()?.let { pace -> -pace.toFloat() }) }))
  }
  profile?.typicalEasyHeartRate?.takeIf { it.contributingRuns > 0 }?.representative.validPositive()?.let {
    add(HomeStat("Heart rate, easy", number(it, locale), "bpm"))
  }
  profile?.typicalEasyCadence?.takeIf { it.contributingRuns > 0 }?.representative.validPositive()?.let {
    add(HomeStat("Cadence", number(it, locale), "spm"))
  }
  profile?.feedback?.takeIf { it.feedbackCount > 0 }?.averageReadiness?.takeIf { it.isFinite() && it in 1.0..5.0 }?.let {
    add(HomeStat("Readiness", number(it, locale), "/ 5", readiness = it.toFloat()))
  }
}

private fun completeDistance(runs: List<RunSummary>): Double? = runs
  .takeIf { it.isNotEmpty() && it.all { run -> run.distanceMeters.validPositive() != null } }
  ?.sumOf { requireNotNull(it.distanceMeters) }
private fun Double?.validPositive(): Double? = this?.takeIf { it.isFinite() && it > 0 }
private fun String?.clean(): String? = this?.takeIf(String::isNotBlank)
private fun compactDistance(meters: Double, locale: Locale) = if (meters >= 1_000) "${number(meters / 1_000, locale)} km" else "${number(meters, locale)} m"
private fun km(meters: Double, locale: Locale, decimals: Int = 1) = String.format(locale, "%.$decimals" + "f", meters / 1_000)
private fun number(value: Double, locale: Locale): String = if (value == value.toLong().toDouble()) String.format(locale, "%d", value.toLong()) else String.format(locale, "%.1f", value)
private fun pace(value: Double): String = value.roundToLong().let { "%d:%02d".format(it / 60, it % 60) }
