package com.example.openruncoach.ui.health

import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.domain.RunSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LatestRunState {
  data object Loading : LatestRunState
  data object Unavailable : LatestRunState
  data object PermissionRequired : LatestRunState
  data object NoRuns : LatestRunState
  data class Success(val run: RunSummary) : LatestRunState
  data object Error : LatestRunState
}

data class HealthConnectUiState(
  val isAvailable: Boolean? = null,
  val hasAllPermissions: Boolean? = null,
  val latestRun: LatestRunState = LatestRunState.Loading,
)

class HealthConnectViewModel(private val repository: HealthConnectRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(HealthConnectUiState())
  val uiState = _uiState.asStateFlow()
  val requiredPermissions: Set<String> get() = repository.requiredPermissions
  private var refreshJob: Job? = null

  fun refresh() {
    refreshJob?.cancel()
    refreshJob = viewModelScope.launch {
      _uiState.value = HealthConnectUiState()
      try {
        val isAvailable = repository.availabilityStatus() == HealthConnectClient.SDK_AVAILABLE
        if (!isAvailable) {
          _uiState.value = HealthConnectUiState(false, false, LatestRunState.Unavailable)
          return@launch
        }
        _uiState.value = _uiState.value.copy(isAvailable = true)

        val hasAllPermissions = repository.hasAllPermissions()
        if (!hasAllPermissions) {
          _uiState.value = HealthConnectUiState(true, false, LatestRunState.PermissionRequired)
          return@launch
        }

        _uiState.value = HealthConnectUiState(true, true, LatestRunState.Loading)
        val run = repository.latestRunningWorkout()
        _uiState.value =
          HealthConnectUiState(
            true,
            true,
            if (run == null) LatestRunState.NoRuns else LatestRunState.Success(run),
          )
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.value = _uiState.value.copy(latestRun = LatestRunState.Error)
      }
    }
  }
}
