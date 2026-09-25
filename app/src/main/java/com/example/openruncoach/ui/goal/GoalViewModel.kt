package com.example.openruncoach.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.AthletePerformanceProfileRepository
import com.example.openruncoach.data.TrainingGoalRepository
import com.example.openruncoach.domain.goal.DistanceGoalCapability
import com.example.openruncoach.domain.goal.GoalAssessment
import com.example.openruncoach.domain.goal.GoalAssessmentEvaluator
import com.example.openruncoach.domain.goal.GoalCapabilities
import com.example.openruncoach.domain.goal.GoalCapabilityEvaluator
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.PerformanceComparison
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.goal.TrainingGoalDraft
import com.example.openruncoach.domain.goal.comparePerformance
import com.example.openruncoach.domain.goal.parseGoalDuration
import com.example.openruncoach.domain.goal.requireGoalDistanceMeters
import com.example.openruncoach.domain.goal.targetPaceSecondsPerKm
import com.example.openruncoach.domain.goal.validateGoalDraft
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GoalCreationStep { OVERVIEW, DISTANCE, TYPE, CONTEXT, DETAILS, REVIEW }

data class GoalDraftState(
  val distance: GoalDistance? = null,
  val customDistanceKm: String = "",
  val goalType: GoalType? = null,
  val targetTime: String = "",
)

data class GoalUiState(
  val activeGoal: TrainingGoal? = null,
  val profile: AthletePerformanceProfile? = null,
  val capabilities: GoalCapabilities = GoalCapabilityEvaluator().evaluate(null),
  val step: GoalCreationStep = GoalCreationStep.OVERVIEW,
  val draft: GoalDraftState = GoalDraftState(),
  val showDetails: Boolean = false,
  val isLoading: Boolean = true,
  val isSaving: Boolean = false,
  val errorMessage: String? = null,
)

data class GoalPresentation(
  val distanceMeters: Double,
  val targetPaceSecondsPerKm: Double?,
  val benchmarkDurationSeconds: Long?,
  val comparison: PerformanceComparison?,
  val assessment: GoalAssessment,
  val improvementSinceGoalStartSeconds: Long? = null,
)

class GoalViewModel(
  private val goalRepository: TrainingGoalRepository,
  private val profileRepository: AthletePerformanceProfileRepository,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val capabilityEvaluator: GoalCapabilityEvaluator = GoalCapabilityEvaluator(),
  private val assessmentEvaluator: GoalAssessmentEvaluator = GoalAssessmentEvaluator(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(GoalUiState())
  val uiState = _uiState

  init {
    viewModelScope.launch {
      combine(goalRepository.observeActiveGoal(), profileRepository.observeProfile()) { goal, profile -> goal to profile }
        .collect { (goal, profile) ->
          _uiState.update { it.copy(activeGoal = goal, profile = profile, capabilities = capabilityEvaluator.evaluate(profile), isLoading = false) }
        }
    }
  }

  fun beginCreation() = _uiState.update {
    it.copy(step = GoalCreationStep.DISTANCE, draft = GoalDraftState(), showDetails = false, errorMessage = null)
  }

  fun selectDistance(distance: GoalDistance) = _uiState.update {
    it.copy(draft = it.draft.copy(distance = distance), errorMessage = null)
  }

  fun setCustomDistance(value: String) = _uiState.update {
    it.copy(draft = it.draft.copy(customDistanceKm = value), errorMessage = null)
  }

  fun continueFromDistance() {
    val draft = _uiState.value.draft
    val meters = draft.customDistanceKm.toDoubleOrNull()?.times(1_000.0)
    if (draft.distance == null || (draft.distance == GoalDistance.CUSTOM && (meters == null || meters !in 100.0..200_000.0))) {
      _uiState.update { it.copy(errorMessage = "Choose a distance. Custom distance must be between 0.1 km and 200 km.") }
    } else _uiState.update { it.copy(step = GoalCreationStep.TYPE, errorMessage = null) }
  }

  fun selectGoalType(type: GoalType) = _uiState.update {
    it.copy(draft = it.draft.copy(goalType = type, targetTime = if (type == GoalType.TARGET_TIME) it.draft.targetTime else ""), errorMessage = null)
  }

  fun continueFromType() {
    if (_uiState.value.draft.goalType == null) _uiState.update { it.copy(errorMessage = "Choose a goal type.") }
    else _uiState.update { it.copy(step = GoalCreationStep.CONTEXT, errorMessage = null) }
  }

  fun setTargetTime(value: String) = _uiState.update { it.copy(draft = it.draft.copy(targetTime = value), errorMessage = null) }
  fun continueFromContext() = _uiState.update { it.copy(step = GoalCreationStep.DETAILS, errorMessage = null) }

  fun review() {
    val draft = buildDraft() ?: return
    val validation = validateGoalDraft(draft)
    if (!validation.isValid) _uiState.update { it.copy(errorMessage = validation.errors.joinToString(" ")) }
    else _uiState.update { it.copy(step = GoalCreationStep.REVIEW, errorMessage = null) }
  }

  fun back() = _uiState.update {
    val previous = when (it.step) {
      GoalCreationStep.OVERVIEW -> GoalCreationStep.OVERVIEW
      GoalCreationStep.DISTANCE -> GoalCreationStep.OVERVIEW
      GoalCreationStep.TYPE -> GoalCreationStep.DISTANCE
      GoalCreationStep.CONTEXT -> GoalCreationStep.TYPE
      GoalCreationStep.DETAILS -> GoalCreationStep.CONTEXT
      GoalCreationStep.REVIEW -> GoalCreationStep.DETAILS
    }
    it.copy(step = previous, errorMessage = null)
  }

  fun startGoal() {
    val draft = buildDraft() ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, errorMessage = null) }
      try {
        goalRepository.createGoal(draft)
        _uiState.update { it.copy(step = GoalCreationStep.OVERVIEW, isSaving = false, showDetails = true) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isSaving = false, errorMessage = "Could not save the goal.") }
      }
    }
  }

  fun toggleDetails() = _uiState.update { it.copy(showDetails = !it.showDetails) }

  fun pauseGoal() = changeGoalStatus { goalRepository.pauseGoal(it) }
  fun archiveGoal() = changeGoalStatus { goalRepository.archiveGoal(it) }

  fun selectedCapability(): DistanceGoalCapability? = _uiState.value.draft.distance?.let { _uiState.value.capabilities.byDistance[it] }

  fun presentationForDraft(): GoalPresentation? = buildDraft()?.let { presentation(it, _uiState.value.profile) }

  fun presentationForGoal(goal: TrainingGoal): GoalPresentation {
    val result = presentation(
      TrainingGoalDraft(goal.distance, goal.customDistanceMeters, goal.goalType, goal.targetTimeSeconds),
      _uiState.value.profile,
    )
    val starting = goal.startingPerformanceSnapshot.bestBenchmark?.durationSeconds
    return result.copy(
      improvementSinceGoalStartSeconds = if (starting != null && result.benchmarkDurationSeconds != null) {
        starting - result.benchmarkDurationSeconds
      } else null,
    )
  }

  private fun presentation(draft: TrainingGoalDraft, profile: AthletePerformanceProfile?): GoalPresentation {
    val distanceMeters = requireGoalDistanceMeters(draft.distance, draft.customDistanceMeters)
    val benchmark = capabilityEvaluator.evaluate(profile).byDistance[draft.distance]?.benchmark?.personalBest
    val pace = draft.targetTimeSeconds?.let { targetPaceSecondsPerKm(it, distanceMeters) }
    val comparison = if (benchmark != null && draft.targetTimeSeconds != null) comparePerformance(benchmark.durationSeconds, draft.targetTimeSeconds) else null
    return GoalPresentation(distanceMeters, pace, benchmark?.durationSeconds, comparison, assessmentEvaluator.evaluate(draft, profile))
  }

  private fun buildDraft(): TrainingGoalDraft? {
    val input = _uiState.value.draft
    val distance = input.distance ?: return null.also { _uiState.update { it.copy(errorMessage = "Choose a distance.") } }
    val type = input.goalType ?: return null.also { _uiState.update { it.copy(errorMessage = "Choose a goal type.") } }
    val custom = if (distance == GoalDistance.CUSTOM) input.customDistanceKm.toDoubleOrNull()?.times(1_000.0) else null
    val target = if (type == GoalType.TARGET_TIME) parseGoalDuration(input.targetTime) else null
    return TrainingGoalDraft(distance, custom, type, target)
  }

  private fun changeGoalStatus(action: suspend (String) -> Unit) {
    val id = _uiState.value.activeGoal?.id ?: return
    viewModelScope.launch {
      try { action(id) } catch (exception: CancellationException) { throw exception }
      catch (_: Exception) { _uiState.update { it.copy(errorMessage = "Could not update the goal.") } }
    }
  }
}
