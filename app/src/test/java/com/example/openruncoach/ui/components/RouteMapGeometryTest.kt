package com.example.openruncoach.ui.components

import com.example.openruncoach.domain.route.RunRoutePoint
import java.time.Instant
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class RouteMapGeometryTest {
  private fun point(index: Int, latitude: Double, longitude: Double) =
    RunRoutePoint(index, Instant.EPOCH.plusSeconds(index.toLong()), latitude, longitude)

  @Test fun cameraFitsWholeRouteWithPaddingWithoutNativeRenderer() {
    val geometry = prepareRouteMapGeometry(listOf(point(0, 52.50, 13.40), point(1, 52.51, 13.42))) ?: error("Missing")
    val camera = fitRouteMapCamera(geometry, 360.0, 260.0, 28.0) ?: error("Missing")
    val world = 512.0 * 2.0.pow(camera.zoom)
    assertTrue(geometry.spanX * world <= 304.0001)
    assertTrue(geometry.spanY * world <= 204.0001)
    assertEquals(camera, fitRouteMapCamera(geometry, 360.0, 260.0, 28.0))
  }
  @Test fun verticalHorizontalZeroSpanAndShortRoutesHaveFiniteCameras() {
    for (end in listOf(point(1, 52.51, 13.4), point(1, 52.5, 13.41), point(1, 52.5, 13.4), point(1, 52.500001, 13.400001))) {
      val geometry = prepareRouteMapGeometry(listOf(point(0, 52.5, 13.4), end)) ?: error("Missing")
      assertTrue(fitRouteMapCamera(geometry, 360.0, 260.0, 28.0)?.zoom?.isFinite() == true)
    }
  }
  @Test fun dateLineBoundsStayLocalAndInvalidPointsAreExcluded() {
    val geometry = prepareRouteMapGeometry(listOf(point(0, 0.0, 179.999), point(1, 0.0, -179.999))) ?: error("Missing")
    assertTrue(geometry.spanX < 0.0001)
    assertNull(prepareRouteMapGeometry(listOf(point(0, Double.NaN, 0.0), point(1, 90.0, 0.0))))
    assertNull(fitRouteMapCamera(geometry, 20.0, 20.0, 28.0))
  }
  @Test fun denseMapRoutePreparationIsBoundedAndPreservesEndpoints() {
    val points = (0..3000).map { point(it, 52.5, 13.4 + it * 0.000001) }
    val geometry = prepareRouteMapGeometry(points) ?: error("Missing")
    assertEquals(2000, geometry.coordinates.size)
    assertEquals(MapCoordinate(points.first().latitude, points.first().longitude), geometry.coordinates.first())
    assertEquals(MapCoordinate(points.last().latitude, points.last().longitude), geometry.coordinates.last())
    assertEquals(geometry, prepareRouteMapGeometry(points))
  }
}
