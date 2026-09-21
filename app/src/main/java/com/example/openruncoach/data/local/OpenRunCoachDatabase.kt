package com.example.openruncoach.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
  entities = [
    RunEntity::class,
    RunFeedbackEntity::class,
    PlannedWorkoutEntity::class,
    PreRunCheckInEntity::class,
    WorkoutAdjustmentEntity::class,
  ],
  version = 6,
  exportSchema = true,
)
abstract class OpenRunCoachDatabase : RoomDatabase() {
  abstract fun runDao(): RunDao
  abstract fun runFeedbackDao(): RunFeedbackDao
  abstract fun coachingWorkflowDao(): CoachingWorkflowDao

  companion object {
    @Volatile private var instance: OpenRunCoachDatabase? = null

    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `run_feedback` (`id` TEXT NOT NULL, `runId` TEXT NOT NULL, `rpe` INTEGER NOT NULL, `cardioEffort` TEXT NOT NULL, `muscleFatigue` TEXT NOT NULL, `painLevel` TEXT NOT NULL, `completionStatus` TEXT NOT NULL, `readiness` INTEGER, `notes` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`runId`) REFERENCES `runs`(`healthConnectRecordId`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_run_feedback_runId` ON `run_feedback` (`runId`)")
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `sourcePackageName` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `sourceAppName` TEXT")
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `sourceDeviceManufacturer` TEXT")
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `sourceDeviceModel` TEXT")
      }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `sourceDeviceType` TEXT")
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `sourceRecordLastModifiedTimeEpochMillis` INTEGER")
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `duplicateGroupId` TEXT")
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `isCanonical` INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `canonicalSelectionReason` TEXT")
      }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `planned_workouts` (`id` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `type` TEXT NOT NULL, `durationMinutes` INTEGER NOT NULL, `targetRpeMin` INTEGER NOT NULL, `targetRpeMax` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
        )
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `pre_run_check_ins` (`plannedWorkoutId` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `readiness` INTEGER NOT NULL, `energy` INTEGER NOT NULL, `sleepQuality` INTEGER NOT NULL, `soreness` TEXT NOT NULL, `sorenessAreas` TEXT NOT NULL, `pain` TEXT NOT NULL, `stress` INTEGER NOT NULL, `motivation` INTEGER NOT NULL, `illness` TEXT NOT NULL, `notes` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`plannedWorkoutId`), FOREIGN KEY(`plannedWorkoutId`) REFERENCES `planned_workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `workout_adjustments` (`plannedWorkoutId` TEXT NOT NULL, `adjustedType` TEXT NOT NULL, `adjustedDurationMinutes` INTEGER NOT NULL, `adjustedTargetRpeMin` INTEGER NOT NULL, `adjustedTargetRpeMax` INTEGER NOT NULL, `readinessStatus` TEXT NOT NULL, `reasons` TEXT NOT NULL, `adjustments` TEXT NOT NULL, `generatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`plannedWorkoutId`), FOREIGN KEY(`plannedWorkoutId`) REFERENCES `planned_workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
      }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          "ALTER TABLE `planned_workouts` ADD COLUMN `lifecycleState` TEXT NOT NULL DEFAULT 'PLANNED'"
        )
        connection.execSQL(
          """UPDATE `planned_workouts` SET `lifecycleState` = CASE WHEN EXISTS (SELECT 1 FROM `workout_adjustments` WHERE `workout_adjustments`.`plannedWorkoutId` = `planned_workouts`.`id`) THEN 'READY' ELSE 'CHECK_IN_REQUIRED' END"""
        )
      }
    }

    fun getInstance(context: Context): OpenRunCoachDatabase =
      instance ?: synchronized(this) {
        instance
          ?: Room.databaseBuilder(
              context.applicationContext,
              OpenRunCoachDatabase::class.java,
              "open_run_coach.db",
            ).addMigrations(
              MIGRATION_1_2,
              MIGRATION_2_3,
              MIGRATION_3_4,
              MIGRATION_4_5,
              MIGRATION_5_6,
            ).build().also { instance = it }
      }
  }
}
