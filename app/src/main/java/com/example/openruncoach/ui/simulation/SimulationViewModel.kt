package com.example.openruncoach.ui.simulation

import androidx.lifecycle.ViewModel
import com.example.openruncoach.domain.simulation.BuiltInScenario
import com.example.openruncoach.domain.simulation.SimulationFixtures
import com.example.openruncoach.domain.simulation.SimulationResult
import com.example.openruncoach.domain.simulation.SimulationRunner
import com.example.openruncoach.domain.workout.IdGenerator
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SimulationUiState(
  val selectedScenario: BuiltInScenario = BuiltInScenario.WELL_RECOVERED,
  val result: SimulationResult? = null,
  val visibleStepCount: Int = 0,
)

class SimulationViewModel : ViewModel() {
  private val _uiState = MutableStateFlow(SimulationUiState())
  val uiState = _uiState.asStateFlow()
  val scenarios = SimulationFixtures.all

  fun selectScenario(scenario: BuiltInScenario) {
    _uiState.value = SimulationUiState(selectedScenario = scenario)
  }

  fun reset() {
    _uiState.update { SimulationUiState(selectedScenario = it.selectedScenario) }
  }

  fun step() {
    val state = _uiState.value
    val result = state.result ?: runScenario(state.selectedScenario)
    _uiState.value = state.copy(
      result = result,
      visibleStepCount = (state.visibleStepCount + 1).coerceAtMost(result.timeline.size),
    )
  }

  fun runComplete() {
    val state = _uiState.value
    val result = runScenario(state.selectedScenario)
    _uiState.value = state.copy(result = result, visibleStepCount = result.timeline.size)
  }

  private fun runScenario(key: BuiltInScenario): SimulationResult {
    val clock = Clock.fixed(SimulationFixtures.fixedInstant, ZoneOffset.UTC)
    val runner = SimulationRunner(clock, IdGenerator { "next-${key.name.lowercase()}" })
    return runner.run(SimulationFixtures.scenario(key))
  }
}
