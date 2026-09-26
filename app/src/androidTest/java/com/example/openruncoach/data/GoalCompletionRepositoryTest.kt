package com.example.openruncoach.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.TrainingGoalEntity
import com.example.openruncoach.data.local.TrainingPlanEntity
import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.RunTimeSeries
import com.example.openruncoach.domain.metrics.TimedMetricSample
import com.example.openruncoach.domain.goal.GoalAssessmentLevel
import com.example.openruncoach.domain.goal.GoalCompletionStatus
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalStatus
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.GoalAssessment
import com.example.openruncoach.domain.goal.StartingPerformanceSnapshot
import com.example.openruncoach.domain.goal.TrainingGoal
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.plan.TrainingPlanStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalCompletionRepositoryTest {
  private val runTime = Instant.parse("2026-10-10T08:00:00Z")

  @Test fun qualifyingRunBeforeMilestoneCompletesGoalAndObsoletesFutureRun() = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java,
    ).build()
    try {
      database.trainingGoalDao().upsertGoal(goalEntity())
      database.trainingPlanDao().upsertPlan(planEntity())
      val repository = GoalCompletionRepository(
        database,
        Clock.fixed(runTime, ZoneOffset.UTC),
      )

      val result = repository.evaluateObservedRun(
        goal = goal(),
        canonicalRun = run(),
        metrics = metrics(),
        feedback = feedback(),
      )

      assertTrue(result.completed)
      assertEquals(GoalCompletionStatus.GOAL_DISTANCE_DEMONSTRATED, result.status)
      assertEquals(GoalStatus.COMPLETED.name, database.trainingGoalDao().getAllGoals().single().goal.status)
      val plan = database.trainingPlanDao().getPlan("plan")!!
      assertEquals(TrainingPlanStatus.COMPLETED.name, plan.status)
      assertNull(plan.plannedCompletionDateEpochDay)
      assertNull(plan.plannedCompletionRunId)
    } finally {
      database.close()
    }
  }

  private fun goalEntity() = TrainingGoalEntity(
    id = "goal",
    distance = GoalDistance.FIVE_K.name,
    customDistanceMeters = null,
    goalType = GoalType.COMPLETE_DISTANCE.name,
    targetTimeSeconds = null,
    createdAtEpochMillis = runTime.minusSeconds(86_400).toEpochMilli(),
    status = GoalStatus.ACTIVE.name,
    confidenceAtCreation = ProfileConfidence.LOW.name,
    startingBestDurationSeconds = null,
    startingBestRunId = null,
    startingBestObservedAtEpochMillis = null,
    startingRecentDurationSeconds = null,
    startingRecentRunId = null,
    startingRecentObservedAtEpochMillis = null,
    startingAverageWeeklyDistanceMeters = 8_000.0,
    startingAverageWeeklyDurationSeconds = 3_600,
    startingLongestRunDistanceMeters = 4_760.0,
    startingLongestRunDurationSeconds = 2_100,
    startingAverageRunsPerWeek = 1.5,
    startingTypicalEasyPaceSecondsPerKm = 430.0,
    startingTypicalEasyHeartRate = null,
    startingProfileConfidence = ProfileConfidence.LOW.name,
    snapshotCapturedAtEpochMillis = runTime.minusSeconds(86_400).toEpochMilli(),
    assessmentLevel = GoalAssessmentLevel.LIMITED_DATA.name,
  )

  private fun goal() = TrainingGoal(
    id = "goal",
    distance = GoalDistance.FIVE_K,
    customDistanceMeters = null,
    goalType = GoalType.COMPLETE_DISTANCE,
    targetTimeSeconds = null,
    createdAt = runTime.minusSeconds(86_400),
    status = GoalStatus.ACTIVE,
    startingPerformanceSnapshot = StartingPerformanceSnapshot(
      null, null, 8_000.0, 3_600, 4_760.0, 2_100, 1.5, 430.0, null,
      ProfileConfidence.LOW, runTime.minusSeconds(86_400),
    ),
    confidenceAtCreation = ProfileConfidence.LOW,
    assessmentAtCreation = GoalAssessment(GoalAssessmentLevel.LIMITED_DATA, listOf("Fixture")),
  )

  private fun planEntity() = TrainingPlanEntity(
    id = "plan",
    goalId = "goal",
    createdAtEpochMillis = runTime.minusSeconds(86_400).toEpochMilli(),
    startDateEpochDay = LocalDate.parse("2026-10-05").toEpochDay(),
    endDateEpochDay = LocalDate.parse("2026-10-18").toEpochDay(),
    generationVersion = 4,
    explanation = "Fixture",
    confidence = ProfileConfidence.LOW.name,
    status = TrainingPlanStatus.ACTIVE.name,
    plannedCompletionDateEpochDay = LocalDate.parse("2026-10-18").toEpochDay(),
    plannedCompletionRunId = "future-completion",
    completionReadiness = "GOAL_COMPLETION_NOT_READY",
    completionPromptDismissed = false,
    completionUpdatedAtEpochMillis = runTime.minusSeconds(86_400).toEpochMilli(),
  )

  private fun run() = RunSummary(
    healthConnectRecordId = "ordinary-easy-run",
    startTime = runTime,
    endTime = runTime.plusSeconds(2_100),
    durationSeconds = 2_100,
    distanceMeters = 5_020.0,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = 418.0,
  )

  private fun feedback() = RunFeedback(
    id = "feedback",
    runId = "ordinary-easy-run",
    rpe = 5,
    cardioEffort = CardioEffort.APPROPRIATE,
    muscleFatigue = MuscleFatigue.MILD,
    painLevel = PainLevel.NONE,
    completionStatus = CompletionStatus.COMPLETED,
    readiness = null,
    notes = null,
    createdAt = runTime,
    updatedAt = runTime,
  )

  private fun metrics() = RunMetrics(
    runId = "ordinary-easy-run",
    startTime = runTime,
    endTime = runTime.plusSeconds(2_100),
    durationSeconds = MetricValue(2_100L, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    distanceMeters = MetricValue(5_020.0, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    series = RunTimeSeries(speed = (0L..2_100L step 10).map { seconds ->
      TimedMetricSample(runTime.plusSeconds(seconds), 2.4)
    }),
    generatedAt = runTime,
  )
}
