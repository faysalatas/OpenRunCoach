package com.example.openruncoach.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.HealthConnectRepository
import com.example.openruncoach.data.RunRepository
import com.example.openruncoach.data.RunSettingsRepository
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import com.example.openruncoach.ui.components.ItemSpacing
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val viewModel: SettingsViewModel = viewModel {
    val healthConnectRepository = HealthConnectRepository(context)
    val settingsRepository = RunSettingsRepository(context)
    SettingsViewModel(
      healthConnectRepository,
      RunRepository(OpenRunCoachDatabase.getInstance(context).runDao(), healthConnectRepository, settingsRepository),
      settingsRepository,
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val permissionLauncher = rememberLauncherForActivityResult(
    PermissionController.createRequestPermissionResultContract()
  ) { viewModel.refreshStatus() }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshStatus() }

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    Text("Settings", style = MaterialTheme.typography.headlineMedium)
    SettingsCard("Preferred workout source") {
      Text("Automatic uses OpenRunCoach's canonical run selection rules.")
      Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
      ) {
        FilterChip(
          selected = state.preferredSourcePackage == null,
          onClick = { viewModel.selectPreferredSource(null) },
          label = { Text("Automatic") },
        )
        state.knownSources.forEach { source ->
          FilterChip(
            selected = state.preferredSourcePackage == source.packageName,
            onClick = { viewModel.selectPreferredSource(source.packageName) },
            label = { Text(source.appName) },
          )
        }
      }
      if (state.preferredSourceError) ErrorText("Could not update the preferred workout source.")
    }
    SettingsCard("Health Connect") {
      when {
        state.accessError -> ErrorText("Could not check Health Connect access.")
        state.isAvailable == null -> Text("Checking availability…")
        state.isAvailable == false -> Text("Health Connect unavailable")
        state.hasAllPermissions == true -> Text("Health Connect available · Permissions granted")
        else -> Text("Health Connect available · Permissions not granted")
      }
      if (state.isAvailable == true && state.hasAllPermissions == false) {
        Button(onClick = { permissionLauncher.launch(viewModel.requiredPermissions) }) { Text("Request permissions") }
      }
      Button(onClick = viewModel::syncRuns, enabled = state.syncState != SyncState.Syncing) { Text("Sync runs") }
      when (val sync = state.syncState) {
        SyncState.Idle -> Unit
        SyncState.Syncing -> Text("Syncing runs…")
        is SyncState.Success -> Text("Synced ${sync.count} runs.")
        SyncState.PermissionRequired -> ErrorText("Grant Health Connect permissions before syncing.")
        SyncState.Unavailable -> ErrorText("Health Connect is unavailable for syncing.")
        SyncState.Error -> ErrorText("Could not sync runs. Saved runs are unchanged.")
      }
    }
    SettingsCard("About") {
      Text("OpenRunCoach")
      Text("Version 1.0", style = MaterialTheme.typography.bodyMedium)
      Text("Health data stays on this device.", style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
  Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(ScreenPadding), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
      Text(title, style = MaterialTheme.typography.titleLarge)
      content()
    }
  }
}

@Composable
private fun ErrorText(message: String) = Text(message, color = MaterialTheme.colorScheme.error)
