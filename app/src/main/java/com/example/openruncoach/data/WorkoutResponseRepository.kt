package com.example.openruncoach.data

import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.workout.WorkoutResponse
import com.example.openruncoach.domain.workout.WorkoutResponseEvaluator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

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
  private val feedbackDao = database.runFeedbackDao()
  private val metricsDao = database.runMetricsDao()

  suspend fun getAll(): List<WorkoutResponse> {
    val runsById = runDao.getCanonicalRuns().associateBy { it.healthConnectRecordId }
    val feedbackByRun = feedbackDao.getAllFeedback().associateBy { it.runId }
    val metricsByRun = metricsDao.getAllRunMetrics().associateBy { it.snapshot.runId }
    return workflowDao.getAllExecutions().mapNotNull { executionEntity ->
      val run = runsById[executionEntity.actualRunId] ?: return@mapNotNull null
      val feedback = feedbackByRun[executionEntity.actualRunId] ?: return@mapNotNull null
      val workout = workflowDao.getWorkout(executionEntity.plannedWorkoutId) ?: return@mapNotNull null
      evaluator.evaluate(
        plannedWorkout = StructuredWorkoutPersistenceMapper.toDomain(workout),
        adjustedWorkout = workflowDao.getAdjustment(executionEntity.plannedWorkoutId)?.toAdjustedWorkout(),
        execution = executionEntity.toDomain(),
        actualRun = run.toRunSummary(),
        feedback = feedback.toDomain(),
        metrics = metricsByRun[executionEntity.actualRunId]?.toDomain(),
      )
    }.sortedBy(WorkoutResponse::runId)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeForRun(runId: String): Flow<WorkoutResponse?> = combine(
    runDao.observeRun(runId),
    feedbackDao.observeFeedbackForRun(runId),
    metricsDao.observeRunMetrics(runId),
    workflowDao.observeExecutionForRun(runId),
  ) { run, feedback, metrics, execution ->
    ResponseSource(run, feedback, metrics, execution)
  }.flatMapLatest { source ->
    val run = source.run
    val feedback = source.feedback
    val execution = source.execution
    if (run == null || !run.isCanonical || feedback == null || execution == null) {
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
          feedback = feedback.toDomain(),
          metrics = source.metrics?.toDomain(),
        )
      }
    }
  }

  private data class ResponseSource(
    val run: com.example.openruncoach.data.local.RunEntity?,
    val feedback: com.example.openruncoach.data.local.RunFeedbackEntity?,
    val metrics: com.example.openruncoach.data.local.RunMetricsWithValues?,
    val execution: com.example.openruncoach.data.local.WorkoutExecutionEntity?,
  )
}
