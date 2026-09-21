package com.example.openruncoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.openruncoach.theme.OpenRunCoachTheme

class PermissionsRationaleActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      OpenRunCoachTheme {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
          Text("Health Connect data")
          Text(
            "OpenRunCoach reads exercise sessions, heart rate, distance, and speed, including older " +
              "records when available. The optional Data Inspector can also read steps and cadence, " +
              "VO₂ max, elevation, power, and calories to show what a provider exposes. Imported runs " +
              "and feedback stay on this device; inspector samples and route points are not stored."
          )
        }
      }
    }
  }
}
