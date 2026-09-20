package com.example.openruncoach.ui.health

import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.domain.RunSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SyncState {
  data object Idle : SyncState
  data object Syncing : SyncState
  data class Success(val count: Int) : SyncState
  data object PermissionRequired : SyncState
  data object Unavailable : SyncState
  data object Error : SyncState
}

data class HealthConnectUiState(
  val recentRuns: List<RunSummary>? = null,
  val historyError: Boolean = false,
  val isAvailable: Boolean? = null,
  val hasAllPermissions: Boolean? = null,
  val accessError: Boolean = false,
  val syncState: SyncState = SyncState.Idle,
)

class HealthConnectViewModel(
  private val healthConnectRepository: HealthConnectRepository,
  private val runRepository: RunRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(HealthConnectUiState())
  val uiState = _uiState.asStateFlow()
  val requiredPermissions: Set<String> get() = healthConnectRepository.requiredPermissions
  private var statusJob: Job? = null
  private var syncJob: Job? = null

  init {
    viewModelScope.launch {
      runRepository.recentRuns
        .catch { _uiState.update { state -> state.copy(historyError = true) } }
        .collect { runs ->
          _uiState.update { state -> state.copy(recentRuns = runs, historyError = false) }
        }
    }
  }

  fun refreshStatus() {
    statusJob?.cancel()
    statusJob = viewModelScope.launch {
      try {
        val isAvailable = healthConnectRepository.availabilityStatus() == HealthConnectClient.SDK_AVAILABLE
        val hasAllPermissions = isAvailable && healthConnectRepository.hasAllPermissions()
        _uiState.update { state ->
          state.copy(
            isAvailable = isAvailable,
            hasAllPermissions = hasAllPermissions,
            accessError = false,
          )
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { state -> state.copy(accessError = true) }
      }
    }
  }

  fun syncRuns() {
    if (syncJob?.isActive == true) return
    syncJob = viewModelScope.launch {
      try {
        val isAvailable = healthConnectRepository.availabilityStatus() == HealthConnectClient.SDK_AVAILABLE
        if (!isAvailable) {
          _uiState.update { it.copy(isAvailable = false, hasAllPermissions = false, syncState = SyncState.Unavailable) }
          return@launch
        }
        if (!healthConnectRepository.hasAllPermissions()) {
          _uiState.update { it.copy(isAvailable = true, hasAllPermissions = false, syncState = SyncState.PermissionRequired) }
          return@launch
        }

        _uiState.update { it.copy(isAvailable = true, hasAllPermissions = true, syncState = SyncState.Syncing) }
        val count = runRepository.syncRuns()
        _uiState.update { it.copy(syncState = SyncState.Success(count)) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(syncState = SyncState.Error) }
      }
    }
  }
}
