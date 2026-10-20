package com.example.openruncoach.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.domain.plan.TrainingAvailability
import com.example.openruncoach.domain.plan.TrainingPlanGenerator
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.simpleDurationWorkout
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingAvailabilityRepositoryTest {
  private val clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC)

  @Test fun settingsSurviveRepositoryRecreationAndDistinguishMissingFromEmpty() = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java).build()
    try {
      val repository = TrainingAvailabilityRepository(database)
      assertNull(repository.get())
      val days = TrainingAvailability(setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), setOf(DayOfWeek.SATURDAY))
      repository.save(days)
      assertEquals(days, TrainingAvailabilityRepository(database).get())
      assertEquals(days, repository.availability.first())
      repository.save(TrainingAvailability(emptySet()))
      assertEquals(TrainingAvailability(emptySet()), repository.get())
      repository.save(null)
      assertNull(repository.get())
    } finally { database.close() }
  }

  @Test fun savingAndReadingAvailabilityNeverRewritesCompletedOrFutureWorkouts() = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java).build()
    try {
      val dao = database.coachingWorkflowDao()
      val actual = RunEntity("actual", 0L, 1_800_000L, 1_800L, 4_000.0, null, null, 450.0, 0L)
      database.runDao().upsertAll(listOf(actual))
      val saved = listOf("completed" to "COMPLETED", "future" to "PLANNED").map { (id, lifecycle) ->
        val workout = simpleDurationWorkout(id, LocalDate.now(clock).plusDays(2), WorkoutType.EASY_RUN, 30, 3, 4, clock.instant())
        val record = StructuredWorkoutPersistenceMapper.toRecord(workout, lifecycle, isPreview = false)
        dao.replaceWorkout(record)
        id to requireNotNull(dao.getWorkout(id))
      }
      val repository = TrainingAvailabilityRepository(database)
      repository.save(TrainingAvailability(setOf(DayOfWeek.SUNDAY)))
      repeat(3) {
        repository.get()
        repository.availability.first()
        TrainingPlanRepository(database, generator = TrainingPlanGenerator(clock), clock = clock).getActivePlan()
      }
      saved.forEach { (id, record) -> assertEquals(record, dao.getWorkout(id)) }
      assertEquals(actual, database.runDao().getRun("actual"))
      repository.save(null)
      saved.forEach { (id, record) -> assertEquals(record, dao.getWorkout(id)) }
      assertEquals(actual, database.runDao().getRun("actual"))
    } finally { database.close() }
  }
}
