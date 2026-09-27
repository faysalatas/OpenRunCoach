package com.example.openruncoach.data

import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.athlete.AthleteState
import com.example.openruncoach.domain.athlete.AthleteStateBuilder

/** Rebuilds AthleteState from authoritative canonical history, profile aggregates, and responses. */
class AthleteStateRepository(
  private val database: OpenRunCoachDatabase,
  private val profileRepository: AthletePerformanceProfileRepository,
  private val builder: AthleteStateBuilder = AthleteStateBuilder(),
) {
  suspend fun rebuild(): AthleteState {
    val profile = profileRepository.recalculate()
    val runs = database.runDao().getCanonicalRuns().map { it.toRunSummary() }
    val responses = WorkoutResponseRepository(database).getAll()
    return builder.build(profile, runs, responses)
  }
}
