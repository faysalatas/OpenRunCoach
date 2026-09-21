package com.example.openruncoach.ui.simulation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing

@Composable
fun SimulationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
  val viewModel: SimulationViewModel = viewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    TextButton(onClick = onBack) { Text("‹ Back") }
    Text("Coaching Simulator", style = MaterialTheme.typography.headlineMedium)
    Text("Uses fixed test data. Nothing on this screen is saved.")
    Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("Scenario", style = MaterialTheme.typography.titleMedium)
      Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
      ) {
        viewModel.scenarios.forEach { scenario ->
          FilterChip(
            selected = state.selectedScenario == scenario.key,
            onClick = { viewModel.selectScenario(scenario.key) },
            label = { Text(scenario.displayName) },
          )
        }
      }
    }
    Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Row(horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        OutlinedButton(onClick = viewModel::reset) { Text("Reset") }
        Button(onClick = viewModel::step) { Text("Step") }
      }
      Button(onClick = viewModel::runComplete, modifier = Modifier.fillMaxWidth()) {
        Text("Run complete simulation")
      }
    }
    state.result?.timeline?.take(state.visibleStepCount)?.forEachIndexed { index, step ->
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
          Text("${index + 1}. ${step.title}", style = MaterialTheme.typography.titleLarge)
          step.details.forEach { detail -> Text(detail) }
        }
      }
      if (index < state.visibleStepCount - 1) Text("↓", style = MaterialTheme.typography.headlineSmall)
    }
  }
}
