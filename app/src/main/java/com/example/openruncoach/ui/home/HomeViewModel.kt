package com.example.openruncoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.TrainingPlanRepository
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.plan.TrainingPlan
import com.example.openruncoach.domain.workout.WorkoutEstimateCalculator
import com.example.openruncoach.ui.common.WeekSelectorUiModel
import com.example.openruncoach.ui.common.buildWeekSelectorUiModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class HomeUiState(
  val isLoading: Boolean = true,
  val selectedDate: LocalDate = LocalDate.now(),
  val week: WeekSelectorUiModel? = null,
  val selectedRun: RunSummary? = null,
  val sourceCount: Int = 0,
  val coachingDecision: CoachingDecision? = null,
  val planSummary: HomePlanSummary? = null,
  val errorMessage: String? = null,
)

data class HomePlanSummary(
  val weekNumber: Int,
  val workoutCount: Int,
  val nextWorkoutName: String?,
  val nextWorkoutDurationSeconds: Long?,
  val nextWorkoutDistanceMeters: Double?,
)

private data class HomeRunState(
  val canonicalRuns: List<RunSummary>,
  val allRuns: List<RunSummary>,
  val selectedDate: LocalDate,
  val selectedRun: RunSummary?,
  val plan: TrainingPlan?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
  runRepository: RunRepository,
  feedbackRepository: RunFeedbackRepository,
  trainingPlanRepository: TrainingPlanRepository,
  private val coachingEngine: CoachingEngine = CoachingEngine(),
  private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
  private val selectedDate = MutableStateFlow(LocalDate.now(zoneId))

  val uiState = combine(
    runRepository.canonicalRuns,
    runRepository.allRuns,
    selectedDate,
    trainingPlanRepository.observeActivePlan(),
  ) { canonicalRuns, allRuns, date, plan ->
    HomeRunState(
      canonicalRuns = canonicalRuns,
      allRuns = allRuns,
      selectedDate = date,
      selectedRun = canonicalRuns.firstOrNull { it.startTime.atZone(zoneId).toLocalDate() == date },
      plan = plan,
    )
  }.flatMapLatest { runState ->
    val run = runState.selectedRun
    val feedbackFlow = if (run == null) flowOf(null) else feedbackRepository.observeFeedbackForRun(run.healthConnectRecordId)
    feedbackFlow.map { feedback -> runState.toUiState(feedback) }
  }.catch {
    emit(HomeUiState(isLoading = false, selectedDate = selectedDate.value, errorMessage = "Could not load runs."))
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(selectedDate = selectedDate.value))

  fun selectDate(date: LocalDate) { selectedDate.value = date }

  fun previousWeek() { selectedDate.value = selectedDate.value.minusWeeks(1) }

  fun nextWeek() { selectedDate.value = selectedDate.value.plusWeeks(1) }

  private fun HomeRunState.toUiState(feedback: RunFeedback?): HomeUiState {
    val run = selectedRun
    val sourceCount = when {
      run == null -> 0
      run.duplicateGroupId != null -> allRuns.count { it.duplicateGroupId == run.duplicateGroupId }
      else -> 1
    }
    return HomeUiState(
      isLoading = false,
      selectedDate = selectedDate,
      week = buildWeekSelectorUiModel(selectedDate, canonicalRuns, zoneId),
      selectedRun = run,
      sourceCount = sourceCount,
      coachingDecision = if (run != null && feedback != null) coachingEngine.analyze(run, canonicalRuns, feedback) else null,
      planSummary = plan.toHomeSummary(LocalDate.now(zoneId)),
    )
  }
}

private fun TrainingPlan?.toHomeSummary(today: LocalDate): HomePlanSummary? {
  this ?: return null
  val week = weeks.firstOrNull { !today.isBefore(it.startDate) && !today.isAfter(it.startDate.plusDays(6)) }
    ?: weeks.firstOrNull { it.startDate.isAfter(today) }
    ?: weeks.lastOrNull()
    ?: return null
  val next = week.workouts.map { it.workout }.filter { !it.date.isBefore(today) }.minByOrNull { it.date }
    ?: week.workouts.firstOrNull()?.workout
  val estimate = next?.let(WorkoutEstimateCalculator::calculate)
  return HomePlanSummary(
    weekNumber = week.weekIndex + 1,
    workoutCount = week.workouts.size,
    nextWorkoutName = next?.name,
    nextWorkoutDurationSeconds = estimate?.estimatedTotalDurationSeconds,
    nextWorkoutDistanceMeters = estimate?.estimatedTotalDistanceMeters,
  )
}
