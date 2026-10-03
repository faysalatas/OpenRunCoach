package com.example.openruncoach.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.PlannedWorkoutEntity
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.data.local.WorkoutExecutionEntity
import com.example.openruncoach.domain.RunSummary
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRunAssociationRepositoryTest {
  @Test
  fun manualAssignmentCanBeReassignedAndUnassignedWithoutDeletingRunOrWorkout() = runBlocking {
    val database = database()
    try {
      database.runDao().upsertAll(listOf(runEntity("provider-a")))
      database.coachingWorkflowDao().upsertPlannedWorkout(workout("workout-a"))
      database.coachingWorkflowDao().upsertPlannedWorkout(workout("workout-b"))
      val repository = WorkoutExecutionRepository(database, CLOCK)
      val run = runSummary("provider-a")

      repository.assignManually(run, "workout-a")
      assertEquals("workout-a", database.coachingWorkflowDao().getAssociationForPhysicalRun("physical-1")?.plannedWorkoutId)
      assertEquals("MANUAL", database.coachingWorkflowDao().getAssociationForPhysicalRun("physical-1")?.source)

      repository.assignManually(run, "workout-b")
      assertEquals("workout-b", database.coachingWorkflowDao().getAssociationForPhysicalRun("physical-1")?.plannedWorkoutId)
      assertEquals(1, database.coachingWorkflowDao().getAllAssociations().size)

      repository.unassign(run)
      assertNull(database.coachingWorkflowDao().getAssociationForPhysicalRun("physical-1"))
      assertEquals("provider-a", database.runDao().observeRun("provider-a").first()?.healthConnectRecordId)
      assertEquals("workout-a", database.coachingWorkflowDao().getWorkout("workout-a")?.workout?.id)
    } finally {
      database.close()
    }
  }

  @Test
  fun providerReplacementWithSamePhysicalIdentityDoesNotCreateSecondAssociation() = runBlocking {
    val database = database()
    try {
      database.runDao().upsertAll(listOf(runEntity("provider-a"), runEntity("provider-b").copy(isCanonical = false)))
      database.coachingWorkflowDao().upsertPlannedWorkout(workout("workout-a"))
      val repository = WorkoutExecutionRepository(database, CLOCK)

      repository.assignManually(runSummary("provider-a"), "workout-a")
      database.runDao().updateAll(listOf(
        runEntity("provider-a").copy(isCanonical = false),
        runEntity("provider-b").copy(isCanonical = true),
      ))
      database.runDao().repointPhysicalRunReferences("physical-1", "provider-b")

      assertEquals(1, database.coachingWorkflowDao().getAllAssociations().size)
      assertEquals("provider-b", database.coachingWorkflowDao().getAllAssociations().single().actualRunId)
    } finally {
      database.close()
    }
  }

  @Test
  fun manualAssignmentRepairsWorkoutAssociatedWithAnotherRun() = runBlocking {
    val database = database()
    try {
      database.runDao().upsertAll(listOf(
        runEntity("old-provider", "physical-old"),
        runEntity("current-provider", "physical-current"),
      ))
      database.coachingWorkflowDao().upsertPlannedWorkout(
        workout("workout-a").copy(
          dateEpochDay = LocalDate.of(2026, 10, 1).toEpochDay(),
          lifecycleState = "COMPLETED",
        )
      )
      val repository = WorkoutExecutionRepository(database, CLOCK)
      repository.assignManually(runSummary("old-provider", "physical-old"), "workout-a")
      database.coachingWorkflowDao().upsertExecution(
        WorkoutExecutionEntity(
          plannedWorkoutId = "workout-a",
          actualRunId = "old-provider",
          physicalRunId = "physical-old",
          status = "COMPLETED_AS_PLANNED",
          comparisonBasis = "ORIGINAL_PRESCRIPTION",
          intendedDistanceMeters = null,
          intendedDurationSeconds = 1_800,
          actualDistanceMeters = 4_000.0,
          actualDurationSeconds = 1_800,
          qualityWorkCompletionFraction = null,
          explanation = "Stale fixture",
          evaluatedAtEpochMillis = START.toEpochMilli(),
        )
      )

      repository.assignManually(runSummary("current-provider", "physical-current"), "workout-a")

      val associations = database.coachingWorkflowDao().getAllAssociations()
      assertEquals(1, associations.size)
      assertEquals("physical-current", associations.single().physicalRunId)
      assertNull(database.coachingWorkflowDao().getExecutionForPhysicalRun("physical-old"))
      assertEquals("PLANNED", database.coachingWorkflowDao().getLifecycleState("workout-a"))
    } finally {
      database.close()
    }
  }

  private fun database() = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java,
  ).build()

  private fun runEntity(id: String, physicalRunId: String = "physical-1") = RunEntity(
    healthConnectRecordId = id,
    startTimeEpochMillis = START.toEpochMilli(),
    endTimeEpochMillis = START.plusSeconds(1_800).toEpochMilli(),
    durationSeconds = 1_800,
    distanceMeters = 4_000.0,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = 450.0,
    importedAtEpochMillis = START.toEpochMilli(),
    physicalRunId = physicalRunId,
  )

  private fun runSummary(id: String, physicalRunId: String = "physical-1") = RunSummary(
    healthConnectRecordId = id,
    startTime = START,
    endTime = START.plusSeconds(1_800),
    durationSeconds = 1_800,
    distanceMeters = 4_000.0,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = 450.0,
    physicalRunId = physicalRunId,
  )

  private fun workout(id: String) = PlannedWorkoutEntity(
    id = id,
    dateEpochDay = LocalDate.of(2026, 9, 29).toEpochDay(),
    type = "EASY_RUN",
    durationMinutes = 30,
    targetRpeMin = 2,
    targetRpeMax = 4,
    createdAtEpochMillis = START.minusSeconds(86_400).toEpochMilli(),
    name = "Easy Run",
    explanation = "Test workout",
  )

  private companion object {
    val START: Instant = Instant.parse("2026-09-29T09:00:00Z")
    val CLOCK: Clock = Clock.fixed(START.plusSeconds(2_000), ZoneOffset.UTC)
  }
}
