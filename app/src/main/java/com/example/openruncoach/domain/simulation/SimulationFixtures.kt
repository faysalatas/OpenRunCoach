package com.example.openruncoach.domain.simulation

import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.coaching.CoachingAction
import com.example.openruncoach.domain.workout.BodyArea
import com.example.openruncoach.domain.workout.IllnessLevel
import com.example.openruncoach.domain.workout.PlannedWorkout
import com.example.openruncoach.domain.workout.PreRunCheckIn
import com.example.openruncoach.domain.workout.ReadinessStatus
import com.example.openruncoach.domain.workout.SorenessLevel
import com.example.openruncoach.domain.workout.WorkoutType
import java.time.Instant
import java.time.LocalDate

object SimulationFixtures {
  val fixedInstant: Instant = Instant.parse("2026-09-21T06:00:00Z")
  val all: List<SimulationScenario> = BuiltInScenario.entries.map(::scenario)

  fun scenario(key: BuiltInScenario): SimulationScenario = when (key) {
    BuiltInScenario.WELL_RECOVERED -> create(
      key, "Well recovered",
      checkIn = checkIn(readiness = 5, energy = 5, sleep = 5, motivation = 5),
      feedback = feedback("actual-well", rpe = 4),
      expected = expected(ReadinessStatus.GREEN, WorkoutType.EASY_RUN, CoachingAction.PROGRESS, WorkoutType.EASY_RUN),
    )
    BuiltInScenario.SORE_LEGS -> create(
      key, "Sore legs",
      baselineType = WorkoutType.TEMPO_RUN,
      checkIn = checkIn(soreness = SorenessLevel.MODERATE, areas = setOf(BodyArea.CALF)),
      actualMinutes = 24,
      feedback = feedback("actual-sore", rpe = 4, fatigue = MuscleFatigue.MODERATE),
      expected = expected(ReadinessStatus.YELLOW, WorkoutType.EASY_RUN, CoachingAction.SMALL_PROGRESS, WorkoutType.EASY_RUN),
    )
    BuiltInScenario.POOR_SLEEP -> create(
      key, "Poor sleep",
      checkIn = checkIn(energy = 2, sleep = 1),
      actualMinutes = 24,
      feedback = feedback("actual-sleep", rpe = 6, fatigue = MuscleFatigue.MODERATE, cardio = CardioEffort.HARD),
      expected = expected(ReadinessStatus.YELLOW, WorkoutType.EASY_RUN, CoachingAction.MAINTAIN, WorkoutType.EASY_RUN),
    )
    BuiltInScenario.SIGNIFICANT_PAIN -> create(
      key, "Significant pain",
      checkIn = checkIn(pain = PainLevel.SIGNIFICANT),
      actualMinutes = 0,
      feedback = feedback("actual-pain", rpe = 1, pain = PainLevel.SIGNIFICANT, completion = CompletionStatus.ABORTED),
      expected = expected(ReadinessStatus.RED, WorkoutType.REST, CoachingAction.RECOVERY, WorkoutType.REST),
    )
    BuiltInScenario.MILD_ILLNESS -> create(
      key, "Mild illness",
      checkIn = checkIn(energy = 2, illness = IllnessLevel.MILD),
      actualMinutes = 24,
      feedback = feedback("actual-mild-illness", rpe = 6, fatigue = MuscleFatigue.MILD),
      expected = expected(ReadinessStatus.YELLOW, WorkoutType.RECOVERY_RUN, CoachingAction.SMALL_PROGRESS, WorkoutType.EASY_RUN),
    )
    BuiltInScenario.SEVERE_ILLNESS -> create(
      key, "Severe illness",
      checkIn = checkIn(energy = 1, illness = IllnessLevel.SEVERE),
      actualMinutes = 0,
      feedback = feedback("actual-severe-illness", rpe = 1, completion = CompletionStatus.ABORTED),
      expected = expected(ReadinessStatus.RED, WorkoutType.REST, CoachingAction.REDUCE, WorkoutType.EASY_RUN),
    )
    BuiltInScenario.HIGH_RPE_COMPLETED -> create(
      key, "High RPE completed",
      checkIn = checkIn(),
      feedback = feedback("actual-high-rpe", rpe = 9, fatigue = MuscleFatigue.SEVERE, cardio = CardioEffort.HARD),
      expected = expected(ReadinessStatus.GREEN, WorkoutType.EASY_RUN, CoachingAction.REDUCE, WorkoutType.EASY_RUN),
    )
    BuiltInScenario.EASY_SUCCESS -> create(
      key, "Easy success",
      checkIn = checkIn(readiness = 5, energy = 5),
      feedback = feedback("actual-easy", rpe = 3, cardio = CardioEffort.EASY),
      expected = expected(ReadinessStatus.GREEN, WorkoutType.EASY_RUN, CoachingAction.PROGRESS, WorkoutType.EASY_RUN),
    )
    BuiltInScenario.ABORTED_WITH_PAIN -> {
      val previous = historyRun()
      create(
        key, "Aborted with pain",
        history = listOf(previous),
        previousFeedback = feedback(previous.healthConnectRecordId, rpe = 8, pain = PainLevel.MILD, completion = CompletionStatus.ABORTED),
        checkIn = checkIn(pain = PainLevel.MILD),
        actualMinutes = 5,
        feedback = feedback("actual-aborted", rpe = 8, pain = PainLevel.MILD, completion = CompletionStatus.ABORTED),
        expected = expected(ReadinessStatus.RED, WorkoutType.REST, CoachingAction.RECOVERY, WorkoutType.REST),
      )
    }
    BuiltInScenario.MULTIPLE_BAD_RECOVERY_SIGNALS -> create(
      key, "Multiple bad recovery signals",
      baselineType = WorkoutType.LONG_RUN,
      checkIn = checkIn(readiness = 2, energy = 1, sleep = 1, soreness = SorenessLevel.MODERATE, stress = 5),
      actualMinutes = 15,
      feedback = feedback("actual-multiple", rpe = 8, fatigue = MuscleFatigue.MODERATE, cardio = CardioEffort.HARD),
      expected = expected(ReadinessStatus.RED, WorkoutType.RECOVERY_RUN, CoachingAction.MAINTAIN, WorkoutType.RECOVERY_RUN),
    )
  }

  private fun create(
    key: BuiltInScenario,
    name: String,
    baselineType: WorkoutType = WorkoutType.EASY_RUN,
    checkIn: PreRunCheckIn,
    actualMinutes: Int = 30,
    feedback: RunFeedback,
    expected: ExpectedSimulationDecision,
    history: List<RunSummary> = listOf(historyRun()),
    previousFeedback: RunFeedback? = null,
  ): SimulationScenario {
    val actual = actualRun(feedback.runId, actualMinutes)
    return SimulationScenario(
      key,
      name,
      history,
      previousFeedback,
      PlannedWorkout("baseline-${key.name.lowercase()}", LocalDate.of(2026, 9, 21), baselineType, 30, 3, if (baselineType == WorkoutType.TEMPO_RUN) 7 else 4, fixedInstant.minusSeconds(86_400)),
      checkIn,
      actual,
      feedback,
      expected,
    )
  }

  private fun checkIn(
    readiness: Int = 4,
    energy: Int = 4,
    sleep: Int = 4,
    soreness: SorenessLevel = SorenessLevel.NONE,
    areas: Set<BodyArea> = emptySet(),
    pain: PainLevel = PainLevel.NONE,
    stress: Int = 2,
    motivation: Int = 4,
    illness: IllnessLevel = IllnessLevel.NONE,
  ) = PreRunCheckIn(LocalDate.of(2026, 9, 21), readiness, energy, sleep, soreness, areas, pain, stress, motivation, illness, null)

  private fun feedback(
    runId: String,
    rpe: Int,
    fatigue: MuscleFatigue = MuscleFatigue.NONE,
    pain: PainLevel = PainLevel.NONE,
    cardio: CardioEffort = CardioEffort.APPROPRIATE,
    completion: CompletionStatus = CompletionStatus.COMPLETED,
  ) = RunFeedback("feedback-$runId", runId, rpe, cardio, fatigue, pain, completion, null, null, fixedInstant, fixedInstant)

  private fun actualRun(id: String, minutes: Int): RunSummary {
    val start = Instant.parse("2026-09-21T08:00:00Z")
    return RunSummary(
      id, start, start.plusSeconds(minutes * 60L), minutes * 60L,
      if (minutes == 0) null else minutes * 160.0,
      null, null, if (minutes == 0) null else 375.0,
      isCanonical = true,
    )
  }

  private fun historyRun(): RunSummary {
    val start = Instant.parse("2026-09-18T08:00:00Z")
    return RunSummary("history-run", start, start.plusSeconds(1_800), 1_800, 5_000.0, null, null, 360.0)
  }

  private fun expected(
    readiness: ReadinessStatus,
    adjusted: WorkoutType,
    coaching: CoachingAction,
    next: WorkoutType,
  ) = ExpectedSimulationDecision(readiness, adjusted, coaching, next)
}
