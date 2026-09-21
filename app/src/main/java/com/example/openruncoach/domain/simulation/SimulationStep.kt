package com.example.openruncoach.domain.simulation

enum class SimulationStage {
  BASELINE_WORKOUT,
  PRE_RUN_CHECK_IN,
  ADJUSTMENT,
  SIMULATED_RUN,
  POST_RUN_FEEDBACK,
  COACH_DECISION,
  NEXT_WORKOUT,
}

data class SimulationStep(
  val stage: SimulationStage,
  val title: String,
  val details: List<String>,
)
