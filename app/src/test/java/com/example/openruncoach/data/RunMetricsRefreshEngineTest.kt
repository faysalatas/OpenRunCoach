package com.example.openruncoach.data

import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.metrics.MetricConfidence
import com.example.openruncoach.domain.metrics.MetricSource
import com.example.openruncoach.domain.metrics.MetricValue
import com.example.openruncoach.domain.metrics.RunMetrics
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RunMetricsRefreshEngineTest {
  private val now = Instant.parse("2026-09-24T12:00:00Z")
  private val policy = RunMetricsFreshnessPolicy(Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(24))
  private val engine = RunMetricsRefreshEngine(policy)

  @Test fun missingMetricsTriggerRefresh() = runTest {
    var persisted: RunMetrics? = null
    val result = engine.refresh(run(), null, false, { metrics() }, { it }, { persisted = it })
    assertTrue(result.refreshed)
    assertSame(result.metrics, persisted)
  }

  @Test fun olderMetricPipelineVersionTriggersRefresh() = runTest {
    var providerCalls = 0
    val result = engine.refresh(run(), metrics().copy(dataVersion = 2), false, {
      providerCalls++
      metrics()
    }, { it }, {})

    assertTrue(result.refreshed)
    assertEquals(1, providerCalls)
  }

  @Test fun currentMetricsSkipProviderWork() = runTest {
    val current = metrics()
    var providerCalls = 0
    val result = engine.refresh(run(), current, false, {
      providerCalls++
      current
    }, { it }, {})
    assertFalse(result.refreshed)
    assertEquals(0, providerCalls)
  }

  @Test fun samsungFailureStillPersistsHealthConnectBase() = runTest {
    val base = metrics()
    var persisted: RunMetrics? = null
    val result = engine.refresh(run(), null, false, { base }, { error("Samsung unavailable") }, { persisted = it })
    assertTrue(result.samsungFailure)
    assertSame(base, result.metrics)
    assertSame(base, persisted)
  }

  @Test fun repeatedRefreshIsIdempotentWhenSnapshotIsCurrent() = runTest {
    var stored: RunMetrics? = null
    var enrichCalls = 0
    suspend fun refresh() = engine.refresh(run(), stored, false, { metrics() }, {
      enrichCalls++
      it
    }, { stored = it })

    val first = refresh()
    val second = refresh()
    assertTrue(first.refreshed)
    assertFalse(second.refreshed)
    assertEquals(1, enrichCalls)
    assertEquals(first.metrics, second.metrics)
  }

  private fun run() = RunSummary(
    healthConnectRecordId = "run",
    startTime = now.minusSeconds(3_600),
    endTime = now.minusSeconds(1_800),
    durationSeconds = 1_800,
    distanceMeters = 5_000.0,
    averageHeartRate = null,
    maxHeartRate = null,
    averagePaceSecondsPerKm = 360.0,
  )

  private fun metrics() = RunMetrics(
    runId = "run",
    startTime = run().startTime,
    endTime = run().endTime,
    durationSeconds = MetricValue(1_800, MetricSource.HEALTH_CONNECT, MetricConfidence.HIGH),
    generatedAt = now,
  )
}
