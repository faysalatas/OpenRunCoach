package com.example.openruncoach.data.simulation

data class SimulationRunPersistenceTrace(
  val runId: String,
  val databaseIdentity: String,
  val daoIdentity: String,
  val repositoryIdentity: String,
  val generatedSpeedSampleCount: Int,
  val metricsSpeedSampleCount: Int,
  val speedSampleEntityCount: Int,
  val persistedSpeedSampleCount: Int,
  val speedSampleCountAfterLoad: Int,
  val loaderRepositorySpeedSampleCount: Int,
)

/** Debug-only evidence captured at the actual simulation write boundaries. */
internal object SimulationPersistenceTrace {
  private val entries = linkedMapOf<String, SimulationRunPersistenceTrace>()

  @Synchronized
  fun reset() {
    entries.clear()
  }

  @Synchronized
  fun record(entry: SimulationRunPersistenceTrace) {
    entries[entry.runId] = entry
  }

  @Synchronized
  fun updateAfterLoad(runId: String, count: Int) {
    entries[runId]?.let { entries[runId] = it.copy(speedSampleCountAfterLoad = count) }
  }

  @Synchronized
  fun snapshot(): Map<String, SimulationRunPersistenceTrace> = entries.toMap()
}
