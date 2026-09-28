package com.example.openruncoach.domain.simulation

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.workout.WorkoutPurpose
import com.example.openruncoach.domain.workout.WorkoutType
import java.time.Instant

enum class SimulationScenarioKey(val displayName: String, val summary: String) {
  EASY_DEVIATION("A · Current Easy deviation", "3.78 km, faster and harder than planned, with mild pain"),
  CLEAN_EASY("B · Clean Easy run", "Easy prescription closely followed with continuous movement"),
  MISSING_HEART_RATE("C · Missing heart rate", "Useful Easy-run evidence with HR genuinely absent"),
  HARD_CONTINUOUS_5K("D · Hard continuous 5K", "Continuous 5K at RPE 9 without pain"),
  HARD_5K_POOR_RESPONSE("E · Hard 5K with walking", "Walking, strong fade, poor recovery, and mild pain"),
  NEAR_COMPLETION("F · Near 5K completion", "Continuous 4.78 km against an active Complete 5K goal"),
  EARLY_GOAL_COMPLETION("G · Early goal completion", "Ordinary Easy workout demonstrates a continuous 5K early"),
  SAME_DAY_ASSOCIATION("H · Same-day association", "One prescription and two distinct canonical runs on the same date"),
  HARD_FIRST_5K("I · Hard first 5K", "Limited history followed by a continuous high-cost 5K"),
  ENDURANCE_THEN_HARD_5K("J · Established endurance + hard 5K", "Comfortable 6–8 km history followed by a hard 5K"),
  ONE_BAD_RUN("K · One bad run after good history", "A painful high-effort Easy run after successful aerobic training"),
  POSITIVE_EASY_TRAINING("L · Repeated positive Easy training", "Repeated well-tolerated Easy sessions with useful volume"),
  LITTLE_EVIDENCE("M · Little evidence", "One unassessed run with no invented capability or readiness"),
  GS1_BEGINNER_5K("GS1 · Beginner 5K Complete", "2.5 km completed; continuity is insufficiently observed"),
  GS2_NEARLY_5K("GS2 · Nearly Complete 5K", "Repeated aerobic runs; continuous 4.8 km without a qualifying 5K"),
  GS3_PERFORMANCE_5K("GS3 · 5K Performance Athlete", "Comfortable continuous 8 km and repeated aerobic evidence"),
  GS4_TARGET_30("GS4 · 5K Target 30:00", "Established distance; 6:00/km is target context only"),
  GS5_COMPLETE_10K("GS5 · 10K Complete from 5K Base", "Comfortable continuous 5–6 km; develop endurance"),
  GS6_HALF_MARATHON("GS6 · Half Marathon Complete", "Shorter aerobic history; develop durability and volume tolerance"),
  GS7_LIMITED("GS7 · Limited Evidence", "5K completion goal with unassessed, sparse evidence"),
  GS8_HIGH_COST("GS8 · High-Cost Latest Run", "10K direction persists after a high-cost latest session"),
}

enum class SimulatedMovement { NONE, CONTINUOUS, WALKING_AND_STRONG_FADE }

data class SimulatedWorkoutInput(
  val name: String,
  val distanceMeters: Double,
  val type: WorkoutType,
  val purpose: WorkoutPurpose,
  val targetRpeMinimum: Int,
  val targetRpeMaximum: Int,
  val targetPaceMinimumSecondsPerKm: Double? = null,
  val targetPaceMaximumSecondsPerKm: Double? = null,
)

data class SimulatedRunInput(
  val id: String,
  val startTime: Instant,
  val durationSeconds: Long,
  val distanceMeters: Double,
  val averagePaceSecondsPerKm: Double,
  val averageHeartRate: Double?,
  val maxHeartRate: Long?,
  val movement: SimulatedMovement,
)

data class SimulatedCheckoutInput(
  val runId: String,
  val rpe: Int,
  val cardioEffort: CardioEffort = CardioEffort.APPROPRIATE,
  val muscleFatigue: MuscleFatigue = MuscleFatigue.NONE,
  val pain: PainLevel = PainLevel.NONE,
  val completion: CompletionStatus = CompletionStatus.COMPLETED,
  val readiness: Int? = 4,
)

data class SimulationScenarioPreset(
  val key: SimulationScenarioKey,
  val goalDistance: GoalDistance,
  val workout: SimulatedWorkoutInput,
  val runs: List<SimulatedRunInput>,
  val checkout: SimulatedCheckoutInput,
  val evaluateGoalCompletion: Boolean = false,
  val historicalSessions: List<SimulatedSessionInput> = emptyList(),
  val unrelatedRuns: List<SimulatedRunInput> = emptyList(),
  val persistPrimaryCheckout: Boolean = true,
  val goalType: GoalType = GoalType.COMPLETE_DISTANCE,
  val targetTimeSeconds: Long? = null,
) {
  val sessions: List<SimulatedSessionInput>
    get() = historicalSessions + SimulatedSessionInput(workout, runs.first(), checkout)

  val allRuns: List<SimulatedRunInput>
    get() = sessions.map(SimulatedSessionInput::run) + unrelatedRuns
}

data class SimulatedSessionInput(
  val workout: SimulatedWorkoutInput,
  val run: SimulatedRunInput,
  val checkout: SimulatedCheckoutInput,
)

object SimulationScenarios {
  private val runDate = Instant.parse("2026-09-27T08:00:00Z")
  val all: List<SimulationScenarioPreset> = SimulationScenarioKey.entries.map(::preset)

  fun preset(key: SimulationScenarioKey): SimulationScenarioPreset = when (key) {
    SimulationScenarioKey.EASY_DEVIATION -> easy(
      key,
      run("easy-deviation", 3_780.0, 1_571, 415.0, 147.0, 168, SimulatedMovement.NONE),
      SimulatedCheckoutInput("easy-deviation", 6, pain = PainLevel.MILD),
      pace = 465.0 to 545.0,
    )
    SimulationScenarioKey.CLEAN_EASY -> easy(
      key,
      run("clean-easy", 4_000.0, 1_920, 480.0, 139.0, 154, SimulatedMovement.CONTINUOUS),
      SimulatedCheckoutInput("clean-easy", 3, cardioEffort = CardioEffort.EASY, readiness = 4),
      pace = 465.0 to 545.0,
    )
    SimulationScenarioKey.MISSING_HEART_RATE -> easy(
      key,
      run("missing-hr", 4_000.0, 1_920, 480.0, null, null, SimulatedMovement.CONTINUOUS),
      SimulatedCheckoutInput("missing-hr", 3, cardioEffort = CardioEffort.EASY),
      pace = 465.0 to 545.0,
    )
    SimulationScenarioKey.HARD_CONTINUOUS_5K -> hard5k(
      key,
      run("hard-continuous-5k", 5_000.0, 1_800, 360.0, 174.0, 188, SimulatedMovement.CONTINUOUS),
      SimulatedCheckoutInput("hard-continuous-5k", 9, readiness = 4),
    )
    SimulationScenarioKey.HARD_5K_POOR_RESPONSE -> hard5k(
      key,
      run("hard-walking-5k", 5_000.0, 2_100, 420.0, 178.0, 191, SimulatedMovement.WALKING_AND_STRONG_FADE),
      SimulatedCheckoutInput(
        "hard-walking-5k",
        9,
        cardioEffort = CardioEffort.LIMIT,
        muscleFatigue = MuscleFatigue.SEVERE,
        pain = PainLevel.MILD,
        readiness = 1,
      ),
    )
    SimulationScenarioKey.NEAR_COMPLETION -> SimulationScenarioPreset(
      key,
      GoalDistance.FIVE_K,
      goalWorkout("Near-completion 5K", 5_000.0),
      listOf(run("near-completion", 4_780.0, 2_000, 418.4, 153.0, 170, SimulatedMovement.CONTINUOUS)),
      SimulatedCheckoutInput("near-completion", 5),
      evaluateGoalCompletion = true,
    )
    SimulationScenarioKey.EARLY_GOAL_COMPLETION -> SimulationScenarioPreset(
      key,
      GoalDistance.FIVE_K,
      easyWorkout("Ordinary Easy Run", 4_000.0, 3, 4, 465.0 to 545.0),
      listOf(run("early-completion", 5_100.0, 2_100, 411.8, 151.0, 169, SimulatedMovement.CONTINUOUS)),
      SimulatedCheckoutInput("early-completion", 5),
      evaluateGoalCompletion = true,
    )
    SimulationScenarioKey.SAME_DAY_ASSOCIATION -> SimulationScenarioPreset(
      key,
      GoalDistance.TEN_K,
      easyWorkout("Planned Easy Run", 4_000.0, 3, 4, 465.0 to 545.0),
      listOf(run("same-day-planned-run", 4_000.0, 1_920, 480.0, 140.0, 156, SimulatedMovement.CONTINUOUS)),
      SimulatedCheckoutInput("same-day-planned-run", 3, cardioEffort = CardioEffort.EASY),
      unrelatedRuns = listOf(
        run("same-day-unrelated-run", 2_000.0, 900, 450.0, 132.0, 148, SimulatedMovement.CONTINUOUS, 3 * 3_600L),
      ),
    )
    SimulationScenarioKey.HARD_FIRST_5K -> hard5k(
      key,
      run("hard-first-5k", 5_000.0, 2_100, 420.0, 174.0, 188, SimulatedMovement.CONTINUOUS),
      SimulatedCheckoutInput("hard-first-5k", 9, readiness = 4),
    )
    SimulationScenarioKey.ENDURANCE_THEN_HARD_5K -> longitudinal(
      key,
      listOf(
        easySession("j-easy-6k", 6_000.0, 2_940, 3, -22),
        easySession("j-easy-7k", 7_000.0, 3_395, 3, -15),
        easySession("j-easy-8k", 8_000.0, 3_840, 4, -7),
      ),
      hardSession("j-hard-5k", 0),
    )
    SimulationScenarioKey.ONE_BAD_RUN -> longitudinal(
      key,
      listOf(
        easySession("k-easy-6k", 6_000.0, 2_940, 3, -22),
        easySession("k-easy-7k", 7_000.0, 3_395, 3, -15),
        easySession("k-easy-75k", 7_500.0, 3_638, 4, -7),
      ),
      SimulatedSessionInput(
        easyWorkout("Difficult Easy Run", 4_000.0, 3, 4, 465.0 to 545.0),
        run("k-bad-easy", 4_000.0, 2_080, 520.0, 166.0, 184, SimulatedMovement.WALKING_AND_STRONG_FADE),
        SimulatedCheckoutInput(
          "k-bad-easy", 8, cardioEffort = CardioEffort.HARD,
          muscleFatigue = MuscleFatigue.MODERATE, pain = PainLevel.MILD, readiness = 1,
        ),
      ),
    )
    SimulationScenarioKey.POSITIVE_EASY_TRAINING -> longitudinal(
      key,
      listOf(
        easySession("l-easy-1", 4_500.0, 2_205, 3, -26),
        easySession("l-easy-2", 5_000.0, 2_425, 3, -20),
        easySession("l-easy-3", 5_500.0, 2_640, 4, -14),
        easySession("l-easy-4", 6_000.0, 2_850, 3, -8),
      ),
      easySession("l-easy-5", 6_200.0, 2_914, 4, 0),
    )
    SimulationScenarioKey.LITTLE_EVIDENCE -> SimulationScenarioPreset(
      key,
      GoalDistance.TEN_K,
      easyWorkout("Unperformed Easy Run", 3_000.0, 3, 4, 480.0 to 560.0),
      listOf(run("m-unassessed-run", 1_200.0, 720, 600.0, null, null, SimulatedMovement.NONE)),
      SimulatedCheckoutInput("m-unassessed-run", 4),
      persistPrimaryCheckout = false,
    )
    SimulationScenarioKey.GS1_BEGINNER_5K -> {
      val session = easySession("gs1-short", 2_500.0, 1_200, 3, 0)
      longitudinal(key, emptyList(), session.copy(run = session.run.copy(movement = SimulatedMovement.NONE)))
        .copy(goalDistance = GoalDistance.FIVE_K, evaluateGoalCompletion = true)
    }
    SimulationScenarioKey.GS2_NEARLY_5K -> longitudinal(
      key,
      listOf(
        easySession("gs2-1", 3_500.0, 1_680, 3, -18),
        easySession("gs2-2", 4_000.0, 1_920, 3, -12),
        easySession("gs2-3", 4_500.0, 2_160, 3, -6),
      ),
      easySession("gs2-4", 4_800.0, 2_304, 3, 0),
    ).copy(goalDistance = GoalDistance.FIVE_K, evaluateGoalCompletion = true)
    SimulationScenarioKey.GS3_PERFORMANCE_5K -> strategyBase(key)
      .copy(goalDistance = GoalDistance.FIVE_K, goalType = GoalType.IMPROVE_PERFORMANCE)
    SimulationScenarioKey.GS4_TARGET_30 -> strategyBase(key)
      .copy(goalDistance = GoalDistance.FIVE_K, goalType = GoalType.TARGET_TIME, targetTimeSeconds = 1_800L)
    SimulationScenarioKey.GS5_COMPLETE_10K -> strategyBase(key, longest = 6_000.0)
    SimulationScenarioKey.GS6_HALF_MARATHON -> strategyBase(key)
      .copy(goalDistance = GoalDistance.HALF_MARATHON)
    SimulationScenarioKey.GS7_LIMITED -> preset(SimulationScenarioKey.LITTLE_EVIDENCE)
      .copy(key = key, goalDistance = GoalDistance.FIVE_K)
    SimulationScenarioKey.GS8_HIGH_COST -> {
      val base = strategyBase(key, longest = 6_000.0)
      val final = hardSession("gs8-hard", 0)
      base.copy(workout = final.workout, runs = listOf(final.run), checkout = final.checkout)
    }
  }

  private fun strategyBase(key: SimulationScenarioKey, longest: Double = 8_000.0): SimulationScenarioPreset {
    val prefix = key.name.lowercase()
    return longitudinal(
      key,
      listOf(
        easySession("$prefix-1", 5_000.0, 2_400, 3, -24),
        easySession("$prefix-2", 5_500.0, 2_640, 3, -18),
        easySession("$prefix-3", 6_000.0, 2_880, 3, -12),
        easySession("$prefix-4", longest, (longest * 0.48).toLong(), 3, -6),
      ),
      easySession("$prefix-5", 5_000.0, 2_400, 3, 0),
    )
  }

  private fun easy(
    key: SimulationScenarioKey,
    actual: SimulatedRunInput,
    checkout: SimulatedCheckoutInput,
    pace: Pair<Double, Double>,
  ) = SimulationScenarioPreset(
    key,
    GoalDistance.TEN_K,
    easyWorkout("Easy Run", 4_000.0, 3, 4, pace),
    listOf(actual),
    checkout,
  )

  private fun hard5k(
    key: SimulationScenarioKey,
    actual: SimulatedRunInput,
    checkout: SimulatedCheckoutInput,
  ) = SimulationScenarioPreset(
    key,
    GoalDistance.TEN_K,
    SimulatedWorkoutInput("Hard continuous 5K", 5_000.0, WorkoutType.RACE_SPECIFIC, WorkoutPurpose.GOAL_COMPLETION, 7, 9),
    listOf(actual),
    checkout,
  )

  private fun longitudinal(
    key: SimulationScenarioKey,
    history: List<SimulatedSessionInput>,
    final: SimulatedSessionInput,
  ) = SimulationScenarioPreset(
    key = key,
    goalDistance = GoalDistance.TEN_K,
    workout = final.workout,
    runs = listOf(final.run),
    checkout = final.checkout,
    historicalSessions = history,
  )

  private fun easySession(
    id: String,
    distance: Double,
    duration: Long,
    rpe: Int,
    dayOffset: Long,
  ): SimulatedSessionInput {
    val pace = duration * 1_000.0 / distance
    return SimulatedSessionInput(
      easyWorkout("Easy Run", distance, 3, 4, 465.0 to 545.0),
      run(id, distance, duration, pace, 140.0 + rpe, 158, SimulatedMovement.CONTINUOUS, dayOffset * 86_400L),
      SimulatedCheckoutInput(id, rpe, cardioEffort = CardioEffort.EASY, readiness = 4),
    )
  }

  private fun hardSession(id: String, dayOffset: Long) = SimulatedSessionInput(
    SimulatedWorkoutInput("Hard continuous 5K", 5_000.0, WorkoutType.RACE_SPECIFIC, WorkoutPurpose.GOAL_COMPLETION, 7, 9),
    run(id, 5_000.0, 1_800, 360.0, 174.0, 188, SimulatedMovement.CONTINUOUS, dayOffset * 86_400L),
    SimulatedCheckoutInput(id, 9, readiness = 4),
  )

  private fun easyWorkout(
    name: String,
    distance: Double,
    rpeMin: Int,
    rpeMax: Int,
    pace: Pair<Double, Double>,
  ) = SimulatedWorkoutInput(
    name, distance, WorkoutType.EASY_RUN, WorkoutPurpose.AEROBIC_BASE, rpeMin, rpeMax, pace.first, pace.second,
  )

  private fun goalWorkout(name: String, distance: Double) = SimulatedWorkoutInput(
    name, distance, WorkoutType.RACE_SPECIFIC, WorkoutPurpose.GOAL_COMPLETION, 4, 5,
  )

  private fun run(
    id: String,
    distance: Double,
    duration: Long,
    pace: Double,
    averageHeartRate: Double?,
    maxHeartRate: Long?,
    movement: SimulatedMovement,
    startOffsetSeconds: Long = 0,
  ) = SimulatedRunInput(
    id, runDate.plusSeconds(startOffsetSeconds), duration, distance, pace, averageHeartRate, maxHeartRate, movement,
  )
}
