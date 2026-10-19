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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.openruncoach.theme.OpenRunCoachTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Explicit recovery on resume/environment changes and local day rollover, never recomposition.
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        AppEnvironmentController.environment(applicationContext).collectLatest { environment ->
          var reconciledDay: java.time.LocalDate? = null
          while (true) {
            val today = java.time.LocalDate.now(environment.clock)
            if (today != reconciledDay) {
              try {
                withContext(Dispatchers.IO) { environment.postRunReconciler().reconcileAll() }
                reconciledDay = today
              } catch (cancelled: CancellationException) {
                throw cancelled
              } catch (_: Exception) {
                android.util.Log.e("PostRunReconciliation", "Resume/day reconciliation failed; retry while active or on next input update.")
              }
            }
            delay(60_000)
          }
        }
      }
    }

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
