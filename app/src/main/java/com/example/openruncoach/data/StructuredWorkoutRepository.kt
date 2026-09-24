package com.example.openruncoach.data

import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.domain.workout.StructuredWorkout
import com.example.openruncoach.domain.workout.WorkoutLifecycleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StructuredWorkoutRepository(database: OpenRunCoachDatabase) {
  private val dao = database.coachingWorkflowDao()

  fun observeWorkout(workoutId: String): Flow<StructuredWorkout?> =
    dao.observeWorkout(workoutId).map { it?.let(StructuredWorkoutPersistenceMapper::toDomain) }

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
