package com.example.openruncoach

import android.content.Context
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.simulation.SimulationScenarioLoader
import com.example.openruncoach.data.simulation.SimulationPersistenceTrace
import com.example.openruncoach.domain.simulation.SimulationScenarioKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

object AppEnvironmentController {
  val simulationClock: Clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC)

  private var simulationDatabase: OpenRunCoachDatabase? = null
  private var generation = 0
  private var environments: MutableStateFlow<OpenRunCoachEnvironment>? = null

  fun environment(context: Context): StateFlow<OpenRunCoachEnvironment> = mutableEnvironment(context)

  private fun mutableEnvironment(context: Context): MutableStateFlow<OpenRunCoachEnvironment> = synchronized(this) {
    environments ?: MutableStateFlow(productionEnvironment(context)).also { environments = it }
  }

  suspend fun loadScenario(context: Context, scenario: SimulationScenarioKey) {
    val database = simulationDatabase(context)
    SimulationRuntimeTrace.reset()
    withContext(Dispatchers.IO) {
      database.clearAllTables()
      SimulationScenarioLoader(database, simulationClock).load(scenario)
    }
    generation += 1
    mutableEnvironment(context).value = OpenRunCoachEnvironment(
      key = "simulation:${scenario.name}:$generation",
      database = database,
      clock = simulationClock,
      isSimulation = true,
    )
  }

  suspend fun resetSimulation(context: Context) {
    val database = simulationDatabase(context)
    withContext(Dispatchers.IO) { database.clearAllTables() }
    SimulationPersistenceTrace.reset()
    SimulationRuntimeTrace.reset()
    generation += 1
    mutableEnvironment(context).value = OpenRunCoachEnvironment(
      key = "simulation:empty:$generation",
      database = database,
      clock = simulationClock,
      isSimulation = true,
    )
  }

  fun exitSimulation(context: Context) {
    generation += 1
    mutableEnvironment(context).value = productionEnvironment(context).copy(key = "production:$generation")
  }

  private fun productionEnvironment(context: Context) = OpenRunCoachEnvironment(
    key = "production",
    database = OpenRunCoachDatabase.getInstance(context),
    clock = Clock.systemDefaultZone(),
    isSimulation = false,
  )

  private fun simulationDatabase(context: Context): OpenRunCoachDatabase = synchronized(this) {
    simulationDatabase ?: OpenRunCoachDatabase.createIsolated(
      context,
      "open_run_coach_simulation.db",
    ).also { simulationDatabase = it }
  }
}
