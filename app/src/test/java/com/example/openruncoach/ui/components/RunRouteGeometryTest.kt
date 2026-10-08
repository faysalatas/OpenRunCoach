package com.example.openruncoach.ui.components

import com.example.openruncoach.domain.route.RunRoutePoint
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunRouteGeometryTest {
  private fun point(index: Int, latitude: Double, longitude: Double) =
    RunRoutePoint(index, Instant.EPOCH.plusSeconds(index.toLong()), latitude, longitude)

  @Test fun longitudeAccountsForLatitude() {
    val geometry = prepareRouteGeometry(listOf(point(0, 60.0, 10.0), point(1, 60.0, 10.01)), 180)
    assertEquals(0.005, geometry.last().x, 0.000001)
    assertEquals(0.0, geometry.last().y, 0.0)
  }

  @Test fun verticalHorizontalAndZeroSpanRoutesRetainTheirTrueExtents() {
    val vertical = prepareRouteGeometry(listOf(point(0, 50.0, 10.0), point(1, 50.01, 10.0)), 180)
    assertTrue(vertical.all { it.x == 0.0 })
    assertEquals(0.01, vertical.last().y, 0.000001)
    val horizontal = prepareRouteGeometry(listOf(point(0, 50.0, 10.0), point(1, 50.0, 10.01)), 180)
    assertTrue(horizontal.all { it.y == 0.0 })
    val stationary = prepareRouteGeometry(listOf(point(0, 50.0, 10.0), point(1, 50.0, 10.0)), 180)
    assertTrue(stationary.all { it.x == 0.0 && it.y == 0.0 })
  }

  @Test fun samplingIsBoundedDeterministicAndKeepsEndpointsWithoutMutatingInput() {
    val points = (0..1600).map { point(it, 50.0 + it * 0.00001, 10.0 + kotlin.math.sin(it / 30.0) * 0.001) }
    val snapshot = points.toList()
    val full = prepareRouteGeometry(points, 2000)
    val compact = prepareRouteGeometry(points, 180)
    assertEquals(180, compact.size)
    assertEquals(compact, prepareRouteGeometry(points, 180))
    assertEquals(full.first(), compact.first())
    assertEquals(full.last(), compact.last())
    assertEquals(snapshot, points)
    assertEquals(listOf(full.first(), full.last()), prepareRouteGeometry(points, 2))
  }

  @Test fun invalidCoordinatesAreNotRenderedAndDateLineRemainsLocal() {
    assertTrue(prepareRouteGeometry(listOf(point(0, Double.NaN, 1.0), point(1, 91.0, 1.0)), 180).isEmpty())
    val geometry = prepareRouteGeometry(listOf(point(0, 0.0, 179.999), point(1, 0.0, -179.999)), 180)
    assertEquals(0.002, geometry.last().x, 0.000001)
  }
}
