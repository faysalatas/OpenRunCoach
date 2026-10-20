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
    TrainingPlanEntity::class,
    TrainingWeekEntity::class,
    TrainingWeekReasonEntity::class,
    TrainingBlockEntity::class,
    TrainingPlanWorkoutEntity::class,
    WorkoutExecutionEntity::class,
    WorkoutRunAssociationEntity::class,
    RunRouteEntity::class,
    RunRoutePointEntity::class,
    RunSplitEntity::class,
    TrainingAvailabilityEntity::class,
  ],
  version = 19,
  exportSchema = true,
)
abstract class OpenRunCoachDatabase : RoomDatabase() {
  abstract fun runDao(): RunDao
  abstract fun runFeedbackDao(): RunFeedbackDao
  abstract fun coachingWorkflowDao(): CoachingWorkflowDao
  abstract fun runMetricsDao(): RunMetricsDao
  abstract fun runRouteDao(): RunRouteDao
  abstract fun athletePerformanceProfileDao(): AthletePerformanceProfileDao
  abstract fun trainingGoalDao(): TrainingGoalDao
  abstract fun trainingPlanDao(): TrainingPlanDao
  abstract fun trainingAvailabilityDao(): TrainingAvailabilityDao

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

    val MIGRATION_12_13 = object : Migration(12, 13) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `training_plans` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `startDateEpochDay` INTEGER NOT NULL, `endDateEpochDay` INTEGER, `generationVersion` INTEGER NOT NULL, `explanation` TEXT NOT NULL, `confidence` TEXT NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`goalId`) REFERENCES `training_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_training_plans_goalId` ON `training_plans` (`goalId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_training_plans_status` ON `training_plans` (`status`)")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `training_weeks` (`planId` TEXT NOT NULL, `weekIndex` INTEGER NOT NULL, `startDateEpochDay` INTEGER NOT NULL, `targetDistanceMeters` REAL, `targetDurationSeconds` INTEGER, `loadClassification` TEXT NOT NULL, `explanation` TEXT NOT NULL, PRIMARY KEY(`planId`, `weekIndex`), FOREIGN KEY(`planId`) REFERENCES `training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_training_weeks_planId` ON `training_weeks` (`planId`)")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `training_week_reasons` (`planId` TEXT NOT NULL, `weekIndex` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`planId`, `weekIndex`, `position`), FOREIGN KEY(`planId`, `weekIndex`) REFERENCES `training_weeks`(`planId`, `weekIndex`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_training_week_reasons_planId_weekIndex` ON `training_week_reasons` (`planId`, `weekIndex`)")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `training_blocks` (`planId` TEXT NOT NULL, `blockIndex` INTEGER NOT NULL, `type` TEXT NOT NULL, `startWeek` INTEGER NOT NULL, `endWeek` INTEGER NOT NULL, `purpose` TEXT NOT NULL, `progressionIntent` TEXT NOT NULL, PRIMARY KEY(`planId`, `blockIndex`), FOREIGN KEY(`planId`) REFERENCES `training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_training_blocks_planId` ON `training_blocks` (`planId`)")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `training_plan_workouts` (`planId` TEXT NOT NULL, `weekIndex` INTEGER NOT NULL, `workoutOrder` INTEGER NOT NULL, `workoutId` TEXT NOT NULL, `slot` TEXT NOT NULL, `generationReason` TEXT NOT NULL, PRIMARY KEY(`planId`, `weekIndex`, `workoutOrder`), FOREIGN KEY(`planId`, `weekIndex`) REFERENCES `training_weeks`(`planId`, `weekIndex`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`workoutId`) REFERENCES `planned_workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_training_plan_workouts_planId_weekIndex` ON `training_plan_workouts` (`planId`, `weekIndex`)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_training_plan_workouts_workoutId` ON `training_plan_workouts` (`workoutId`)")
      }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `goal_completion_milestones` (`goalId` TEXT NOT NULL, `readiness` TEXT NOT NULL, `scheduledDateEpochDay` INTEGER, `promptDismissed` INTEGER NOT NULL DEFAULT 0, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`goalId`), FOREIGN KEY(`goalId`) REFERENCES `training_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_completion_milestones_goalId` ON `goal_completion_milestones` (`goalId`)")
        connection.execSQL(
          """UPDATE `training_plans` SET `status` = 'SUPERSEDED' WHERE `id` IN (
            SELECT DISTINCT `training_plan_workouts`.`planId` FROM `training_plan_workouts`
            INNER JOIN `planned_workouts` ON `planned_workouts`.`id` = `training_plan_workouts`.`workoutId`
            WHERE `planned_workouts`.`purpose` = 'GOAL_COMPLETION'
          )"""
        )
      }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TEMP TABLE `migration15_goal_reasons` AS SELECT * FROM `training_goal_assessment_reasons`")
        connection.execSQL("CREATE TEMP TABLE `migration15_weeks` AS SELECT * FROM `training_weeks`")
        connection.execSQL("CREATE TEMP TABLE `migration15_week_reasons` AS SELECT * FROM `training_week_reasons`")
        connection.execSQL("CREATE TEMP TABLE `migration15_blocks` AS SELECT * FROM `training_blocks`")
        connection.execSQL("CREATE TEMP TABLE `migration15_plan_workouts` AS SELECT * FROM `training_plan_workouts`")
        connection.execSQL(
          """CREATE TEMP TABLE `migration15_plans` AS
            SELECT p.*,
              CASE WHEN p.status = 'ACTIVE' AND g.goalType = 'COMPLETE_DISTANCE'
                THEN COALESCE(m.scheduledDateEpochDay, g.targetDateEpochDay) ELSE NULL END AS migratedCompletionDate,
              CASE WHEN p.status = 'ACTIVE' AND g.goalType = 'COMPLETE_DISTANCE' THEN (
                SELECT pw.id FROM training_plan_workouts tpw
                INNER JOIN planned_workouts pw ON pw.id = tpw.workoutId
                WHERE tpw.planId = p.id AND pw.purpose = 'GOAL_COMPLETION' LIMIT 1
              ) ELSE NULL END AS migratedCompletionRunId,
              CASE WHEN p.status = 'ACTIVE' AND g.goalType = 'COMPLETE_DISTANCE'
                AND m.readiness = 'GOAL_COMPLETION_READY'
                THEN 'GOAL_COMPLETION_READY'
                WHEN p.status = 'ACTIVE' AND g.goalType = 'COMPLETE_DISTANCE'
                THEN 'GOAL_COMPLETION_NOT_READY'
                ELSE 'GOAL_COMPLETION_NOT_READY' END AS migratedReadiness,
              CASE WHEN p.status = 'ACTIVE' AND g.goalType = 'COMPLETE_DISTANCE'
                THEN COALESCE(m.promptDismissed, 0) ELSE 0 END AS migratedPromptDismissed,
              COALESCE(m.updatedAtEpochMillis, p.createdAtEpochMillis) AS migratedUpdatedAt
            FROM training_plans p
            INNER JOIN training_goals g ON g.id = p.goalId
            LEFT JOIN goal_completion_milestones m ON m.goalId = p.goalId"""
        )
        connection.execSQL(
          """CREATE TABLE `_new_training_goals` (`id` TEXT NOT NULL, `distance` TEXT NOT NULL, `customDistanceMeters` REAL, `goalType` TEXT NOT NULL, `targetTimeSeconds` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `status` TEXT NOT NULL, `confidenceAtCreation` TEXT NOT NULL, `startingBestDurationSeconds` INTEGER, `startingBestRunId` TEXT, `startingBestObservedAtEpochMillis` INTEGER, `startingRecentDurationSeconds` INTEGER, `startingRecentRunId` TEXT, `startingRecentObservedAtEpochMillis` INTEGER, `startingAverageWeeklyDistanceMeters` REAL NOT NULL, `startingAverageWeeklyDurationSeconds` INTEGER NOT NULL, `startingLongestRunDistanceMeters` REAL, `startingLongestRunDurationSeconds` INTEGER, `startingAverageRunsPerWeek` REAL NOT NULL, `startingTypicalEasyPaceSecondsPerKm` REAL, `startingTypicalEasyHeartRate` REAL, `startingProfileConfidence` TEXT NOT NULL, `snapshotCapturedAtEpochMillis` INTEGER NOT NULL, `assessmentLevel` TEXT NOT NULL, PRIMARY KEY(`id`))"""
        )
        connection.execSQL(
          """INSERT INTO `_new_training_goals` SELECT `id`, `distance`, `customDistanceMeters`, `goalType`,
            `targetTimeSeconds`, `createdAtEpochMillis`, `status`, `confidenceAtCreation`,
            `startingBestDurationSeconds`, `startingBestRunId`, `startingBestObservedAtEpochMillis`,
            `startingRecentDurationSeconds`, `startingRecentRunId`, `startingRecentObservedAtEpochMillis`,
            `startingAverageWeeklyDistanceMeters`, `startingAverageWeeklyDurationSeconds`,
            `startingLongestRunDistanceMeters`, `startingLongestRunDurationSeconds`, `startingAverageRunsPerWeek`,
            `startingTypicalEasyPaceSecondsPerKm`, `startingTypicalEasyHeartRate`, `startingProfileConfidence`,
            `snapshotCapturedAtEpochMillis`, `assessmentLevel` FROM `training_goals`"""
        )
        // Do not rely on dropping training_goals to cascade. Room migrations can run with foreign-key
        // enforcement disabled, in which case these rows survive and collide with their snapshots.
        connection.execSQL("DELETE FROM `training_plan_workouts`")
        connection.execSQL("DELETE FROM `training_week_reasons`")
        connection.execSQL("DELETE FROM `training_blocks`")
        connection.execSQL("DELETE FROM `training_weeks`")
        connection.execSQL("DELETE FROM `training_plans`")
        connection.execSQL("DELETE FROM `training_goal_assessment_reasons`")
        connection.execSQL("DROP TABLE `goal_completion_milestones`")
        connection.execSQL("DROP TABLE `training_goals`")
        connection.execSQL("ALTER TABLE `_new_training_goals` RENAME TO `training_goals`")
        connection.execSQL("ALTER TABLE `training_plans` ADD COLUMN `plannedCompletionDateEpochDay` INTEGER")
        connection.execSQL("ALTER TABLE `training_plans` ADD COLUMN `plannedCompletionRunId` TEXT")
        connection.execSQL("ALTER TABLE `training_plans` ADD COLUMN `completionReadiness` TEXT NOT NULL DEFAULT 'GOAL_COMPLETION_NOT_READY'")
        connection.execSQL("ALTER TABLE `training_plans` ADD COLUMN `completionPromptDismissed` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `training_plans` ADD COLUMN `completionUpdatedAtEpochMillis` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
          """CREATE TEMP TABLE `migration15_distinct_goal_reasons` AS
            SELECT `goalId`, `text`, MIN(`position`) AS `legacyPosition`
            FROM `migration15_goal_reasons`
            GROUP BY `goalId`, `text`"""
        )
        connection.execSQL(
          """INSERT INTO `training_goal_assessment_reasons` (`goalId`, `position`, `text`)
            SELECT reason.`goalId`,
              (SELECT COUNT(*) FROM `migration15_distinct_goal_reasons` earlier
                WHERE earlier.`goalId` = reason.`goalId`
                  AND (earlier.`legacyPosition` < reason.`legacyPosition`
                    OR (earlier.`legacyPosition` = reason.`legacyPosition`
                      AND earlier.`text` < reason.`text`))),
              reason.`text`
            FROM `migration15_distinct_goal_reasons` reason
            ORDER BY reason.`goalId`, reason.`legacyPosition`, reason.`text`"""
        )
        connection.execSQL(
          """INSERT INTO `training_plans` (`id`, `goalId`, `createdAtEpochMillis`, `startDateEpochDay`,
            `endDateEpochDay`, `generationVersion`, `explanation`, `confidence`, `status`,
            `plannedCompletionDateEpochDay`, `plannedCompletionRunId`, `completionReadiness`,
            `completionPromptDismissed`, `completionUpdatedAtEpochMillis`)
            SELECT `id`, `goalId`, `createdAtEpochMillis`, `startDateEpochDay`, `endDateEpochDay`,
            `generationVersion`, `explanation`, `confidence`, `status`, `migratedCompletionDate`,
            `migratedCompletionRunId`, `migratedReadiness`, `migratedPromptDismissed`, `migratedUpdatedAt`
            FROM `migration15_plans`"""
        )
        connection.execSQL("INSERT INTO `training_weeks` SELECT * FROM `migration15_weeks`")
        connection.execSQL("INSERT INTO `training_week_reasons` SELECT * FROM `migration15_week_reasons`")
        connection.execSQL("INSERT INTO `training_blocks` SELECT * FROM `migration15_blocks`")
        connection.execSQL("INSERT INTO `training_plan_workouts` SELECT * FROM `migration15_plan_workouts`")
      }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `workout_executions` (`plannedWorkoutId` TEXT NOT NULL, `actualRunId` TEXT NOT NULL, `status` TEXT NOT NULL, `comparisonBasis` TEXT NOT NULL, `intendedDistanceMeters` REAL, `intendedDurationSeconds` INTEGER, `actualDistanceMeters` REAL, `actualDurationSeconds` INTEGER NOT NULL, `qualityWorkCompletionFraction` REAL, `explanation` TEXT NOT NULL, `evaluatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`plannedWorkoutId`), FOREIGN KEY(`plannedWorkoutId`) REFERENCES `planned_workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`actualRunId`) REFERENCES `runs`(`healthConnectRecordId`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL(
          "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_executions_actualRunId` ON `workout_executions` (`actualRunId`)"
        )
      }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `run_routes` (`runId` TEXT NOT NULL, `source` TEXT NOT NULL, `sourceAppName` TEXT, `confidence` TEXT NOT NULL, `rejectedPointCount` INTEGER NOT NULL, `derivedDistanceMeters` REAL, `canonicalDistanceMeters` REAL, `distanceDifferencePercent` REAL, `distanceScaleFactor` REAL NOT NULL, `hasLargeSamplingGap` INTEGER NOT NULL, PRIMARY KEY(`runId`), FOREIGN KEY(`runId`) REFERENCES `runs`(`healthConnectRecordId`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_run_routes_runId` ON `run_routes` (`runId`)")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `run_route_points` (`runId` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `timestampEpochMillis` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `altitudeMeters` REAL, `horizontalAccuracyMeters` REAL, `verticalAccuracyMeters` REAL, PRIMARY KEY(`runId`, `sequence`), FOREIGN KEY(`runId`) REFERENCES `run_routes`(`runId`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_run_route_points_runId` ON `run_route_points` (`runId`)")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `run_splits` (`runId` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, `derivation` TEXT NOT NULL, `startTimeEpochMillis` INTEGER NOT NULL, `endTimeEpochMillis` INTEGER NOT NULL, `distanceMeters` REAL NOT NULL, `durationSeconds` REAL NOT NULL, `averagePaceSecondsPerKm` REAL NOT NULL, `averageHeartRate` REAL, `averageCadence` REAL, `partial` INTEGER NOT NULL, `confidence` TEXT NOT NULL, PRIMARY KEY(`runId`, `ordinal`), FOREIGN KEY(`runId`) REFERENCES `run_routes`(`runId`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_run_splits_runId` ON `run_splits` (`runId`)")
      }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `physicalRunId` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `relevance` TEXT NOT NULL DEFAULT 'ACTIVE'")
        connection.execSQL("ALTER TABLE `runs` ADD COLUMN `canonicalMatchDecision` TEXT NOT NULL DEFAULT 'SEPARATE'")
        connection.execSQL("UPDATE `runs` SET `physicalRunId` = CASE WHEN `duplicateGroupId` IS NOT NULL THEN `duplicateGroupId` ELSE `healthConnectRecordId` END")
        connection.execSQL("DROP INDEX IF EXISTS `index_workout_executions_actualRunId`")
        connection.execSQL("ALTER TABLE `workout_executions` ADD COLUMN `physicalRunId` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("UPDATE `workout_executions` SET `physicalRunId` = COALESCE((SELECT `physicalRunId` FROM `runs` WHERE `runs`.`healthConnectRecordId` = `workout_executions`.`actualRunId`), `actualRunId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_executions_actualRunId` ON `workout_executions` (`actualRunId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_executions_physicalRunId` ON `workout_executions` (`physicalRunId`)")
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `workout_run_associations` (`plannedWorkoutId` TEXT NOT NULL, `physicalRunId` TEXT NOT NULL, `actualRunId` TEXT NOT NULL, `source` TEXT NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`plannedWorkoutId`), FOREIGN KEY(`plannedWorkoutId`) REFERENCES `planned_workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`actualRunId`) REFERENCES `runs`(`healthConnectRecordId`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_run_associations_physicalRunId` ON `workout_run_associations` (`physicalRunId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_run_associations_actualRunId` ON `workout_run_associations` (`actualRunId`)")
        connection.execSQL(
          """INSERT OR IGNORE INTO `workout_run_associations` (`plannedWorkoutId`, `physicalRunId`, `actualRunId`, `source`, `updatedAtEpochMillis`)
            SELECT `plannedWorkoutId`, `physicalRunId`, `actualRunId`, 'MIGRATED', `evaluatedAtEpochMillis`
            FROM `workout_executions` ORDER BY `evaluatedAtEpochMillis`, `plannedWorkoutId`"""
        )
      }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """CREATE TABLE IF NOT EXISTS `training_availability` (`id` INTEGER NOT NULL, `availableDaysMask` INTEGER NOT NULL, `preferredDaysMask` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
        )
      }
    }

    fun getInstance(context: Context): OpenRunCoachDatabase =
      instance ?: synchronized(this) {
        instance
          ?: build(context, "open_run_coach.db").also { instance = it }
      }

    internal fun createIsolated(context: Context, databaseName: String): OpenRunCoachDatabase {
      require(databaseName.isNotBlank() && databaseName != "open_run_coach.db")
      return build(context, databaseName)
    }

    private fun build(context: Context, databaseName: String): OpenRunCoachDatabase =
      Room.databaseBuilder(
        context.applicationContext,
        OpenRunCoachDatabase::class.java,
        databaseName,
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
              MIGRATION_12_13,
              MIGRATION_13_14,
              MIGRATION_14_15,
              MIGRATION_15_16,
              MIGRATION_16_17,
              MIGRATION_17_18,
              MIGRATION_18_19,
            ).build()
  }
}
