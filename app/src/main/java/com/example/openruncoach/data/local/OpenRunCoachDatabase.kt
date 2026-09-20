package com.example.openruncoach.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RunEntity::class], version = 1, exportSchema = true)
abstract class OpenRunCoachDatabase : RoomDatabase() {
  abstract fun runDao(): RunDao

  companion object {
    @Volatile private var instance: OpenRunCoachDatabase? = null

    fun getInstance(context: Context): OpenRunCoachDatabase =
      instance ?: synchronized(this) {
        instance
          ?: Room.databaseBuilder(
              context.applicationContext,
              OpenRunCoachDatabase::class.java,
              "open_run_coach.db",
            ).build().also { instance = it }
      }
  }
}
