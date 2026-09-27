package com.example.openruncoach.ui.simulation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.AppEnvironmentController
import com.example.openruncoach.OpenRunCoachEnvironment
import com.example.openruncoach.CoachAnalysisRuntimeTrace
import com.example.openruncoach.SimulationRuntimeTrace
import com.example.openruncoach.data.AthleteStateContinuityTrace
import com.example.openruncoach.data.AthleteStateRepositoryIdentityTrace
import com.example.openruncoach.data.simulation.SimulationPersistenceTrace
import com.example.openruncoach.data.simulation.SimulationRunPersistenceTrace
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
  val continuityTrace: List<AthleteStateContinuityTrace> = emptyList(),
  val athleteStateRepositoryIdentity: AthleteStateRepositoryIdentityTrace? = null,
  val activeEnvironmentDatabaseIdentity: String? = null,
  val coachAnalysisRuntimeTrace: CoachAnalysisRuntimeTrace? = null,
  val persistenceTrace: Map<String, SimulationRunPersistenceTrace> = emptyMap(),
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
        val result = withContext(Dispatchers.IO) { environment.athleteStateRepository().rebuildWithTrace() }
        _uiState.update {
          it.copy(
            isLoading = false,
            athleteState = result.state,
            continuityTrace = result.continuityTrace,
            athleteStateRepositoryIdentity = result.repositoryIdentity,
            activeEnvironmentDatabaseIdentity = environment.database.identity(),
            coachAnalysisRuntimeTrace = SimulationRuntimeTrace.coachAnalysisSnapshot(),
            persistenceTrace = SimulationPersistenceTrace.snapshot(),
            errorMessage = null,
          )
        }
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

private fun Any.identity() = "${javaClass.simpleName}@${System.identityHashCode(this)}"
