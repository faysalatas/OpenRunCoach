package com.example.openruncoach.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.data.local.WorkoutExecutionEntity
import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedbackDraft
import com.example.openruncoach.domain.goal.GoalDistance
import com.example.openruncoach.domain.goal.GoalStatus
import com.example.openruncoach.domain.goal.GoalType
import com.example.openruncoach.domain.goal.TrainingGoalDraft
import com.example.openruncoach.domain.goal.TrainingGoalFactory
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunContinuityEvidence
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.RunTimeSeries
import com.example.openruncoach.domain.metrics.TimedMetricSample
import com.example.openruncoach.domain.workout.SessionCost
import com.example.openruncoach.domain.workout.PaceResponse
import com.example.openruncoach.domain.workout.StructuredWorkoutSamples
import com.example.openruncoach.domain.workout.WorkoutExecutionStatus
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.simpleDurationWorkout
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostRunReconcilerTest {
  private val start = Instant.parse("2026-09-27T08:00:00Z")
  private val clock = Clock.fixed(start.plusSeconds(14_400), ZoneOffset.UTC)
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Test fun identicalInputsAndConcurrentReconciliationConvergeWithoutDuplicateRows() = withDatabase { db ->
    seed(db)
    RunFeedbackRepository(db, clock).saveFeedback(draft())
    RunMetricsRepository(db, clock).save(metrics())
    val first = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
    val stored = db.coachingWorkflowDao().getStoredExecutions()
    val profile = db.athletePerformanceProfileDao().getProfile()
    val repeated = coroutineScope {
      (1..6).map { async(Dispatchers.IO) { PostRunReconciler(db, clock).reconcilePhysicalRun("anchor") } }.awaitAll()
    }
    repeated.forEach { assertEquals(first, it) }
    assertEquals(stored, db.coachingWorkflowDao().getStoredExecutions())
    assertEquals(profile, db.athletePerformanceProfileDao().getProfile())
    assertEquals(1, WorkoutResponseRepository(db).getAll().size)
    assertEquals(1, first.athleteState.recentResponseCount)
  }

  @Test fun checkoutBeforeEnrichmentRebuildsTheSamePhysicalResponse() = withDatabase { db ->
    seed(db)
    val checkout = RunFeedbackRepository(db, clock).saveFeedback(draft())
    assertEquals(RunContinuityEvidence.UNKNOWN, WorkoutResponseRepository(db).getForRun("anchor")!!.continuity)
    RunMetricsRepository(db, clock).save(metrics())
    val current = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
    assertEquals(RunContinuityEvidence.CONTINUOUS, current.response!!.continuity)
    assertEquals("anchor", current.response!!.physicalRunId)
    assertEquals(checkout, RunFeedbackRepository(db, clock).getFeedbackForRun("anchor"))
    assertEquals(1, db.coachingWorkflowDao().getStoredExecutions().size)
    assertEquals(1, WorkoutResponseRepository(db).getAll().size)
  }

  @Test fun preferredCanonicalRepresentationKeepsPhysicalCheckoutResponseAndEnrichment() = withDatabase { db ->
    seed(db)
    val checkout = RunFeedbackRepository(db, clock).saveFeedback(draft())
    db.runDao().upsertAll(listOf(run("other").copy(isCanonical = false, sourcePackageName = "com.strava")))
    val metricWriter = RunMetricsRepository(db, clock)
    val runs = RunRepository(db.runDao(), HealthConnectRepository(context), RunSettingsRepository(context), clock,
      runMetricsRefresher = RunMetricsRefresher { current, _ ->
        val enriched = metrics(current.healthConnectRecordId)
        metricWriter.save(enriched)
        RunMetricsRefreshOutcome(enriched, true)
      }, postRunReconciler = PostRunReconciler(db, clock))
    for (preferred in listOf("com.strava", "com.sec.android.app.shealth", "com.strava")) {
      runs.refreshCanonicalSelection(preferred)
      val canonical = db.runDao().getCanonicalRuns().single()
      assertEquals(preferred, canonical.sourcePackageName)
      assertEquals("anchor", canonical.physicalRunId)
      val result = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
      assertEquals("anchor", result.response!!.physicalRunId)
      assertEquals(canonical.healthConnectRecordId, result.response!!.runId)
      assertEquals(RunContinuityEvidence.CONTINUOUS, result.response!!.continuity)
      assertEquals(1, result.athleteState.recentResponseCount)
      val saved = metricWriter.getRunMetrics(canonical.healthConnectRecordId)!!
      assertEquals(MetricSource.SAMSUNG_HEALTH, saved.movingAverageCadence!!.source)
      assertEquals(165.0, saved.movingAverageCadence!!.value, 0.0)
      assertEquals(checkout, RunFeedbackRepository(db, clock).getFeedbackForRun(canonical.healthConnectRecordId))
      assertEquals(1, db.coachingWorkflowDao().getStoredExecutions().size)
      assertEquals(1, WorkoutResponseRepository(db).getAll().size)
    }
  }

  @Test fun associationCorrectionReplacesAWithBWithoutDoubleCounting() = withDatabase { db ->
    seed(db)
    RunFeedbackRepository(db, clock).saveFeedback(draft())
    val actual = db.runDao().getCanonicalRuns().single().toRunSummary()
    WorkoutExecutionRepository(db, clock).assignManually(actual, "B")
    val result = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
    assertEquals("B", result.execution!!.plannedWorkoutId)
    assertEquals("B", result.response!!.plannedWorkoutId)
    assertEquals(WorkoutExecutionStatus.PARTIALLY_COMPLETED, result.execution!!.status)
    assertEquals("PLANNED", db.coachingWorkflowDao().getLifecycleState("A"))
    assertEquals(listOf("B"), db.coachingWorkflowDao().getStoredExecutions().map { it.plannedWorkoutId })
    assertEquals(1, result.athleteState.recentResponseCount)
  }

  @Test fun unassignmentAndRestartDoNotRestoreAnOldAssociation() = withDatabase { db ->
    seed(db)
    RunFeedbackRepository(db, clock).saveFeedback(draft())
    val actual = db.runDao().getCanonicalRuns().single().toRunSummary()
    WorkoutExecutionRepository(db, clock).unassign(actual)
    PostRunReconciler(db, clock).reconcileAll()
    val result = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
    assertNull(result.execution)
    assertNull(result.response)
    assertTrue(db.coachingWorkflowDao().getStoredExecutions().isEmpty())
    assertNull(db.coachingWorkflowDao().getAssociationForPhysicalRun("anchor"))
    assertNotNull(db.runDao().getRun("anchor"))
    assertEquals(0, result.athleteState.recentResponseCount)
    assertEquals(4_500.0, result.athleteState.recentActualVolume7DaysMeters, 0.0)
    WorkoutExecutionRepository(db, clock).assignManually(actual, "A")
    assertEquals(1, WorkoutResponseRepository(db).getAll().size)
  }

  @Test fun checkoutEditsAndConcurrentWritersReplaceCostAndReasons() = withDatabase { db ->
    seed(db)
    val first = RunFeedbackRepository(db, clock).saveFeedback(draft())
    val before = WorkoutResponseRepository(db).getForRun("anchor")!!
    RunFeedbackRepository(db, clock).updateFeedback(draft().copy(rpe = 9, painLevel = PainLevel.SIGNIFICANT))
    val after = WorkoutResponseRepository(db).getForRun("anchor")!!
    assertTrue(before.sessionCost != after.sessionCost)
    assertEquals(SessionCost.HIGH, after.sessionCost)
    assertEquals(PainLevel.SIGNIFICANT, after.pain)
    assertTrue(after.reasons.any { it.contains("pain", ignoreCase = true) })
    coroutineScope {
      listOf(3, 8).map { rpe -> async(Dispatchers.IO) {
        RunFeedbackRepository(db, clock).updateFeedback(draft().copy(rpe = rpe))
      } }.awaitAll()
    }
    val checkout = RunFeedbackRepository(db, clock).getFeedbackForRun("anchor")!!
    assertEquals(first.id, checkout.id)
    assertEquals(checkout.rpe, WorkoutResponseRepository(db).getForRun("anchor")!!.reportedRpe)
    assertEquals(1, db.runFeedbackDao().getAllFeedback().size)
    assertEquals(1, db.coachingWorkflowDao().getStoredExecutions().size)
  }

  @Test fun missingCheckoutInvalidatesStaleExecutionWithoutFabricatingFeedback() = withDatabase { db ->
    seed(db)
    db.coachingWorkflowDao().upsertExecution(staleExecution())
    val result = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
    assertNull(result.execution)
    assertNull(result.response)
    assertNull(result.coachingDecision)
    assertNull(result.athleteState.latestSessionCost)
    assertTrue(db.coachingWorkflowDao().getStoredExecutions().isEmpty())
    assertTrue(db.runFeedbackDao().getAllFeedback().isEmpty())
    assertNotNull(db.coachingWorkflowDao().getAssociationForPhysicalRun("anchor"))
  }

  @Test fun neverAssociatedRunKeepsActualVolumeAndCanIndependentlyDemonstrateGoal() = withDatabase { db ->
    db.runDao().upsertAll(listOf(run().copy(distanceMeters = 5_050.0)))
    createFiveKGoal(db)
    RunFeedbackRepository(db, clock).saveFeedback(draft())
    RunMetricsRepository(db, clock).save(metrics())
    val result = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
    assertNull(result.execution)
    assertNull(result.response)
    assertEquals(5_050.0, result.athleteState.recentActualVolume7DaysMeters, 0.0)
    assertEquals(GoalStatus.COMPLETED.name, db.trainingGoalDao().getAllGoals().single().goal.status)
    assertNotNull(db.runDao().getRun("anchor"))
  }

  @Test fun qualityWorkoutKeepsSegmentEvidenceUnknownInsteadOfUsingWholeRunPace() = withDatabase { db ->
    seed(db)
    val workout = StructuredWorkoutSamples.intervals().copy(id = "A")
    db.coachingWorkflowDao().replaceWorkout(StructuredWorkoutPersistenceMapper.toRecord(workout, "PLANNED"))
    RunFeedbackRepository(db, clock).saveFeedback(draft().copy(rpe = 7))
    val first = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
    assertNull(first.execution!!.qualityWorkCompletionFraction)
    assertEquals(PaceResponse.UNKNOWN, first.response!!.paceResponse)
    // Preserve existing explicit repetition evidence while the physical performance is unchanged.
    db.coachingWorkflowDao().upsertExecution(first.execution!!.toEntity().copy(qualityWorkCompletionFraction = 0.9))
    val repeated = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
    assertEquals(0.9, repeated.execution!!.qualityWorkCompletionFraction!!, 0.0)
    assertEquals(PaceResponse.UNKNOWN, repeated.response!!.paceResponse)
  }

  @Test fun ignoreExcludesDerivedEvidenceAndRestoreReturnsItExactlyOnce() = withDatabase { db ->
    seed(db)
    RunFeedbackRepository(db, clock).saveFeedback(draft())
    RunMetricsRepository(db, clock).save(metrics())
    val reconciler = PostRunReconciler(db, clock)
    val before = reconciler.reconcilePhysicalRun("anchor")
    val ignored = reconciler.setRelevance("anchor", true)
    assertNull(ignored.execution)
    assertNull(ignored.response)
    assertEquals(0.0, ignored.athleteState.recentActualVolume7DaysMeters, 0.0)
    assertNull(ignored.athleteState.longestCompletedDistanceMeters)
    assertEquals(0, ignored.athleteState.recentResponseCount)
    assertNotNull(db.runDao().getRun("anchor"))
    assertNotNull(RunFeedbackRepository(db, clock).getFeedbackForRun("anchor"))
    assertNotNull(db.coachingWorkflowDao().getAssociationForPhysicalRun("anchor"))
    val restored = reconciler.setRelevance("anchor", false)
    assertEquals(before.response, restored.response)
    assertEquals(before.athleteState, restored.athleteState)
    reconciler.setRelevance("anchor", false)
    assertEquals(1, WorkoutResponseRepository(db).getAll().size)
  }

  @Test fun repositoryAndDatabaseRecreationConvergeAfterRestart() = runBlocking {
    val name = "post-run-restart-test.db"
    context.deleteDatabase(name)
    var db = Room.databaseBuilder(context, OpenRunCoachDatabase::class.java, name).build()
    try {
      seed(db)
      RunFeedbackRepository(db, clock).saveFeedback(draft())
      RunMetricsRepository(db, clock).save(metrics())
      val before = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
      db.close()
      db = Room.databaseBuilder(context, OpenRunCoachDatabase::class.java, name).build()
      PostRunReconciler(db, clock).reconcileAll()
      assertEquals(before, PostRunReconciler(db, clock).reconcilePhysicalRun("anchor"))
      assertEquals(1, db.coachingWorkflowDao().getStoredExecutions().size)
    } finally { db.close(); context.deleteDatabase(name) }
  }

  @Test fun repeatedProviderUpsertAndCanonicalRefreshDoNotDuplicateTrainingEvidence() = withDatabase { db ->
    seed(db)
    RunFeedbackRepository(db, clock).saveFeedback(draft())
    RunMetricsRepository(db, clock).save(metrics())
    val runs = RunRepository(db.runDao(), HealthConnectRepository(context), RunSettingsRepository(context), clock,
      postRunReconciler = PostRunReconciler(db, clock))
    repeat(3) {
      db.runDao().upsertAll(listOf(run(), run("copy").copy(isCanonical = false)))
      runs.refreshCanonicalSelection(null)
      val result = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
      assertEquals(1, db.runDao().getCanonicalRuns().size)
      assertEquals(1, db.coachingWorkflowDao().getStoredExecutions().size)
      assertEquals(1, WorkoutResponseRepository(db).getAll().size)
      assertEquals(4_500.0, result.athleteState.recentActualVolume7DaysMeters, 0.0)
    }
  }

  @Test fun goalDistanceWithLateContinuousEvidenceCompletesOnlyAfterEnrichment() = withDatabase { db ->
    seed(db)
    db.runDao().upsertAll(listOf(run().copy(distanceMeters = 5_050.0)))
    createFiveKGoal(db)
    RunFeedbackRepository(db, clock).saveFeedback(draft())
    val first = PostRunReconciler(db, clock).reconcilePhysicalRun("anchor")
    assertFalse(first.goalCompletion!!.completed)
    assertEquals(GoalStatus.ACTIVE.name, db.trainingGoalDao().getActiveGoal()!!.goal.status)
    RunMetricsRepository(db, clock).save(metrics())
    assertEquals(GoalStatus.COMPLETED.name, db.trainingGoalDao().getAllGoals().single().goal.status)
    PostRunReconciler(db, clock).reconcileAll()
    assertEquals("anchor", WorkoutResponseRepository(db).getAll().single().physicalRunId)
    assertEquals(1, db.runFeedbackDao().getAllFeedback().size)
  }

  @Test fun ignoredGoalDistanceCannotCompleteUntilRestored() = withDatabase { db ->
    seed(db)
    db.runDao().upsertAll(listOf(run().copy(distanceMeters = 5_050.0)))
    createFiveKGoal(db)
    RunFeedbackRepository(db, clock).saveFeedback(draft())
    PostRunReconciler(db, clock).setRelevance("anchor", true)
    RunMetricsRepository(db, clock).save(metrics())
    assertEquals(GoalStatus.ACTIVE.name, db.trainingGoalDao().getActiveGoal()!!.goal.status)
    PostRunReconciler(db, clock).setRelevance("anchor", false)
    assertEquals(GoalStatus.COMPLETED.name, db.trainingGoalDao().getAllGoals().single().goal.status)
  }

  @Test fun transactionRollbackDoesNotLeavePartialCheckoutOrDerivedState() = withDatabase { db ->
    seed(db)
    val profile = db.athletePerformanceProfileDao().getProfile()
    try {
      db.withTransaction {
        RunFeedbackRepository(db, clock).saveFeedback(draft())
        error("Rollback fixture")
      }
    } catch (expected: IllegalStateException) {
      assertEquals("Rollback fixture", expected.message)
    }
    assertTrue(db.runFeedbackDao().getAllFeedback().isEmpty())
    assertTrue(db.coachingWorkflowDao().getStoredExecutions().isEmpty())
    assertEquals(profile, db.athletePerformanceProfileDao().getProfile())
  }

  @Test fun staleMetricWindowDoesNotProveGoalContinuity() = withDatabase { db ->
    seed(db)
    db.runDao().upsertAll(listOf(run().copy(distanceMeters = 5_050.0)))
    createFiveKGoal(db)
    RunFeedbackRepository(db, clock).saveFeedback(draft())
    RunMetricsRepository(db, clock).save(metrics().copy(endTime = start.plusSeconds(2_100)))
    assertEquals(RunContinuityEvidence.UNKNOWN, WorkoutResponseRepository(db).getForRun("anchor")!!.continuity)
    assertNotNull(db.trainingGoalDao().getActiveGoal())
  }

  @Test fun obsoleteRawExecutionForAnotherWorkoutIsRemovedDuringRecovery() = withDatabase { db ->
    seed(db)
    RunFeedbackRepository(db, clock).saveFeedback(draft())
    db.coachingWorkflowDao().upsertExecution(staleExecution().copy(plannedWorkoutId = "B"))
    PostRunReconciler(db, clock).reconcileAll()
    assertEquals(listOf("A"), db.coachingWorkflowDao().getStoredExecutions().map { it.plannedWorkoutId })
  }

  private fun withDatabase(block: suspend (OpenRunCoachDatabase) -> Unit) = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(context, OpenRunCoachDatabase::class.java).build()
    try { block(db) } finally { db.close() }
  }

  private suspend fun seed(db: OpenRunCoachDatabase) {
    db.runDao().upsertAll(listOf(run()))
    listOf("A" to 30, "B" to 45).forEach { (id, minutes) ->
      val workout = simpleDurationWorkout(id, start.atZone(ZoneOffset.UTC).toLocalDate(),
        WorkoutType.EASY_RUN, minutes, 3, 4, start.minusSeconds(86_400))
      db.coachingWorkflowDao().replaceWorkout(StructuredWorkoutPersistenceMapper.toRecord(workout, "PLANNED"))
    }
    WorkoutExecutionRepository(db, clock).assignManually(run().toRunSummary(), "A")
  }

  private suspend fun createFiveKGoal(db: OpenRunCoachDatabase) = TrainingGoalRepository(db,
    AthletePerformanceProfileRepository(db),
    TrainingGoalFactory(Clock.fixed(start.minusSeconds(86_400), ZoneOffset.UTC), idProvider = { "goal" }),
  ).createGoal(TrainingGoalDraft(GoalDistance.FIVE_K, goalType = GoalType.COMPLETE_DISTANCE))

  private fun run(id: String = "anchor") = RunEntity(
    id, start.toEpochMilli(), start.plusSeconds(1_800).toEpochMilli(), 1_800, 4_500.0,
    null, null, 400.0, start.toEpochMilli(), sourcePackageName = "com.sec.android.app.shealth", physicalRunId = "anchor",
  )

  private fun draft() = RunFeedbackDraft("anchor", 3, CardioEffort.EASY, MuscleFatigue.NONE,
    PainLevel.NONE, CompletionStatus.COMPLETED, null, null)

  private fun metrics(runId: String = "anchor") = RunMetrics(
    runId, start, start.plusSeconds(1_800), MetricValue(1_800L, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    movingAverageCadence = MetricValue(165.0, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH),
    series = RunTimeSeries(speed = (0L..1_800L step 10).map {
      TimedMetricSample(start.plusSeconds(it), 2.5, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH)
    }), generatedAt = clock.instant(),
  )

  private fun staleExecution() = WorkoutExecutionEntity("A", "anchor", "anchor", "COMPLETED_AS_PLANNED",
    "ORIGINAL_PRESCRIPTION", null, 1_800, 4_500.0, 1_800, null, "Old derived fixture", clock.millis())
}
