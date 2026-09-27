package com.example.openruncoach.ui.simulation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun DeveloperSimulationDestination(onBack: () -> Unit) {
  LaunchedEffect(Unit) { onBack() }
}
