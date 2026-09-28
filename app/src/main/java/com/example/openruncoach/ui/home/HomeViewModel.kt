package com.example.openruncoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.TrainingGoalRepository
import com.example.openruncoach.data.TrainingPlanRepository
import com.example.openruncoach.data.WorkoutResponseRepository
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.plan.TrainingPlan
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.RpeTarget
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutDescriptionFormatter
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutResponse
import com.example.openruncoach.domain.workout.WorkoutType
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
  val isLoading: Boolean = true,
  val greeting: String = "Ready for your next run?",
  val latestRun: RunSummary? = null,
  val coachingDecision: CoachingDecision? = null,
  val nextWorkout: HomeWorkoutSummary? = null,
  val week: HomeWeekSummary? = null,
  val weeklyMetrics: HomeWeeklyMetrics? = null,
  val hasActivePlan: Boolean = false,
  val activeGoal: HomeGoalSummary? = null,
  val errorMessage: String? = null,
)

data class HomeWorkoutSummary(
  val name: String,
  val date: LocalDate,
  val isToday: Boolean,
  val details: List<String>,
)

data class HomeWeekDay(
  val date: LocalDate,
  val dayLabel: String,
  val isToday: Boolean,
  val hasRun: Boolean,
  val hasPlannedWorkout: Boolean,
)

data class HomeWeekSummary(
  val days: List<HomeWeekDay>,
  val progressLabel: String,
  val dateRangeLabel: String,
  val isCurrentWeek: Boolean,
)

data class HomeWeeklyMetrics(
  val runCount: Int,
  val totalDistanceMeters: Double?,
  val averagePaceSecondsPerKm: Double?,
)

data class HomeGoalSummary(val label: String)

private data class HomeRunState(
  val canonicalRuns: List<RunSummary>,
  val latestRun: RunSummary?,
  val plan: TrainingPlan?,
  val goal: TrainingGoal?,
  val selectedWeekStart: LocalDate,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
  runRepository: RunRepository,
  feedbackRepository: RunFeedbackRepository,
  trainingPlanRepository: TrainingPlanRepository,
  goalRepository: TrainingGoalRepository,
  private val workoutResponseRepository: WorkoutResponseRepository,
  private val coachingEngine: CoachingEngine = CoachingEngine(),
  private val clock: Clock = Clock.systemDefaultZone(),
  private val zoneId: ZoneId = clock.zone,
) : ViewModel() {
  private val selectedWeekStart = MutableStateFlow(weekStart(LocalDate.now(clock)))

  val uiState = combine(
    runRepository.canonicalRuns,
    trainingPlanRepository.observeActivePlan(),
    goalRepository.observeActiveGoal(),
    selectedWeekStart,
  ) { canonicalRuns, plan, goal, weekStart ->
    HomeRunState(
      canonicalRuns = canonicalRuns,
      latestRun = canonicalRuns.firstOrNull(),
      plan = plan,
      goal = goal,
      selectedWeekStart = weekStart,
    )
  }.flatMapLatest { runState ->
    val run = runState.latestRun
    val feedbackFlow = if (run == null) flowOf(null) else feedbackRepository.observeFeedbackForRun(run.healthConnectRecordId)
    val responseFlow = if (run == null) flowOf(null) else workoutResponseRepository.observeForRun(run.healthConnectRecordId)
    combine(feedbackFlow, responseFlow) { feedback, response -> runState.toUiState(feedback, response) }
  }.catch {
    emit(HomeUiState(isLoading = false, greeting = greeting(), errorMessage = "Could not load training data."))
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(greeting = greeting()))

  private fun HomeRunState.toUiState(feedback: RunFeedback?, response: WorkoutResponse?): HomeUiState {
    val today = LocalDate.now(clock)
    val run = latestRun
    val execution = run?.let { selected ->
      plan?.weeks.orEmpty().flatMap { it.workouts }.mapNotNull { it.executionResult }
        .firstOrNull { it.actualRunId == selected.healthConnectRecordId }
    }
    return HomeUiState(
      isLoading = false,
      greeting = greeting(),
      latestRun = run,
      coachingDecision = if (run != null && feedback != null) {
        coachingEngine.analyze(run, canonicalRuns, feedback, execution, response)
      } else null,
      nextWorkout = plan.nextWorkoutSummary(today),
      week = buildWeekSummary(today, selectedWeekStart, canonicalRuns, plan, zoneId),
      weeklyMetrics = buildWeeklyMetrics(selectedWeekStart, canonicalRuns, zoneId),
      hasActivePlan = plan != null,
      activeGoal = goal?.toHomeSummary(),
    )
  }

  fun previousWeek() {
    selectedWeekStart.value = selectedWeekStart.value.minusWeeks(1)
  }

  fun nextWeek() {
    selectedWeekStart.value = selectedWeekStart.value.plusWeeks(1)
  }

  private fun greeting(): String = when (clock.instant().atZone(zoneId).hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
  }
}

private fun TrainingPlan?.nextWorkoutSummary(today: LocalDate): HomeWorkoutSummary? {
  this ?: return null
  val next = weeks.flatMap { it.workouts }
    .filter { it.workout.type != WorkoutType.REST && it.executionResult == null && !it.workout.date.isBefore(today) }
    .minByOrNull { it.workout.date }
    ?.workout
    ?: return null
  return HomeWorkoutSummary(
    name = next.name,
    date = next.date,
    isToday = next.date == today,
    details = next.homeDetails(),
  )
}

private fun StructuredWorkout.homeDetails(): List<String> {
  val estimate = WorkoutEstimateCalculator.calculate(this)
  val targets = blocks.flatMap { it.segments }
    .flatMap { listOf(it.primaryIntensity) + it.secondaryIntensities }
  val paceTargets = targets.filterIsInstance<PaceTarget>().distinct()
  val rpeTargets = targets.filterIsInstance<RpeTarget>().distinct()
  return buildList {
    estimate.estimatedTotalDistanceMeters?.let { add(formatMetricDistance(it)) }
    estimate.estimatedMinimumDurationSeconds?.let { minimum ->
      val maximum = estimate.estimatedMaximumDurationSeconds ?: minimum
      add(WorkoutDescriptionFormatter.durationRange(minimum, maximum))
    }
    paceTargets.singleOrNull()?.let { add(WorkoutDescriptionFormatter.paceRange(it)) }
    rpeTargets.singleOrNull()?.let { add("RPE ${it.minimum}–${it.maximum}") }
  }.distinct().take(3)
}

private fun buildWeekSummary(
  today: LocalDate,
  monday: LocalDate,
  runs: List<RunSummary>,
  plan: TrainingPlan?,
  zoneId: ZoneId = ZoneId.systemDefault(),
  locale: Locale = Locale.getDefault(),
): HomeWeekSummary {
  val sunday = monday.plusDays(6)
  val runDates = runs.asSequence()
    .map { it.startTime.atZone(zoneId).toLocalDate() }
    .filter { it in monday..sunday }
    .toSet()
  val currentEntries = plan?.weeks.orEmpty().flatMap { it.workouts }
    .filter { it.workout.date in monday..sunday && it.workout.type != WorkoutType.REST }
  val plannedDates = currentEntries.mapTo(mutableSetOf()) { it.workout.date }
  val completedPlanned = currentEntries.count { it.executionResult?.isCompleted == true }
  val progressLabel = if (currentEntries.isNotEmpty()) {
    "$completedPlanned / ${currentEntries.size} runs"
  } else {
    "${runDates.size} ${if (runDates.size == 1) "run" else "runs"} recorded"
  }
  return HomeWeekSummary(
    days = (0L..6L).map { offset ->
      val date = monday.plusDays(offset)
      HomeWeekDay(
        date = date,
        dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
        isToday = date == today,
        hasRun = date in runDates,
        hasPlannedWorkout = date in plannedDates,
      )
    },
    progressLabel = progressLabel,
    dateRangeLabel = formatWeekRange(monday, sunday, locale),
    isCurrentWeek = monday == weekStart(today),
  )
}

private fun buildWeeklyMetrics(
  monday: LocalDate,
  runs: List<RunSummary>,
  zoneId: ZoneId = ZoneId.systemDefault(),
): HomeWeeklyMetrics {
  val sunday = monday.plusDays(6)
  val weekRuns = runs.filter { it.startTime.atZone(zoneId).toLocalDate() in monday..sunday }
  val distances = weekRuns.map { it.distanceMeters }
  val totalDistance = distances.takeIf { it.isNotEmpty() && it.all { distance -> distance != null } }
    ?.sumOf { requireNotNull(it) }
  val averagePace = totalDistance?.takeIf { it > 0.0 }
    ?.let { distance -> weekRuns.sumOf(RunSummary::durationSeconds) * 1_000.0 / distance }
  return HomeWeeklyMetrics(
    runCount = weekRuns.size,
    totalDistanceMeters = totalDistance,
    averagePaceSecondsPerKm = averagePace,
  )
}

private fun formatMetricDistance(meters: Double): String =
  if (meters >= 1_000.0) "%.2f km".format(Locale.getDefault(), meters / 1_000.0)
  else "%.0f m".format(Locale.getDefault(), meters)

private fun weekStart(date: LocalDate): LocalDate =
  date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun formatWeekRange(start: LocalDate, end: LocalDate, locale: Locale): String {
  val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
  return "${start.format(formatter)} – ${end.format(formatter)}"
}

private fun TrainingGoal.toHomeSummary(): HomeGoalSummary {
  val distanceLabel = if (distance == GoalDistance.CUSTOM) formatMetricDistance(distanceMeters) else distance.displayName
  val label = when (goalType) {
    GoalType.COMPLETE_DISTANCE -> "Complete $distanceLabel"
    GoalType.IMPROVE_PERFORMANCE -> "Improve $distanceLabel performance"
    GoalType.TARGET_TIME -> "$distanceLabel target time"
  }
  return HomeGoalSummary(label)
}
