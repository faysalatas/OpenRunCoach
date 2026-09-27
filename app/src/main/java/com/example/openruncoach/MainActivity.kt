package com.example.openruncoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.openruncoach.theme.OpenRunCoachTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      val context = LocalContext.current
      val environmentFlow = remember(context) { AppEnvironmentController.environment(context) }
      val environment by environmentFlow.collectAsStateWithLifecycle()
      OpenRunCoachTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          key(environment.key) {
            CompositionLocalProvider(LocalOpenRunCoachEnvironment provides environment) { MainNavigation() }
          }
        }
      }
    }
  }
}
