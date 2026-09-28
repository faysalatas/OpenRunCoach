package com.example.openruncoach.data

import com.example.openruncoach.domain.athlete.AthleteState
import com.example.openruncoach.domain.goal.GoalStrategy
import com.example.openruncoach.domain.goal.GoalStrategyResolver
import com.example.openruncoach.domain.goal.TrainingGoal

data class GoalStrategySnapshot(val goal: TrainingGoal?, val athleteState: AthleteState, val strategy: GoalStrategy?)

/** Derived on demand from the same authoritative goal/state sources in either environment. */
class GoalStrategyRepository(
  private val goals: TrainingGoalRepository,
  private val athletes: AthleteStateRepository,
  private val resolver: GoalStrategyResolver = GoalStrategyResolver(),
) {
  suspend fun rebuild(): GoalStrategySnapshot = forState(athletes.rebuild())

  /** Reuses an already rebuilt state when diagnostics also need its continuity trace. */
  suspend fun forState(state: AthleteState): GoalStrategySnapshot {
    val goal = goals.getActiveGoal()
    return GoalStrategySnapshot(goal, state, goal?.let { resolver.resolve(it, state) })
  }
}
