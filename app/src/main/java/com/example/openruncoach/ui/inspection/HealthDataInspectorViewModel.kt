package com.example.openruncoach.ui.inspection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.healthconnect.inspection.HealthConnectDataInspector
import com.example.openruncoach.data.healthconnect.inspection.HealthConnectInspectionTarget
import com.example.openruncoach.data.samsunghealth.SamsungHealthAvailabilityState
import com.example.openruncoach.data.samsunghealth.SamsungHealthExerciseInspector
import com.example.openruncoach.data.samsunghealth.SamsungHealthInspectionResult
import com.example.openruncoach.data.samsunghealth.SamsungHealthPermissionManager
import com.example.openruncoach.data.samsunghealth.SamsungMatchSelectionRepository
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.SourceDeviceType
import com.example.openruncoach.domain.inspection.MetricAvailability
import com.example.openruncoach.domain.inspection.MetricInspection
import com.example.openruncoach.domain.inspection.RouteInspectionStatus
import com.example.openruncoach.domain.inspection.RouteMetricSummary
import com.example.openruncoach.domain.inspection.RunDataInspection
import com.example.openruncoach.domain.inspection.RunMetricType
import com.example.openruncoach.domain.samsunghealth.SamsungHealthInspection
import com.example.openruncoach.domain.samsunghealth.SamsungWorkoutTarget
import android.app.Activity
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
  val isInspectingSamsungHealth: Boolean = false,
  val samsungHealthAvailability: SamsungHealthAvailabilityState? = null,
  val samsungHealthInspection: SamsungHealthInspection? = null,
  val errorMessage: String? = null,
)

class HealthDataInspectorViewModel(
  private val runId: String,
  runRepository: RunRepository,
  private val inspector: HealthConnectDataInspector,
  private val samsungInspector: SamsungHealthExerciseInspector,
  private val samsungPermissionManager: SamsungHealthPermissionManager,
  private val samsungMatchSelectionRepository: SamsungMatchSelectionRepository,
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
        _uiState.update { it.copy(isInspecting = false, inspection = result, isInspectingSamsungHealth = true) }
        inspectSamsungHealth(run, result)
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isInspecting = false, errorMessage = "Could not inspect Health Connect data.") }
      }
    }
  }

  fun requestSamsungHealthPermission(activity: Activity) {
    val run = _uiState.value.run ?: return
    viewModelScope.launch {
      _uiState.update { it.copy(isInspectingSamsungHealth = true) }
      val availability = samsungPermissionManager.requestReadPermission(activity)
      _uiState.update { it.copy(samsungHealthAvailability = availability) }
      if (availability.status == com.example.openruncoach.data.samsunghealth.SamsungHealthAvailability.AVAILABLE) {
        inspectSamsungHealth(run, _uiState.value.inspection)
      } else {
        _uiState.update { it.copy(isInspectingSamsungHealth = false) }
      }
    }
  }

  fun resolveSamsungHealthIssue(activity: Activity) {
    samsungPermissionManager.resolveLastIssue(activity)?.let { availability ->
      _uiState.update { it.copy(samsungHealthAvailability = availability) }
    }
  }

  fun selectSamsungWorkout(candidateId: String) {
    val run = _uiState.value.run ?: return
    viewModelScope.launch {
      samsungMatchSelectionRepository.selectCandidate(run.healthConnectRecordId, candidateId)
      _uiState.update { it.copy(isInspectingSamsungHealth = true) }
      inspectSamsungHealth(run, _uiState.value.inspection)
    }
  }

  private suspend fun inspectSamsungHealth(run: RunSummary, healthConnect: RunDataInspection?) {
    val selectedCandidateId = samsungMatchSelectionRepository.selectedCandidateId(run.healthConnectRecordId)
    val result: SamsungHealthInspectionResult = samsungInspector.inspect(
      SamsungWorkoutTarget(
        startTime = run.startTime,
        endTime = run.endTime,
        durationSeconds = run.durationSeconds,
        distanceMeters = run.distanceMeters,
        canonicalSourceAppId = run.sourcePackageName.takeIf(String::isNotBlank),
        preferWatchSource = run.sourcePackageName == SAMSUNG_HEALTH_PACKAGE &&
          run.sourceDeviceType == SourceDeviceType.WATCH,
        averageHeartRate = run.averageHeartRate,
        maxHeartRate = run.maxHeartRate?.toDouble(),
      ),
      healthConnect,
      selectedCandidateId,
    )
    _uiState.update {
      it.copy(
        isInspectingSamsungHealth = false,
        samsungHealthAvailability = result.availability,
        samsungHealthInspection = result.inspection,
      )
    }
  }

  private companion object {
    const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
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
