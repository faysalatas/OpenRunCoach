package com.example.openruncoach.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.coaching.CoachingEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CoachDetailUiState(
  val isLoading: Boolean = true,
  val decision: CoachingDecision? = null,
  val errorMessage: String? = null,
)

class CoachDetailViewModel(
  runId: String,
  runRepository: RunRepository,
  feedbackRepository: RunFeedbackRepository,
  coachingEngine: CoachingEngine = CoachingEngine(),
) : ViewModel() {
  val uiState = combine(
    runRepository.observeRun(runId),
    runRepository.canonicalRuns,
    feedbackRepository.observeFeedbackForRun(runId),
  ) { run, history, feedback ->
    CoachDetailUiState(
      isLoading = false,
      decision = if (run != null && feedback != null) coachingEngine.analyze(run, history, feedback) else null,
    )
  }.catch {
    emit(CoachDetailUiState(isLoading = false, errorMessage = "Could not load coach analysis."))
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoachDetailUiState())
}
