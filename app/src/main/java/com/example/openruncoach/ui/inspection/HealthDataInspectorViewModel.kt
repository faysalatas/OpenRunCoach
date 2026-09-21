package com.example.openruncoach.ui.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.healthconnect.inspection.HealthConnectDataInspector
import com.example.openruncoach.data.healthconnect.inspection.HealthConnectInspectionTarget
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.inspection.MetricAvailability
import com.example.openruncoach.domain.inspection.MetricInspection
import com.example.openruncoach.domain.inspection.RouteInspectionStatus
import com.example.openruncoach.domain.inspection.RouteMetricSummary
import com.example.openruncoach.domain.inspection.RunDataInspection
import com.example.openruncoach.domain.inspection.RunMetricType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HealthDataInspectorUiState(
  val run: RunSummary? = null,
  val isLoadingRun: Boolean = true,
  val isInspecting: Boolean = false,
  val inspection: RunDataInspection? = null,
  val errorMessage: String? = null,
)

class HealthDataInspectorViewModel(
  private val runId: String,
  runRepository: RunRepository,
  private val inspector: HealthConnectDataInspector,
) : ViewModel() {
  private val _uiState = MutableStateFlow(HealthDataInspectorUiState())
  val uiState = _uiState.asStateFlow()
  private var inspectionJob: Job? = null
  private var inspectedRunId: String? = null

  init {
    viewModelScope.launch {
      runRepository.observeRun(runId).collect { run ->
        _uiState.update { it.copy(run = run, isLoadingRun = false) }
        if (run != null && run.isCanonical && inspectedRunId != run.healthConnectRecordId) {
          inspectedRunId = run.healthConnectRecordId
          inspect()
        }
      }
    }
  }

  fun inspect() {
    val run = _uiState.value.run ?: return
    if (!run.isCanonical) {
      _uiState.update { it.copy(errorMessage = "Only canonical runs can be inspected.") }
      return
    }
    inspectionJob?.cancel()
    inspectionJob = viewModelScope.launch {
      _uiState.update { it.copy(isInspecting = true, errorMessage = null) }
      try {
        val result = inspector.inspect(
          HealthConnectInspectionTarget(
            exerciseSessionRecordId = run.healthConnectRecordId,
            startTime = run.startTime,
            endTime = run.endTime,
            sourcePackageName = run.sourcePackageName.takeIf(String::isNotBlank),
            sourceAppName = run.sourceAppName,
          )
        )
        _uiState.update { it.copy(isInspecting = false, inspection = result) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isInspecting = false, errorMessage = "Could not inspect Health Connect data.") }
      }
    }
  }

  fun onRouteInspectionResult(pointCount: Int?) {
    _uiState.update { state ->
      val inspection = state.inspection ?: return@update state
      val replacement = if (pointCount == null) {
        MetricInspection(
          RunMetricType.ROUTE,
          MetricAvailability.PERMISSION_REQUIRED,
          summary = RouteMetricSummary(RouteInspectionStatus.CONSENT_REQUIRED),
          notes = listOf("Route consent was not granted"),
        )
      } else {
        MetricInspection(
          RunMetricType.ROUTE,
          MetricAvailability.AVAILABLE,
          recordCount = 1,
          sourcePackageName = inspection.sourcePackageName,
          sourceAppName = inspection.sourceAppName,
          summary = RouteMetricSummary(RouteInspectionStatus.AVAILABLE, pointCount),
          notes = listOf("Route points are not stored"),
        )
      }
      state.copy(
        inspection = inspection.copy(
          metrics = inspection.metrics.map { if (it.metric == RunMetricType.ROUTE) replacement else it }
        )
      )
    }
  }
}
