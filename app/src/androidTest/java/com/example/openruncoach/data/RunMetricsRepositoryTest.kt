package com.example.openruncoach.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.data.local.RunEntity
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.RunSampleMetricType
import com.example.openruncoach.domain.metrics.RunTimeSeries
import com.example.openruncoach.domain.metrics.TimedMetricSample
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunMetricsRepositoryTest {
  @Test fun directAndCollectionLoadingPreserveTheSameSpeedSeries() = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java,
    ).build()
    try {
      val repository = RunMetricsRepository(database.runMetricsDao())
      database.runDao().upsertAll(listOf(run()))
      repository.save(metrics())

      assertEquals(181, database.runMetricsDao().countSamples(RUN_ID, RunSampleMetricType.SPEED.name))
      val direct = requireNotNull(repository.getRunMetrics(RUN_ID))
      val collected = repository.getAllRunMetrics().single { it.runId == RUN_ID }

      assertEquals(181, direct.series.speed.size)
      assertEquals(direct.series.speed, collected.series.speed)
      assertEquals(direct.series.cadence, collected.series.cadence)
      assertEquals(direct.startTime, collected.startTime)
      assertEquals(direct.endTime, collected.endTime)
    } finally {
      database.close()
    }
  }

  @Test fun existingRepositoryObservesSamplesWrittenAfterItsInitialRead() = runBlocking {
    val database = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(), OpenRunCoachDatabase::class.java,
    ).build()
    try {
      database.runDao().upsertAll(listOf(run()))
      val writer = RunMetricsRepository(database.runMetricsDao())
      val existingReader = RunMetricsRepository(database.runMetricsDao())
      writer.save(metrics().copy(series = RunTimeSeries()))

      assertEquals(0, existingReader.getRunMetrics(RUN_ID)?.series?.speed?.size)

      writer.save(metrics())

      assertEquals(181, existingReader.getRunMetrics(RUN_ID)?.series?.speed?.size)
      assertEquals(181, existingReader.observeRunMetrics(RUN_ID).first()?.series?.speed?.size)
    } finally {
      database.close()
    }
  }

  private fun run() = RunEntity(
    healthConnectRecordId = RUN_ID,
    startTimeEpochMillis = START.toEpochMilli(),
    endTimeEpochMillis = START.plusSeconds(1_800).toEpochMilli(),
    durationSeconds = 1_800,
    distanceMeters = 5_000.0,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = 360.0,
    importedAtEpochMillis = START.plusSeconds(2_000).toEpochMilli(),
  )

  private fun metrics() = RunMetrics(
    runId = RUN_ID,
    startTime = START,
    endTime = START.plusSeconds(1_800),
    durationSeconds = MetricValue(1_800L, MetricSource.DERIVED, MetricConfidence.HIGH),
    distanceMeters = MetricValue(5_000.0, MetricSource.DERIVED, MetricConfidence.HIGH),
    series = RunTimeSeries(speed = (0L..1_800L step 10).map { seconds ->
      TimedMetricSample(START.plusSeconds(seconds), 5_000.0 / 1_800.0)
    }),
    generatedAt = START.plusSeconds(2_000),
  )

  private companion object {
    const val RUN_ID = "hard-continuous-5k"
    val START: Instant = Instant.parse("2026-09-27T08:00:00Z")
  }
}
