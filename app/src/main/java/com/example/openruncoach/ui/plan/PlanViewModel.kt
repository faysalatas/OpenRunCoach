package com.example.openruncoach.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.AthletePerformanceProfileRepository
import com.example.openruncoach.data.CompletionScheduleDiagnostics
import com.example.openruncoach.data.CompletionScheduleUpdate
import com.example.openruncoach.data.GoalCompletionRepository
import com.example.openruncoach.data.TrainingGoalRepository
import com.example.openruncoach.data.TrainingPlanRepository
import com.example.openruncoach.data.PlanWorkoutPresentation
import com.example.openruncoach.data.PreviousPlanSummary
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.goal.CompletionDateSuggestion
import com.example.openruncoach.domain.goal.CompletionDateAssessment
import com.example.openruncoach.domain.goal.CompletionDateFeasibilityEvaluator
import com.example.openruncoach.domain.goal.GoalCompletionDateSuggester
import com.example.openruncoach.domain.goal.GoalCompletionMilestone
import com.example.openruncoach.domain.goal.GoalCompletionReadiness
import com.example.openruncoach.domain.plan.TrainingPlan
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanUiState(
  val activeGoal: TrainingGoal? = null,
  val plan: TrainingPlan? = null,
  val selectedWeekIndex: Int = 0,
  val isLoading: Boolean = true,
  val isGenerating: Boolean = false,
  val completionMilestone: GoalCompletionMilestone? = null,
  val completionDateSuggestions: List<CompletionDateSuggestion> = emptyList(),
  val completionDateAssessment: CompletionDateAssessment? = null,
  val showCompletionScheduler: Boolean = false,
  val errorMessage: String? = null,
  val workoutMetadata: Map<String, PlanWorkoutPresentation> = emptyMap(),
  val showPlanHistory: Boolean = false,
  val historyLoading: Boolean = false,
  val previousPlans: List<PreviousPlanSummary> = emptyList(),
  val previousPlan: TrainingPlan? = null,
  val previousWeekIndex: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(
  private val planRepository: TrainingPlanRepository,
  private val goalRepository: TrainingGoalRepository,
  private val profileRepository: AthletePerformanceProfileRepository,
  private val completionRepository: GoalCompletionRepository,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val dateSuggester: GoalCompletionDateSuggester = GoalCompletionDateSuggester(),
  private val feasibilityEvaluator: CompletionDateFeasibilityEvaluator = CompletionDateFeasibilityEvaluator(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(PlanUiState())
  val uiState = _uiState.asStateFlow()
  private var historyJob: Job? = null
  private var previousPlanJob: Job? = null

  init {
    viewModelScope.launch {
      combine(
        goalRepository.observeActiveGoal(),
        planRepository.observeActivePlan(),
        profileRepository.observeProfile(),
      ) { goal, plan, profile -> Triple(goal, plan, profile) }
        .catch { _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load your training plan.") } }
        .collect { (goal, storedPlan, profile) ->
          val milestone = if (goal != null && profile != null) completionRepository.refreshReadiness(goal, profile) else null
          val plan = storedPlan?.takeIf { it.goalId == goal?.id }?.takeUnless { candidate ->
            milestone?.scheduledDate == null &&
              candidate.weeks.flatMap { it.workouts }.any { it.workout.purpose == com.example.openruncoach.domain.workout.WorkoutPurpose.GOAL_COMPLETION }
          }
          _uiState.update { state ->
            val currentWeek = selectedPlanWeek(state.plan?.id, state.selectedWeekIndex, plan, LocalDate.now(clock))
            val prompt = milestone?.readiness == GoalCompletionReadiness.GOAL_COMPLETION_READY &&
              !milestone.promptDismissed &&
              (state.completionMilestone?.goalId != milestone.goalId ||
                state.completionMilestone.readiness != GoalCompletionReadiness.GOAL_COMPLETION_READY)
            state.copy(
              activeGoal = goal,
              plan = plan,
              selectedWeekIndex = currentWeek,
              isLoading = false,
              completionMilestone = milestone,
              completionDateSuggestions = suggestions(plan, goal, profile),
              completionDateAssessment = dateAssessment(goal, profile, milestone),
              showCompletionScheduler = state.showCompletionScheduler || prompt,
            )
          }
        }
    }
    viewModelScope.launch {
      _uiState.map { it.previousPlan?.id ?: it.plan?.id }.distinctUntilChanged()
        .flatMapLatest { id ->
          if (id == null) flowOf(null to emptyMap<String, PlanWorkoutPresentation>())
          else planRepository.observeWorkoutPresentation(id).map { id to it }
        }
        .catch { _uiState.update { it.copy(errorMessage = "Could not load workout status.") } }
        .collect { (id, metadata) ->
          _uiState.update { state ->
            if ((state.previousPlan?.id ?: state.plan?.id) != id) state
            else state.copy(workoutMetadata = metadata)
          }
        }
    }
  }

  fun showPlanHistory() {
    _uiState.update { it.copy(showPlanHistory = true) }
    if (historyJob?.isActive == true) return
    historyJob = viewModelScope.launch {
      _uiState.update { it.copy(historyLoading = true) }
      planRepository.observePreviousPlans()
        .catch { _uiState.update { it.copy(historyLoading = false, errorMessage = "Could not load previous plans.") } }
        .collect { plans -> _uiState.update { it.copy(previousPlans = plans, historyLoading = false) } }
    }
  }

  fun hidePlanHistory() {
    previousPlanJob?.cancel()
    _uiState.update { it.copy(showPlanHistory = false, historyLoading = false) }
  }

  fun openPreviousPlan(id: String) {
    if (_uiState.value.previousPlans.none { it.id == id }) return
    previousPlanJob?.cancel()
    previousPlanJob = viewModelScope.launch {
      _uiState.update { it.copy(historyLoading = true) }
      try {
        val plan = planRepository.getPlanSnapshot(id) ?: error("Saved plan unavailable")
        _uiState.update { it.copy(previousPlan = plan, previousWeekIndex = 0,
          showPlanHistory = false, historyLoading = false, workoutMetadata = emptyMap()) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        _uiState.update { it.copy(historyLoading = false, errorMessage = "Could not open this previous plan.") }
      }
    }
  }

  fun closePreviousPlan() {
    _uiState.update { it.copy(previousPlan = null, workoutMetadata = emptyMap()) }
  }

  fun selectPreviousWeek(index: Int) {
    _uiState.update { it.copy(previousWeekIndex = index.coerceIn(0, it.previousPlan?.weeks?.lastIndex ?: 0)) }
  }

  fun selectWeek(index: Int) {
    _uiState.update { state -> state.copy(selectedWeekIndex = index.coerceIn(0, state.plan?.weeks?.lastIndex ?: 0)) }
  }

  fun generatePlan() {
    val goal = _uiState.value.activeGoal ?: return
    if (_uiState.value.isGenerating) return
    viewModelScope.launch {
      _uiState.update { it.copy(isGenerating = true, errorMessage = null) }
      try {
        val profile = profileRepository.getProfile() ?: profileRepository.recalculate()
        val plan = planRepository.generate(goal, profile)
        val milestone = completionRepository.refreshReadiness(goal, profile)
        _uiState.update {
          it.copy(
            plan = plan,
            selectedWeekIndex = 0,
            isGenerating = false,
            completionMilestone = milestone,
            completionDateSuggestions = suggestions(plan, goal, profile),
            completionDateAssessment = dateAssessment(goal, profile, milestone),
          )
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        _uiState.update { it.copy(isGenerating = false, errorMessage = "Could not generate the training plan.") }
      }
    }
  }

  fun showCompletionScheduler() = _uiState.update { it.copy(showCompletionScheduler = true) }

  fun hideCompletionScheduler() = _uiState.update { it.copy(showCompletionScheduler = false) }

  fun scheduleCompletion(date: LocalDate) = updateCompletionSchedule("schedule date=$date") { goal, profile ->
    if (_uiState.value.plan == null) planRepository.createWithCompletionDate(goal, profile, date)
    else planRepository.scheduleCompletion(goal, profile, date)
  }

  fun postponeCompletion() {
    if (_uiState.value.plan == null) {
      _uiState.update { it.copy(showCompletionScheduler = false) }
    } else updateCompletionSchedule("postpone") { goal, profile ->
      planRepository.postponeCompletion(goal, profile)
    }
  }

  private fun updateCompletionSchedule(
    request: String,
    update: suspend (TrainingGoal, AthletePerformanceProfile) -> CompletionScheduleUpdate,
  ) {
    val goal = _uiState.value.activeGoal ?: return
    if (_uiState.value.isGenerating) return
    val currentPlanId = _uiState.value.plan?.id
    CompletionScheduleDiagnostics.debug(
      "userRequest=$request goalId=${goal.id} activePlanId=$currentPlanId " +
        "milestoneId=${_uiState.value.completionMilestone?.goalId} " +
        "scheduleState=${_uiState.value.completionMilestone?.readiness}"
    )
    viewModelScope.launch {
      _uiState.update { it.copy(isGenerating = true, errorMessage = null) }
      try {
        val profile = profileRepository.getProfile() ?: profileRepository.recalculate()
        val result = update(goal, profile)
        val milestone = result.milestone
        val plan = result.plan
        _uiState.update {
          it.copy(
            plan = plan,
            completionMilestone = milestone,
            completionDateSuggestions = suggestions(plan, goal, profile),
            completionDateAssessment = dateAssessment(goal, profile, milestone),
            showCompletionScheduler = false,
            selectedWeekIndex = milestone.scheduledDate?.let { date ->
              plan.weeks.indexOfFirst { week -> !date.isBefore(week.startDate) && !date.isAfter(week.startDate.plusDays(6)) }
                .takeIf { index -> index >= 0 }
            } ?: 0,
            isGenerating = false,
          )
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        CompletionScheduleDiagnostics.error(
          "Failed to schedule completion run: request=$request goalId=${goal.id} planId=$currentPlanId",
          exception,
        )
        _uiState.update { it.copy(isGenerating = false, errorMessage = "Could not update the Completion Run schedule.") }
      }
    }
  }

  private fun suggestions(
    plan: TrainingPlan?,
    goal: TrainingGoal?,
    profile: AthletePerformanceProfile?,
  ): List<CompletionDateSuggestion> = dateSuggester.suggest(
    today = LocalDate.now(clock),
    plannedWorkouts = plan?.weeks.orEmpty().flatMap { it.workouts }.map { it.workout },
    goal = goal,
    profile = profile,
  )

  private fun dateAssessment(
    goal: TrainingGoal?,
    profile: AthletePerformanceProfile?,
    milestone: GoalCompletionMilestone?,
  ): CompletionDateAssessment? = if (goal == null || profile == null) null else milestone?.scheduledDate?.let { date ->
    feasibilityEvaluator.evaluate(goal, profile, date, LocalDate.now(clock))
  }
}
