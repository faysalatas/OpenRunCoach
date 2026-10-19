package com.example.openruncoach.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.StructuredWorkoutRepository
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutDetailUiState(
  val workout: StructuredWorkout? = null,
  val executionResult: WorkoutExecutionResult? = null,
  val actualRun: RunSummary? = null,
  val feedback: RunFeedback? = null,
  val originalWorkout: StructuredWorkout? = null,
  val adjustmentReasons: List<String> = emptyList(),
  val isLoading: Boolean = true,
  val errorMessage: String? = null,
)

class WorkoutDetailViewModel(workoutId: String, repository: StructuredWorkoutRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(WorkoutDetailUiState())
  val uiState = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      repository.observeWorkoutDetails(workoutId)
        .catch { _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load the workout.") } }
        .collect { details ->
          _uiState.update {
            it.copy(
              workout = details?.workout,
              executionResult = details?.executionResult,
              actualRun = details?.actualRun,
              feedback = details?.feedback,
              originalWorkout = details?.originalWorkout,
              adjustmentReasons = details?.adjustmentReasons.orEmpty(),
              isLoading = false,
            )
          }
        }
    }
  }
}
