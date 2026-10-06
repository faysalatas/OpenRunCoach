package com.example.openruncoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.openruncoach.domain.route.RunRoutePoint
import com.example.openruncoach.theme.OpenRunColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor

enum class RunRouteCanvasStyle { COMPACT, DETAIL }

/** Provider-independent route silhouette shared by History and completed Run Detail. */
@Composable
fun RunRouteCanvas(
  points: List<RunRoutePoint>,
  modifier: Modifier = Modifier,
  style: RunRouteCanvasStyle = RunRouteCanvasStyle.DETAIL,
) {
  val pointLimit = if (style == RunRouteCanvasStyle.COMPACT) 180 else 2_000
  val geometry = remember(points, pointLimit) { prepareRouteGeometry(points, pointLimit) }
  if (geometry.size < 2) return
  val background = if (style == RunRouteCanvasStyle.COMPACT) {
    OpenRunColors.Surface
  } else {
    OpenRunColors.SurfaceElevated
  }
  val shape = RoundedCornerShape(if (style == RunRouteCanvasStyle.COMPACT) 12.dp else 18.dp)
  Canvas(
    modifier
      .clip(shape)
      .background(background)
      .semantics { contentDescription = "Recorded route" }
  ) {
    val padding = if (style == RunRouteCanvasStyle.COMPACT) 10.dp.toPx() else 24.dp.toPx()
    val availableWidth = (size.width - padding * 2f).coerceAtLeast(1f)
    val availableHeight = (size.height - padding * 2f).coerceAtLeast(1f)
    val minX = geometry.minOf(RouteGeometryPoint::x)
    val maxX = geometry.maxOf(RouteGeometryPoint::x)
    val minY = geometry.minOf(RouteGeometryPoint::y)
    val maxY = geometry.maxOf(RouteGeometryPoint::y)
    val rangeX = (maxX - minX).takeIf { it > 0.0 } ?: 1.0
    val rangeY = (maxY - minY).takeIf { it > 0.0 } ?: 1.0
    val scale = minOf(availableWidth.toDouble() / rangeX, availableHeight.toDouble() / rangeY)
    val drawnWidth = rangeX * scale
    val drawnHeight = rangeY * scale
    val left = (size.width - drawnWidth.toFloat()) / 2f
    val top = (size.height - drawnHeight.toFloat()) / 2f

    if (style == RunRouteCanvasStyle.DETAIL) {
      val gridColor = OpenRunColors.BorderSubtle.copy(alpha = 0.22f)
      for (index in 1..3) {
        val x = size.width * index / 4f
        val y = size.height * index / 4f
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
      }
    }

    fun project(point: RouteGeometryPoint) = Offset(
      x = left + ((point.x - minX) * scale).toFloat(),
      y = top + drawnHeight.toFloat() - ((point.y - minY) * scale).toFloat(),
    )

    val path = Path()
    geometry.forEachIndexed { index, point ->
      val offset = project(point)
      if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
    }
    val routeWidth = if (style == RunRouteCanvasStyle.COMPACT) 2.dp.toPx() else 3.dp.toPx()
    drawPath(path, OpenRunColors.Accent, style = Stroke(routeWidth, cap = StrokeCap.Round))

    val start = project(geometry.first())
    val finish = project(geometry.last())
    val markerRadius = if (style == RunRouteCanvasStyle.COMPACT) 3.dp.toPx() else 5.dp.toPx()
    drawCircle(OpenRunColors.Accent, markerRadius, start)
    drawCircle(background, markerRadius * 1.45f, finish)
    drawCircle(
      OpenRunColors.Accent,
      markerRadius * 1.2f,
      finish,
      style = Stroke(if (style == RunRouteCanvasStyle.COMPACT) 1.dp.toPx() else 2.dp.toPx()),
    )
  }
}

internal data class RouteGeometryPoint(val x: Double, val y: Double)

internal fun prepareRouteGeometry(points: List<RunRoutePoint>, pointLimit: Int): List<RouteGeometryPoint> {
  val valid = points.filter { it.latitude.isFinite() && it.longitude.isFinite() }
  if (valid.size < 2) return emptyList()
  val selected = downsampleRoute(valid, pointLimit.coerceAtLeast(2))
  val meanLatitudeRadians = Math.toRadians(valid.map(RunRoutePoint::latitude).average())
  val longitudeScale = cos(meanLatitudeRadians).coerceAtLeast(0.01)
  return selected.map { RouteGeometryPoint(it.longitude * longitudeScale, it.latitude) }
}

/** Deterministic local-deviation sampling that retains endpoints and major bends. */
private fun downsampleRoute(points: List<RunRoutePoint>, limit: Int): List<RunRoutePoint> {
  if (points.size <= limit) return points
  val result = ArrayList<RunRoutePoint>(limit)
  result += points.first()
  val bucketSize = (points.size - 2).toDouble() / (limit - 2)
  repeat(limit - 2) { bucket ->
    val start = (floor(bucket * bucketSize).toInt() + 1).coerceAtMost(points.lastIndex - 1)
    val endExclusive = (floor((bucket + 1) * bucketSize).toInt() + 1)
      .coerceIn(start + 1, points.lastIndex)
    val anchor = result.last()
    val next = points[endExclusive]
    result += points.subList(start, endExclusive).maxByOrNull { point ->
      perpendicularDistance(point, anchor, next)
    } ?: points[start]
  }
  result += points.last()
  return result
}

private fun perpendicularDistance(
  point: RunRoutePoint,
  start: RunRoutePoint,
  end: RunRoutePoint,
): Double {
  val dx = end.longitude - start.longitude
  val dy = end.latitude - start.latitude
  if (abs(dx) + abs(dy) < 1e-12) {
    return abs(point.longitude - start.longitude) + abs(point.latitude - start.latitude)
  }
  return abs(
    dy * point.longitude - dx * point.latitude + end.longitude * start.latitude - end.latitude * start.longitude
  ) / kotlin.math.sqrt(dx * dx + dy * dy)
}
