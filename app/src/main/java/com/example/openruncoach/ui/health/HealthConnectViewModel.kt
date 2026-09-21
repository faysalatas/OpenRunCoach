package com.example.openruncoach.ui.health

import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.PossibleDuplicate
import com.example.openruncoach.domain.WorkoutSource
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
  val duplicateDiagnostics: Map<String, List<PossibleDuplicate>> = emptyMap(),
  val duplicateDiagnosticsError: Boolean = false,
  val historyError: Boolean = false,
  val isAvailable: Boolean? = null,
  val hasAllPermissions: Boolean? = null,
  val accessError: Boolean = false,
  val syncState: SyncState = SyncState.Idle,
  val knownSources: List<WorkoutSource> = emptyList(),
  val preferredSourcePackage: String? = null,
  val preferredSourceError: Boolean = false,
)

class HealthConnectViewModel(
  private val healthConnectRepository: HealthConnectRepository,
  private val runRepository: RunRepository,
  private val settingsRepository: RunSettingsRepository,
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
    viewModelScope.launch {
      runRepository.duplicateDiagnostics
        .catch { _uiState.update { state -> state.copy(duplicateDiagnosticsError = true) } }
        .collect { diagnostics ->
          _uiState.update { state ->
            state.copy(duplicateDiagnostics = diagnostics, duplicateDiagnosticsError = false)
          }
        }
    }
    viewModelScope.launch {
      runRepository.knownSources
        .catch { _uiState.update { state -> state.copy(preferredSourceError = true) } }
        .collect { sources -> _uiState.update { state -> state.copy(knownSources = sources) } }
    }
    viewModelScope.launch {
      settingsRepository.preferredSourcePackage
        .catch { _uiState.update { state -> state.copy(preferredSourceError = true) } }
        .collect { preferredSource ->
          try {
            runRepository.refreshCanonicalSelection(preferredSource)
            _uiState.update { state ->
              state.copy(preferredSourcePackage = preferredSource, preferredSourceError = false)
            }
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: Exception) {
            _uiState.update { state -> state.copy(preferredSourceError = true) }
          }
        }
    }
  }

  fun selectPreferredSource(packageName: String?) {
    viewModelScope.launch {
      try {
        settingsRepository.setPreferredSourcePackage(packageName)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { state -> state.copy(preferredSourceError = true) }
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
