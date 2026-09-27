package com.example.openruncoach.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.ui.components.EmptyState
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.RunHistoryItem
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing

@Composable
fun HistoryScreen(onRunClick: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  val viewModel: HistoryViewModel = viewModel {
    val settingsRepository = RunSettingsRepository(context)
    HistoryViewModel(
      RunRepository(
        environment.database.runDao(),
        HealthConnectRepository(context),
        settingsRepository,
        clock = environment.clock,
      ),
      environment.clock.zone,
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  LazyColumn(
    modifier = modifier.fillMaxSize().padding(horizontal = ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(ItemSpacing),
  ) {
    item { Text("Run History", modifier = Modifier.padding(top = ScreenPadding, bottom = ItemSpacing), style = MaterialTheme.typography.headlineMedium) }
    when {
      state.isLoading -> item { EmptyState("Loading", "Loading run history…") }
      state.errorMessage != null -> item { EmptyState("History unavailable", state.errorMessage!!) }
      state.groups.isEmpty() -> item { EmptyState("No runs yet", "Sync Health Connect from Settings to import your runs.") }
      else -> state.groups.forEach { group ->
        item(key = group.title) {
          Text(group.title, modifier = Modifier.padding(top = SectionSpacing, bottom = ItemSpacing), style = MaterialTheme.typography.titleLarge)
        }
        items(group.runs, key = { it.healthConnectRecordId }) { run ->
          RunHistoryItem(run, onClick = { onRunClick(run.healthConnectRecordId) })
        }
      }
    }
    item { Column(Modifier.padding(bottom = ScreenPadding)) {} }
  }
}
