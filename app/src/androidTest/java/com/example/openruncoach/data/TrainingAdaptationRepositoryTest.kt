package com.example.openruncoach.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.data.local.WorkoutAdjustmentEntity
import com.example.openruncoach.domain.*
import com.example.openruncoach.domain.goal.*
import com.example.openruncoach.domain.plan.*
import com.example.openruncoach.domain.profile.ProfileConfidence
import com.example.openruncoach.domain.workout.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingAdaptationRepositoryTest {
  private val clock = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC)
  private val today = LocalDate.now(clock)
  private val context: Context get() = ApplicationProvider.getApplicationContext()
  private val future = StructuredWorkoutSamples.easyRun().copy(id = "future", date = today.plusDays(1), goalId = "goal")

  @Test fun checkoutCorrectionAndDuplicateTriggersConvergeAcrossPlanAndDetail() = withDatabase { db ->
    seed(db)
    val baseline = db.coachingWorkflowDao().getWorkout(future.id)
    val feedback = RunFeedbackRepository(db, clock)
    feedback.saveFeedback(draft(PainLevel.SIGNIFICANT))
    val rest = db.coachingWorkflowDao().getAdjustment(future.id)!!
    assertEquals(WorkoutType.REST, AdaptationAdjustmentCodec.effective(rest)!!.type)
    assertTrue(TrainingPlanRepository(db).observeWorkoutPresentation("plan").first().getValue(future.id).adjusted)
    assertEquals(baseline, db.coachingWorkflowDao().getWorkout(future.id))
    assertSameProjection(db)
    val execution = db.coachingWorkflowDao().getStoredExecutions()
    coroutineScope {
      (1..5).map { async(Dispatchers.IO) { PostRunReconciler(db, clock).reconcileAll() } }.awaitAll()
    }
    assertEquals(rest, db.coachingWorkflowDao().getAdjustment(future.id))
    assertEquals(execution, db.coachingWorkflowDao().getStoredExecutions())
    feedback.saveFeedback(draft(PainLevel.NONE))
    val corrected = db.coachingWorkflowDao().getAdjustment(future.id)!!
    assertTrue(corrected.adjustments.startsWith(AdaptationAdjustmentCodec.KEEP_PREFIX))
    assertFalse(TrainingPlanRepository(db).observeWorkoutPresentation("plan").first().getValue(future.id).adjusted)
    assertEquals(future, AdaptationAdjustmentCodec.effective(corrected))
    assertEquals(1, db.runFeedbackDao().getAllFeedback().size)
    assertSameProjection(db)
    val plan = TrainingPlanRepository(db, clock = clock).getActivePlan()!!
    TrainingPlanRepository(db, clock = clock).saveAsActive(plan)
    assertEquals(baseline, db.coachingWorkflowDao().getWorkout(future.id))
  }

  @Test fun missingCheckoutRemainsUnknownAndNeverInventsRecovery() = withDatabase { db ->
    seed(db)
    PostRunReconciler(db, clock).reconcileAll()
    assertTrue(db.runFeedbackDao().getAllFeedback().isEmpty())
    assertTrue(WorkoutResponseRepository(db).getAll().isEmpty())
    val adjustment = db.coachingWorkflowDao().getAdjustment(future.id)!!
    assertTrue(adjustment.adjustments.startsWith(AdaptationAdjustmentCodec.KEEP_PREFIX))
    assertTrue(adjustment.reasons.contains("Missing feedback"))
    assertSameProjection(db)
  }

  @Test fun unassignedPainStillAdaptsFutureWorkWithoutCreatingAnAssociation() = withDatabase { db ->
    seed(db, associate = false)
    RunFeedbackRepository(db, clock).saveFeedback(draft(PainLevel.SIGNIFICANT))
    assertNull(db.coachingWorkflowDao().getAssociationForPhysicalRun("physical"))
    assertTrue(WorkoutResponseRepository(db).getAll().isEmpty())
    assertEquals(WorkoutType.REST.name, db.coachingWorkflowDao().getAdjustment(future.id)!!.adjustedType)
  }

  @Test fun unresolvedSameDayRunDoesNotRewriteItsPotentialPrescription() = withDatabase { db ->
    seed(db, associate = false)
    val dao = db.coachingWorkflowDao()
    val original = dao.getWorkout(future.id)!!
    val sameDay = original.copy(workout = original.workout.copy(dateEpochDay = today.toEpochDay()))
    dao.replaceWorkout(sameDay)
    val run = db.runDao().getAllRuns().single()
    val start = clock.instant().minusSeconds(7_200)
    db.runDao().upsertAll(listOf(run.copy(startTimeEpochMillis = start.toEpochMilli(),
      endTimeEpochMillis = start.plusSeconds(1_800).toEpochMilli())))
    PostRunReconciler(db, clock).reconcileAll()
    assertNull(dao.getAssociationForPhysicalRun("physical"))
    assertNull(dao.getAdjustment(future.id))
    assertEquals(sameDay, dao.getWorkout(future.id))
  }

  @Test fun associatedPrescriptionAndLegacyPreRunAdjustmentKeepTheirHistoryAndOwnership() = withDatabase { db ->
    seed(db)
    val legacy = WorkoutAdjustmentEntity(future.id, "EASY_RUN", 20, 2, 3, "YELLOW",
      "Saved pre-run check-in", "Reduce duration", clock.millis())
    db.coachingWorkflowDao().upsertAdjustment(legacy)
    RunFeedbackRepository(db, clock).saveFeedback(draft(PainLevel.SIGNIFICANT))
    assertEquals(legacy, db.coachingWorkflowDao().getAdjustment(future.id))
    val execution = db.coachingWorkflowDao().getStoredExecutions().single()
    val oldWorkout = db.coachingWorkflowDao().getWorkout("performed")
    PostRunReconciler(db, clock).reconcileAll()
    assertEquals(execution, db.coachingWorkflowDao().getStoredExecutions().single())
    assertEquals(oldWorkout, db.coachingWorkflowDao().getWorkout("performed"))
    assertEquals("performed", db.coachingWorkflowDao().getAssociationForPhysicalRun("physical")!!.plannedWorkoutId)
  }

  @Test fun fileDatabaseRecreationAndDayRolloverRetainOneStructuredAdjustment() = runBlocking {
    val name = "adaptation-${System.nanoTime()}.db"
    fun open() = Room.databaseBuilder(context, OpenRunCoachDatabase::class.java, name).build()
    var db = open()
    try {
      seed(db)
      RunFeedbackRepository(db, clock).saveFeedback(draft(PainLevel.SIGNIFICANT))
      val saved = db.coachingWorkflowDao().getAdjustment(future.id)
      val original = db.coachingWorkflowDao().getWorkout(future.id)
      db.close()
      db = open()
      PostRunReconciler(db, clock).reconcileAll()
      assertEquals(saved, db.coachingWorkflowDao().getAdjustment(future.id))
      assertEquals(original, db.coachingWorkflowDao().getWorkout(future.id))
      val nextDay = Clock.offset(clock, java.time.Duration.ofDays(1))
      PostRunReconciler(db, nextDay).reconcileAll()
      assertEquals(WorkoutType.REST, AdaptationAdjustmentCodec.effective(db.coachingWorkflowDao().getAdjustment(future.id)!!)!!.type)
      assertSameProjection(db)
    } finally { db.close(); context.deleteDatabase(name) }
  }

  @Test fun structuredCodecPreservesQualityBlocksAndAdherenceUsesEffectiveDistance() {
    val interval = StructuredWorkoutSamples.intervals()
    val repeat = interval.blocks[1] as RepeatBlock
    val changed = repeat.copy(segments = repeat.segments.map { segment ->
      if (segment.category == SegmentCategory.WORK) segment.copy(completion = DistanceCompletion(840.0)) else segment
    })
    val effective = interval.copy(blocks = listOf(interval.blocks[0], changed, interval.blocks[2]), origin = WorkoutOrigin.ADJUSTED)
    val decision = com.example.openruncoach.domain.adaptation.TrainingAdaptationDecision(
      com.example.openruncoach.domain.adaptation.TrainingAdaptationAction.PROGRESS, TrainingCapability.SPEED, listOf("Repeated explicit quality evidence."))
    val encoded = AdaptationAdjustmentCodec.encode(com.example.openruncoach.domain.adaptation.AdaptedPrescription(interval, effective, decision), clock.millis(), today)
    assertEquals(effective, AdaptationAdjustmentCodec.effective(encoded))
    assertEquals(repeat.segments[1], (AdaptationAdjustmentCodec.effective(encoded)!!.blocks[1] as RepeatBlock).segments[1])
    val distance = StructuredWorkoutSamples.longRun()
    val shorter = distance.copy(blocks = listOf((distance.blocks.single() as SingleSegmentBlock).let {
      it.copy(segment = it.segment.copy(completion = DistanceCompletion(6_400.0)))
    }))
    val actual = RunSummary("actual", clock.instant().minusSeconds(2_000), clock.instant(), 2_000, 6_400.0, null, null, null)
    val feedback = RunFeedback("checkout", "actual", 3, CardioEffort.EASY, MuscleFatigue.NONE, PainLevel.NONE,
      CompletionStatus.COMPLETED, null, null, clock.instant(), clock.instant())
    val adjusted = AdjustedWorkout(distance.id, distance.type, 0, 3, 4, shorter)
    val execution = WorkoutAdherenceEvaluator(clock).evaluate(distance, adjusted, actual, feedback)
    assertEquals(6_400.0, execution.intendedDistanceMeters!!, 0.0)
    assertEquals(WorkoutExecutionStatus.COMPLETED_AS_PLANNED, execution.status)
  }

  private suspend fun assertSameProjection(db: OpenRunCoachDatabase) {
    val entry = TrainingPlanRepository(db, clock = clock).getActivePlan()!!.weeks.single().workouts.single()
    val detail = StructuredWorkoutRepository(db).observeWorkoutDetails(future.id).first()!!
    assertEquals(entry.workout, detail.workout)
    assertEquals(entry.originalWorkout, detail.originalWorkout)
    assertEquals(entry.adjustmentReasons, detail.adjustmentReasons)
  }

  private suspend fun seed(db: OpenRunCoachDatabase, associate: Boolean = true) {
    TrainingGoalRepository(db, AthletePerformanceProfileRepository(db), TrainingGoalFactory(clock, idProvider = { "goal" }))
      .createGoal(TrainingGoalDraft(GoalDistance.TEN_K, goalType = GoalType.COMPLETE_DISTANCE))
    val week = TrainingWeek(0, today, listOf(PlannedWorkoutEntry(WeeklyWorkoutSlot.EARLY_WEEK, future, "Fixture")),
      null, null, WeekLoadClassification.NORMAL, "Fixture week", listOf("Fixture"))
    TrainingPlanRepository(db, clock = clock).saveAsActive(TrainingPlan("plan", "goal", clock.instant(), today,
      today.plusDays(6), listOf(week), emptyList(), 1, "Fixture plan", ProfileConfidence.LOW, TrainingPlanStatus.ACTIVE))
    val start = clock.instant().minusSeconds(86_400)
    val run = RunEntity("physical", start.toEpochMilli(), start.plusSeconds(1_800).toEpochMilli(), 1_800,
      4_500.0, null, null, 400.0, clock.millis(), physicalRunId = "physical")
    db.runDao().upsertAll(listOf(run))
    if (associate) {
      val workout = simpleDurationWorkout("performed", today.minusDays(1), WorkoutType.EASY_RUN, 30, 3, 4,
        start.minusSeconds(86_400), goalId = "goal")
      db.coachingWorkflowDao().replaceWorkout(StructuredWorkoutPersistenceMapper.toRecord(workout, "PLANNED"))
      WorkoutExecutionRepository(db, clock).assignManually(run.toRunSummary(), "performed")
    }
  }

  private fun draft(pain: PainLevel) = RunFeedbackDraft("physical", 3, CardioEffort.EASY, MuscleFatigue.NONE,
    pain, CompletionStatus.COMPLETED, null, null)

  private fun withDatabase(block: suspend (OpenRunCoachDatabase) -> Unit) = runBlocking {
    val db = Room.inMemoryDatabaseBuilder(context, OpenRunCoachDatabase::class.java).build()
    try { block(db) } finally { db.close() }
  }
}
