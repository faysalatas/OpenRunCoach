package com.example.openruncoach.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedbackDraft
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunFeedbackRepositoryTest {
  @Test
  fun savingAgainUpdatesTheOnlyFeedbackForARun() = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java,
    ).build()
    try {
      database.runDao().upsertAll(listOf(runEntity("run-1")))
      val firstRepository = RunFeedbackRepository(database, fixedClock(1000))
      assertNull(firstRepository.observeFeedbackForRun("run-1").first())

      val first = firstRepository.saveFeedback(draft("run-1"))
      val second = RunFeedbackRepository(database, fixedClock(2000)).saveFeedback(
        draft("run-1").copy(rpe = 9, readiness = 4, notes = "  Tired legs  "),
      )

      assertEquals(first.id, second.id)
      assertEquals(Instant.ofEpochMilli(1000), second.createdAt)
      assertEquals(Instant.ofEpochMilli(2000), second.updatedAt)
      assertEquals(9, second.rpe)
      assertEquals("Tired legs", second.notes)
      assertEquals(second, firstRepository.getFeedbackForRun("run-1"))
      assertEquals(second, firstRepository.observeFeedbackForRun("run-1").first())

      val third = RunFeedbackRepository(database, fixedClock(3000)).updateFeedback(
        draft("run-1").copy(rpe = 7),
      )
      assertEquals(first.id, third.id)
      assertEquals(Instant.ofEpochMilli(1000), third.createdAt)
      assertEquals(Instant.ofEpochMilli(3000), third.updatedAt)
    } finally {
      database.close()
    }
  }

  @Test
  fun migrationPreservesExistingRunsAndAddsFeedback() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val databaseName = "feedback-migration-test.db"
    context.deleteDatabase(databaseName)
    val legacy = context.openOrCreateDatabase(databaseName, android.content.Context.MODE_PRIVATE, null)
    legacy.execSQL(
      """CREATE TABLE IF NOT EXISTS `runs` (`healthConnectRecordId` TEXT NOT NULL, `startTimeEpochMillis` INTEGER NOT NULL, `endTimeEpochMillis` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `distanceMeters` REAL, `averageHeartRate` REAL, `maxHeartRate` INTEGER, `averagePaceSecondsPerKm` REAL, `importedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`healthConnectRecordId`))"""
    )
    legacy.execSQL("INSERT INTO runs VALUES ('run-1', 1000, 2000, 1, NULL, NULL, NULL, NULL, 1000)")
    legacy.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
    legacy.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '167300767cf5b2d8c9f9f0144189cd09')")
    legacy.version = 1
    legacy.close()

    val database = Room.databaseBuilder(context, OpenRunCoachDatabase::class.java, databaseName)
      .addMigrations(OpenRunCoachDatabase.MIGRATION_1_2)
      .build()
    try {
      assertEquals("run-1", database.runDao().observeRun("run-1").first()?.healthConnectRecordId)
      val feedback = RunFeedbackRepository(database, fixedClock(2000)).saveFeedback(draft("run-1"))
      assertNotNull(feedback.id)
      assertEquals("run-1", feedback.runId)
    } finally {
      database.close()
      context.deleteDatabase(databaseName)
    }
  }

  private fun draft(runId: String) = RunFeedbackDraft(
    runId = runId,
    rpe = 5,
    cardioEffort = CardioEffort.APPROPRIATE,
    muscleFatigue = MuscleFatigue.MILD,
    painLevel = PainLevel.NONE,
    completionStatus = CompletionStatus.COMPLETED,
    readiness = null,
    notes = null,
  )

  private fun runEntity(id: String) = RunEntity(
    healthConnectRecordId = id,
    startTimeEpochMillis = 1000,
    endTimeEpochMillis = 2000,
    durationSeconds = 1,
    distanceMeters = null,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = null,
    importedAtEpochMillis = 1000,
  )

  private fun fixedClock(millis: Long): Clock = Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
}
