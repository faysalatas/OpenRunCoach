package com.example.openruncoach.data

import androidx.room.withTransaction
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.RunActualResolver
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.athlete.AthleteState
import com.example.openruncoach.domain.athlete.AthleteStateBuilder
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.goal.GoalCompletionResult
import com.example.openruncoach.domain.profile.AthletePerformanceProfileCalculator
import com.example.openruncoach.domain.workout.WorkoutAdherenceEvaluator
import com.example.openruncoach.domain.workout.WorkoutExecutionEvidence
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import com.example.openruncoach.domain.workout.WorkoutResponse
import java.time.Clock

/**
 * The post-run commit boundary and input seam for later training adaptation. No next workout is
 * generated here. Responses and AthleteState remain rebuildable projections, not duplicate rows.
 */
data class PostRunReconciliationResult(
  val physicalRunId: String,
  val execution: WorkoutExecutionResult?,
  val response: WorkoutResponse?,
  val athleteState: AthleteState,
  val goalCompletion: GoalCompletionResult?,
  val coachingDecision: CoachingDecision?,
)

/**
 * Resolves current persisted inputs, never caller-supplied run/checkout snapshots. Room owns the
 * transaction and serializes competing writers, including calls through different repository
 * instances. Input writers can join this transaction so cancellation rolls the whole update back.
 * No provider I/O, automatic association, or UI work belongs inside this boundary.
 */
class PostRunReconciler(
  private val database: OpenRunCoachDatabase,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val profiles: AthletePerformanceProfileRepository = AthletePerformanceProfileRepository(
    database, AthletePerformanceProfileCalculator(clock, clock.zone),
  ),
) {
  suspend fun reconcilePhysicalRun(physicalRunId: String): PostRunReconciliationResult = database.withTransaction {
    val id = database.runDao().getRun(physicalRunId)?.physicalRunId ?: physicalRunId
    reconcile(listOf(id)).single()
  }

  /** Explicit startup/import recovery; passive readers must never call this. */
  suspend fun reconcileAll(): List<PostRunReconciliationResult> = database.withTransaction {
    val ids = (database.runDao().getAllRuns().map { it.physicalRunId } +
      database.coachingWorkflowDao().getStoredExecutions().map { it.physicalRunId }).distinct().sorted()
    reconcile(ids)
  }

  suspend fun setRelevance(physicalRunId: String, ignored: Boolean) = database.withTransaction {
    database.runDao().updateRelevance(physicalRunId,
      if (ignored) RunRelevance.IGNORED.name else RunRelevance.ACTIVE.name)
    reconcilePhysicalRun(physicalRunId)
  }

  /** Repository input mutations join the same commit; provider reads must happen beforehand. */
  internal suspend fun <T> updateInputs(update: suspend () -> T): T = database.withTransaction {
    val result = update()
    reconcileAll()
    result
  }

  private suspend fun reconcile(ids: List<String>): List<PostRunReconciliationResult> {
    val runs = database.runDao()
    val workflow = database.coachingWorkflowDao()
    val feedback = RunFeedbackRepository(database, clock)
    val responses = WorkoutResponseRepository(database)
    val adherence = WorkoutAdherenceEvaluator(clock)
    for (id in ids) {
      val canonical = runs.getCanonicalRunByPhysicalId(id)?.toRunSummary()
      val association = workflow.getAssociationForPhysicalRun(id)
      val stored = workflow.getStoredExecutionsForPhysicalRun(id)
      val previous = stored.firstOrNull { it.plannedWorkoutId == association?.plannedWorkoutId } ?: stored.firstOrNull()
      val checkout = feedback.getFeedbackForRun(id)
      val record = association?.let { workflow.getWorkout(it.plannedWorkoutId) }
      if (canonical == null || canonical.relevance != RunRelevance.ACTIVE || checkout == null || association == null || record == null) {
        workflow.deleteExecutionForPhysicalRun(id)
        val staleLifecycle = association?.plannedWorkoutId?.takeIf {
          record?.workout?.lifecycleState in completedLifecycleStates
        }
        (stored.map { it.plannedWorkoutId } + listOfNotNull(staleLifecycle)).distinct().forEach { workoutId ->
          if (workflow.getAssociationForWorkout(workoutId)?.physicalRunId in listOf(null, id)) {
            workflow.updateLifecycleState(workoutId, WorkoutLifecycleState.PLANNED.name)
          }
        }
        continue
      }
      val metrics = database.runMetricsDao().getRunMetrics(canonical.healthConnectRecordId)?.toDomain().forCanonicalRun(canonical)
      val actual = RunActualResolver.resolve(canonical, metrics)
      val workout = StructuredWorkoutPersistenceMapper.toDomain(record)
      val adjustment = workflow.getAdjustment(workout.id)?.toAdjustedWorkout()
      // Preserve explicit quality evidence only for the same association and actual performance.
      val quality = previous?.takeIf {
        it.plannedWorkoutId == workout.id && it.actualDistanceMeters == actual.distanceMeters &&
          it.actualDurationSeconds == actual.durationSeconds
      }?.qualityWorkCompletionFraction
      val rebuilt = adherence.evaluate(workout, adjustment, actual, checkout, WorkoutExecutionEvidence(quality)).toEntity()
      if (stored.size != 1 || previous == null || rebuilt.copy(evaluatedAtEpochMillis = previous.evaluatedAtEpochMillis) != previous) {
        workflow.deleteExecutionForPhysicalRun(id)
        stored.filter { it.plannedWorkoutId != workout.id }.forEach {
          if (workflow.getAssociationForWorkout(it.plannedWorkoutId) == null) {
            workflow.updateLifecycleState(it.plannedWorkoutId, WorkoutLifecycleState.PLANNED.name)
          }
        }
        workflow.upsertExecution(rebuilt)
      }
      if (association.actualRunId != canonical.healthConnectRecordId) {
        workflow.upsertAssociation(association.copy(actualRunId = canonical.healthConnectRecordId))
      }
      if (record.workout.lifecycleState !in completedLifecycleStates) {
        workflow.updateLifecycleState(workout.id, WorkoutLifecycleState.COMPLETED.name)
      }
    }

    val profile = profiles.recalculate()
    val canonicalRuns = runs.getCanonicalRuns().mapNotNull {
      runs.getActualRun(it.healthConnectRecordId)?.toActualSummary()
    }
    val allResponses = responses.getAll()
    val athleteState = AthleteStateBuilder().build(profile, canonicalRuns, allResponses)
    val completion = GoalCompletionRepository(database, clock)
    val results = ids.map { id ->
      val canonical = runs.getActualRun(id)?.toActualSummary()
      val checkout = feedback.getFeedbackForRun(id)
      val active = canonical?.takeIf { it.relevance == RunRelevance.ACTIVE }
      val goal = database.trainingGoalDao().getActiveGoal()?.toDomain()
      val goalResult = if (active != null && checkout != null && goal != null) {
        completion.evaluateObservedRun(goal, active,
          database.runMetricsDao().getRunMetrics(active.healthConnectRecordId)?.toDomain().forCanonicalRun(active), checkout)
      } else null
      val execution = workflow.getExecutionForPhysicalRun(id)?.toDomain()
      val response = allResponses.singleOrNull { it.physicalRunId == id }
      PostRunReconciliationResult(id, execution, response, athleteState, goalResult,
        if (active != null && checkout != null) CoachingEngine(clock).analyze(
          active, canonicalRuns, checkout, execution, response,
        ) else null)
    }
    database.trainingGoalDao().getActiveGoal()?.toDomain()?.let { completion.refreshReadiness(it, profile) }
    return results
  }

  private companion object {
    val completedLifecycleStates = setOf("COMPLETED", "CHECKOUT_REQUIRED", "ANALYZED", "NEXT_WORKOUT_CREATED")
  }
}
