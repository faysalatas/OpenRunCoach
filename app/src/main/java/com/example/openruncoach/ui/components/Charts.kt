package com.example.openruncoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.openruncoach.theme.LocalOpenRunPalette
import com.example.openruncoach.theme.OpenRunType

/** Presentation values supplied by callers; no plan generation or provider models. */
enum class BarState { Done, Current, Planned, TodayPlanned, Rest, Goal }
data class ChartBar(val label: String, val value: Float, val valueLabel: String? = null, val state: BarState)
data class ChartPoint(val value: Float?, val label: String? = null)
data class SplitBar(val label: String, val speed: Float, val valueLabel: String)

@Composable
fun WeekBars(bars: List<ChartBar>, contentDescription: String, modifier: Modifier = Modifier, animate: Boolean = true) {
  BarChart(bars, contentDescription, modifier, week = true, animate = animate)
}

@Composable
fun LongRunBars(bars: List<ChartBar>, contentDescription: String, modifier: Modifier = Modifier, animate: Boolean = true) {
  BarChart(bars, contentDescription, modifier, animate = animate)
}

@Composable
fun WeeklyDistanceBars(bars: List<ChartBar>, contentDescription: String, modifier: Modifier = Modifier, animate: Boolean = true) {
  BarChart(bars, contentDescription, modifier, weeklyDistance = true, animate = animate)
}

private fun Modifier.chartDescription(description: String) = clearAndSetSemantics {
  contentDescription = description
}

@Composable
private fun BarChart(bars: List<ChartBar>, description: String, modifier: Modifier,
  week: Boolean = false, weeklyDistance: Boolean = false, animate: Boolean,
) {
  if (bars.isEmpty()) return
  val p = LocalOpenRunPalette.current
  val text = rememberTextMeasurer()
  val fontScale = LocalDensity.current.fontScale
  val reveal = chartReveal(animate)
  val labels = OpenRunType.caption
  // Section 3.11 explicitly specifies 11 sp for the eight-column value labels.
  val values = OpenRunType.caption.copy(fontSize = if (weeklyDistance) 11.sp else 12.sp, fontWeight = FontWeight.SemiBold)
  BoxWithConstraints(modifier.fillMaxWidth().chartDescription(description)) {
    val width = maxOf(maxWidth, (bars.size * 38).dp * fontScale)
    Box(Modifier.horizontalScroll(rememberScrollState())) {
      Canvas(Modifier.width(width).height((if (week) 126.dp else 174.dp) + 30.dp * (fontScale - 1f))) {
        val gap = 8.dp.toPx()
        val col = (size.width - gap * (bars.size - 1)) / bars.size
        val top = if (week) 4.dp.toPx() else 24.dp.toPx() * fontScale
        val baseline = size.height - 28.dp.toPx() * fontScale
        val max = bars.maxOf { if (it.value.isFinite()) it.value.coerceAtLeast(0f) else 0f }.takeIf { it > 0f } ?: 1f
        bars.forEachIndexed { i, b ->
          if (!b.value.isFinite() || b.value < 0f) return@forEachIndexed
          val h = ((baseline - top) * b.value / max * reveal).coerceAtLeast(0f)
          val x = i * (col + gap)
          val y = baseline - h
          val active = b.state == BarState.Current || b.state == BarState.TodayPlanned
          when (b.state) {
            BarState.Rest -> drawLine(p.line, Offset(x, baseline), Offset(x + col, baseline), 3.dp.toPx())
            BarState.Done, BarState.Current -> if (h > 0f) drawRoundRect(if (active) p.accent else p.ink,
              Offset(x, y), Size(col, h), CornerRadius(6.dp.toPx()))
            BarState.Planned, BarState.TodayPlanned, BarState.Goal -> if (h > 0f) {
              val inset = 1.dp.toPx()
              val location = Offset(x + inset, y + inset)
              val dimensions = Size((col - inset * 2).coerceAtLeast(0f), (h - inset * 2).coerceAtLeast(0f))
              if (b.state == BarState.Goal) drawRoundRect(p.line, location, dimensions, CornerRadius(6.dp.toPx()))
              drawRoundRect(when (b.state) { BarState.Goal -> p.ink; BarState.TodayPlanned -> p.accent; else -> p.dash },
                location, dimensions, CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx(),
                  pathEffect = if (b.state == BarState.Planned) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null))
            }
          }
          if (!week && b.valueLabel != null) drawCentered(text, b.valueLabel, values,
            if (active) p.accentText else p.muted, x + col / 2, (y - 22.dp.toPx() * fontScale).coerceAtLeast(0f))
          drawCentered(text, b.label, if (active) labels.copy(fontWeight = FontWeight.SemiBold) else labels,
            if (active) p.accentText else p.muted, x + col / 2, baseline + 8.dp.toPx())
        }
      }
    }
  }
}

private fun DrawScope.drawCentered(measurer: TextMeasurer, label: String, style: TextStyle,
  color: Color, x: Float, y: Float,
) {
  val measured = measurer.measure(label, style.copy(color = color))
  drawText(measured, topLeft = Offset((x - measured.size.width / 2f).coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f)), y))
}

@Composable
fun PaceLineChart(points: List<ChartPoint>, contentDescription: String, modifier: Modifier = Modifier,
  targetRange: ClosedFloatingPointRange<Float>? = null, good: Boolean = false,
) {
  LineChart(points, contentDescription, modifier, fasterIsHigher = true, targetRange = targetRange, good = good)
}

@Composable
fun HeartRateChart(points: List<ChartPoint>, contentDescription: String, modifier: Modifier = Modifier) {
  LineChart(points, contentDescription, modifier, heart = true)
}

@Composable
fun Sparkline(points: List<ChartPoint>, contentDescription: String, modifier: Modifier = Modifier, good: Boolean = true) {
  LineChart(points, contentDescription, modifier, sparkline = true, good = good)
}

/** Null/nonfinite samples break paths; missing evidence never becomes an interpolated line. */
@Composable
private fun LineChart(points: List<ChartPoint>, description: String, modifier: Modifier,
  fasterIsHigher: Boolean = false, targetRange: ClosedFloatingPointRange<Float>? = null,
  heart: Boolean = false, sparkline: Boolean = false, good: Boolean = false,
) {
  val valid = points.mapNotNull { it.value?.takeIf(Float::isFinite) }
  if (valid.isEmpty()) return
  val band = targetRange?.takeIf { it.start.isFinite() && it.endInclusive.isFinite() && it.start <= it.endInclusive }
  val lastValid = points.indexOfLast { it.value?.isFinite() == true }
  val low = minOf(valid.min(), band?.start ?: valid.min())
  val high = maxOf(valid.max(), band?.endInclusive ?: valid.max())
  val range = (high - low).takeIf { it > 0f } ?: 1f
  val p = LocalOpenRunPalette.current
  val text = rememberTextMeasurer()
  val fontScale = LocalDensity.current.fontScale
  val style = OpenRunType.caption
  val hasLabels = !sparkline && points.any { !it.label.isNullOrBlank() }
  val line = when { heart -> p.heart; good -> p.good; sparkline -> p.muted; else -> p.accent }
  val sizing = if (sparkline) Modifier.size(120.dp, 30.dp) else Modifier.fillMaxWidth().height(140.dp + if (hasLabels) 25.dp * fontScale else 0.dp)
  BoxWithConstraints(modifier.chartDescription(description)) {
    val width = if (sparkline) 120.dp else maxOf(maxWidth, (points.count { it.label != null } * 50).dp * fontScale)
    Box(Modifier.horizontalScroll(rememberScrollState())) {
      Canvas(sizing.then(Modifier.width(width))) {
        val inset = 6.dp.toPx()
        val bottom = size.height - inset - if (hasLabels) 25.dp.toPx() * fontScale else 0f
        val h = (bottom - inset).coerceAtLeast(1f)
        fun y(v: Float): Float {
          if (high == low) return inset + h / 2
          val fraction = (v - low) / range
          return inset + h * if (fasterIsHigher) fraction else 1 - fraction
        }
        if (!sparkline) repeat(3) { i ->
          val gy = inset + h * i / 2
          drawLine(p.line.copy(alpha = .65f), Offset(inset, gy), Offset(size.width - inset, gy), 1.dp.toPx())
        }
        if (band != null) {
          val y1 = y(band.start); val y2 = y(band.endInclusive)
          drawRoundRect(p.band, Offset(inset, minOf(y1, y2)), Size(size.width - inset * 2, kotlin.math.abs(y2 - y1)), CornerRadius(6.dp.toPx()))
        }
        var previous: Offset? = null
        points.forEachIndexed { index, point ->
          val value = point.value?.takeIf(Float::isFinite)
          if (value == null) { previous = null; return@forEachIndexed }
          val x = if (points.size == 1) size.width / 2 else inset + (size.width - inset * 2) * index / (points.size - 1)
          val location = Offset(x, y(value))
          previous?.let { drawLine(line, it, location, 3.dp.toPx(), cap = StrokeCap.Round) }
          if (sparkline && index == lastValid) drawCircle(line, 4.dp.toPx(), location)
          if (!sparkline && !heart) drawCircle(line, 5.dp.toPx(), location)
          if (points.size == 1 && (heart || sparkline)) drawCircle(line, 4.dp.toPx(), location)
          if (hasLabels && point.label != null) drawCentered(text, point.label, style, p.muted, x, bottom + 8.dp.toPx())
          previous = location
        }
      }
    }
  }
}

@Composable
fun SplitBars(splits: List<SplitBar>, contentDescription: String, modifier: Modifier = Modifier) {
  val valid = splits.filter { it.speed.isFinite() && it.speed > 0f }
  if (valid.isEmpty()) return
  val p = LocalOpenRunPalette.current
  val text = rememberTextMeasurer()
  val fontScale = LocalDensity.current.fontScale
  val rowHeight = 32.dp * fontScale
  val style = OpenRunType.caption
  Canvas(modifier.fillMaxWidth().height(rowHeight * valid.size).chartDescription(contentDescription)) {
    val labelWidth = 28.dp.toPx() * fontScale
    val valueWidth = 40.dp.toPx() * fontScale
    val gap = 8.dp.toPx()
    val available = (size.width - labelWidth - valueWidth - gap * 2).coerceAtLeast(0f)
    val max = valid.maxOf { it.speed }
    valid.forEachIndexed { index, split ->
      val cy = rowHeight.toPx() * (index + .5f)
      val label = text.measure(split.label, style.copy(color = p.muted))
      val value = text.measure(split.valueLabel, style.copy(color = p.ink, fontWeight = FontWeight.SemiBold))
      drawText(label, topLeft = Offset(0f, cy - label.size.height / 2))
      drawRoundRect(if (index == valid.lastIndex) p.accent else p.ink,
        Offset(labelWidth + gap, cy - 6.dp.toPx()), Size(available * splitBarWidthFraction(split.speed, max), 12.dp.toPx()), CornerRadius(6.dp.toPx()))
      drawText(value, topLeft = Offset(size.width - value.size.width, cy - value.size.height / 2))
    }
  }
}
