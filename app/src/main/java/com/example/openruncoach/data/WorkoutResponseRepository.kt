package com.example.openruncoach.data

import androidx.room.withTransaction
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.workout.WorkoutResponse
import com.example.openruncoach.domain.RunActualResolver
import com.example.openruncoach.domain.RunRelevance
import com.example.openruncoach.domain.workout.WorkoutResponseEvaluator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Derives one deterministic response from persisted canonical inputs. The response is not a second
 * completion record; stable run/workout identities keep re-analysis idempotent.
 */
class WorkoutResponseRepository(
  private val database: OpenRunCoachDatabase,
  private val evaluator: WorkoutResponseEvaluator = WorkoutResponseEvaluator(),
) {
  private val workflowDao = database.coachingWorkflowDao()
  private val runDao = database.runDao()
  private val feedbackRepository = RunFeedbackRepository(database)
  private val metricsDao = database.runMetricsDao()

  /** Same source predicates and evaluator as observeForRun, without observing a Flow or writing. */
  internal suspend fun getForRun(runId: String): WorkoutResponse? = database.withTransaction {
    val physicalId = runDao.getRun(runId)?.physicalRunId ?: runId
    val run = runDao.getCanonicalRunByPhysicalId(physicalId) ?: return@withTransaction null
    if (run.relevance != RunRelevance.ACTIVE.name) return@withTransaction null
    val feedback = feedbackRepository.getFeedbackForRun(run.physicalRunId) ?: return@withTransaction null
    val execution = workflowDao.getExecutionForPhysicalRun(physicalId) ?: return@withTransaction null
    if (execution.actualRunId != run.healthConnectRecordId) return@withTransaction null
    val workout = workflowDao.getWorkout(execution.plannedWorkoutId) ?: return@withTransaction null
    val metrics = metricsDao.getRunMetrics(run.healthConnectRecordId)?.toDomain().forCanonicalRun(run.toRunSummary())
    if (RunActualResolver.resolve(run.toRunSummary(), metrics).durationSeconds <= 0L) return@withTransaction null
    evaluator.evaluate(
      plannedWorkout = StructuredWorkoutPersistenceMapper.toDomain(workout),
      adjustedWorkout = workflowDao.getAdjustment(execution.plannedWorkoutId)?.toAdjustedWorkout(),
      execution = execution.toDomain(),
      actualRun = run.toRunSummary(),
      feedback = feedback,
      metrics = metrics,
    )
  }

  suspend fun getAll(): List<WorkoutResponse> = database.withTransaction {
    runDao.getCanonicalRuns().distinctBy { it.physicalRunId }
      .mapNotNull { getForRun(it.physicalRunId) }.sortedBy(WorkoutResponse::physicalRunId)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeForRun(runId: String): Flow<WorkoutResponse?> = runDao.observeAllRuns().map { runs ->
    val physicalId = runs.firstOrNull { it.healthConnectRecordId == runId }?.physicalRunId ?: runId
    runs.firstOrNull { it.physicalRunId == physicalId && it.isCanonical && it.relevance == RunRelevance.ACTIVE.name }
  }.distinctUntilChanged().flatMapLatest { canonical ->
    if (canonical == null) flowOf(null) else combine(
      feedbackRepository.observeFeedbackForRun(canonical.physicalRunId),
      metricsDao.observeRunMetrics(canonical.healthConnectRecordId),
      workflowDao.observeExecutionForRun(canonical.healthConnectRecordId),
    ) { _, _, execution -> execution }.flatMapLatest { execution ->
      if (execution == null) flowOf(null) else combine(
        workflowDao.observeWorkout(execution.plannedWorkoutId),
        workflowDao.observeAdjustment(execution.plannedWorkoutId),
      ) { _, _ -> getForRun(canonical.physicalRunId) }
    }
  }
}
