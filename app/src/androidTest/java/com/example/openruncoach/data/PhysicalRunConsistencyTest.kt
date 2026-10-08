package com.example.openruncoach.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.local.*
import com.example.openruncoach.domain.*
import com.example.openruncoach.domain.metrics.*
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import com.example.openruncoach.domain.route.*
import com.example.openruncoach.domain.workout.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhysicalRunConsistencyTest {
  private val start = Instant.parse("2026-10-01T12:00:00Z")
  private val clock = Clock.fixed(start.plusSeconds(86400), ZoneOffset.UTC)

  @Test fun soleNoncanonicalLegacyCheckoutResolvesAndMigratesOnlyOnExplicitEdit() = withDatabase { db ->
    seedRuns(db)
    val legacy = feedback("legacy", "strava", "Keep these notes")
    db.runFeedbackDao().insertFeedback(legacy)
    val repository = RunFeedbackRepository(db, clock)
    assertEquals("legacy", repository.getFeedbackForRun("samsung")?.id)
    assertEquals("legacy", repository.observeFeedbackForRun("strava").first()?.id)
    assertEquals("samsung", repository.getFeedbackForRun("strava")?.runId)
    assertEquals(legacy, db.runFeedbackDao().getAllFeedback().single())
    val saved = repository.updateFeedback(draft("strava").copy(notes = legacy.notes))
    assertEquals("legacy", saved.id)
    assertEquals("samsung", db.runFeedbackDao().getAllFeedback().single().runId)
    assertEquals(legacy.notes, saved.notes)
  }

  @Test fun writesFromEitherProviderEditOnePhysicalCheckout() = withDatabase { db ->
    seedRuns(db)
    val repository = RunFeedbackRepository(db, clock)
    val first = repository.saveFeedback(draft("strava"))
    val second = repository.saveFeedback(draft("samsung").copy(rpe = 7))
    assertEquals(first.id, second.id)
    assertEquals(1, db.runFeedbackDao().getAllFeedback().size)
    assertEquals("samsung", db.runFeedbackDao().getAllFeedback().single().runId)
    assertEquals(second, repository.getFeedbackForRun("strava"))
  }

  @Test fun duplicateLegacyAnswersKeepAnchorAuthorityAndNeverDoubleCountProfileFeedback() = withDatabase { db ->
    seedRuns(db)
    val anchor = feedback("anchor-feedback", "samsung", null)
    val legacy = feedback("provider-feedback", "strava", "hat to stop because of loose layces").copy(muscleFatigue = "MILD", rpe = 9)
    db.runFeedbackDao().insertFeedback(anchor)
    db.runFeedbackDao().insertFeedback(legacy)
    val repository = RunFeedbackRepository(db, clock)
    val resolution = repository.getResolution("strava")!!
    assertTrue(resolution.legacyConflict)
    assertEquals("anchor-feedback", resolution.authoritativeFeedback?.id)
    assertEquals(setOf(anchor, legacy), resolution.originalRecords.toSet())
    val profile = AthletePerformanceProfileRepository(db, AthletePerformanceProfileCalculator(clock, ZoneOffset.UTC)).recalculate()
    assertEquals(1, profile.feedback.feedbackCount)
    assertEquals(4.0, profile.feedback.averageRpe!!, 0.0)
    assertEquals(1, profile.runsLast28Days)
    assertEquals(setOf(anchor, legacy), db.runFeedbackDao().getAllFeedback().toSet())
  }

  @Test fun preferenceRoundTripNeverMovesOrChangesCheckoutAuthority() = withDatabase { db ->
    seedRuns(db)
    db.runFeedbackDao().insertFeedback(feedback("legacy-only", "strava", "Original notes"))
    val context = ApplicationProvider.getApplicationContext<Context>()
    val runs = RunRepository(db.runDao(), HealthConnectRepository(context), RunSettingsRepository(context), clock,
      runMetricsRefresher = RunMetricsRefresher { summary, _ ->
        RunMetricsRefreshOutcome(metrics(summary), refreshed = false)
      })
    val feedbackRepository = RunFeedbackRepository(db, clock)
    val before = feedbackRepository.getFeedbackForRun("samsung")
    val rawBefore = db.runFeedbackDao().getAllFeedback()
    for (source in listOf("com.strava", "com.sec.android.app.shealth", "com.strava")) {
      runs.refreshCanonicalSelection(source)
      assertEquals(source, db.runDao().getCanonicalRuns().single().sourcePackageName)
      assertEquals(before, feedbackRepository.getFeedbackForRun(db.runDao().getCanonicalRuns().single().healthConnectRecordId))
      assertEquals(rawBefore, db.runFeedbackDao().getAllFeedback())
    }
  }

  @Test fun conflictingCustomIdentityAnswersStayWholeAndDeterministic() = withDatabase { db ->
    seedRuns(db, "custom-physical")
    val first = feedback("first", "strava", "First whole answer").copy(createdAtEpochMillis = 1, painLevel = "SIGNIFICANT")
    val second = feedback("second", "samsung", "Second whole answer").copy(createdAtEpochMillis = 2, rpe = 1)
    db.runFeedbackDao().insertFeedback(first)
    db.runFeedbackDao().insertFeedback(second)
    val repository = RunFeedbackRepository(db, clock)
    val resolution = repository.getResolution("custom-physical")!!
    assertTrue(resolution.legacyConflict)
    assertEquals("first", resolution.authoritativeFeedback?.id)
    assertEquals(PainLevel.SIGNIFICANT, resolution.authoritativeFeedback?.painLevel)
    assertEquals(4, resolution.authoritativeFeedback?.rpe)
    assertEquals("custom-physical", repository.saveFeedback(draft("samsung")).runId)
    assertEquals(second, db.runFeedbackDao().getFeedbackForRun("samsung"))
    assertEquals(2, db.runFeedbackDao().getAllFeedback().size)
  }

  @Test fun explicitAnswerAtAnEmptyPhysicalAnchorPreservesBothConflictingLegacyRows() = withDatabase { db ->
    seedRuns(db)
    db.runDao().upsertAll(listOf(db.runDao().getRun("strava")!!.copy(healthConnectRecordId = "third-provider")))
    val first = feedback("first", "strava", "First original notes")
    val second = feedback("second", "third-provider", "Second original notes").copy(rpe = 9)
    db.runFeedbackDao().insertFeedback(first)
    db.runFeedbackDao().insertFeedback(second)
    val repository = RunFeedbackRepository(db, clock)
    assertEquals("first", repository.getFeedbackForRun("samsung")?.id)
    val saved = repository.updateFeedback(draft("third-provider"))
    assertEquals("samsung", saved.runId)
    assertEquals(saved.id, repository.getFeedbackForRun("strava")?.id)
    assertEquals(first, db.runFeedbackDao().getFeedbackForRun("strava"))
    assertEquals(second, db.runFeedbackDao().getFeedbackForRun("third-provider"))
    assertEquals(3, db.runFeedbackDao().getAllFeedback().size)
    assertEquals(1, repository.getAllFeedback().size)
  }

  @Test fun responseFromEitherProviderUsesTheSameCheckoutThroughCanonicalSwitch() = withDatabase { db ->
    seedRuns(db)
    db.runFeedbackDao().insertFeedback(feedback("strava-checkout", "strava", "Same athlete answer"))
    val workout = StructuredWorkoutSamples.easyRun()
    db.coachingWorkflowDao().replaceWorkout(StructuredWorkoutPersistenceMapper.toRecord(workout, "COMPLETED"))
    db.coachingWorkflowDao().upsertAssociation(WorkoutRunAssociationEntity(workout.id, "samsung", "samsung", "MANUAL", clock.millis()))
    db.coachingWorkflowDao().upsertExecution(WorkoutExecutionEntity(workout.id, "samsung", "samsung", "COMPLETED_AS_PLANNED",
      "ORIGINAL_PRESCRIPTION", null, 2100, 1952.169, 2000, null, "Completed", clock.millis()))
    val responses = WorkoutResponseRepository(db)
    val before = responses.getForRun("samsung")!!
    assertEquals(before, responses.getForRun("strava"))
    assertEquals(before, responses.observeForRun("strava").first())
    val records = db.runDao().getAllRuns().map { it.copy(isCanonical = it.healthConnectRecordId == "strava") }
    db.runDao().updateAll(records)
    db.runDao().repointPhysicalRunReferences("samsung", "strava")
    val after = responses.getForRun("samsung")!!
    assertEquals(before.reportedRpe, after.reportedRpe)
    assertEquals(before.pain, after.pain)
    assertEquals(before.completion, after.completion)
    assertEquals(after, responses.observeForRun("strava").first())
    assertEquals(1, responses.getAll().size)
  }

  @Test fun missingSplitReadsAndObservationsDoNotWriteAndUseRealSamples() = withDatabase { db ->
    seedRuns(db)
    val routes = RunRouteRepository(db.runRouteDao())
    val route = route(1952.169)
    routes.save(route, emptyList())
    RunMetricsRepository(db.runMetricsDao()).save(metrics(db.runDao().getRun("samsung")!!.toRunSummary()).copy(
      series = RunTimeSeries(heartRate = route.points.map { TimedMetricSample(it.timestamp, 145.0) },
        cadence = route.points.map { TimedMetricSample(it.timestamp, 165.0) })))
    val before = changes(db)
    val result = routes.get("strava")!!
    assertEquals(2, result.splits.size)
    assertEquals(SplitDerivationSource.ROUTE_DERIVED_ON_READ, result.splitDerivationSource)
    assertEquals(0, result.persistedSplitCount)
    assertTrue(result.splits.all { it.averageHeartRate == 145.0 && it.averageCadence == 165.0 })
    assertEquals(result, routes.observe("samsung").first())
    assertEquals(before, changes(db))
    assertTrue(db.runRouteDao().getRoute("samsung")!!.splits.isEmpty())
  }

  @Test fun explicitBackfillIsIdempotentAndCanonicalSwitchKeepsPhysicalSplitOwnership() = withDatabase { db ->
    seedRuns(db, distance = 4680.438)
    val routes = RunRouteRepository(db.runRouteDao())
    routes.save(route(4680.438), emptyList())
    val points = db.runRouteDao().getRoute("samsung")!!.points
    routes.backfillSplits("strava")
    val first = db.runRouteDao().getRoute("samsung")!!.splits
    routes.backfillSplits("samsung")
    assertEquals(5, first.size)
    assertEquals(first, db.runRouteDao().getRoute("samsung")!!.splits)
    assertEquals(SplitDerivationSource.PERSISTED_CACHE, routes.get("strava")!!.splitDerivationSource)
    db.runDao().updateAll(db.runDao().getAllRuns().map { it.copy(isCanonical = it.healthConnectRecordId == "strava") })
    assertEquals("samsung", routes.get("strava")!!.route.runId)
    assertEquals(5, routes.get("strava")!!.splits.size)
    assertEquals(points, db.runRouteDao().getRoute("samsung")!!.points)
  }

  @Test fun preferredSummarySwitchKeepsAllActualConsumersConsistentAndSamsungRouteAvailable() = withDatabase { db ->
    seedRuns(db, distance = 4680.438)
    db.runDao().updateAll(db.runDao().getAllRuns().map { row ->
      if (row.healthConnectRecordId == "samsung") row.copy(distanceMeters = 4755.3) else row
    })
    val routes = RunRouteRepository(db.runRouteDao())
    routes.save(route(4680.438), emptyList())
    val storedPoints = db.runRouteDao().getRoute("samsung")!!.points
    val workout = StructuredWorkoutSamples.easyRun()
    db.coachingWorkflowDao().replaceWorkout(StructuredWorkoutPersistenceMapper.toRecord(workout, "COMPLETED"))
    db.coachingWorkflowDao().upsertAssociation(WorkoutRunAssociationEntity(workout.id, "samsung", "samsung", "MANUAL", clock.millis()))
    db.coachingWorkflowDao().upsertExecution(WorkoutExecutionEntity(workout.id, "samsung", "samsung", "COMPLETED_AS_PLANNED",
      "ORIGINAL_PRESCRIPTION", null, 2100, 4755.3, 2000, null, "Saved completion", clock.millis()))
    val context = ApplicationProvider.getApplicationContext<Context>()
    val measurements = RunMetricsRepository(db.runMetricsDao())
    val runs = RunRepository(db.runDao(), HealthConnectRepository(context), RunSettingsRepository(context), clock,
      runMetricsRefresher = RunMetricsRefresher { summary, _ ->
        val enriched = metrics(summary).copy(
          distanceMeters = MetricValue(4755.3, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH),
          averagePaceSecondsPerKm = MetricValue(405.0, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH),
          averageHeartRate = MetricValue(145.0, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH),
          movingAverageCadence = MetricValue(165.0, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH))
        measurements.save(enriched)
        RunMetricsRefreshOutcome(enriched, refreshed = true)
      })
    for (source in listOf("com.strava", "com.sec.android.app.shealth", "com.strava")) {
      runs.refreshCanonicalSelection(source)
      val actual = runs.canonicalRuns.first().single()
      val expectedDistance = if (source == "com.strava") 4680.438 else 4755.3
      assertEquals(expectedDistance, actual.distanceMeters!!, 0.000001)
      assertEquals(2000000.0 / expectedDistance, actual.averagePaceSecondsPerKm!!, 0.000001)
      assertEquals(actual, runs.recentRuns.first().single())
      assertEquals(actual, runs.observeRun(actual.healthConnectRecordId).first())
      assertEquals(actual, runs.observeRun("strava").first())
      assertEquals(actual, runs.observeRun("samsung").first())
      assertEquals(actual, runs.observeRun(actual.physicalRunId).first())
      assertEquals(actual.healthConnectRecordId, measurements.observeActualRunMetrics("strava").first()!!.runId)
      val details = StructuredWorkoutRepository(db).observeWorkoutDetails(workout.id).first()!!
      assertEquals(actual, details.actualRun)
      assertEquals(actual.distanceMeters, details.executionResult!!.actualDistanceMeters)
      assertEquals(WorkoutExecutionStatus.COMPLETED_AS_PLANNED, details.executionResult!!.status)
      val execution = WorkoutExecutionRepository(db).observeForRun("strava").first()!!
      assertEquals(actual.distanceMeters, execution.actualDistanceMeters)
      assertEquals(4755.3, db.coachingWorkflowDao().getExecutionForPhysicalRun("samsung")!!.actualDistanceMeters!!, 0.0)
      val data = routes.get(actual.healthConnectRecordId)!!
      val pacing = RunPacingSummaryCalculator.summarize(actual, data)
      assertEquals(actual.averagePaceSecondsPerKm, pacing.wholeRunAveragePaceSecondsPerKm)
      assertEquals(expectedDistance, data.splits.sumOf { it.distanceMeters }, 0.01)
      assertEquals(MetricSource.SAMSUNG_HEALTH, data.route.source)
      val enriched = measurements.getRunMetrics(actual.healthConnectRecordId)!!
      assertEquals(MetricSource.SAMSUNG_HEALTH, enriched.averageHeartRate!!.source)
      assertEquals(MetricSource.SAMSUNG_HEALTH, enriched.movingAverageCadence!!.source)
    }
    assertEquals(storedPoints, db.runRouteDao().getRoute("samsung")!!.points)
  }

  @Test fun missingDistanceUsesSameReadOnlyFallbackForHistoryDetailAndRouteSplits() = withDatabase { db ->
    seedRuns(db, distance = 1952.169)
    db.runDao().updateAll(db.runDao().getAllRuns().map { it.copy(distanceMeters = null) })
    val canonical = db.runDao().getRun("samsung")!!.toRunSummary()
    RunMetricsRepository(db.runMetricsDao()).save(metrics(canonical).copy(
      distanceMeters = MetricValue(1952.169, MetricSource.SAMSUNG_HEALTH, MetricConfidence.HIGH)))
    val routes = RunRouteRepository(db.runRouteDao())
    routes.save(route(1952.169), emptyList())
    val context = ApplicationProvider.getApplicationContext<Context>()
    // No optional metrics repository is required for History/Home to receive the same fallback.
    val runs = RunRepository(db.runDao(), HealthConnectRepository(context), RunSettingsRepository(context), clock)
    val before = changes(db)
    val history = runs.canonicalRuns.first().single()
    assertEquals(1952.169, history.distanceMeters!!, 0.000001)
    assertEquals(ActualValueBasis.MATCHED_METRIC_FALLBACK, history.actualProvenance!!.distance.basis)
    assertEquals(history, runs.observeRun("samsung").first())
    assertEquals(1952.169, routes.get("strava")!!.splits.sumOf { it.distanceMeters }, 0.01)
    assertEquals(before, changes(db))
    assertNull(db.runDao().getRun("samsung")!!.distanceMeters)
  }

  private suspend fun seedRuns(db: OpenRunCoachDatabase, physicalId: String = "samsung", distance: Double = 1952.169) {
    val run = RunEntity("samsung", start.toEpochMilli(), start.plusSeconds(2000).toEpochMilli(), 2000,
      distance, 145.0, 160, 2000000 / distance, clock.millis(), sourcePackageName = "com.sec.android.app.shealth",
      physicalRunId = physicalId)
    db.runDao().upsertAll(listOf(run, run.copy(healthConnectRecordId = "strava", isCanonical = false, sourcePackageName = "com.strava")))
  }

  private fun feedback(id: String, runId: String, notes: String?) = RunFeedbackEntity(id, runId, 4, "APPROPRIATE",
    "NONE", "NONE", "COMPLETED", null, notes, start.toEpochMilli(), start.toEpochMilli())
  private fun draft(id: String) = RunFeedbackDraft(id, 4, CardioEffort.APPROPRIATE, MuscleFatigue.NONE,
    PainLevel.NONE, CompletionStatus.COMPLETED, null, "Explicitly edited")
  private fun route(distance: Double) = RunRoute("samsung", MetricSource.SAMSUNG_HEALTH, "Samsung Health", MetricConfidence.HIGH,
    (0..400).map { i -> RunRoutePoint(i, start.plusSeconds(i * 5L), distance * i / 400 / 6371008.8 * 180 / Math.PI, 0.0) })
  private fun metrics(run: RunSummary) = RunMetrics(run.healthConnectRecordId, run.startTime, run.endTime,
    MetricValue(run.durationSeconds, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH), generatedAt = start)
  private fun changes(db: OpenRunCoachDatabase) = db.openHelper.readableDatabase.query("SELECT total_changes()").use { it.moveToFirst(); it.getLong(0) }
  private fun withDatabase(block: suspend (OpenRunCoachDatabase) -> Unit) = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java).build()
    try { block(db) } finally { db.close() }
  }
}
