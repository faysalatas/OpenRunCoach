package com.example.openruncoach.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.openruncoach.ui.components.EmptyState
import com.example.openruncoach.ui.components.ScreenPadding
import com.example.openruncoach.ui.components.SectionSpacing

@Composable
fun PlanScreen(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().padding(ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
  ) {
    Text("Plan", style = MaterialTheme.typography.headlineMedium)
    EmptyState("Training plan", "Your adaptive training plan will appear here in a future milestone.")
  }
}
