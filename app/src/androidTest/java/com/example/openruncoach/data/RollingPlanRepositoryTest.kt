package com.example.openruncoach.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.OpenRunCoachEnvironment
import com.example.openruncoach.data.diagnostics.DiagnosticDumpRepository
import com.example.openruncoach.data.diagnostics.DumpPrivacyMode
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.goal.*
import com.example.openruncoach.domain.plan.*
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import com.example.openruncoach.domain.workout.*
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.io.File
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
class RollingPlanRepositoryTest {
  private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)
  private val oldClock = Clock.offset(clock, Duration.ofDays(-90))
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Test fun concurrentReconciliationAppendsOnceAndRetainsSavedRowsAndUnknownCheckout() = withDatabase { db ->
    val oldPlan = seed(db)
    val dao = db.trainingPlanDao()
    val oldWeeks = dao.getWeeks(oldPlan.id)
    val oldReasons = dao.getReasons(oldPlan.id)
    val oldBlocks = dao.getBlocks(oldPlan.id)
    val oldReferences = dao.getWorkoutReferences(oldPlan.id)
    val savedWorkout = db.coachingWorkflowDao().getWorkout(oldReferences.single().workoutId)
    coroutineScope {
      (1..5).map { async(Dispatchers.IO) { PostRunReconciler(db, clock).reconcileAll() } }.awaitAll()
    }
    val plan = TrainingPlanRepository(db, clock = clock).getActivePlan()!!
    assertEquals(oldPlan.id, plan.id)
    assertEquals(5, plan.weeks.size)
    assertEquals(oldWeeks, dao.getWeeks(plan.id).take(1))
    assertEquals(oldReasons, dao.getReasons(plan.id).filter { it.weekIndex == 0 })
    assertEquals(oldBlocks, dao.getBlocks(plan.id).take(oldBlocks.size))
    assertEquals(oldReferences, dao.getWorkoutReferences(plan.id).filter { it.weekIndex == 0 })
    assertEquals(savedWorkout, db.coachingWorkflowDao().getWorkout(oldReferences.single().workoutId))
    assertEquals(1, dao.getAllPlans().size)
    assertEquals(5, dao.getWorkoutReferences(plan.id).map { it.workoutId }.distinct().size)
    assertTrue(db.runFeedbackDao().getAllFeedback().isEmpty())
    assertTrue(db.coachingWorkflowDao().getStoredExecutions().isEmpty())
    assertTrue(WorkoutResponseRepository(db).getAll().isEmpty())
    val snapshot = dao.getWorkoutReferences(plan.id)
    PostRunReconciler(db, clock).reconcileAll()
    assertEquals(snapshot, dao.getWorkoutReferences(plan.id))
  }

  @Test fun gettersAndAvailabilityReadsDoNotReplenishExpiredForecast() = withDatabase { db ->
    val plan = seed(db)
    val before = db.trainingPlanDao().getWorkoutReferences(plan.id)
    val repository = TrainingPlanRepository(db, clock = clock)
    repeat(3) {
      repository.getActivePlan()
      repository.getActivePlanSnapshot()
      repository.observeActivePlan().first()
      TrainingAvailabilityRepository(db).get()
    }
    assertEquals(before, db.trainingPlanDao().getWorkoutReferences(plan.id))
    assertEquals(plan.endDate!!.toEpochDay(), db.trainingPlanDao().getActivePlan()!!.endDateEpochDay)
  }

  @Test fun diagnosticExportDoesNotReplenishAnExpiredForecast() = withDatabase { db ->
    val plan = seed(db)
    val before = db.trainingPlanDao().getAllPlans()
    val references = db.trainingPlanDao().getWorkoutReferences(plan.id)
    val scratch = File(context.cacheDir, "rolling-dump-${System.nanoTime()}").apply { mkdirs() }
    try {
      DiagnosticDumpRepository(context, OpenRunCoachEnvironment("rolling-test", db, clock, isSimulation = true))
        .writeArchive(File(scratch, "dump.zip"), scratch, DumpPrivacyMode.SHARE_SAFE) {}
      assertEquals(before, db.trainingPlanDao().getAllPlans())
      assertEquals(references, db.trainingPlanDao().getWorkoutReferences(plan.id))
    } finally { scratch.deleteRecursively() }
  }

  @Test fun newWeeksUsePersistedAvailabilityAndKeepSavedHistory() = withDatabase { db ->
    val plan = seed(db)
    val oldWorkout = plan.weeks.single().workouts.single().workout
    db.coachingWorkflowDao().updateLifecycleState(oldWorkout.id, "SKIPPED")
    val retained = db.coachingWorkflowDao().getWorkout(oldWorkout.id)
    TrainingAvailabilityRepository(db).save(TrainingAvailability(setOf(DayOfWeek.SUNDAY)))
    PostRunReconciler(db, clock).reconcileAll()
    val result = TrainingPlanRepository(db, clock = clock).getActivePlan()!!
    assertEquals(retained, db.coachingWorkflowDao().getWorkout(oldWorkout.id))
    assertTrue(result.weeks.drop(1).flatMap { it.workouts }.all { it.workout.date.dayOfWeek == DayOfWeek.SUNDAY })
    assertEquals(LocalDate.now(clock), result.weeks[1].startDate)
  }

  @Test fun pausedArchivedAndCompletedGoalsDoNotExtend() = withDatabase { db ->
    val plan = seed(db)
    val before = db.trainingPlanDao().getWorkoutReferences(plan.id)
    GoalStatus.entries.filter { it != GoalStatus.ACTIVE }.forEach { status ->
      db.trainingGoalDao().updateStatus(plan.goalId, status.name)
      PostRunReconciler(db, clock).reconcileAll()
      assertEquals(before, db.trainingPlanDao().getWorkoutReferences(plan.id))
    }
  }

  @Test fun rollbackDoesNotLeavePartialWeeksAndRetryCreatesOneForecast() = withDatabase { db ->
    val plan = seed(db)
    try {
      db.withTransaction {
        PostRunReconciler(db, clock).reconcileAll()
        error("Abort input update")
      }
      fail("Transaction must abort")
    } catch (_: IllegalStateException) { }
    assertEquals(1, db.trainingPlanDao().getWeeks(plan.id).size)
    assertEquals(1, db.trainingPlanDao().getWorkoutReferences(plan.id).size)
    PostRunReconciler(db, clock).reconcileAll()
    assertEquals(5, db.trainingPlanDao().getWeeks(plan.id).size)
    assertEquals(5, db.trainingPlanDao().getWorkoutReferences(plan.id).size)
  }

  @Test fun fileDatabaseRestartRetainsForecastAndDayRolloverAppendsOneWeek() = runBlocking<Unit> {
    val name = "rolling-${System.nanoTime()}.db"
    fun open() = Room.databaseBuilder(context, OpenRunCoachDatabase::class.java, name).build()
    var db = open()
    try {
      val plan = seed(db)
      PostRunReconciler(db, clock).reconcileAll()
      val saved = db.trainingPlanDao().getWorkoutReferences(plan.id)
      db.close()
      db = open()
      PostRunReconciler(db, clock).reconcileAll()
      assertEquals(saved, db.trainingPlanDao().getWorkoutReferences(plan.id))
      PostRunReconciler(db, Clock.offset(clock, Duration.ofDays(1))).reconcileAll()
      assertEquals(saved.size + 1, db.trainingPlanDao().getWorkoutReferences(plan.id).size)
      assertEquals(saved, db.trainingPlanDao().getWorkoutReferences(plan.id).take(saved.size))
    } finally { db.close(); context.deleteDatabase(name) }
  }

  @Test fun satisfiedHorizonDoesNotLoadForecastEvidence() = runBlocking<Unit> {
    val queries = java.util.concurrent.CopyOnWriteArrayList<String>()
    val db = Room.inMemoryDatabaseBuilder(context, OpenRunCoachDatabase::class.java)
      .setQueryCallback({ sql, _ -> queries.add(sql) }, java.util.concurrent.Executor { it.run() })
      .build()
    try {
      seed(db)
      PostRunReconciler(db, clock).reconcileAll()
      val repository = TrainingPlanRepository(db, clock = clock)
      val plan = requireNotNull(repository.getActivePlan())
      val profile = AthletePerformanceProfileRepository(db,
        AthletePerformanceProfileCalculator(clock, clock.zone)).recalculate()
      queries.clear()
      repository.extendForecast(profile)
      assertTrue("Covered forecast must skip evidence queries: $queries", queries.none { sql ->
        sql.trimStart().startsWith("SELECT", ignoreCase = true) &&
          listOf("run_metrics", "run_metric_values", "run_metric_samples", "training_availability").any {
            sql.contains(it, ignoreCase = true)
          }
      })
      assertEquals(plan, repository.getActivePlan())
    } finally { db.close() }
  }

  @Test fun reconciliationReusesInjectedForecastExtender() = withDatabase { db ->
    var calls = 0
    val extender = PlanForecastExtender { calls++ }
    val reconciler = PostRunReconciler(db, clock, forecastExtender = extender)
    reconciler.reconcileAll()
    reconciler.reconcileAll()
    assertEquals(2, calls)
    assertTrue(db.trainingPlanDao().getAllPlans().isEmpty())
  }

  private suspend fun seed(db: OpenRunCoachDatabase): TrainingPlan {
    val profiles = AthletePerformanceProfileRepository(db, AthletePerformanceProfileCalculator(oldClock, oldClock.zone))
    val goal = TrainingGoalRepository(db, profiles, TrainingGoalFactory(oldClock, idProvider = { "goal" }))
      .createGoal(TrainingGoalDraft(GoalDistance.TEN_K, goalType = GoalType.COMPLETE_DISTANCE))
    val profile = profiles.recalculate()
    val plan = TrainingPlanGenerator(oldClock).generate(goal, profile)
    TrainingPlanRepository(db, clock = oldClock).saveAsActive(plan)
    return plan
  }

  private fun withDatabase(block: suspend (OpenRunCoachDatabase) -> Unit) = runBlocking<Unit> {
    val db = Room.inMemoryDatabaseBuilder(context, OpenRunCoachDatabase::class.java).build()
    try { block(db) } finally { db.close() }
  }
}
