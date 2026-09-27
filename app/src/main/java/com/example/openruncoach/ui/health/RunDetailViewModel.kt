package com.example.openruncoach.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunMetricsRepository
import com.example.openruncoach.data.RunMetricsRefresher
import com.example.openruncoach.data.GoalCompletionRepository
import com.example.openruncoach.data.TrainingGoalRepository
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.data.WorkoutResponseRepository
import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.PossibleDuplicate
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunFeedbackDraft
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.metrics.RunChartData
import com.example.openruncoach.domain.metrics.RunChartPreparer
import com.example.openruncoach.domain.workout.WorkoutExecutionResult
import com.example.openruncoach.domain.workout.WorkoutResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedbackForm(
  val rpe: String = "",
  val cardioEffort: CardioEffort = CardioEffort.APPROPRIATE,
  val muscleFatigue: MuscleFatigue = MuscleFatigue.NONE,
  val painLevel: PainLevel = PainLevel.NONE,
  val completionStatus: CompletionStatus = CompletionStatus.COMPLETED,
  val readiness: String = "",
  val notes: String = "",
)

data class RunDetailUiState(
  val run: RunSummary? = null,
  val runLoaded: Boolean = false,
  val possibleDuplicates: List<PossibleDuplicate> = emptyList(),
  val duplicateRecords: List<RunSummary> = emptyList(),
  val feedback: RunFeedback? = null,
  val feedbackLoaded: Boolean = false,
  val form: FeedbackForm = FeedbackForm(),
  val isDirty: Boolean = false,
  val isSaving: Boolean = false,
  val error: String? = null,
  val saved: Boolean = false,
  val coachingDecision: CoachingDecision? = null,
  val isEditingFeedback: Boolean = false,
  val showSourceDetails: Boolean = false,
  val metrics: RunMetrics? = null,
  val showMetricDetails: Boolean = false,
  val charts: RunChartData? = null,
  val isUpdatingMetrics: Boolean = false,
  val metricsUpdateError: Boolean = false,
  val workoutExecution: WorkoutExecutionResult? = null,
  val workoutResponse: WorkoutResponse? = null,
)

class RunDetailViewModel(
  private val runId: String,
  runRepository: RunRepository,
  private val feedbackRepository: RunFeedbackRepository,
  runMetricsRepository: RunMetricsRepository,
  private val runMetricsRefresher: RunMetricsRefresher?,
  private val goalRepository: TrainingGoalRepository? = null,
  private val goalCompletionRepository: GoalCompletionRepository? = null,
  private val workoutExecutionRepository: WorkoutExecutionRepository? = null,
  private val workoutResponseRepository: WorkoutResponseRepository? = null,
  private val coachingEngine: CoachingEngine = CoachingEngine(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(RunDetailUiState())
  val uiState = _uiState.asStateFlow()
  private var allRuns: List<RunSummary> = emptyList()
  private var canonicalHistory: List<RunSummary> = emptyList()
  private var metricsRefreshJob: Job? = null
  private var autoRefreshRunId: String? = null

  init {
    viewModelScope.launch {
      runRepository.observeRun(runId)
        .catch { _uiState.update { it.copy(error = "Could not load run.", runLoaded = true) } }
        .collect { run ->
          _uiState.update { state ->
            state.copy(
              run = run,
              runLoaded = true,
              charts = state.metrics?.let { metrics -> prepareCharts(metrics, run) },
            ).withDerivedState()
          }
          if (runMetricsRefresher != null && run != null && run.isCanonical && autoRefreshRunId != run.healthConnectRecordId) {
            autoRefreshRunId = run.healthConnectRecordId
            refreshMetrics(run, force = false)
          }
        }
    }
    viewModelScope.launch {
      runMetricsRepository.observeRunMetrics(runId)
        .catch { _uiState.update { it.copy(error = "Could not load run metrics.") } }
        .collect { metrics -> if (metrics != null) _uiState.update {
          it.copy(metrics = metrics, charts = prepareCharts(metrics, it.run))
        } }
    }
    viewModelScope.launch {
      runRepository.allRuns.collect { runs ->
        allRuns = runs
        _uiState.update { it.withDerivedState() }
      }
    }
    viewModelScope.launch {
      runRepository.canonicalRuns.collect { runs ->
        canonicalHistory = runs
        _uiState.update { it.withDerivedState() }
      }
    }
    viewModelScope.launch {
      runRepository.duplicateDiagnostics
        .catch { _uiState.update { it.copy(error = "Could not check duplicate runs.") } }
        .collect { diagnostics ->
          _uiState.update { it.copy(possibleDuplicates = diagnostics[runId].orEmpty()) }
        }
    }
    viewModelScope.launch {
      feedbackRepository.observeFeedbackForRun(runId)
        .catch { _uiState.update { it.copy(error = "Could not load feedback.", feedbackLoaded = true) } }
        .collect { feedback ->
          _uiState.update { state ->
            if (state.isDirty) state.copy(feedback = feedback, feedbackLoaded = true).withDerivedState()
            else state.copy(
              feedback = feedback,
              feedbackLoaded = true,
              form = feedback?.toForm() ?: FeedbackForm(),
            ).withDerivedState()
          }
        }
    }
    workoutExecutionRepository?.let { repository ->
      viewModelScope.launch {
        repository.observeForRun(runId)
          .catch { _uiState.update { it.copy(error = "Could not load workout completion.") } }
          .collect { execution -> _uiState.update { it.copy(workoutExecution = execution).withDerivedState() } }
      }
    }
    workoutResponseRepository?.let { repository ->
      viewModelScope.launch {
        repository.observeForRun(runId)
          .catch { _uiState.update { it.copy(error = "Could not interpret workout response.") } }
          .collect { response -> _uiState.update { it.copy(workoutResponse = response).withDerivedState() } }
      }
    }
  }

  fun updateForm(change: (FeedbackForm) -> FeedbackForm) {
    _uiState.update { it.copy(form = change(it.form), isDirty = true, error = null, saved = false) }
  }

  fun editFeedback() {
    _uiState.update { it.copy(isEditingFeedback = true, saved = false, error = null) }
  }

  fun cancelFeedbackEdit() {
    _uiState.update { state ->
      state.copy(
        isEditingFeedback = false,
        isDirty = false,
        form = state.feedback?.toForm() ?: FeedbackForm(),
        error = null,
      )
    }
  }

  fun toggleSourceDetails() {
    _uiState.update { it.copy(showSourceDetails = !it.showSourceDetails) }
  }

  fun toggleMetricDetails() {
    _uiState.update { it.copy(showMetricDetails = !it.showMetricDetails) }
  }

  fun refreshMetrics() {
    val run = _uiState.value.run ?: return
    refreshMetrics(run, force = true)
  }

  private fun refreshMetrics(run: RunSummary, force: Boolean) {
    val refresher = runMetricsRefresher ?: return
    if (metricsRefreshJob?.isActive == true) return
    metricsRefreshJob = viewModelScope.launch {
      _uiState.update { it.copy(isUpdatingMetrics = true, metricsUpdateError = false) }
      try {
        val outcome = refresher.refresh(run, force)
        _uiState.update {
          it.copy(
            metrics = outcome.metrics,
            charts = prepareCharts(outcome.metrics, run),
            isUpdatingMetrics = false,
          )
        }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Exception) {
        _uiState.update { it.copy(isUpdatingMetrics = false, metricsUpdateError = true) }
      }
    }
  }

  private fun prepareCharts(metrics: RunMetrics, run: RunSummary?): RunChartData = RunChartPreparer.prepare(
    metrics = metrics,
    canonicalDurationSeconds = run?.durationSeconds ?: metrics.durationSeconds.value,
    canonicalDistanceMeters = run?.distanceMeters ?: metrics.distanceMeters?.value,
  )

  fun saveFeedback() {
    val state = _uiState.value
    if (state.isSaving || !state.feedbackLoaded || state.run == null) return
    val rpe = state.form.rpe.toIntOrNull()
    val readiness = state.form.readiness.takeIf(String::isNotBlank)?.toIntOrNull()
    if (rpe == null || (state.form.readiness.isNotBlank() && readiness == null)) {
      _uiState.update { it.copy(error = "Enter a whole number for RPE and readiness.") }
      return
    }
    val draft = RunFeedbackDraft(
      runId = runId,
      rpe = rpe,
      cardioEffort = state.form.cardioEffort,
      muscleFatigue = state.form.muscleFatigue,
      painLevel = state.form.painLevel,
      completionStatus = state.form.completionStatus,
      readiness = readiness,
      notes = state.form.notes,
    )
    draft.validationError()?.let { error ->
      _uiState.update { it.copy(error = error) }
      return
    }
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, error = null, saved = false) }
      try {
        val saved = feedbackRepository.saveFeedback(draft)
        val execution = workoutExecutionRepository?.recordForAssociatedWorkout(state.run, saved)
        val activeGoal = goalRepository?.getActiveGoal()
        if (activeGoal != null && state.run.isCanonical) {
          goalCompletionRepository?.evaluateObservedRun(activeGoal, state.run, state.metrics, saved)
        }
        _uiState.update { it.copy(
          feedback = saved,
          form = saved.toForm(),
          isDirty = false,
          isSaving = false,
          saved = true,
          isEditingFeedback = false,
          workoutExecution = execution ?: it.workoutExecution,
        ).withDerivedState() }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isSaving = false, error = "Could not save feedback.") }
      }
    }
  }

  private fun RunDetailUiState.withDerivedState(): RunDetailUiState {
    val selectedRun = run
    val duplicateRecords = if (selectedRun?.duplicateGroupId == null) emptyList() else {
      allRuns.filter { it.duplicateGroupId == selectedRun.duplicateGroupId }
    }
    return copy(
      duplicateRecords = duplicateRecords,
      coachingDecision = if (selectedRun != null && feedback != null) {
        coachingEngine.analyze(selectedRun, canonicalHistory, feedback, workoutExecution, workoutResponse)
      } else null,
    )
  }
}

private fun RunFeedback.toForm() = FeedbackForm(
  rpe = rpe.toString(),
  cardioEffort = cardioEffort,
  muscleFatigue = muscleFatigue,
  painLevel = painLevel,
  completionStatus = completionStatus,
  readiness = readiness?.toString() ?: "",
  notes = notes ?: "",
)
