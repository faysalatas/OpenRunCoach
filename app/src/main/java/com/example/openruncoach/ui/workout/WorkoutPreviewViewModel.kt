package com.example.openruncoach.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.StructuredWorkoutRepository
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.StructuredWorkoutSamples
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutPreviewUiState(
  val workouts: List<StructuredWorkout> = StructuredWorkoutSamples.all,
  val isSaving: Boolean = false,
  val openWorkoutId: String? = null,
  val errorMessage: String? = null,
)

class WorkoutPreviewViewModel(private val repository: StructuredWorkoutRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(WorkoutPreviewUiState())
  val uiState = _uiState.asStateFlow()

  fun open(workout: StructuredWorkout) {
    if (_uiState.value.isSaving) return
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, errorMessage = null) }
      try {
        if (repository.getWorkout(workout.id) == null) repository.save(workout, isPreview = true)
        _uiState.update { it.copy(isSaving = false, openWorkoutId = workout.id) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        _uiState.update { it.copy(isSaving = false, errorMessage = "Could not save the sample workout.") }
      }
    }
  }

  fun consumedNavigation() = _uiState.update { it.copy(openWorkoutId = null) }
}
