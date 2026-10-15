package com.example.openruncoach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.openruncoach.theme.OpenRunShapeTokens

/** Shared effort glyph; accompanying text supplies the accessible label. */
@Composable
fun WorkoutIntensitySignal(activeBars: Int, inactiveColor: Color = Color.White.copy(alpha = 0.24f)) {
  Row(Modifier.width(18.dp).height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.Bottom) {
    repeat(4) { index ->
      Box(Modifier.weight(1f).height((5 + index * 3).dp).background(
        if (index < activeBars) MaterialTheme.colorScheme.primary else inactiveColor,
        OpenRunShapeTokens.Compact))
    }
  }
}
