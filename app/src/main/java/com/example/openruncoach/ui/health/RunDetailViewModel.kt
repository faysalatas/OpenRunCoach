package com.example.openruncoach.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.domain.CardioEffort
import com.example.openruncoach.domain.CompletionStatus
import com.example.openruncoach.domain.MuscleFatigue
import com.example.openruncoach.domain.PainLevel
import com.example.openruncoach.domain.RunFeedback
import com.example.openruncoach.domain.RunFeedbackDraft
import com.example.openruncoach.domain.RunSummary
import kotlinx.coroutines.CancellationException
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
  val feedback: RunFeedback? = null,
  val feedbackLoaded: Boolean = false,
  val form: FeedbackForm = FeedbackForm(),
  val isDirty: Boolean = false,
  val isSaving: Boolean = false,
  val error: String? = null,
  val saved: Boolean = false,
)

class RunDetailViewModel(
  private val runId: String,
  runRepository: RunRepository,
  private val feedbackRepository: RunFeedbackRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(RunDetailUiState())
  val uiState = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      runRepository.observeRun(runId)
        .catch { _uiState.update { it.copy(error = "Could not load run.", runLoaded = true) } }
        .collect { run -> _uiState.update { it.copy(run = run, runLoaded = true) } }
    }
    viewModelScope.launch {
      feedbackRepository.observeFeedbackForRun(runId)
        .catch { _uiState.update { it.copy(error = "Could not load feedback.", feedbackLoaded = true) } }
        .collect { feedback ->
          _uiState.update { state ->
            if (state.isDirty) state.copy(feedback = feedback, feedbackLoaded = true)
            else state.copy(
              feedback = feedback,
              feedbackLoaded = true,
              form = feedback?.toForm() ?: FeedbackForm(),
            )
          }
        }
    }
  }

  fun updateForm(change: (FeedbackForm) -> FeedbackForm) {
    _uiState.update { it.copy(form = change(it.form), isDirty = true, error = null, saved = false) }
  }

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
        _uiState.update { it.copy(
          feedback = saved,
          form = saved.toForm(),
          isDirty = false,
          isSaving = false,
          saved = true,
        ) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(isSaving = false, error = "Could not save feedback.") }
      }
    }
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
