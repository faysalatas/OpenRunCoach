package com.example.openruncoach.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunRouteMigrationTest {
  @get:Rule
  val helper = MigrationTestHelper(
    instrumentation = InstrumentationRegistry.getInstrumentation(),
    databaseClass = OpenRunCoachDatabase::class.java,
  )

  @Test
  fun migrationCreatesNormalizedRouteAndSplitTables() {
    helper.createDatabase(TEST_DATABASE, 16).use { database ->
      database.execSQL(
        """INSERT INTO `runs` (`healthConnectRecordId`, `startTimeEpochMillis`, `endTimeEpochMillis`,
          `durationSeconds`, `distanceMeters`, `averageHeartRate`, `maxHeartRate`,
          `averagePaceSecondsPerKm`, `importedAtEpochMillis`, `sourcePackageName`, `sourceAppName`,
          `sourceDeviceManufacturer`, `sourceDeviceModel`, `sourceDeviceType`,
          `sourceRecordLastModifiedTimeEpochMillis`, `duplicateGroupId`, `isCanonical`,
          `canonicalSelectionReason`) VALUES ('run', 0, 1000, 1, 10.0, NULL, NULL, 100.0, 0,
          '', NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL)"""
      )
    }

    helper.runMigrationsAndValidate(
      TEST_DATABASE,
      17,
      true,
      OpenRunCoachDatabase.MIGRATION_16_17,
    ).use { database ->
      database.execSQL("PRAGMA foreign_keys=ON")
      database.execSQL(
        """INSERT INTO `run_routes` VALUES ('run', 'SAMSUNG_HEALTH', 'Samsung Health', 'HIGH',
          0, 10.0, 10.0, 0.0, 1.0, 0)"""
      )
      database.execSQL(
        """INSERT INTO `run_route_points` VALUES ('run', 0, 0, 52.0, 13.0, NULL, NULL, NULL)"""
      )
      database.execSQL(
        """INSERT INTO `run_route_points` VALUES ('run', 1, 0, 52.0, 13.0001, NULL, NULL, NULL)"""
      )
      database.execSQL(
        """INSERT INTO `run_splits` VALUES ('run', 1, 'ROUTE_DERIVED', 0, 1000, 10.0,
          1.0, 100.0, NULL, NULL, 1, 'HIGH')"""
      )
      database.execSQL("DELETE FROM `runs` WHERE `healthConnectRecordId` = 'run'")
      database.query("SELECT COUNT(*) FROM `run_route_points`").use { cursor ->
        cursor.moveToFirst()
        check(cursor.getInt(0) == 0)
      }
      database.query("SELECT COUNT(*) FROM `run_splits`").use { cursor ->
        cursor.moveToFirst()
        check(cursor.getInt(0) == 0)
      }
    }
  }

  private companion object {
    const val TEST_DATABASE = "run-route-migration"
  }
}
