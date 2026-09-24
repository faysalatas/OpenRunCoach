package com.example.openruncoach.data

import com.example.openruncoach.data.healthconnect.HealthConnectRunMetricsProvider
import com.example.openruncoach.data.samsunghealth.SamsungHealthRunMetricsEnricher
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.inspection.RunDataInspection
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.samsunghealth.SamsungHealthInspection

/** Builds and atomically replaces one summary snapshot. Raw series remain in memory only. */
class RunMetricsRefreshService(
  private val healthConnectProvider: HealthConnectRunMetricsProvider,
  private val samsungEnricher: SamsungHealthRunMetricsEnricher,
  private val repository: RunMetricsRepository,
) {
  suspend fun refresh(
    canonicalRun: RunSummary,
    healthConnectInspection: RunDataInspection?,
    samsungInspection: SamsungHealthInspection?,
  ): RunMetrics {
    val base = healthConnectProvider.build(canonicalRun, healthConnectInspection)
    val enriched = samsungEnricher.enrich(base, samsungInspection)
    repository.save(enriched)
    return enriched
  }
}
