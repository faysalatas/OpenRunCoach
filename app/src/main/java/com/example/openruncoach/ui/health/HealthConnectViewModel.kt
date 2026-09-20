package com.example.openruncoach.ui.health

import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.HealthConnectRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HealthConnectUiState(
  val isAvailable: Boolean,
  val hasAllPermissions: Boolean,
)

class HealthConnectViewModel(private val repository: HealthConnectRepository) : ViewModel() {
  private val _uiState = MutableStateFlow<HealthConnectUiState?>(null)
  val uiState = _uiState.asStateFlow()
  val requiredPermissions: Set<String> get() = repository.requiredPermissions

  fun refresh() {
    viewModelScope.launch {
      try {
        val isAvailable = repository.availabilityStatus() == HealthConnectClient.SDK_AVAILABLE
        _uiState.value =
          HealthConnectUiState(
            isAvailable = isAvailable,
            hasAllPermissions = isAvailable && repository.hasAllPermissions(),
          )
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.value = HealthConnectUiState(isAvailable = false, hasAllPermissions = false)
      }
    }
  }
}
