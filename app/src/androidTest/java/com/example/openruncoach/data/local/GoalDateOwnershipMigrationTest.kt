package com.example.openruncoach.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalDateOwnershipMigrationTest {
  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    OpenRunCoachDatabase::class.java,
  )

  @Test
  fun legacyCompleteDistanceGoalDateMovesToActivePlanAndLeavesGoalDateFree() {
    val oldDate = LocalDate.parse("2026-10-18").toEpochDay()
    helper.createDatabase(TEST_DATABASE, 14).apply {
      execSQL(
        """INSERT INTO training_goals (
          id, distance, customDistanceMeters, goalType, targetTimeSeconds, targetDateEpochDay,
          createdAtEpochMillis, status, confidenceAtCreation,
          startingBestDurationSeconds, startingBestRunId, startingBestObservedAtEpochMillis,
          startingRecentDurationSeconds, startingRecentRunId, startingRecentObservedAtEpochMillis,
          startingAverageWeeklyDistanceMeters, startingAverageWeeklyDurationSeconds,
          startingLongestRunDistanceMeters, startingLongestRunDurationSeconds, startingAverageRunsPerWeek,
          startingTypicalEasyPaceSecondsPerKm, startingTypicalEasyHeartRate,
          startingProfileConfidence, snapshotCapturedAtEpochMillis, assessmentLevel
        ) VALUES (
          'goal', 'FIVE_K', NULL, 'COMPLETE_DISTANCE', NULL, $oldDate,
          1, 'ACTIVE', 'LOW', NULL, NULL, NULL, NULL, NULL, NULL,
          10000.0, 4200, 4800.0, 2100, 3.0, 430.0, 145.0, 'LOW', 1, 'PLAUSIBLE'
        )"""
      )
      execSQL(
        """INSERT INTO training_plans (
          id, goalId, createdAtEpochMillis, startDateEpochDay, endDateEpochDay,
          generationVersion, explanation, confidence, status
        ) VALUES ('plan', 'goal', 2, 1, 28, 4, 'Fixture plan', 'LOW', 'ACTIVE')"""
      )
      execSQL(
        """INSERT INTO goal_completion_milestones (
          goalId, readiness, scheduledDateEpochDay, promptDismissed, updatedAtEpochMillis
        ) VALUES ('goal', 'GOAL_COMPLETION_READY', NULL, 0, 3)"""
      )
      close()
    }

    helper.runMigrationsAndValidate(
      TEST_DATABASE,
      15,
      true,
      OpenRunCoachDatabase.MIGRATION_14_15,
    ).apply {
      query("PRAGMA table_info(training_goals)").use { cursor ->
        val nameColumn = cursor.getColumnIndexOrThrow("name")
        var hasLegacyDate = false
        while (cursor.moveToNext()) {
          if (cursor.getString(nameColumn) == "targetDateEpochDay") hasLegacyDate = true
        }
        assertFalse(hasLegacyDate)
      }
      query(
        """SELECT goalId, plannedCompletionDateEpochDay, completionReadiness
          FROM training_plans WHERE id = 'plan'"""
      ).use { cursor ->
        cursor.moveToFirst()
        assertEquals("goal", cursor.getString(0))
        assertEquals(oldDate, cursor.getLong(1))
        assertEquals("GOAL_COMPLETION_READY", cursor.getString(2))
      }
      close()
    }
  }

  private companion object {
    const val TEST_DATABASE = "goal-date-ownership-migration"
  }
}
