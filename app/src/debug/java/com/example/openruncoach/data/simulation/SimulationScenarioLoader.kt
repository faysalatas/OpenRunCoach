package com.example.openruncoach.data.simulation

import com.example.openruncoach.data.GoalCompletionRepository
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunMetricsRepository
import com.example.openruncoach.data.TrainingGoalRepository
import com.example.openruncoach.data.TrainingPlanRepository
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.domain.RunFeedbackDraft
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.SourceDeviceType
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.TrainingGoalDraft
import com.example.openruncoach.domain.goal.TrainingGoalFactory
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.RunTimeSeries
import com.example.openruncoach.domain.metrics.TimedMetricSample
import com.example.openruncoach.domain.plan.PlannedWorkoutEntry
import com.example.openruncoach.domain.plan.TrainingBlock
import com.example.openruncoach.domain.plan.TrainingBlockType
import com.example.openruncoach.domain.plan.TrainingPlan
import com.example.openruncoach.domain.plan.TrainingPlanStatus
import com.example.openruncoach.domain.plan.TrainingWeek
import com.example.openruncoach.domain.plan.WeekLoadClassification
import com.example.openruncoach.domain.plan.WeeklyWorkoutSlot
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.simulation.SimulatedMovement
import com.example.openruncoach.domain.simulation.SimulatedRunInput
import com.example.openruncoach.domain.simulation.SimulatedWorkoutInput
import com.example.openruncoach.domain.simulation.SimulationScenarioKey
import com.example.openruncoach.domain.simulation.SimulationScenarios
import com.example.openruncoach.domain.selectCanonicalRuns
import com.example.openruncoach.domain.workout.DistanceCompletion
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.domain.workout.PlannedWorkout
import com.example.openruncoach.domain.workout.RpeTarget
import com.example.openruncoach.domain.workout.SegmentCategory
import com.example.openruncoach.domain.workout.SingleSegmentBlock
import com.example.openruncoach.domain.workout.WorkoutOrigin
import com.example.openruncoach.domain.workout.WorkoutSegment
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class SimulationScenarioLoader(
  private val database: OpenRunCoachDatabase,
  private val clock: Clock,
) {
  suspend fun load(key: SimulationScenarioKey) {
    val scenario = SimulationScenarios.preset(key)
    val profileRepository = com.example.openruncoach.data.AthletePerformanceProfileRepository(
      database,
      AthletePerformanceProfileCalculator(clock, clock.zone),
    )
    val goalRepository = TrainingGoalRepository(
      database,
      profileRepository,
      TrainingGoalFactory(
        Clock.fixed(clock.instant().minus(Duration.ofDays(70)), clock.zone),
        idProvider = { "simulation-goal" },
      ),
    )
    val goal = goalRepository.createGoal(
      TrainingGoalDraft(scenario.goalDistance, goalType = GoalType.COMPLETE_DISTANCE)
    )
    val sessionWorkouts = scenario.sessions.map { session ->
      session.workout.toWorkout(
        date = session.run.startTime.atZone(clock.zone).toLocalDate(),
        clock = clock,
        id = "simulation-workout:${session.run.id}",
      )
    }
    TrainingPlanRepository(database).saveAsActive(plan(goal.id, sessionWorkouts, clock))

    val runDataSource = SimulationRunDataSource(database)
    val sessionRuns = runDataSource.persist(scenario.sessions.map { it.run }, clock)
    val metricsRepository = RunMetricsRepository(database.runMetricsDao())
    scenario.sessions.forEach { session -> metricsRepository.save(session.run.toMetrics(clock)) }

    scenario.sessions.forEachIndexed { index, session ->
      val isPrimary = index == scenario.sessions.lastIndex
      if (isPrimary && !scenario.persistPrimaryCheckout) return@forEachIndexed
      val checkoutInput = session.checkout
      val feedbackRepository = RunFeedbackRepository(
        database,
        clock,
        profileRepository,
        idProvider = { "feedback:${checkoutInput.runId}" },
      )
      val feedback = feedbackRepository.saveFeedback(
        RunFeedbackDraft(
          runId = checkoutInput.runId,
          rpe = checkoutInput.rpe,
          cardioEffort = checkoutInput.cardioEffort,
          muscleFatigue = checkoutInput.muscleFatigue,
          painLevel = checkoutInput.pain,
          completionStatus = checkoutInput.completion,
          readiness = checkoutInput.readiness,
          notes = "Deterministic debug simulation checkout.",
        )
      )
      val actual = requireNotNull(sessionRuns.singleOrNull { it.healthConnectRecordId == checkoutInput.runId })
      WorkoutExecutionRepository(database, clock, athleteProfileRepository = profileRepository)
        .recordForAssociatedWorkout(actual, feedback)

      if (isPrimary && scenario.evaluateGoalCompletion) {
        GoalCompletionRepository(database, clock).evaluateObservedRun(
          goal,
          actual,
          metricsRepository.getRunMetrics(actual.healthConnectRecordId),
          feedback,
        )
      }
    }

    // Persist spontaneous runs after authoritative workout associations. A later same-day run
    // cannot replace the stable execution identity that already links the planned workout to A.
    if (scenario.unrelatedRuns.isNotEmpty()) {
      runDataSource.persist(scenario.unrelatedRuns, clock)
      scenario.unrelatedRuns.forEach { metricsRepository.save(it.toMetrics(clock)) }
    }
    profileRepository.recalculate()
  }
}

private class SimulationRunDataSource(private val database: OpenRunCoachDatabase) {
  suspend fun persist(inputs: List<SimulatedRunInput>, clock: Clock): List<RunSummary> {
    val summaries = inputs.map { it.toRunSummary(clock) }
    val selections = selectCanonicalRuns(summaries, preferredSourcePackage = null).associateBy { it.runId }
    val entities = summaries.map { run ->
      val selection = requireNotNull(selections[run.healthConnectRecordId])
      RunEntity(
        healthConnectRecordId = run.healthConnectRecordId,
        startTimeEpochMillis = run.startTime.toEpochMilli(),
        endTimeEpochMillis = run.endTime.toEpochMilli(),
        durationSeconds = run.durationSeconds,
        distanceMeters = run.distanceMeters,
        averageHeartRate = run.averageHeartRate,
        maxHeartRate = run.maxHeartRate,
        averagePaceSecondsPerKm = run.averagePaceSecondsPerKm,
        importedAtEpochMillis = clock.millis(),
        sourcePackageName = run.sourcePackageName,
        sourceAppName = run.sourceAppName,
        sourceDeviceManufacturer = run.sourceDeviceManufacturer,
        sourceDeviceModel = run.sourceDeviceModel,
        sourceDeviceType = run.sourceDeviceType?.name,
        sourceRecordLastModifiedTimeEpochMillis = run.sourceRecordLastModifiedTime?.toEpochMilli(),
        duplicateGroupId = selection.duplicateGroupId,
        isCanonical = selection.isCanonical,
        canonicalSelectionReason = selection.reason,
      )
    }
    database.runDao().upsertAll(entities)
    return summaries.map { run ->
      val selection = requireNotNull(selections[run.healthConnectRecordId])
      run.copy(
        duplicateGroupId = selection.duplicateGroupId,
        isCanonical = selection.isCanonical,
        canonicalSelectionReason = selection.reason,
      )
    }
  }
}

private fun SimulatedRunInput.toRunSummary(clock: Clock) = RunSummary(
  healthConnectRecordId = id,
  startTime = startTime,
  endTime = startTime.plusSeconds(durationSeconds),
  durationSeconds = durationSeconds,
  distanceMeters = distanceMeters,
  averageHeartRate = averageHeartRate,
  maxHeartRate = maxHeartRate,
  averagePaceSecondsPerKm = averagePaceSecondsPerKm,
  sourcePackageName = "com.example.openruncoach.simulation",
  sourceAppName = "OpenRunCoach Simulation",
  sourceDeviceManufacturer = "OpenRunCoach",
  sourceDeviceModel = "Deterministic simulator",
  sourceDeviceType = SourceDeviceType.OTHER,
  sourceRecordLastModifiedTime = clock.instant(),
)

private fun SimulatedRunInput.toMetrics(clock: Clock): RunMetrics {
  val speedSamples = when (movement) {
    SimulatedMovement.NONE -> emptyList()
    SimulatedMovement.CONTINUOUS -> sampledSpeeds { 1_000.0 / averagePaceSecondsPerKm }
    SimulatedMovement.WALKING_AND_STRONG_FADE -> sampledSpeeds { fraction -> if (fraction < 0.5) 3.2 else 1.0 }
  }
  return RunMetrics(
    runId = id,
    startTime = startTime,
    endTime = startTime.plusSeconds(durationSeconds),
    durationSeconds = metric(durationSeconds),
    distanceMeters = metric(distanceMeters),
    averagePaceSecondsPerKm = metric(averagePaceSecondsPerKm, MetricSource.DERIVED),
    averageSpeedMetersPerSecond = metric(1_000.0 / averagePaceSecondsPerKm, MetricSource.DERIVED),
    averageHeartRate = averageHeartRate?.let { metric(it) },
    maxHeartRate = maxHeartRate?.toDouble()?.let { metric(it) },
    recordingSource = "OpenRunCoach Simulation",
    recordingDeviceName = "Deterministic simulator",
    series = RunTimeSeries(speed = speedSamples),
    generatedAt = clock.instant(),
  )
}

private fun SimulatedRunInput.sampledSpeeds(value: (Double) -> Double): List<TimedMetricSample<Double>> {
  val step = 10L
  return (0L..durationSeconds).step(step).map { second ->
    TimedMetricSample(
      timestamp = startTime.plusSeconds(second),
      value = value(second.toDouble() / durationSeconds),
      source = MetricSource.DERIVED,
      confidence = MetricConfidence.HIGH,
      sourceAppName = "OpenRunCoach Simulation",
      sourceDeviceName = "Deterministic simulator",
    )
  }
}

private fun <T> metric(value: T, source: MetricSource = MetricSource.DERIVED) =
  MetricValue(value, source, MetricConfidence.HIGH)

private fun SimulatedWorkoutInput.toWorkout(date: LocalDate, clock: Clock, id: String): PlannedWorkout {
  val pace = if (targetPaceMinimumSecondsPerKm != null && targetPaceMaximumSecondsPerKm != null) {
    listOf(PaceTarget(targetPaceMinimumSecondsPerKm, targetPaceMaximumSecondsPerKm))
  } else emptyList()
  return PlannedWorkout(
    id = id,
    date = date,
    type = type,
    name = name,
    purpose = purpose,
    blocks = listOf(SingleSegmentBlock(
      id = "$id:block",
      order = 0,
      segment = WorkoutSegment(
        id = "$id:segment",
        order = 0,
        category = SegmentCategory.EASY,
        name = name,
        completion = DistanceCompletion(distanceMeters),
        primaryIntensity = RpeTarget(targetRpeMinimum, targetRpeMaximum),
        secondaryIntensities = pace,
      ),
    )),
    explanation = "Deterministic debug prescription evaluated by the normal workout pipeline.",
    cues = listOf("Use the prescribed effort and record the actual response."),
    createdAt = clock.instant().minus(Duration.ofDays(60)),
    goalId = "simulation-goal",
    origin = WorkoutOrigin.GENERATED,
  )
}

private fun plan(goalId: String, workouts: List<PlannedWorkout>, clock: Clock): TrainingPlan {
  require(workouts.isNotEmpty())
  val grouped = workouts.sortedBy(PlannedWorkout::date)
    .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    .toSortedMap()
  val weeks = grouped.entries.mapIndexed { index, (weekStart, weekWorkouts) ->
    TrainingWeek(
      weekIndex = index,
      startDate = weekStart,
      workouts = weekWorkouts.mapIndexed { workoutIndex, workout ->
        PlannedWorkoutEntry(
          WeeklyWorkoutSlot.entries[workoutIndex.coerceAtMost(WeeklyWorkoutSlot.entries.lastIndex)],
          workout,
          "Debug simulation evidence session.",
        )
      },
      targetDistanceMeters = weekWorkouts.sumOf { workout -> workout.blocks.flatMap { it.segments }
        .sumOf { (it.completion as? DistanceCompletion)?.meters ?: 0.0 } },
      targetDurationSeconds = null,
      loadClassification = WeekLoadClassification.NORMAL,
      explanation = "Deterministic evidence week for manual coaching verification.",
      reasons = listOf("Scenario inputs are isolated from real athlete data."),
    )
  }
  val firstWeek = grouped.firstKey()
  val lastWeek = grouped.lastKey()
  return TrainingPlan(
    id = "simulation-plan",
    goalId = goalId,
    createdAt = clock.instant().minus(Duration.ofDays(60)),
    startDate = firstWeek,
    endDate = lastWeek.plusDays(6),
    weeks = weeks,
    blocks = listOf(TrainingBlock(
      TrainingBlockType.FOUNDATION,
      0,
      weeks.lastIndex,
      "Exercise the normal coaching pipeline.",
      "No automatic progression is injected.",
    )),
    generationVersion = 4,
    explanation = "Debug-only deterministic simulation plan.",
    confidence = ProfileConfidence.LOW,
    status = TrainingPlanStatus.ACTIVE,
  )
}
