package com.example.openruncoach.ui.simulation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.AppEnvironmentController
import com.example.openruncoach.OpenRunCoachEnvironment
import com.example.openruncoach.domain.athlete.AthleteState
import com.example.openruncoach.domain.simulation.SimulationScenarioKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SimulationUiState(
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val athleteState: AthleteState? = null,
)

class SimulationViewModel(
  private val context: Context,
  private val environment: OpenRunCoachEnvironment,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SimulationUiState())
  val uiState = _uiState.asStateFlow()

  init {
    refreshAthleteState()
  }

  fun load(scenario: SimulationScenarioKey) = mutate {
    AppEnvironmentController.loadScenario(context, scenario)
  }

  fun reset() = mutate {
    AppEnvironmentController.resetSimulation(context)
  }

  fun exit() {
    AppEnvironmentController.exitSimulation(context)
  }

  fun refreshAthleteState() {
    if (_uiState.value.isLoading) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      try {
        val state = withContext(Dispatchers.IO) { environment.athleteStateRepository().rebuild() }
        _uiState.update { it.copy(isLoading = false, athleteState = state, errorMessage = null) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        _uiState.update {
          it.copy(isLoading = false, errorMessage = "Could not rebuild Athlete State from current evidence.")
        }
      }
    }
  }

  private fun mutate(operation: suspend () -> Unit) {
    if (_uiState.value.isLoading) return
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      try {
        operation()
        _uiState.update { it.copy(isLoading = false) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        _uiState.update { it.copy(isLoading = false, errorMessage = "Could not prepare the simulation scenario.") }
      }
    }
  }
}
