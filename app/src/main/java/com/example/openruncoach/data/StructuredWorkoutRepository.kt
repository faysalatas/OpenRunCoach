package com.example.openruncoach.data

import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class StructuredWorkoutDetails(
  val workout: StructuredWorkout,
  val executionResult: WorkoutExecutionResult?,
  val actualRun: RunSummary? = null,
  val feedback: RunFeedback? = null,
)

class StructuredWorkoutRepository(database: OpenRunCoachDatabase) {
  private val dao = database.coachingWorkflowDao()
  private val runDao = database.runDao()
  private val feedbackDao = database.runFeedbackDao()

  fun observeWorkout(workoutId: String): Flow<StructuredWorkout?> =
    dao.observeWorkout(workoutId).map { it?.let(StructuredWorkoutPersistenceMapper::toDomain) }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeWorkoutDetails(workoutId: String): Flow<StructuredWorkoutDetails?> = combine(
    dao.observeWorkout(workoutId),
    dao.observeExecution(workoutId),
  ) { workout, execution -> workout to execution }
    .flatMapLatest { (workout, execution) ->
      if (workout == null) flowOf(null)
      else if (execution == null) {
        flowOf(StructuredWorkoutDetails(StructuredWorkoutPersistenceMapper.toDomain(workout), null))
      } else combine(
        runDao.observeRun(execution.actualRunId),
        feedbackDao.observeFeedbackForRun(execution.actualRunId),
      ) { run, feedback ->
        StructuredWorkoutDetails(
          workout = StructuredWorkoutPersistenceMapper.toDomain(workout),
          executionResult = execution.toDomain(),
          actualRun = run?.toRunSummary(),
          feedback = feedback?.toDomain(),
        )
      }
  }

  suspend fun getWorkout(workoutId: String): StructuredWorkout? =
    dao.getWorkout(workoutId)?.let(StructuredWorkoutPersistenceMapper::toDomain)

  suspend fun save(workout: StructuredWorkout, isPreview: Boolean = false) {
    val existingState = dao.getLifecycleState(workout.id) ?: WorkoutLifecycleState.PLANNED.name
    dao.replaceWorkout(StructuredWorkoutPersistenceMapper.toRecord(workout, existingState, isPreview))
  }

  suspend fun savePreviews(workouts: List<StructuredWorkout>) {
    workouts.forEach { save(it, isPreview = true) }
  }
}
