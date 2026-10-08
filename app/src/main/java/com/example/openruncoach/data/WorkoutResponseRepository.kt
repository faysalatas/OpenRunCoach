package com.example.openruncoach.data

import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.workout.WorkoutResponse
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
  database: OpenRunCoachDatabase,
  private val evaluator: WorkoutResponseEvaluator = WorkoutResponseEvaluator(),
) {
  private val workflowDao = database.coachingWorkflowDao()
  private val runDao = database.runDao()
  private val feedbackRepository = RunFeedbackRepository(database)
  private val metricsDao = database.runMetricsDao()

  /** Same source predicates and evaluator as observeForRun, without observing a Flow or writing. */
  internal suspend fun getForRun(runId: String): WorkoutResponse? {
    val physicalId = runDao.getRun(runId)?.physicalRunId ?: runId
    val run = runDao.getCanonicalRunByPhysicalId(physicalId) ?: return null
    if (run.relevance != RunRelevance.ACTIVE.name) return null
    val feedback = feedbackRepository.getFeedbackForRun(run.physicalRunId) ?: return null
    val execution = workflowDao.getExecutionForRun(run.healthConnectRecordId) ?: return null
    val workout = workflowDao.getWorkout(execution.plannedWorkoutId) ?: return null
    return evaluator.evaluate(
      plannedWorkout = StructuredWorkoutPersistenceMapper.toDomain(workout),
      adjustedWorkout = workflowDao.getAdjustment(execution.plannedWorkoutId)?.toAdjustedWorkout(),
      execution = execution.toDomain(),
      actualRun = run.toRunSummary(),
      feedback = feedback,
      metrics = metricsDao.getRunMetrics(run.healthConnectRecordId)?.toDomain(),
    )
  }

  suspend fun getAll(): List<WorkoutResponse> {
    val runsById = runDao.getCanonicalRuns().associateBy { it.healthConnectRecordId }
    val feedbackByRun = feedbackRepository.getAllFeedback().associateBy { it.runId }
    val executions = workflowDao.getAllExecutions()
    val metricsByRun = RunMetricsRepository(metricsDao).getAllRunMetrics().associateBy { it.runId }
    return executions.mapNotNull { executionEntity ->
      val run = runsById[executionEntity.actualRunId] ?: return@mapNotNull null
      val feedback = feedbackByRun[run.physicalRunId] ?: return@mapNotNull null
      val workout = workflowDao.getWorkout(executionEntity.plannedWorkoutId) ?: return@mapNotNull null
      evaluator.evaluate(
        plannedWorkout = StructuredWorkoutPersistenceMapper.toDomain(workout),
        adjustedWorkout = workflowDao.getAdjustment(executionEntity.plannedWorkoutId)?.toAdjustedWorkout(),
        execution = executionEntity.toDomain(),
        actualRun = run.toRunSummary(),
        feedback = feedback,
        metrics = metricsByRun[executionEntity.actualRunId],
      )
    }.sortedBy(WorkoutResponse::runId)
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
    ) { feedback, metrics, execution -> ResponseSource(canonical, feedback, metrics, execution) }.flatMapLatest { source ->
      val run = source.run
      val feedback = source.feedback
      val execution = source.execution
      if (run == null || !run.isCanonical || run.relevance != RunRelevance.ACTIVE.name || feedback == null || execution == null) {
        flowOf(null)
      } else combine(
        workflowDao.observeWorkout(execution.plannedWorkoutId),
        workflowDao.observeAdjustment(execution.plannedWorkoutId),
      ) { workout, adjustment ->
        workout?.let {
          evaluator.evaluate(
            plannedWorkout = StructuredWorkoutPersistenceMapper.toDomain(it),
            adjustedWorkout = adjustment?.toAdjustedWorkout(),
            execution = execution.toDomain(),
            actualRun = run.toRunSummary(),
            feedback = feedback,
            metrics = source.metrics?.toDomain(),
          )
        }
      }
    }
  }

  private data class ResponseSource(
    val run: com.example.openruncoach.data.local.RunEntity?,
    val feedback: com.example.openruncoach.domain.RunFeedback?,
    val metrics: com.example.openruncoach.data.local.RunMetricsWithValues?,
    val execution: com.example.openruncoach.data.local.WorkoutExecutionEntity?,
  )
}
