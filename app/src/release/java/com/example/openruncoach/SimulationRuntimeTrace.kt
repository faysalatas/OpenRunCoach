package com.example.openruncoach

object SimulationRuntimeTrace {
  const val enabled = false
  fun recordCoachAnalysis(
    environmentKey: String,
    database: Any,
    metricsDao: Any,
    workoutResponseRepository: Any,
  ) = Unit

  fun recordCoachResponse(
    environmentKey: String,
    response: Any?,
    runId: String?,
    continuity: String?,
  ) = Unit
}
