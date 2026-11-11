package com.example.openruncoach.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import com.example.openruncoach.theme.*
import com.example.openruncoach.ui.components.OpenRunCard
import com.example.openruncoach.ui.components.OpenRunDisclosure
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.openruncoach.domain.metrics.PreparedRunChart
import com.example.openruncoach.domain.metrics.RunChartData
import com.example.openruncoach.domain.metrics.RunMetrics
import com.example.openruncoach.domain.workout.PaceTarget
import com.example.openruncoach.theme.LocalRunChartColors
import com.example.openruncoach.ui.common.formatPace
import kotlin.math.roundToInt

@Composable
fun RunChartsSection(
  metrics: RunMetrics,
  charts: RunChartData?,
  paceTarget: PaceTarget? = null,
  chartHeight: Dp = 108.dp,
) {
  val p = LocalOpenRunPalette.current
  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    charts?.pace?.takeIf(::hasDrawableSeries)?.let { chart -> OpenRunCard {
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Pace", style = OpenRunType.title)
        charts?.averagePaceSecondsPerKm?.let { Text("avg ${paceClock(it)} /km", style = OpenRunType.meta, color = p.muted) }
      }
      ChartSection("Pace", "min/km", chart, (charts?.durationSeconds ?: metrics.durationSeconds.value), p.accent, chartHeight, paceTarget)
      Text(if (paceTarget != null) "Shaded: target range. Higher means faster." else "Higher means faster.", style = OpenRunType.meta, color = p.muted)
      paceTarget?.let { Text("Target ${paceClock(it.minimumSecondsPerKm)}–${paceClock(it.maximumSecondsPerKm)} /km", style = OpenRunType.meta, color = p.muted) }
    } }
    val hrChart = charts?.heartRate?.takeIf(::hasDrawableSeries)
    if (hrChart != null || metrics.averageHeartRate != null || metrics.maxHeartRate != null) OpenRunCard {
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Heart rate", style = OpenRunType.title)
        val hr = listOfNotNull(metrics.averageHeartRate?.value?.let { "avg ${it.roundToInt()}" }, metrics.maxHeartRate?.value?.let { "max ${it.roundToInt()}" }).joinToString(" · ")
        if (hr.isNotBlank()) Text("$hr bpm", style = OpenRunType.meta, color = p.muted)
      }
      hrChart?.let { ChartSection("Heart rate", "bpm", it, (charts?.durationSeconds ?: metrics.durationSeconds.value), p.heart, chartHeight) }
    }
    val cadence = charts?.cadence?.takeIf(::hasDrawableSeries)
    if (cadence != null || metrics.averageCadence != null || metrics.movingAverageCadence != null || metrics.maxCadence != null) OpenRunCard {
      Text("Cadence", style = OpenRunType.title)
      val values = listOfNotNull(metrics.movingAverageCadence?.value?.let { "${it.roundToInt()} spm moving avg" },
        metrics.averageCadence?.value?.let { "${it.roundToInt()} spm session avg" }, metrics.maxCadence?.value?.let { "${it.roundToInt()} spm max" }).joinToString(" · ")
      if (values.isNotBlank()) Text(values, style = OpenRunType.meta, color = p.muted)
      cadence?.let { OpenRunDisclosure("Cadence chart") { ChartSection("Cadence", "spm", it, (charts?.durationSeconds ?: metrics.durationSeconds.value), p.accent, chartHeight) } }
    }
  }
}

/** Shared compact two-column presentation; unknown values stay unknown, never zero. */
@Composable
internal fun RunComparisonSummary(
  firstValue: String?,
  firstLabel: String,
  secondValue: String?,
  secondLabel: String,
  accentSecond: Boolean = false,
  targetKey: Boolean = false,
) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceAtLeast(1f)
    val valueStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp,
      lineHeight = 22.sp, fontFeatureSettings = "tnum")
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val minimumColumnWidth = remember(firstValue, secondValue, firstLabel, secondLabel, targetKey, valueStyle, labelStyle, density, measurer) {
      val textWidth = maxOf(
        measurer.measure(firstValue ?: "—", valueStyle, maxLines = 1).size.width,
        measurer.measure(secondValue ?: "—", valueStyle, maxLines = 1).size.width,
        measurer.measure(firstLabel, labelStyle, maxLines = 1).size.width,
        measurer.measure(secondLabel, labelStyle, maxLines = 1).size.width +
          with(density) { (if (targetKey) 22.dp else 0.dp).roundToPx() },
      )
      with(density) { textWidth.toDp() } + 4.dp
    }
    val columnWidth = maxOf((maxWidth - 12.dp) / 2, minimumColumnWidth)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Column(Modifier.width(columnWidth).heightIn(min = 44.dp * fontScale), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(firstValue ?: "—", style = valueStyle, maxLines = 1)
        Text(firstLabel, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Column(Modifier.width(columnWidth).heightIn(min = 44.dp * fontScale),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (secondValue != null) {
          Text(secondValue, style = valueStyle, maxLines = 1,
            color = if (accentSecond) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (targetKey) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Box(Modifier.width(14.dp).height(3.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), RoundedCornerShape(2.dp)))
              Box(Modifier.width(14.dp).height(3.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
            }
            Text(secondLabel, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
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
  height: Dp,
  targetRange: PaceTarget? = null,
) {
  val axisWidth = PaceAxisWidth * LocalDensity.current.fontScale.coerceAtLeast(1f)
  Column(Modifier.semantics { contentDescription = "$title ($subtitle) over elapsed time" },
    verticalArrangement = Arrangement.spacedBy(4.dp)) {
    LineChart(chart, durationSeconds, color, height, axisWidth, targetRange)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Spacer(Modifier.width(axisWidth))
      Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("0:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatElapsed(durationSeconds / 2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatElapsed(durationSeconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun LineChart(
  chart: PreparedRunChart,
  durationSeconds: Long,
  lineColor: Color,
  height: Dp,
  axisWidth: Dp,
  targetRange: PaceTarget? = null,
) {
  val gridColor = LocalOpenRunPalette.current.line
  val referenceColor = LocalOpenRunPalette.current.muted
  val palette = LocalOpenRunPalette.current
  val surfaceColor = palette.card
  val points = remember(chart) { chart.points }
  val bounds = remember(chart) {
    val minimum = points.minOfOrNull { it.value } ?: 0.0
    val maximum = points.maxOfOrNull { it.value } ?: minimum
    val padding = maxOf(5.0, (maximum - minimum) * 0.1)
    (minimum - padding).coerceAtLeast(0.0) to maximum + padding
  }
  val scale = remember(chart, targetRange) {
    chart.scale?.let { original ->
      // Display-only extension keeps a legitimate target visible without changing samples.
      val lower = minOf(original.minimumValue, targetRange?.minimumSecondsPerKm ?: original.minimumValue)
      val upper = maxOf(original.maximumValue, targetRange?.maximumSecondsPerKm ?: original.maximumValue)
      original.copy(minimumValue = lower, maximumValue = upper,
        tickValues = listOf(lower, (lower + upper) / 2, upper))
    }
  }
  val minimum = scale?.minimumValue ?: bounds.first
  val maximum = scale?.maximumValue ?: bounds.second
  val range = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
  val ticks = scale?.tickValues ?: listOf(maximum, (minimum + maximum) / 2, minimum)
  Row(
    Modifier.fillMaxWidth().height(height)
      .background(surfaceColor, RoundedCornerShape(12.dp)).padding(2.dp)
  ) {
    Column(
      Modifier.width(axisWidth).fillMaxHeight().padding(end = 6.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      ticks.forEach { value ->
        Text(if (scale != null) formatPaceAxis(value) else value.roundToInt().toString(),
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Spacer(Modifier.weight(1f).fillMaxHeight().drawWithCache {
      val totalSeconds = durationSeconds.coerceAtLeast(1).toDouble()
      val gridFractions = scale?.tickValues?.map(scale::verticalFraction) ?: listOf(0.0, 0.5, 1.0)
      val paths = chart.segments.filter { it.size >= 2 }.map { segment ->
        val line = Path().apply {
          segment.forEachIndexed { index, point ->
            val x = (point.elapsedSeconds / totalSeconds).coerceIn(0.0, 1.0).toFloat() * size.width
            val fraction = scale?.verticalFraction(point.value)
              ?: (1.0 - (point.value - minimum) / range).coerceIn(0.0, 1.0)
            val y = fraction.toFloat() * size.height
            if (index == 0) moveTo(x, y) else lineTo(x, y)
          }
        }
        val area = Path().apply {
          addPath(line)
          val endX = (segment.last().elapsedSeconds / totalSeconds).coerceIn(0.0, 1.0).toFloat() * size.width
          val startX = (segment.first().elapsedSeconds / totalSeconds).coerceIn(0.0, 1.0).toFloat() * size.width
          lineTo(endX, size.height)
          lineTo(startX, size.height)
          close()
        }
        line to area
      }
      val fill = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.14f), Color.Transparent), endY = size.height)
      val lineStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
      val dash = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
      onDrawBehind {
        gridFractions.forEach { fraction ->
          val y = fraction.toFloat() * size.height
          drawLine(gridColor.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
        }
        if (scale != null && targetRange != null) {
          val firstY = scale.verticalFraction(targetRange.minimumSecondsPerKm).toFloat() * size.height
          val secondY = scale.verticalFraction(targetRange.maximumSecondsPerKm).toFloat() * size.height
          val top = minOf(firstY, secondY).coerceIn(0f, size.height)
          val bottom = maxOf(firstY, secondY).coerceIn(0f, size.height)
          if (bottom > top) drawRect(
            color = palette.band,
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(size.width, bottom - top),
          )
        }
        scale?.referenceValue?.let { reference ->
          val y = scale.verticalFraction(reference).toFloat() * size.height
          drawLine(
            color = referenceColor.copy(alpha = 0.4f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 0.75.dp.toPx(),
            pathEffect = dash,
          )
        }
        paths.forEach { (line, area) ->
          // No area fill: recorded line and optional target band only.
          drawPath(line, lineColor, style = lineStroke)
        }
      }
    })
  }
}

private fun formatElapsed(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun formatPaceAxis(secondsPerKm: Double): String {
  val seconds = secondsPerKm.roundToInt()
  return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun paceClock(secondsPerKm: Double): String {
  val seconds = secondsPerKm.roundToInt()
  return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private val PaceAxisWidth = 40.dp

private fun hasDrawableSeries(chart: PreparedRunChart): Boolean = chart.segments.any {
  it.size >= 2 && it.last().elapsedSeconds > it.first().elapsedSeconds
}
