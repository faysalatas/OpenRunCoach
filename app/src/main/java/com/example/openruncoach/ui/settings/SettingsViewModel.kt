package com.example.openruncoach.ui.settings

import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.TrainingAvailabilityRepository
import com.example.openruncoach.data.TrainingPlanRepository
import com.example.openruncoach.domain.plan.TrainingAvailability
import com.example.openruncoach.domain.plan.RunningDayPreference
import com.example.openruncoach.domain.plan.completionDateConflict
import java.time.DayOfWeek
import java.time.LocalDate
import com.example.openruncoach.domain.WorkoutSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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

data class SettingsUiState(
  val isAvailable: Boolean? = null,
  val hasAllPermissions: Boolean? = null,
  val accessError: Boolean = false,
  val syncState: SyncState = SyncState.Idle,
  val knownSources: List<WorkoutSource> = emptyList(),
  val preferredSourcePackage: String? = null,
  val preferredSourceError: Boolean = false,
  val availabilityLoading: Boolean = true,
  val availability: TrainingAvailability? = null,
  val availabilityDraft: TrainingAvailability? = null,
  val availabilityDirty: Boolean = false,
  val availabilitySaving: Boolean = false,
  val availabilityError: String? = null,
  val availabilityMessage: String? = null,
  val completionDate: LocalDate? = null,
) {
  val availabilityConflict: String? get() = availabilityDraft.completionDateConflict(completionDate)?.let {
    "Completion Run on ${it.date} conflicts with these days. It will stay on its saved date. Before regenerating, make that day available or explicitly choose another Completion Run date in Plan."
  }
}

class SettingsViewModel(
  private val healthConnectRepository: HealthConnectRepository,
  private val runRepository: RunRepository,
  private val settingsRepository: RunSettingsRepository,
  private val availabilityRepository: TrainingAvailabilityRepository,
  private val planRepository: TrainingPlanRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState = _uiState.asStateFlow()
  val requiredPermissions: Set<String> get() = healthConnectRepository.requiredPermissions
  private var statusJob: Job? = null
  private var syncJob: Job? = null

  init {
    viewModelScope.launch {
      combine(availabilityRepository.availability, planRepository.observeActivePlan()) { availability, plan ->
        availability to plan?.completionMilestone?.scheduledDate
      }.catch { _uiState.update { it.copy(availabilityError = "Could not load running days.") } }
        .collect { (availability, completionDate) ->
          _uiState.update { state -> state.copy(
            availabilityLoading = false, availability = availability,
            availabilityDraft = if (state.availabilityDirty) state.availabilityDraft else availability,
            completionDate = completionDate,
          ) }
        }
    }
    viewModelScope.launch {
      runRepository.knownSources
        .catch { _uiState.update { it.copy(preferredSourceError = true) } }
        .collect { sources -> _uiState.update { it.copy(knownSources = sources) } }
    }
    viewModelScope.launch {
      settingsRepository.preferredSourcePackage
        .catch { _uiState.update { it.copy(preferredSourceError = true) } }
        .collect { preferredSource ->
          try {
            runRepository.refreshCanonicalSelection(preferredSource)
            _uiState.update { it.copy(preferredSourcePackage = preferredSource, preferredSourceError = false) }
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: Exception) {
            _uiState.update { it.copy(preferredSourceError = true) }
          }
        }
    }
  }

  fun customizeAvailability() = _uiState.update {
    it.copy(availabilityDraft = TrainingAvailability(DayOfWeek.entries.toSet()), availabilityDirty = true,
      availabilityMessage = null, availabilityError = null)
  }

  fun selectRunningDay(day: DayOfWeek, preference: RunningDayPreference) = _uiState.update {
    if (it.availabilitySaving || it.availabilityLoading) it else it.copy(
      availabilityDraft = (it.availabilityDraft ?: TrainingAvailability(DayOfWeek.entries.toSet())).withDay(day, preference),
      availabilityDirty = true, availabilityMessage = null, availabilityError = null,
    )
  }

  fun resetAvailabilityDraft() = _uiState.update {
    it.copy(availabilityDraft = null, availabilityDirty = true, availabilityMessage = null, availabilityError = null)
  }

  fun discardAvailabilityChanges() = _uiState.update {
    it.copy(availabilityDraft = it.availability, availabilityDirty = false, availabilityError = null, availabilityMessage = null)
  }

  fun saveAvailability() {
    val state = _uiState.value
    if (!state.availabilityDirty || state.availabilitySaving || state.availabilityLoading) return
    _uiState.update { it.copy(availabilitySaving = true, availabilityError = null) }
    viewModelScope.launch {
      try {
        availabilityRepository.save(state.availabilityDraft)
        _uiState.update { it.copy(availability = state.availabilityDraft, availabilityDirty = false,
          availabilitySaving = false, availabilityMessage = "Running days saved. Existing prescriptions stay as scheduled. Use Plan → Plan details → Regenerate plan to apply these days.") }
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        _uiState.update { it.copy(availabilitySaving = false, availabilityError = "Could not save running days. Try again.") }
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
        _uiState.update { it.copy(preferredSourceError = true) }
      }
    }
  }

  fun refreshStatus() {
    statusJob?.cancel()
    statusJob = viewModelScope.launch {
      try {
        val available = healthConnectRepository.availabilityStatus() == HealthConnectClient.SDK_AVAILABLE
        val permissions = available && healthConnectRepository.hasAllPermissions()
        _uiState.update { it.copy(isAvailable = available, hasAllPermissions = permissions, accessError = false) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(accessError = true) }
      }
    }
  }

  fun syncRuns() {
    if (syncJob?.isActive == true) return
    syncJob = viewModelScope.launch {
      try {
        val available = healthConnectRepository.availabilityStatus() == HealthConnectClient.SDK_AVAILABLE
        if (!available) {
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
