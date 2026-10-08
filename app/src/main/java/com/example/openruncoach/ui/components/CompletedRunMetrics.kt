package com.example.openruncoach.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CompletedRunMetric(val value: String, val label: String, @DrawableRes val icon: Int)

/** Always one equal-column row; scroll only when accessible text needs more width. */
@Composable
fun CompletedRunMetrics(items: List<CompletedRunMetric>) {
  if (items.isEmpty()) return
  OpenRunCard(contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
    BoxWithConstraints {
      val density = LocalDensity.current
      val measurer = rememberTextMeasurer()
      val valueStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp,
        lineHeight = 22.sp, fontFeatureSettings = "tnum")
      val labelStyle = MaterialTheme.typography.labelSmall
      val minimumColumnWidth = remember(items, density, measurer, valueStyle, labelStyle) {
        val widestText = items.maxOf { metric -> maxOf(
          measurer.measure(metric.value, valueStyle, maxLines = 1).size.width,
          measurer.measure(metric.label, labelStyle, maxLines = 1).size.width,
        ) }
        with(density) { widestText.toDp() } + 6.dp
      }
      val columnWidth = maxOf((maxWidth - (items.size - 1).dp) / items.size, minimumColumnWidth)
      Row(Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Min)) {
        items.forEachIndexed { index, metric ->
          if (index > 0) VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
          Column(Modifier.width(columnWidth).padding(horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(painterResource(metric.icon), contentDescription = null, modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(metric.value, style = valueStyle, maxLines = 1)
            Text(metric.label, style = labelStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
          }
        }
      }
    }
  }
}
