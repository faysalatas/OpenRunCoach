package com.example.openruncoach.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.data.diagnostics.DiagnosticDumpRepository
import com.example.openruncoach.data.diagnostics.DumpPrivacyMode
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding

@Composable
internal fun DiagnosticDumpSettings() {
  val environment = LocalOpenRunCoachEnvironment.current
  val context = LocalContext.current.applicationContext
  val model: DiagnosticDumpViewModel = viewModel(key = "diagnostic-dump-${environment.key}") {
    DiagnosticDumpViewModel(DiagnosticDumpRepository(context, environment))
  }
  val state by model.uiState.collectAsStateWithLifecycle()
  val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), model::destinationChosen)

  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("Advanced / Diagnostics", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("Diagnostic dump", style = MaterialTheme.typography.titleLarge)
      Text("Export OpenRunCoach data and derived state for debugging.")
      Text("Share-safe uses relative route coordinates. The archive includes health data and notes. Nothing is uploaded automatically.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Button(enabled = !state.busy, onClick = {
        model.chooseDestination(DumpPrivacyMode.SHARE_SAFE)?.let { destination.launch(it) }
      }) { Text("Export share-safe dump") }
      TextButton(enabled = !state.busy, onClick = model::showFullWarning) { Text("Export full dump") }
      if (state.busy && !state.awaitingDestination) LinearProgressIndicator(Modifier.fillMaxWidth())
      state.status?.let { message ->
        Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
          color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
  if (state.showFullWarning) {
    AlertDialog(
      onDismissRequest = model::dismissFullWarning,
      title = { Text("Export full dump?") },
      text = { Text("Exact GPS coordinates can reveal home, work and frequently visited places.") },
      dismissButton = { TextButton(onClick = model::dismissFullWarning) { Text("Cancel") } },
      confirmButton = { TextButton(onClick = {
        model.chooseDestination(DumpPrivacyMode.FULL)?.let { destination.launch(it) }
      }) { Text("Export full dump") } },
    )
  }
}
