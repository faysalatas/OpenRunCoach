package com.example.openruncoach.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.CoachingWorkflowRepository
import com.example.openruncoach.data.PersistedWorkoutLifecycle
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.workout.BodyArea
import com.example.openruncoach.domain.workout.IllnessLevel
import com.example.openruncoach.domain.workout.PreRunCheckIn
import com.example.openruncoach.domain.workout.RecentTrainingVolume
import com.example.openruncoach.domain.workout.SorenessLevel
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckInForm(
  val readiness: Int = 3,
  val energy: Int = 3,
  val sleepQuality: Int = 3,
  val soreness: SorenessLevel = SorenessLevel.NONE,
  val sorenessAreas: Set<BodyArea> = emptySet(),
  val pain: PainLevel = PainLevel.NONE,
  val stress: Int = 3,
  val motivation: Int = 3,
  val illness: IllnessLevel = IllnessLevel.NONE,
  val notes: String = "",
)

data class PlanUiState(
  val isLoading: Boolean = true,
  val lifecycle: PersistedWorkoutLifecycle? = null,
  val showCheckIn: Boolean = false,
  val isSaving: Boolean = false,
  val form: CheckInForm = CheckInForm(),
  val errorMessage: String? = null,
)

class PlanViewModel(
  private val workflowRepository: CoachingWorkflowRepository,
  runRepository: RunRepository,
  private val feedbackRepository: RunFeedbackRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(PlanUiState())
  val uiState = _uiState.asStateFlow()
  private var canonicalRuns: List<RunSummary> = emptyList()

  init {
    viewModelScope.launch {
      try {
        workflowRepository.ensureInitialWorkout()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isLoading = false, errorMessage = "Could not create the planned workout.") }
      }
    }
    viewModelScope.launch {
      workflowRepository.currentLifecycle
        .catch { _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load the workout plan.") } }
        .collect { lifecycle -> _uiState.update { it.copy(isLoading = false, lifecycle = lifecycle) } }
    }
    viewModelScope.launch {
      runRepository.canonicalRuns.collect { canonicalRuns = it }
    }
  }

  fun beginCheckIn() {
    _uiState.update {
      if (it.lifecycle?.lifecycleState == WorkoutLifecycleState.CHECK_IN_REQUIRED) {
        it.copy(showCheckIn = true, errorMessage = null)
      } else it
    }
  }

  fun cancelCheckIn() { _uiState.update { it.copy(showCheckIn = false, errorMessage = null) } }

  fun updateForm(change: (CheckInForm) -> CheckInForm) {
    _uiState.update { it.copy(form = change(it.form), errorMessage = null) }
  }

  fun saveCheckIn() {
    val state = _uiState.value
    val baseline = state.lifecycle?.baselineWorkout ?: return
    if (state.isSaving ||
      state.lifecycle.checkIn != null ||
      state.lifecycle.lifecycleState != WorkoutLifecycleState.CHECK_IN_REQUIRED
    ) return
    val checkIn = state.form.toDomain(baseline.date)
    checkIn.validationError()?.let { error ->
      _uiState.update { it.copy(errorMessage = error) }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, errorMessage = null) }
      try {
        val recent = canonicalRuns.filter { run ->
          val runDate = run.startTime.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
          !runDate.isAfter(baseline.date) && !runDate.isBefore(baseline.date.minusDays(7))
        }
        val previousFeedback = canonicalRuns.firstOrNull()?.let { feedbackRepository.getFeedbackForRun(it.healthConnectRecordId) }
        workflowRepository.evaluateAndSaveCheckIn(
          baseline,
          checkIn,
          recent,
          RecentTrainingVolume(
            recent.size,
            recent.sumOf(RunSummary::durationSeconds),
            recent.mapNotNull(RunSummary::distanceMeters).sum(),
          ),
          previousFeedback,
        )
        _uiState.update { it.copy(isSaving = false, showCheckIn = false) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isSaving = false, errorMessage = exception.message ?: "Could not save check-in.") }
      }
    }
  }
}

private fun CheckInForm.toDomain(date: java.time.LocalDate) = PreRunCheckIn(
  date,
  readiness,
  energy,
  sleepQuality,
  soreness,
  sorenessAreas,
  pain,
  stress,
  motivation,
  illness,
  notes.trim().takeIf(String::isNotEmpty),
)
