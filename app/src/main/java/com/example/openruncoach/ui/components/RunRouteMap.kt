package com.example.openruncoach.ui.components

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.openruncoach.BuildConfig
import com.example.openruncoach.domain.route.RunRoutePoint
import com.example.openruncoach.theme.OpenRunColors
import com.example.openruncoach.theme.OpenRunShapeTokens
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.tile.TileOperation
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

data class RouteMapConfiguration(val styleUri: String)

/** Detail-only street context; Canvas is immediately useful until real map tiles render. */
@Composable
fun RunRouteMap(
  points: List<RunRoutePoint>,
  modifier: Modifier = Modifier,
  configuration: RouteMapConfiguration = RouteMapConfiguration(BuildConfig.ROUTE_MAP_STYLE_URI),
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val geometry = remember(points) { prepareRouteMapGeometry(points) }
  if (geometry == null || configuration.styleUri.isBlank()) {
    RunRouteCanvas(points, modifier, RunRouteCanvasStyle.DETAIL)
    return
  }
  val controller = remember(context, configuration, lifecycleOwner) { RouteMapController(context, configuration) }
  DisposableEffect(controller, lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> controller.start()
        Lifecycle.Event.ON_RESUME -> controller.resume()
        Lifecycle.Event.ON_PAUSE -> controller.pause()
        Lifecycle.Event.ON_STOP -> controller.stop()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.start()
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.resume()
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      controller.destroy()
    }
  }
  Box(modifier.clip(OpenRunShapeTokens.Card)) {
    AndroidView(factory = { controller.view }, modifier = Modifier.fillMaxSize(), update = {
      controller.updateRoute(geometry)
    })
    if (!controller.mapVisible) RunRouteCanvas(points, Modifier.fillMaxSize(), RunRouteCanvasStyle.DETAIL)
  }
}

private class RouteMapController(context: Context, configuration: RouteMapConfiguration) : ComponentCallbacks {
  private val applicationContext = context.applicationContext
  private var map: MapLibreMap? = null
  private var style: Style? = null
  private var geometry: RouteMapGeometry? = null
  private var cameraKey: Triple<RouteMapGeometry, Int, Int>? = null
  private var destroyed = false
  private var started = false
  private var resumed = false
  private var failed = false
  private var hasBackgroundTile = false
  var mapVisible by mutableStateOf(false)
    private set
  val view: MapView

  init {
    MapLibre.getInstance(applicationContext)
    view = MapView(context, MapLibreMapOptions.createFromAttributes(context)
      .textureMode(true).foregroundLoadColor(OpenRunColors.Surface.toArgb())
      .setPrefetchZoomDelta(0).compassEnabled(false).logoEnabled(false)
      .rotateGesturesEnabled(false).tiltGesturesEnabled(false).scrollGesturesEnabled(false)
      .zoomGesturesEnabled(false).doubleTapGesturesEnabled(false).quickZoomGesturesEnabled(false))
    view.onCreate(null)
    view.contentDescription = "Recorded route over a street map"
    view.addOnDidFailLoadingMapListener { view.post { fail() } }
    view.addOnTileActionListener { operation, _, _, _, _, _, source ->
      if (source !in setOf(ROUTE_SOURCE, START_SOURCE, FINISH_SOURCE)) {
        view.post {
          if (!destroyed) {
            if (operation == TileOperation.Error) fail()
            if (operation == TileOperation.EndParse) hasBackgroundTile = true
          }
        }
      }
    }
    view.addOnDidFinishRenderingMapListener { fully ->
      val renderedKey = cameraKey
      val ready = fully && hasBackgroundTile && renderedKey != null && style != null
      view.post { if (!destroyed && ready && !failed && renderedKey == cameraKey) mapVisible = true }
    }
    view.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
      if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) fitCamera()
    }
    view.getMapAsync { readyMap ->
      if (!destroyed) {
        map = readyMap
        readyMap.setStyle(configuration.styleUri) { loadedStyle ->
          if (!destroyed) {
            style = loadedStyle
            installRouteLayers(loadedStyle)
            updateSources()
            fitCamera()
          }
        }
      }
    }
    applicationContext.registerComponentCallbacks(this)
  }

  fun updateRoute(next: RouteMapGeometry) {
    if (destroyed || geometry == next) return
    geometry = next
    mapVisible = false
    cameraKey = null
    updateSources()
    fitCamera()
  }

  private fun installRouteLayers(style: Style) {
    // Subdue only basemap layers, before adding the full-strength local route.
    style.layers.forEach { layer ->
      when (layer) {
        is BackgroundLayer -> layer.setProperties(backgroundColor(OpenRunColors.Background.toArgb()))
        is LineLayer -> layer.setProperties(lineOpacity(0.6f))
        is SymbolLayer -> layer.setProperties(textOpacity(0.45f), iconOpacity(0.25f))
      }
    }
    style.addSource(GeoJsonSource(ROUTE_SOURCE))
    style.addSource(GeoJsonSource(START_SOURCE))
    style.addSource(GeoJsonSource(FINISH_SOURCE))
    style.addLayer(LineLayer("run-route-line", ROUTE_SOURCE).withProperties(
      lineColor(OpenRunColors.Accent.toArgb()), lineWidth(3f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
    style.addLayer(CircleLayer("run-start", START_SOURCE).withProperties(
      circleRadius(5f), circleColor(OpenRunColors.Accent.toArgb()), circleStrokeWidth(2f), circleStrokeColor(OpenRunColors.Surface.toArgb())))
    style.addLayer(CircleLayer("run-finish", FINISH_SOURCE).withProperties(
      circleRadius(6f), circleColor(OpenRunColors.Surface.toArgb()), circleStrokeWidth(2f), circleStrokeColor(OpenRunColors.Accent.toArgb())))
  }

  private fun updateSources() {
    val current = geometry ?: return
    val loaded = style ?: return
    val coordinates = current.coordinates.map { Point.fromLngLat(it.longitude, it.latitude) }
    loaded.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(LineString.fromLngLats(coordinates))
    loaded.getSourceAs<GeoJsonSource>(START_SOURCE)?.setGeoJson(coordinates.first())
    loaded.getSourceAs<GeoJsonSource>(FINISH_SOURCE)?.setGeoJson(coordinates.last())
  }

  private fun fitCamera() {
    if (destroyed || style == null) return
    val current = geometry ?: return
    val readyMap = map ?: return
    val key = Triple(current, view.width, view.height)
    if (key == cameraKey) return
    // Camera math is in logical pixels, matching MapLibre's 512 px world.
    val density = view.resources.displayMetrics.density.toDouble()
    val camera = fitRouteMapCamera(current, view.width / density, view.height / density, 28.0) ?: return
    readyMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
      .target(LatLng(camera.center.latitude, camera.center.longitude)).zoom(camera.zoom).bearing(0.0).tilt(0.0).build()))
    cameraKey = key
  }

  private fun fail() { if (!destroyed) { failed = true; mapVisible = false } }
  fun start() { if (!destroyed && !started) { view.onStart(); started = true } }
  fun resume() { start(); if (!destroyed && !resumed) { view.onResume(); resumed = true } }
  fun pause() { if (resumed) { view.onPause(); resumed = false } }
  fun stop() { pause(); if (started) { view.onStop(); started = false } }
  fun destroy() {
    if (destroyed) return
    stop()
    destroyed = true
    applicationContext.unregisterComponentCallbacks(this)
    view.onDestroy()
  }
  override fun onLowMemory() { if (!destroyed) view.onLowMemory() }
  override fun onConfigurationChanged(newConfig: Configuration) = Unit
}

private const val ROUTE_SOURCE = "local-run-route"
private const val START_SOURCE = "local-run-start"
private const val FINISH_SOURCE = "local-run-finish"
