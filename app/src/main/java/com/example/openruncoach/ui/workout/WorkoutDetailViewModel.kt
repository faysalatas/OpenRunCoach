package com.example.openruncoach.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.StructuredWorkoutRepository
import com.example.openruncoach.domain.workout.StructuredWorkout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutDetailUiState(
  val workout: StructuredWorkout? = null,
  val isLoading: Boolean = true,
  val errorMessage: String? = null,
)

class WorkoutDetailViewModel(workoutId: String, repository: StructuredWorkoutRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(WorkoutDetailUiState())
  val uiState = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      repository.observeWorkout(workoutId)
        .catch { _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load the workout.") } }
        .collect { workout -> _uiState.update { it.copy(workout = workout, isLoading = false) } }
    }
  }
}
