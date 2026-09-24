package com.example.openruncoach.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.openruncoach.domain.metrics.PreparedRunChart
import com.example.openruncoach.domain.metrics.RunChartData
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.theme.LocalRunChartColors
import com.example.openruncoach.ui.common.formatPace
import com.example.openruncoach.ui.components.ItemSpacing
import kotlin.math.roundToInt

@Composable
fun RunChartsSection(metrics: RunMetrics, charts: RunChartData) {
  val colors = LocalRunChartColors.current
  Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
    charts.pace?.let { chart ->
      ChartSection(
        title = "Pace",
        subtitle = listOfNotNull(
          charts.averagePaceSecondsPerKm?.let { "Avg ${formatPace(it)}" },
          charts.fastestThirtySecondPaceSecondsPerKm?.let { "Fastest 30 sec ${formatPace(it)}" },
        ).joinToString(" · "),
        chart = chart,
        durationSeconds = charts.durationSeconds,
        color = colors.pace,
      )
    }
    charts.heartRate?.let { chart ->
      ChartSection(
        title = "Heart Rate",
        subtitle = listOfNotNull(
          metrics.averageHeartRate?.value?.roundToInt()?.let { "$it avg" },
          metrics.maxHeartRate?.value?.roundToInt()?.let { "$it max" },
        ).joinToString(" · "),
        chart = chart,
        durationSeconds = charts.durationSeconds,
        color = colors.heartRate,
      )
    }
    charts.cadence?.let { chart ->
      ChartSection(
        title = "Cadence",
        subtitle = listOfNotNull(
          metrics.movingAverageCadence?.value?.roundToInt()?.let { "$it spm moving avg" },
          metrics.maxCadence?.value?.roundToInt()?.let { "$it max" },
        ).joinToString(" · "),
        chart = chart,
        durationSeconds = charts.durationSeconds,
        color = colors.cadence,
      )
    }
  }
}

@Composable
private fun ChartSection(
  title: String,
  subtitle: String,
  chart: PreparedRunChart,
  durationSeconds: Long,
  color: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Column {
        Text(title.uppercase(), style = MaterialTheme.typography.titleMedium)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
      }
      chart.sourceDeviceName?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (chart.scale != null) {
      Text("min/km", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    LineChart(chart, durationSeconds, color)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      if (chart.scale != null) Spacer(Modifier.width(PaceAxisWidth))
      Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0:00", style = MaterialTheme.typography.labelSmall)
        Text(formatElapsed(durationSeconds / 2), style = MaterialTheme.typography.labelSmall)
        Text(formatElapsed(durationSeconds), style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

@Composable
private fun LineChart(
  chart: PreparedRunChart,
  durationSeconds: Long,
  lineColor: Color,
) {
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
  Row(
    Modifier.fillMaxWidth().height(132.dp)
      .background(surfaceColor, RoundedCornerShape(12.dp)).padding(10.dp)
  ) {
    chart.scale?.let { scale ->
      Column(
        Modifier.width(PaceAxisWidth).fillMaxHeight().padding(end = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        scale.tickValues.forEach { value ->
          Text(formatPaceAxis(value), style = MaterialTheme.typography.labelSmall)
        }
      }
    }
    Canvas(Modifier.weight(1f).fillMaxHeight()) {
      val points = chart.points
      if (points.isEmpty()) return@Canvas
      val scale = chart.scale
      val minimum = scale?.minimumValue ?: points.minOf { it.value }
      val maximum = scale?.maximumValue ?: points.maxOf { it.value }
      val range = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
      val totalSeconds = durationSeconds.coerceAtLeast(1).toDouble()
      val gridFractions = scale?.tickValues?.map(scale::verticalFraction) ?: listOf(0.0, 0.5, 1.0)
      gridFractions.forEach { fraction ->
        val y = fraction.toFloat() * size.height
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
      }
      scale?.referenceValue?.let { reference ->
        val y = scale.verticalFraction(reference).toFloat() * size.height
        drawLine(
          color = gridColor.copy(alpha = 0.8f),
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = 1.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
        )
      }
      chart.segments.forEach { segment ->
        if (segment.isEmpty()) return@forEach
        val path = Path()
        segment.forEachIndexed { index, point ->
          val x = (point.elapsedSeconds / totalSeconds).coerceIn(0.0, 1.0).toFloat() * size.width
          val yFraction = scale?.verticalFraction(point.value)
            ?: (1.0 - (point.value - minimum) / range).coerceIn(0.0, 1.0)
          val y = yFraction.toFloat() * size.height
          if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
      }
    }
  }
}

private fun formatElapsed(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun formatPaceAxis(secondsPerKm: Double): String {
  val seconds = secondsPerKm.roundToInt()
  return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private val PaceAxisWidth = 48.dp
