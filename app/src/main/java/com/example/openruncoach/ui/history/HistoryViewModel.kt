package com.example.openruncoach.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunRouteRepository
import com.example.openruncoach.data.WorkoutExecutionRepository
import com.example.openruncoach.domain.RunSummary
import com.example.openruncoach.domain.route.RunRoute
import com.example.openruncoach.domain.workout.WorkoutType
import com.example.openruncoach.domain.workout.displayName
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryRunItem(
  val run: RunSummary,
  val dateLabel: String,
  val workoutTypeLabel: String,
  val route: RunRoute? = null,
  val workoutType: WorkoutType? = null,
)

data class RunHistoryGroup(val title: String, val runs: List<HistoryRunItem>)

data class HistoryUiState(
  val isLoading: Boolean = true,
  val groups: List<RunHistoryGroup> = emptyList(),
  val errorMessage: String? = null,
  val showIgnored: Boolean = false,
  val ignoredCount: Int = 0,
)

class HistoryViewModel(
  runRepository: RunRepository,
  runRouteRepository: RunRouteRepository,
  private val workoutExecutionRepository: WorkoutExecutionRepository,
  zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
  private val showIgnored = MutableStateFlow(false)

  val uiState = combine(
    runRepository.canonicalRuns,
    runRepository.ignoredRuns,
    runRouteRepository.observeAll(),
    showIgnored,
  ) { active, ignored, routes, showingIgnored ->
    val runs = if (showingIgnored) ignored else active
    val workouts = workoutExecutionRepository.getAssociatedWorkoutsByRunId()
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    val groups = runs.sortedByDescending(RunSummary::startTime)
      .groupBy { YearMonth.from(it.startTime.atZone(zoneId)) }
      .map { (month, monthRuns) ->
        RunHistoryGroup(
          month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
          monthRuns.map { run ->
            HistoryRunItem(
              run = run,
              dateLabel = run.startTime.atZone(zoneId).format(dateFormatter),
              workoutTypeLabel = workouts[run.healthConnectRecordId]?.let { it.name.ifBlank { it.type.displayName() } } ?: "Run",
              route = routes[run.physicalRunId]?.route,
              workoutType = workouts[run.healthConnectRecordId]?.type,
            )
          },
        )
      }
    HistoryUiState(
      isLoading = false,
      groups = groups,
      showIgnored = showingIgnored,
      ignoredCount = ignored.size,
    )
  }.catch {
    emit(HistoryUiState(isLoading = false, errorMessage = "Could not load run history."))
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

  fun toggleIgnored() {
    showIgnored.value = !showIgnored.value
  }
}
