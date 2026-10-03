package com.example.openruncoach.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalRunIdentityMigrationTest {
  @get:Rule
  val helper = MigrationTestHelper(
    instrumentation = InstrumentationRegistry.getInstrumentation(),
    databaseClass = OpenRunCoachDatabase::class.java,
  )

  @Test
  fun migrationPreservesRunAndExecutionWhileAddingStableIdentityAndAssociation() {
    helper.createDatabase(DATABASE, 17).use { database ->
      database.execSQL(
        """INSERT INTO `runs` (`healthConnectRecordId`, `startTimeEpochMillis`, `endTimeEpochMillis`,
          `durationSeconds`, `distanceMeters`, `averageHeartRate`, `maxHeartRate`,
          `averagePaceSecondsPerKm`, `importedAtEpochMillis`, `sourcePackageName`, `sourceAppName`,
          `sourceDeviceManufacturer`, `sourceDeviceModel`, `sourceDeviceType`,
          `sourceRecordLastModifiedTimeEpochMillis`, `duplicateGroupId`, `isCanonical`,
          `canonicalSelectionReason`) VALUES ('run', 0, 1800000, 1800, 4000.0, NULL, NULL,
          450.0, 0, 'provider', 'Provider', NULL, NULL, NULL, NULL, 'physical-existing', 1, NULL)"""
      )
      database.execSQL(
        """INSERT INTO `planned_workouts` (`id`, `dateEpochDay`, `type`, `durationMinutes`,
          `targetRpeMin`, `targetRpeMax`, `createdAtEpochMillis`, `lifecycleState`, `name`,
          `purpose`, `explanation`, `goalId`, `origin`, `isPreview`, `canChangeType`,
          `canChangeTotalDuration`, `canChangeSegmentCompletion`, `canChangeRepetitions`,
          `canChangeIntensity`, `canChangeRecovery`) VALUES ('workout', 0, 'EASY_RUN', 30,
          2, 4, 0, 'COMPLETED', 'Easy Run', 'AEROBIC_BASE', 'Legacy', NULL, 'MANUAL',
          0, 1, 1, 1, 1, 1, 1)"""
      )
      database.execSQL(
        """INSERT INTO `workout_executions` (`plannedWorkoutId`, `actualRunId`, `status`,
          `comparisonBasis`, `intendedDistanceMeters`, `intendedDurationSeconds`,
          `actualDistanceMeters`, `actualDurationSeconds`, `qualityWorkCompletionFraction`,
          `explanation`, `evaluatedAtEpochMillis`) VALUES ('workout', 'run',
          'COMPLETED_AS_PLANNED', 'ORIGINAL_PRESCRIPTION', 4000.0, NULL, 4000.0, 1800,
          NULL, 'Legacy execution', 1800000)"""
      )
    }

    helper.runMigrationsAndValidate(DATABASE, 18, true, OpenRunCoachDatabase.MIGRATION_17_18).use { database ->
      database.query("SELECT physicalRunId, relevance, canonicalMatchDecision FROM runs WHERE healthConnectRecordId = 'run'").use {
        check(it.moveToFirst())
        check(it.getString(0) == "physical-existing")
        check(it.getString(1) == "ACTIVE")
        check(it.getString(2) == "SEPARATE")
      }
      database.query("SELECT plannedWorkoutId, physicalRunId, actualRunId, source FROM workout_run_associations").use {
        check(it.moveToFirst())
        check(it.getString(0) == "workout")
        check(it.getString(1) == "physical-existing")
        check(it.getString(2) == "run")
        check(it.getString(3) == "MIGRATED")
      }
    }
  }

  private companion object { const val DATABASE = "canonical-run-identity-migration" }
}
