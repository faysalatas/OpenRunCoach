package com.example.openruncoach.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.RunFeedbackRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.WorkoutResponseRepository
import com.example.openruncoach.domain.coaching.CoachingDecision
import com.example.openruncoach.domain.coaching.CoachingEngine
import com.example.openruncoach.domain.workout.WorkoutResponse
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CoachDetailUiState(
  val isLoading: Boolean = true,
  val decision: CoachingDecision? = null,
  val response: WorkoutResponse? = null,
  val errorMessage: String? = null,
)

class CoachDetailViewModel(
  runId: String,
  runRepository: RunRepository,
  feedbackRepository: RunFeedbackRepository,
  workoutResponseRepository: WorkoutResponseRepository,
  coachingEngine: CoachingEngine = CoachingEngine(),
) : ViewModel() {
  val uiState = combine(
    runRepository.observeRun(runId),
    runRepository.canonicalRuns,
    feedbackRepository.observeFeedbackForRun(runId),
    workoutResponseRepository.observeForRun(runId),
  ) { run, history, feedback, response ->
    CoachDetailUiState(
      isLoading = false,
      decision = if (run != null && feedback != null) {
        coachingEngine.analyze(run, history, feedback, workoutResponse = response)
      } else null,
      response = response,
    )
  }.catch {
    emit(CoachDetailUiState(isLoading = false, errorMessage = "Could not load coach analysis."))
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoachDetailUiState())
}
