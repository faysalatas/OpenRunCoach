package com.example.openruncoach.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.domain.RunSummary
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class RunHistoryGroup(val title: String, val runs: List<RunSummary>)

data class HistoryUiState(
  val isLoading: Boolean = true,
  val groups: List<RunHistoryGroup> = emptyList(),
  val errorMessage: String? = null,
)

class HistoryViewModel(
  runRepository: RunRepository,
  zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
  val uiState = runRepository.canonicalRuns.map { runs ->
    val groups = runs.groupBy { YearMonth.from(it.startTime.atZone(zoneId)) }
      .map { (month, monthRuns) ->
        RunHistoryGroup(
          month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
          monthRuns,
        )
      }
    HistoryUiState(isLoading = false, groups = groups)
  }.catch {
    emit(HistoryUiState(isLoading = false, errorMessage = "Could not load run history."))
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
