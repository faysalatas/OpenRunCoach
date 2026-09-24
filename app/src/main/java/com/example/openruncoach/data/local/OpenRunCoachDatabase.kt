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
    RunMetricsEntity::class,
    RunMetricValueEntity::class,
    RunMetricSampleEntity::class,
    AthletePerformanceProfileEntity::class,
    AthleteProfileWeeklyVolumeEntity::class,
    AthleteProfileBenchmarkEntity::class,
    AthleteProfileExplanationEntity::class,
    TrainingGoalEntity::class,
    TrainingGoalAssessmentReasonEntity::class,
    WorkoutBlockEntity::class,
    WorkoutSegmentEntity::class,
    WorkoutIntensityTargetEntity::class,
    WorkoutCueEntity::class,
  ],
  version = 12,
  exportSchema = true,
)
abstract class OpenRunCoachDatabase : RoomDatabase() {
  abstract fun runDao(): RunDao
  abstract fun runFeedbackDao(): RunFeedbackDao
  abstract fun coachingWorkflowDao(): CoachingWorkflowDao
  abstract fun runMetricsDao(): RunMetricsDao
  abstract fun athletePerformanceProfileDao(): AthletePerformanceProfileDao
  abstract fun trainingGoalDao(): TrainingGoalDao

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

    val MIGRATION_6_7 = object : Migration(6, 7) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `run_metrics` (`runId` TEXT NOT NULL, `startTimeEpochMillis` INTEGER NOT NULL, `endTimeEpochMillis` INTEGER NOT NULL, `generatedAtEpochMillis` INTEGER NOT NULL, `recordingSource` TEXT, `recordingDeviceManufacturer` TEXT, `recordingDeviceModel` TEXT, `recordingDeviceName` TEXT, PRIMARY KEY(`runId`), FOREIGN KEY(`runId`) REFERENCES `runs`(`healthConnectRecordId`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_run_metrics_runId` ON `run_metrics` (`runId`)")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `run_metric_values` (`runId` TEXT NOT NULL, `metric` TEXT NOT NULL, `value` REAL NOT NULL, `source` TEXT NOT NULL, `confidence` TEXT NOT NULL, `verification` TEXT NOT NULL, `secondaryValue` REAL, `secondarySource` TEXT, `verificationReason` TEXT, PRIMARY KEY(`runId`, `metric`), FOREIGN KEY(`runId`) REFERENCES `run_metrics`(`runId`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_run_metric_values_runId` ON `run_metric_values` (`runId`)")
      }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `athlete_performance_profile` (`id` TEXT NOT NULL, `version` INTEGER NOT NULL, `calculatedAtEpochMillis` INTEGER NOT NULL, `totalCanonicalRuns` INTEGER NOT NULL, `runsLast7Days` INTEGER NOT NULL, `runsLast28Days` INTEGER NOT NULL, `activeWeeksLast8Weeks` INTEGER NOT NULL, `distanceLast7DaysMeters` REAL NOT NULL, `durationLast7DaysSeconds` INTEGER NOT NULL, `distanceLast28DaysMeters` REAL NOT NULL, `durationLast28DaysSeconds` INTEGER NOT NULL, `averageWeeklyDistanceLast4WeeksMeters` REAL NOT NULL, `averageWeeklyDurationLast4WeeksSeconds` INTEGER NOT NULL, `longestRunId` TEXT, `longestRunDateEpochMillis` INTEGER, `longestRunDistanceMeters` REAL, `longestRunDurationSeconds` INTEGER, `averageRunsPerWeek` REAL NOT NULL, `currentRunningStreakWeeks` INTEGER NOT NULL, `consistency` TEXT NOT NULL, `recentPaceTrend` TEXT NOT NULL, `easyPaceRepresentative` REAL, `easyPaceMinimum` REAL, `easyPaceMaximum` REAL, `easyPaceRunCount` INTEGER NOT NULL, `easyPaceConfidence` TEXT, `easyHeartRateRepresentative` REAL, `easyHeartRateMinimum` REAL, `easyHeartRateMaximum` REAL, `easyHeartRateRunCount` INTEGER NOT NULL, `easyHeartRateConfidence` TEXT, `recentAverageHeartRate` REAL, `recentHeartRateTrend` TEXT NOT NULL, `vo2MaxLatest` REAL, `vo2MaxPrevious` REAL, `vo2MaxTrend` TEXT NOT NULL, `easyCadenceRepresentative` REAL, `easyCadenceMinimum` REAL, `easyCadenceMaximum` REAL, `easyCadenceRunCount` INTEGER NOT NULL, `easyCadenceConfidence` TEXT, `recentCadenceTrend` TEXT NOT NULL, `feedbackCount` INTEGER NOT NULL, `averageRpe` REAL, `completedFraction` REAL, `partialFraction` REAL, `abortedFraction` REAL, `painFrequency` REAL, `significantPainFrequency` REAL, `averageReadiness` REAL, `profileConfidence` TEXT NOT NULL, PRIMARY KEY(`id`))"""
        )
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `athlete_profile_weekly_volume` (`profileId` TEXT NOT NULL, `weekStartEpochDay` INTEGER NOT NULL, `runCount` INTEGER NOT NULL, `distanceMeters` REAL NOT NULL, `durationSeconds` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `weekStartEpochDay`))"""
        )
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `athlete_profile_benchmarks` (`profileId` TEXT NOT NULL, `targetDistance` TEXT NOT NULL, `resultType` TEXT NOT NULL, `actualDistanceMeters` REAL NOT NULL, `durationSeconds` INTEGER NOT NULL, `paceSecondsPerKm` REAL NOT NULL, `dateEpochMillis` INTEGER NOT NULL, `runId` TEXT NOT NULL, `confidence` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`profileId`, `targetDistance`, `resultType`))"""
        )
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `athlete_profile_explanations` (`profileId` TEXT NOT NULL, `category` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`profileId`, `category`, `position`))"""
        )
      }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `training_goals` (`id` TEXT NOT NULL, `distance` TEXT NOT NULL, `customDistanceMeters` REAL, `goalType` TEXT NOT NULL, `targetTimeSeconds` INTEGER, `targetDateEpochDay` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `status` TEXT NOT NULL, `confidenceAtCreation` TEXT NOT NULL, `startingBestDurationSeconds` INTEGER, `startingBestRunId` TEXT, `startingBestObservedAtEpochMillis` INTEGER, `startingRecentDurationSeconds` INTEGER, `startingRecentRunId` TEXT, `startingRecentObservedAtEpochMillis` INTEGER, `startingAverageWeeklyDistanceMeters` REAL NOT NULL, `startingAverageWeeklyDurationSeconds` INTEGER NOT NULL, `startingLongestRunDistanceMeters` REAL, `startingLongestRunDurationSeconds` INTEGER, `startingAverageRunsPerWeek` REAL NOT NULL, `startingTypicalEasyPaceSecondsPerKm` REAL, `startingTypicalEasyHeartRate` REAL, `startingProfileConfidence` TEXT NOT NULL, `snapshotCapturedAtEpochMillis` INTEGER NOT NULL, `assessmentLevel` TEXT NOT NULL, PRIMARY KEY(`id`))"""
        )
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `training_goal_assessment_reasons` (`goalId` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`goalId`, `position`), FOREIGN KEY(`goalId`) REFERENCES `training_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_training_goal_assessment_reasons_goalId` ON `training_goal_assessment_reasons` (`goalId`)")
      }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `name` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `purpose` TEXT NOT NULL DEFAULT 'AEROBIC_BASE'")
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `explanation` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `goalId` TEXT")
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'MANUAL'")
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `isPreview` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `canChangeType` INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `canChangeTotalDuration` INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `canChangeSegmentCompletion` INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `canChangeRepetitions` INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `canChangeIntensity` INTEGER NOT NULL DEFAULT 1")
        connection.execSQL("ALTER TABLE `planned_workouts` ADD COLUMN `canChangeRecovery` INTEGER NOT NULL DEFAULT 1")
        connection.execSQL(
          """UPDATE `planned_workouts` SET `name` = CASE `type` WHEN 'EASY_RUN' THEN 'Easy Run' WHEN 'RECOVERY_RUN' THEN 'Recovery Run' WHEN 'LONG_RUN' THEN 'Long Run' WHEN 'TEMPO_RUN' THEN 'Tempo Run' WHEN 'INTERVALS' THEN 'Intervals' WHEN 'REST' THEN 'Rest' ELSE `type` END"""
        )
        connection.execSQL(
          """UPDATE `planned_workouts` SET `purpose` = CASE `type` WHEN 'RECOVERY_RUN' THEN 'RECOVERY' WHEN 'REST' THEN 'RECOVERY' WHEN 'LONG_RUN' THEN 'ENDURANCE' WHEN 'TEMPO_RUN' THEN 'LACTATE_THRESHOLD' WHEN 'INTERVALS' THEN 'VO2MAX' ELSE 'AEROBIC_BASE' END"""
        )
        connection.execSQL("UPDATE `planned_workouts` SET `explanation` = 'Migrated from the previous planned workout format.'")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `workout_blocks` (`id` TEXT NOT NULL, `workoutId` TEXT NOT NULL, `blockOrder` INTEGER NOT NULL, `blockType` TEXT NOT NULL, `repetitions` INTEGER NOT NULL, `label` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`workoutId`) REFERENCES `planned_workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_blocks_workoutId` ON `workout_blocks` (`workoutId`)")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `workout_segments` (`id` TEXT NOT NULL, `blockId` TEXT NOT NULL, `segmentOrder` INTEGER NOT NULL, `category` TEXT NOT NULL, `name` TEXT NOT NULL, `completionType` TEXT NOT NULL, `durationSeconds` INTEGER, `distanceMeters` REAL, `completionRepetitions` INTEGER, `notes` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`blockId`) REFERENCES `workout_blocks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_segments_blockId` ON `workout_segments` (`blockId`)")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `workout_intensity_targets` (`segmentId` TEXT NOT NULL, `position` INTEGER NOT NULL, `targetType` TEXT NOT NULL, `minimumValue` REAL, `maximumValue` REAL, `heartRateZone` TEXT, `confidence` TEXT NOT NULL, PRIMARY KEY(`segmentId`, `position`), FOREIGN KEY(`segmentId`) REFERENCES `workout_segments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_intensity_targets_segmentId` ON `workout_intensity_targets` (`segmentId`)")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `workout_cues` (`workoutId` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`workoutId`, `position`), FOREIGN KEY(`workoutId`) REFERENCES `planned_workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_cues_workoutId` ON `workout_cues` (`workoutId`)")
        connection.execSQL(
          """INSERT INTO `workout_blocks` (`id`, `workoutId`, `blockOrder`, `blockType`, `repetitions`, `label`) SELECT `id` || ':block:0', `id`, 0, 'SINGLE', 1, NULL FROM `planned_workouts` WHERE `type` != 'REST'"""
        )
        connection.execSQL(
          """INSERT INTO `workout_segments` (`id`, `blockId`, `segmentOrder`, `category`, `name`, `completionType`, `durationSeconds`, `distanceMeters`, `completionRepetitions`, `notes`) SELECT `id` || ':segment:0', `id` || ':block:0', 0, CASE WHEN `type` = 'RECOVERY_RUN' THEN 'RECOVERY' ELSE 'EASY' END, `name`, 'DURATION', `durationMinutes` * 60, NULL, NULL, NULL FROM `planned_workouts` WHERE `type` != 'REST'"""
        )
        connection.execSQL(
          """INSERT INTO `workout_intensity_targets` (`segmentId`, `position`, `targetType`, `minimumValue`, `maximumValue`, `heartRateZone`, `confidence`) SELECT `id` || ':segment:0', 0, CASE WHEN `targetRpeMin` > 0 THEN 'RPE' ELSE 'NONE' END, CASE WHEN `targetRpeMin` > 0 THEN `targetRpeMin` ELSE NULL END, CASE WHEN `targetRpeMin` > 0 THEN `targetRpeMax` ELSE NULL END, NULL, 'HIGH' FROM `planned_workouts` WHERE `type` != 'REST'"""
        )
      }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `run_metric_values` ADD COLUMN `sourceAppName` TEXT")
        connection.execSQL("ALTER TABLE `run_metric_values` ADD COLUMN `sourceDeviceName` TEXT")
      }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `run_metrics` ADD COLUMN `dataVersion` INTEGER NOT NULL DEFAULT 1")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `run_metric_samples` (`runId` TEXT NOT NULL, `metricType` TEXT NOT NULL, `timestampEpochMillis` INTEGER NOT NULL, `value` REAL NOT NULL, `source` TEXT NOT NULL, `confidence` TEXT NOT NULL, `sourceAppName` TEXT, `sourceDeviceName` TEXT, PRIMARY KEY(`runId`, `metricType`, `timestampEpochMillis`, `source`), FOREIGN KEY(`runId`) REFERENCES `run_metrics`(`runId`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_run_metric_samples_runId` ON `run_metric_samples` (`runId`)")
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
              MIGRATION_6_7,
              MIGRATION_7_8,
              MIGRATION_8_9,
              MIGRATION_9_10,
              MIGRATION_10_11,
              MIGRATION_11_12,
            ).build().also { instance = it }
      }
  }
}
