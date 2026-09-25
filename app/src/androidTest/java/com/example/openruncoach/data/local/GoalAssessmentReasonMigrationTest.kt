package com.example.openruncoach.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalAssessmentReasonMigrationTest {
  @get:Rule
  val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    OpenRunCoachDatabase::class.java,
  )

  @Test
  fun migrationReindexesReasonsPerGoalWithoutLosingDistinctData() {
    helper.createDatabase(TEST_DATABASE, 14).apply {
      insertGoal("goal-a", targetDateEpochDay = 21_000)
      insertGoal("goal-b")
      insertGoal("goal-empty")
      insertGoal("goal-one")
      insertGoal("goal-duplicates")

      insertReason("goal-a", 8, "Build weekly consistency")
      insertReason("goal-a", 2, "Current endurance supports the goal")
      insertReason("goal-a", 5, "Recent training is steady")

      insertReason("goal-b", 40, "Fourth reason")
      insertReason("goal-b", 10, "First reason")
      insertReason("goal-b", 30, "Third reason")
      insertReason("goal-b", 20, "Second reason")

      insertReason("goal-one", 99, "Only reason")

      // Version 14 permits the same reason text at different positions. Keep its earliest
      // occurrence, preserve every distinct text, and compact the resulting positions.
      insertReason("goal-duplicates", 7, "Repeated reason")
      insertReason("goal-duplicates", 3, "Distinct reason")
      insertReason("goal-duplicates", 12, "Repeated reason")

      execSQL(
        """INSERT INTO training_plans (
          id, goalId, createdAtEpochMillis, startDateEpochDay, endDateEpochDay,
          generationVersion, explanation, confidence, status
        ) VALUES ('plan-a', 'goal-a', 200, 20900, 21000, 4, 'Existing plan', 'LOW', 'ACTIVE')"""
      )
      execSQL(
        """INSERT INTO goal_completion_milestones (
          goalId, readiness, scheduledDateEpochDay, promptDismissed, updatedAtEpochMillis
        ) VALUES ('goal-a', 'GOAL_COMPLETION_READY', NULL, 1, 300)"""
      )
      execSQL(
        """INSERT INTO training_weeks (
          planId, weekIndex, startDateEpochDay, targetDistanceMeters,
          targetDurationSeconds, loadClassification, explanation
        ) VALUES ('plan-a', 0, 20900, 12000.0, 5400, 'FOUNDATION', 'Existing week')"""
      )
      execSQL(
        """INSERT INTO training_week_reasons (planId, weekIndex, position, text)
          VALUES ('plan-a', 0, 0, 'Existing week reason')"""
      )
      execSQL(
        """INSERT INTO training_blocks (
          planId, blockIndex, type, startWeek, endWeek, purpose, progressionIntent
        ) VALUES ('plan-a', 0, 'FOUNDATION', 0, 0, 'Build consistency', 'Maintain')"""
      )
      execSQL(
        """INSERT INTO planned_workouts (
          id, dateEpochDay, type, durationMinutes, targetRpeMin, targetRpeMax,
          createdAtEpochMillis, name, purpose, explanation, goalId
        ) VALUES (
          'workout-a', 20901, 'EASY_RUN', 30, 3, 4, 200,
          'Existing workout', 'GOAL_COMPLETION', 'Existing workout explanation', 'goal-a'
        )"""
      )
      execSQL(
        """INSERT INTO training_plan_workouts (
          planId, weekIndex, workoutOrder, workoutId, slot, generationReason
        ) VALUES ('plan-a', 0, 0, 'workout-a', 'WEEKDAY', 'Existing assignment')"""
      )
      execSQL(
        """INSERT INTO runs (
          healthConnectRecordId, startTimeEpochMillis, endTimeEpochMillis,
          durationSeconds, distanceMeters, importedAtEpochMillis, sourcePackageName
        ) VALUES ('unrelated-run', 1000, 2000, 1000, 2500.0, 3000, 'test.provider')"""
      )
      close()
    }

    helper.runMigrationsAndValidate(
      TEST_DATABASE,
      15,
      true,
      OpenRunCoachDatabase.MIGRATION_14_15,
    ).apply {
      assertReasons(
        "goal-a",
        listOf(
          0 to "Current endurance supports the goal",
          1 to "Recent training is steady",
          2 to "Build weekly consistency",
        ),
      )
      assertReasons(
        "goal-b",
        listOf(
          0 to "First reason",
          1 to "Second reason",
          2 to "Third reason",
          3 to "Fourth reason",
        ),
      )
      assertReasons("goal-empty", emptyList())
      assertReasons("goal-one", listOf(0 to "Only reason"))
      assertReasons(
        "goal-duplicates",
        listOf(0 to "Distinct reason", 1 to "Repeated reason"),
      )

      query(
        """SELECT goalId, position, COUNT(*)
          FROM training_goal_assessment_reasons
          GROUP BY goalId, position
          HAVING COUNT(*) > 1"""
      ).use { cursor -> assertFalse(cursor.moveToFirst()) }
      query("SELECT COUNT(*) FROM training_goals").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(5, cursor.getInt(0))
      }
      query(
        """SELECT plannedCompletionDateEpochDay, plannedCompletionRunId, completionReadiness,
          completionPromptDismissed, completionUpdatedAtEpochMillis
          FROM training_plans WHERE id = 'plan-a'"""
      ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(21_000, cursor.getLong(0))
        assertEquals("workout-a", cursor.getString(1))
        assertEquals("GOAL_COMPLETION_SCHEDULED", cursor.getString(2))
        assertEquals(1, cursor.getInt(3))
        assertEquals(300, cursor.getLong(4))
      }
      assertRowCount("training_weeks", 1)
      assertRowCount("training_week_reasons", 1)
      assertRowCount("training_blocks", 1)
      assertRowCount("training_plan_workouts", 1)
      query("SELECT sourcePackageName FROM runs WHERE healthConnectRecordId = 'unrelated-run'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("test.provider", cursor.getString(0))
      }
      close()
    }
  }

  private fun SupportSQLiteDatabase.insertGoal(
    id: String,
    targetDateEpochDay: Long? = null,
  ) {
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
      ) VALUES (?, 'FIVE_K', NULL, 'COMPLETE_DISTANCE', NULL, ?,
        100, 'ACTIVE', 'LOW', NULL, NULL, NULL, NULL, NULL, NULL,
        10000.0, 4200, 4800.0, 2100, 3.0, 430.0, 145.0, 'LOW', 100, 'PLAUSIBLE')""",
      arrayOf<Any?>(id, targetDateEpochDay),
    )
  }

  private fun SupportSQLiteDatabase.insertReason(goalId: String, position: Int, text: String) {
    execSQL(
      "INSERT INTO training_goal_assessment_reasons (goalId, position, text) VALUES (?, ?, ?)",
      arrayOf<Any?>(goalId, position, text),
    )
  }

  private fun SupportSQLiteDatabase.assertReasons(
    goalId: String,
    expected: List<Pair<Int, String>>,
  ) {
    query(
      "SELECT position, text FROM training_goal_assessment_reasons WHERE goalId = ? ORDER BY position",
      arrayOf(goalId),
    ).use { cursor ->
      val actual = buildList {
        while (cursor.moveToNext()) add(cursor.getInt(0) to cursor.getString(1))
      }
      assertEquals(expected, actual)
    }
  }

  private fun SupportSQLiteDatabase.assertRowCount(table: String, expected: Int) {
    query("SELECT COUNT(*) FROM `$table`").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(expected, cursor.getInt(0))
    }
  }

  private companion object {
    const val TEST_DATABASE = "goal-assessment-reason-migration"
  }
}
