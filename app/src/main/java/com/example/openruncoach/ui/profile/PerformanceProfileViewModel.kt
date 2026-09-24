package com.example.openruncoach.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.AthletePerformanceProfileRepository
import com.example.openruncoach.domain.profile.AthletePerformanceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PerformanceProfileUiState(
  val profile: AthletePerformanceProfile? = null,
  val isLoading: Boolean = true,
  val showExplanations: Boolean = false,
  val errorMessage: String? = null,
)

class PerformanceProfileViewModel(
  private val repository: AthletePerformanceProfileRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(PerformanceProfileUiState())
  val uiState = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      repository.observeProfile()
        .catch { _uiState.update { it.copy(isLoading = false, errorMessage = "Could not load the performance profile.") } }
        .collect { profile -> _uiState.update { it.copy(profile = profile, isLoading = profile == null) } }
    }
    viewModelScope.launch {
      try {
        repository.recalculate()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isLoading = false, errorMessage = "Could not calculate the performance profile.") }
      }
    }
  }

  fun toggleExplanations() {
    _uiState.update { it.copy(showExplanations = !it.showExplanations) }
  }
}
