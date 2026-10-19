package com.example.openruncoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.TrainingGoalRepository
import com.example.openruncoach.data.TrainingPlanRepository
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.data.WorkoutResponseRepository
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.aggregateActualPaceSecondsPerKm
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.plan.TrainingPlan
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.domain.workout.WorkoutResponse
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.displayName
import com.example.openruncoach.ui.workout.listSummary
import com.example.openruncoach.ui.workout.planTitle
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
  val upcomingWorkouts: List<HomeWorkoutSummary> = emptyList(),
  val week: HomeWeekSummary? = null,
  val weeklyMetrics: HomeWeeklyMetrics? = null,
  val progress: HomeProgressSummary? = null,
  val hasActivePlan: Boolean = false,
  val activeGoal: HomeGoalSummary? = null,
  val errorMessage: String? = null,
)

data class HomeWorkoutSummary(
  val name: String,
  val type: WorkoutType,
  val date: LocalDate,
  val isToday: Boolean,
  val details: List<String>,
  val distanceLabel: String?,
  val paceLabel: String?,
  val intensity: HomeWorkoutIntensity?,
  val adaptationReason: String? = null,
)

enum class HomeWorkoutIntensity(val label: String, val activeBars: Int) {
  EASY("Easy", 1),
  MODERATE("Moderate", 2),
  HARD("Hard", 3),
  VERY_HARD("Very hard", 4),
}

data class HomeWeekDay(
  val date: LocalDate,
  val dayLabel: String,
  val isToday: Boolean,
  val hasRun: Boolean,
  val hasPlannedWorkout: Boolean,
  val workoutTypeLabel: String?,
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
  val days: List<HomeDailyMetric>,
  val distanceIsPlanned: Boolean,
  val paceDifferenceSecondsPerKm: Double?,
  val distanceDifferenceMeters: Double?,
)

data class HomeDailyMetric(
  val date: LocalDate,
  val distanceMeters: Double?,
  val averagePaceSecondsPerKm: Double?,
)

data class HomeGoalSummary(val label: String)

private data class HomeRunState(
  val canonicalRuns: List<RunSummary>,
  val latestRun: RunSummary?,
  val plan: TrainingPlan?,
  val goal: TrainingGoal?,
  val selectedWeekStart: LocalDate,
  val completedWorkoutTypesByRunId: Map<String, WorkoutType>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
  runRepository: RunRepository,
  feedbackRepository: RunFeedbackRepository,
  trainingPlanRepository: TrainingPlanRepository,
  goalRepository: TrainingGoalRepository,
  private val workoutResponseRepository: WorkoutResponseRepository,
  private val workoutExecutionRepository: WorkoutExecutionRepository,
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
      completedWorkoutTypesByRunId = workoutExecutionRepository.getCompletedWorkoutTypesByRunId(),
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
    val upcoming = plan.upcomingWorkoutSummaries(today)
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
      nextWorkout = upcoming.firstOrNull(),
      upcomingWorkouts = upcoming,
      week = buildWeekSummary(
        today,
        selectedWeekStart,
        canonicalRuns,
        plan,
        completedWorkoutTypesByRunId,
        zoneId,
      ),
      weeklyMetrics = buildWeeklyMetrics(selectedWeekStart, canonicalRuns, plan, zoneId),
      progress = HomeProgressTrendCalculator.calculate(
        referenceDate = today,
        canonicalRuns = canonicalRuns,
        completedWorkoutTypesByRunId = completedWorkoutTypesByRunId,
        zoneId = zoneId,
      ),
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

private fun TrainingPlan?.upcomingWorkoutSummaries(today: LocalDate): List<HomeWorkoutSummary> {
  this ?: return emptyList()
  return weeks.flatMap { it.workouts }
    .filter { (it.workout.type != WorkoutType.REST || it.originalWorkout.type != WorkoutType.REST) &&
      it.executionResult == null && !it.workout.date.isBefore(today) }
    .sortedWith(compareBy({ it.workout.date }, { it.workout.name }))
    .take(5)
    .map { entry ->
      val prescription = entry.workout.listSummary()
      HomeWorkoutSummary(
        name = entry.workout.name,
        type = entry.workout.type,
        date = entry.workout.date,
        isToday = entry.workout.date == today,
        details = listOfNotNull(prescription.repetitionLabel, prescription.distanceLabel,
          prescription.paceLabel, prescription.rpeLabel, prescription.durationLabel).distinct().take(3),
        distanceLabel = prescription.distanceLabel,
        paceLabel = prescription.paceLabel,
        intensity = HomeWorkoutIntensity.entries.firstOrNull { it.label == prescription.effortLabel },
        adaptationReason = entry.adjustmentReasons.joinToString(" ").takeIf(String::isNotBlank),
      )
    }
}

private fun buildWeekSummary(
  today: LocalDate,
  monday: LocalDate,
  runs: List<RunSummary>,
  plan: TrainingPlan?,
  completedWorkoutTypesByRunId: Map<String, WorkoutType>,
  zoneId: ZoneId = ZoneId.systemDefault(),
  locale: Locale = Locale.getDefault(),
): HomeWeekSummary {
  val sunday = monday.plusDays(6)
  val weekRuns = runs.filter { it.startTime.atZone(zoneId).toLocalDate() in monday..sunday }
  val runDates = weekRuns.asSequence()
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
        workoutTypeLabel = weekRuns
          .filter { it.startTime.atZone(zoneId).toLocalDate() == date }
          .mapNotNull { completedWorkoutTypesByRunId[it.healthConnectRecordId] }
          .distinct()
          .let { actualTypes ->
            when {
              actualTypes.size == 1 -> actualTypes.single().displayName()
              actualTypes.size > 1 -> "Multiple run types"
              date in runDates -> "Run recorded"
              else -> currentEntries.filter { it.workout.date == date }.map { it.workout.type }.distinct().let { plannedTypes ->
                when (plannedTypes.size) {
                  0 -> null
                  1 -> plannedTypes.single().displayName()
                  else -> "Multiple workouts"
                }
              }
            }
          },
      )
    },
    progressLabel = progressLabel,
    dateRangeLabel = formatWeekRange(monday, sunday, locale),
    isCurrentWeek = monday == weekStart(today),
  )
}

internal fun buildWeeklyMetrics(
  monday: LocalDate,
  runs: List<RunSummary>,
  plan: TrainingPlan?,
  zoneId: ZoneId = ZoneId.systemDefault(),
): HomeWeeklyMetrics {
  val sunday = monday.plusDays(6)
  val weekRuns = runs.filter { it.startTime.atZone(zoneId).toLocalDate() in monday..sunday }
  val plannedEntries = plan?.weeks.orEmpty().flatMap { it.workouts }
    .filter { it.workout.date in monday..sunday && it.workout.type != WorkoutType.REST }
  val usePlannedDistance = weekRuns.isEmpty() && plannedEntries.isNotEmpty()
  val distances = weekRuns.map { it.distanceMeters }
  val actualDistance = distances.takeIf { it.isNotEmpty() && it.all { distance -> distance != null } }
    ?.sumOf { requireNotNull(it) }
  val plannedDistances = plannedEntries.map {
    WorkoutEstimateCalculator.calculate(it.workout).estimatedTotalDistanceMeters
  }
  val plannedDistance = plannedDistances.takeIf { it.isNotEmpty() && it.all { distance -> distance != null } }
    ?.sumOf { requireNotNull(it) }
  val totalDistance = if (usePlannedDistance) plannedDistance else actualDistance
  val averagePace = aggregateActualPaceSecondsPerKm(weekRuns)
  val comparisons = calculateFourWeekComparisons(monday, runs, actualDistance, averagePace, zoneId)
  val dailyMetrics = (0L..6L).map { offset ->
    val date = monday.plusDays(offset)
    val dayRuns = weekRuns.filter { it.startTime.atZone(zoneId).toLocalDate() == date }
    val dayDistance = dayRuns.map { it.distanceMeters }
      .takeIf { it.isNotEmpty() && it.all { distance -> distance != null } }
      ?.sumOf { requireNotNull(it) }
    val plannedDayDistances = plannedEntries.filter { it.workout.date == date }
      .map { WorkoutEstimateCalculator.calculate(it.workout).estimatedTotalDistanceMeters }
    val plannedDayDistance = plannedDayDistances
      .takeIf { it.isNotEmpty() && it.all { distance -> distance != null } }
      ?.sumOf { requireNotNull(it) }
    HomeDailyMetric(
      date = date,
      distanceMeters = if (usePlannedDistance) plannedDayDistance else dayDistance,
      averagePaceSecondsPerKm = aggregateActualPaceSecondsPerKm(dayRuns),
    )
  }
  return HomeWeeklyMetrics(
    runCount = weekRuns.size,
    totalDistanceMeters = totalDistance,
    averagePaceSecondsPerKm = averagePace,
    days = dailyMetrics,
    distanceIsPlanned = usePlannedDistance,
    paceDifferenceSecondsPerKm = comparisons.paceDifferenceSecondsPerKm,
    distanceDifferenceMeters = comparisons.distanceDifferenceMeters,
  )
}

internal data class HomeFourWeekComparisons(
  val paceDifferenceSecondsPerKm: Double?,
  val distanceDifferenceMeters: Double?,
)

internal fun calculateFourWeekComparisons(
  currentWeekStart: LocalDate,
  runs: List<RunSummary>,
  currentDistanceMeters: Double?,
  currentPaceSecondsPerKm: Double?,
  zoneId: ZoneId = ZoneId.systemDefault(),
): HomeFourWeekComparisons {
  val historicalWeeks = (1L..4L).map { weeksAgo ->
    val start = currentWeekStart.minusWeeks(weeksAgo)
    val end = start.plusDays(6)
    runs.filter { it.startTime.atZone(zoneId).toLocalDate() in start..end }
  }
  val hasCompleteDistanceHistory = historicalWeeks.all { weekRuns ->
    weekRuns.isNotEmpty() && weekRuns.all { run ->
      run.distanceMeters?.let { it.isFinite() && it > 0.0 } == true
    }
  }
  if (!hasCompleteDistanceHistory) return HomeFourWeekComparisons(null, null)

  val historicalWeeklyDistances = historicalWeeks.map { weekRuns ->
    weekRuns.sumOf { requireNotNull(it.distanceMeters) }
  }
  val historicalDistanceAverage = historicalWeeklyDistances.average()
  val distanceDifference = currentDistanceMeters?.let { it - historicalDistanceAverage }

  val hasCompletePaceHistory = historicalWeeks.flatten().all { it.durationSeconds > 0 }
  val historicalPace = if (hasCompletePaceHistory) {
    aggregateActualPaceSecondsPerKm(historicalWeeks.flatten())
  } else null
  return HomeFourWeekComparisons(
    paceDifferenceSecondsPerKm = if (currentPaceSecondsPerKm != null && historicalPace != null) {
      historicalPace - currentPaceSecondsPerKm
    } else null,
    distanceDifferenceMeters = distanceDifference,
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

private fun TrainingGoal.toHomeSummary(): HomeGoalSummary = HomeGoalSummary(planTitle())
