package com.example.openruncoach.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.openruncoach.data.diagnostics.DiagnosticDumpRepository
import com.example.openruncoach.data.diagnostics.DumpPrivacyMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagnosticDumpUiState(
  val busy: Boolean = false,
  val awaitingDestination: Boolean = false,
  val showFullWarning: Boolean = false,
  val mode: DumpPrivacyMode = DumpPrivacyMode.SHARE_SAFE,
  val status: String? = null,
  val failed: Boolean = false,
)

class DiagnosticDumpViewModel(private val repository: DiagnosticDumpRepository) : ViewModel() {
  private val state = MutableStateFlow(DiagnosticDumpUiState())
  val uiState = state.asStateFlow()

  fun showFullWarning() {
    if (!state.value.busy) state.value = state.value.copy(showFullWarning = true)
  }

  fun dismissFullWarning() { state.value = state.value.copy(showFullWarning = false) }

  /** Reserve the export before launching SAF so repeated taps cannot start multiple pickers. */
  fun chooseDestination(mode: DumpPrivacyMode): String? {
    if (state.value.busy) return null
    state.value = DiagnosticDumpUiState(busy = true, awaitingDestination = true, mode = mode)
    return DiagnosticDumpRepository.suggestedFilename()
  }

  fun destinationChosen(uri: Uri?) {
    val pending = state.value
    if (!pending.awaitingDestination) return
    if (uri == null) {
      state.value = DiagnosticDumpUiState(status = "Export cancelled.")
      return
    }
    state.value = pending.copy(awaitingDestination = false, status = "Preparing dump…")
    viewModelScope.launch {
      try {
        val result = repository.export(uri, pending.mode) { progress -> state.value = state.value.copy(status = progress) }
        state.value = DiagnosticDumpUiState(status = "Saved diagnostic dump. ${result.exportedTableCount}/${result.applicationTableCount} tables exported." +
          if (result.optionalErrorCount > 0) " ${result.optionalErrorCount} optional sections failed; see errors.json." else "")
      } catch (error: CancellationException) {
        state.value = DiagnosticDumpUiState(status = "Export cancelled. Remove any unfinished destination file.")
        throw error
      } catch (_: Exception) {
        state.value = DiagnosticDumpUiState(failed = true, status = "Could not save the diagnostic dump. Check available space and destination access, then try again. Remove any unfinished destination file.")
      }
    }
  }
}
