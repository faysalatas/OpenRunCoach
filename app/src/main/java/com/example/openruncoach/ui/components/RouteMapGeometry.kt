package com.example.openruncoach.ui.components

import com.example.openruncoach.domain.route.RunRoutePoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

internal data class MapCoordinate(val latitude: Double, val longitude: Double)
internal data class RouteMapGeometry(
  val coordinates: List<MapCoordinate>,
  val center: MapCoordinate,
  val spanX: Double,
  val spanY: Double,
)
internal data class RouteMapCamera(val center: MapCoordinate, val zoom: Double)

/** Normalized Web Mercator camera bounds only; MapLibre projects the actual route. */
internal fun prepareRouteMapGeometry(points: List<RunRoutePoint>): RouteMapGeometry? {
  val valid = points.filter { it.latitude.isFinite() && it.latitude in -85.05112878..85.05112878 &&
    it.longitude.isFinite() && it.longitude in -180.0..180.0 }
  if (valid.size < 2) return null
  val originLongitude = valid.first().longitude
  fun unwrappedLongitude(longitude: Double) = originLongitude + ((longitude - originLongitude + 540.0) % 360.0) - 180.0
  val xs = valid.map { (unwrappedLongitude(it.longitude) + 180.0) / 360.0 }
  val ys = valid.map { (1.0 - ln(tan(PI / 4 + Math.toRadians(it.latitude) / 2)) / PI) / 2 }
  val west = xs.min()
  val east = xs.max()
  val north = ys.min()
  val south = ys.max()
  val centerLongitude = (((west + east) / 2 * 360.0 - 180.0 + 540.0) % 360.0) - 180.0
  val centerLatitude = Math.toDegrees(atan(sinh(PI * (1 - north - south))))
  val coordinates = if (valid.size <= 2_000) valid else (0 until 2_000).map {
    valid[(it.toLong() * valid.lastIndex / 1_999).toInt()]
  }
  return RouteMapGeometry(coordinates.map { MapCoordinate(it.latitude, it.longitude) },
    MapCoordinate(centerLatitude, centerLongitude), east - west, south - north)
}

/** One fit on size/route change. Degenerate paths get a small but finite viewport. */
internal fun fitRouteMapCamera(geometry: RouteMapGeometry, width: Double, height: Double, padding: Double): RouteMapCamera? {
  if (width <= padding * 2 || height <= padding * 2) return null
  val xScale = (width - padding * 2) / (geometry.spanX.coerceAtLeast(0.000001) * 512.0)
  val yScale = (height - padding * 2) / (geometry.spanY.coerceAtLeast(0.000001) * 512.0)
  val zoom = (ln(minOf(xScale, yScale)) / ln(2.0)).coerceIn(0.0, 18.0)
  return RouteMapCamera(geometry.center, zoom)
}
