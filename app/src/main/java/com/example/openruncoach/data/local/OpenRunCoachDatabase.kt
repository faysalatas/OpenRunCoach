package com.example.openruncoach.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(entities = [RunEntity::class, RunFeedbackEntity::class], version = 2, exportSchema = true)
abstract class OpenRunCoachDatabase : RoomDatabase() {
  abstract fun runDao(): RunDao
  abstract fun runFeedbackDao(): RunFeedbackDao

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

    fun getInstance(context: Context): OpenRunCoachDatabase =
      instance ?: synchronized(this) {
        instance
          ?: Room.databaseBuilder(
              context.applicationContext,
              OpenRunCoachDatabase::class.java,
              "open_run_coach.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
      }
  }
}
