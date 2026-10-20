package com.example.openruncoach.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingAvailabilityMigrationTest {
  @get:Rule val helper = MigrationTestHelper(
    instrumentation = InstrumentationRegistry.getInstrumentation(), databaseClass = OpenRunCoachDatabase::class.java,
  )

  @Test fun upgradeAddsNoAssumedDaysAndPreservesActualRuns() {
    helper.createDatabase(DATABASE, 18).use { database ->
      database.execSQL("""INSERT INTO runs (healthConnectRecordId, startTimeEpochMillis, endTimeEpochMillis,
        durationSeconds, distanceMeters, importedAtEpochMillis, physicalRunId)
        VALUES ('existing-run', 0, 1800000, 1800, 4000.0, 0, 'physical-existing')""")
    }
    helper.runMigrationsAndValidate(DATABASE, 19, true, OpenRunCoachDatabase.MIGRATION_18_19).use { database ->
      database.query("SELECT COUNT(*) FROM training_availability").use {
        check(it.moveToFirst() && it.getInt(0) == 0)
      }
      database.query("SELECT durationSeconds, distanceMeters, physicalRunId FROM runs WHERE healthConnectRecordId = 'existing-run'").use {
        check(it.moveToFirst())
        check(it.getLong(0) == 1800L && it.getDouble(1) == 4000.0 && it.getString(2) == "physical-existing")
      }
    }
  }

  private companion object { const val DATABASE = "training-availability-migration" }
}
