package com.example.openruncoach.ui.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openruncoach.data.HealthConnectRepository

@Composable
fun HealthConnectScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val viewModel: HealthConnectViewModel =
    viewModel { HealthConnectViewModel(HealthConnectRepository(context)) }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val permissionLauncher =
    rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
      viewModel.refresh()
    }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

  Column(modifier = modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val currentState = state
    if (currentState == null) {
      Text("Checking Health Connect...")
      return@Column
    }
    Text(if (currentState.isAvailable) "Health Connect available" else "Health Connect unavailable")
    Text(if (currentState.hasAllPermissions) "Permissions granted" else "Permissions not granted")
    Button(
      onClick = { permissionLauncher.launch(viewModel.requiredPermissions) },
      enabled = currentState.isAvailable && !currentState.hasAllPermissions,
    ) {
      Text("Request permissions")
    }
  }
}
