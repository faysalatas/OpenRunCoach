package com.example.openruncoach.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutExecutionMigrationTest {
  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    OpenRunCoachDatabase::class.java,
  )

  @Test fun migrationAddsEmptyExecutionStorageWithoutChangingExistingWorkoutsOrRuns() {
    helper.createDatabase(TEST_DATABASE, 15).apply {
      execSQL(
        """INSERT INTO runs (
          healthConnectRecordId, startTimeEpochMillis, endTimeEpochMillis,
          durationSeconds, distanceMeters, importedAtEpochMillis, sourcePackageName
        ) VALUES ('run', 1000, 2800, 1800, 3780.0, 3000, 'test.provider')"""
      )
      execSQL(
        """INSERT INTO planned_workouts (
          id, dateEpochDay, type, durationMinutes, targetRpeMin, targetRpeMax,
          createdAtEpochMillis, name, purpose, explanation
        ) VALUES (
          'planned', 20722, 'EASY_RUN', 30, 3, 4, 500,
          'Easy Run', 'AEROBIC_BASE', 'Test workout'
        )"""
      )
      close()
    }

    helper.runMigrationsAndValidate(
      TEST_DATABASE,
      16,
      true,
      OpenRunCoachDatabase.MIGRATION_15_16,
    ).apply {
      query("SELECT COUNT(*) FROM runs WHERE healthConnectRecordId = 'run'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(0))
      }
      query("SELECT COUNT(*) FROM planned_workouts WHERE id = 'planned'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(0))
      }
      query("SELECT COUNT(*) FROM workout_executions").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(0, cursor.getInt(0))
      }
      close()
    }
  }

  private companion object {
    const val TEST_DATABASE = "workout-execution-migration"
  }
}
