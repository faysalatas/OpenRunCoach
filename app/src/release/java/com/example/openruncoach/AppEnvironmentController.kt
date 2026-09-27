package com.example.openruncoach

import android.content.Context
import com.example.openruncoach.data.local.OpenRunCoachDatabase
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppEnvironmentController {
  private var environment: StateFlow<OpenRunCoachEnvironment>? = null

  fun environment(context: Context): StateFlow<OpenRunCoachEnvironment> = synchronized(this) {
    environment ?: MutableStateFlow(
      OpenRunCoachEnvironment(
        key = "production",
        database = OpenRunCoachDatabase.getInstance(context),
        clock = Clock.systemDefaultZone(),
        isSimulation = false,
      )
    ).also { environment = it }
  }
}
