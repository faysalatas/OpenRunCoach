package com.example.openruncoach.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.openruncoach.AppEnvironmentController
import com.example.openruncoach.LocalOpenRunCoachEnvironment
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding

@Composable
fun DeveloperToolsSettings(onOpenSimulator: () -> Unit, onOpenWorkoutPreview: () -> Unit) {
  val context = LocalContext.current
  val environment = LocalOpenRunCoachEnvironment.current
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text("Developer Tools", style = MaterialTheme.typography.titleLarge)
      if (environment.isSimulation) {
        Text("SIMULATION MODE · isolated synthetic data")
      } else {
        Text("Load deterministic coaching inputs into an isolated debug database.")
      }
      Button(onClick = onOpenSimulator) { Text("Simulation") }
      Button(onClick = onOpenWorkoutPreview) { Text("Workout Model Preview") }
      if (environment.isSimulation) {
        OutlinedButton(onClick = { AppEnvironmentController.exitSimulation(context) }) {
          Text("Exit Simulation")
        }
      }
    }
  }
}
