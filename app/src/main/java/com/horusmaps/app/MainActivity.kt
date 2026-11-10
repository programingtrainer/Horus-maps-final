package com.horusmaps.app

import android.Manifest
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.Style
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import java.util.Locale


/**
 * Downloads the selected MapLibre style and enables the experimental native globe projection.
 * If the style cannot be fetched or parsed, callers fall back to loading its original URI.
 */
private suspend fun fetchGlobeStyleJson(styleUrl: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = java.net.URL(styleUrl).openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "HorusMaps-Android")
            val status = connection.responseCode
            if (status !in 200..299) error("Style request failed: HTTP $status")
            val jsonText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val styleJson = org.json.JSONObject(jsonText)
            styleJson.put("projection", org.json.JSONObject().put("type", "globe"))
            styleJson.toString()
        } finally {
            connection.disconnect()
        }
    }.onFailure {
        android.util.Log.w("HorusMaps", "Globe style preparation failed; using original style URI", it)
    }.getOrNull()
}

private fun installMapAppearance(context: android.content.Context, style: Style, configuredLanguage: String) {
    // Match the old map's device-language labels.
    val language = if (configuredLanguage == "device") java.util.Locale.getDefault().language.lowercase(java.util.Locale.ROOT) else configuredLanguage.lowercase(java.util.Locale.ROOT)
    val localizedName = org.maplibre.android.style.expressions.Expression.coalesce(
        org.maplibre.android.style.expressions.Expression.get("name:$language"),
        org.maplibre.android.style.expressions.Expression.get("name_$language")
    )

    style.layers
        .filterIsInstance<org.maplibre.android.style.layers.SymbolLayer>()
        .forEach { layer ->
            val textExpression = runCatching {
                layer.getTextField().getExpression()?.toString().orEmpty()
            }.getOrDefault("")

            if (textExpression.contains("name", ignoreCase = true)) {
                layer.setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.textField(localizedName)
                )
            }
        }

    // Treat the continent overview and country map as two distinct cartographic
    // levels: political boundaries/labels are completely hidden at world scale,
    // then fade in only as the user zooms into country scale.
    val countryDetailOpacity = org.maplibre.android.style.expressions.Expression.interpolate(
        org.maplibre.android.style.expressions.Expression.linear(),
        org.maplibre.android.style.expressions.Expression.zoom(),
        org.maplibre.android.style.expressions.Expression.stop(0.0, 0.0),
        org.maplibre.android.style.expressions.Expression.stop(2.0, 0.0),
        org.maplibre.android.style.expressions.Expression.stop(2.9, 0.55),
        org.maplibre.android.style.expressions.Expression.stop(3.7, 1.0)
    )
    style.getLayer("boundary_2")?.setProperties(
        org.maplibre.android.style.layers.PropertyFactory.lineOpacity(countryDetailOpacity)
    )
    listOf("label_country_1", "label_country_2", "label_country_3").forEach { id ->
        (style.getLayer(id) as? org.maplibre.android.style.layers.SymbolLayer)?.setProperties(
            org.maplibre.android.style.layers.PropertyFactory.textOpacity(countryDetailOpacity)
        )
    }

    // Seven dissolved terrestrial continent geometries generated from Natural Earth 1:50m.
    // Country polygons are merged by shared edges, so ocean partition lines are never drawn.
    runCatching {
        val continentGeoJson = context.assets.open("continents.geojson").bufferedReader().use { it.readText() }
        if (style.getSource("horus-continent-shapes") == null) {
            style.addSource(
                org.maplibre.android.style.sources.GeoJsonSource(
                    "horus-continent-shapes",
                    continentGeoJson
                )
            )
        }

        // At world scale, preserve the basemap's natural land and ocean appearance.
        // Country borders/labels fade out while continent outlines remain visible.
        // Country detail fades back in smoothly as the user zooms closer.
        val continentOpacity = org.maplibre.android.style.expressions.Expression.interpolate(
            org.maplibre.android.style.expressions.Expression.linear(),
            org.maplibre.android.style.expressions.Expression.zoom(),
            org.maplibre.android.style.expressions.Expression.stop(0.0, 1.0),
            org.maplibre.android.style.expressions.Expression.stop(2.0, 1.0),
            org.maplibre.android.style.expressions.Expression.stop(2.9, 0.55),
            org.maplibre.android.style.expressions.Expression.stop(3.7, 0.0)
        )
        val firstSymbolLayerId = style.layers
            .filterIsInstance<org.maplibre.android.style.layers.SymbolLayer>()
            .firstOrNull()?.id

        if (style.getLayer("horus-continent-outline") == null) {
            val outline = org.maplibre.android.style.layers.LineLayer(
                "horus-continent-outline",
                "horus-continent-shapes"
            ).apply {
                minZoom = 0.0f
                maxZoom = 3.46f
                setProperties(
                    // Natural land/water colors remain untouched; draw only the continent edges.
                    org.maplibre.android.style.layers.PropertyFactory.lineColor(
                        org.maplibre.android.style.expressions.Expression.match(
                            org.maplibre.android.style.expressions.Expression.get("continent"),
                            org.maplibre.android.style.expressions.Expression.literal("Africa"),
                            org.maplibre.android.style.expressions.Expression.literal("#F4A261"),
                            org.maplibre.android.style.expressions.Expression.literal("Asia"),
                            org.maplibre.android.style.expressions.Expression.literal("#E63946"),
                            org.maplibre.android.style.expressions.Expression.literal("Europe"),
                            org.maplibre.android.style.expressions.Expression.literal("#4361EE"),
                            org.maplibre.android.style.expressions.Expression.literal("North America"),
                            org.maplibre.android.style.expressions.Expression.literal("#2A9D8F"),
                            org.maplibre.android.style.expressions.Expression.literal("South America"),
                            org.maplibre.android.style.expressions.Expression.literal("#9B5DE5"),
                            org.maplibre.android.style.expressions.Expression.literal("Oceania"),
                            org.maplibre.android.style.expressions.Expression.literal("#D946A6"),
                            org.maplibre.android.style.expressions.Expression.literal("Antarctica"),
                            org.maplibre.android.style.expressions.Expression.literal("#00A6D6"),
                            org.maplibre.android.style.expressions.Expression.literal("#263238")
                        )
                    ),
                    org.maplibre.android.style.layers.PropertyFactory.lineWidth(
                        org.maplibre.android.style.expressions.Expression.interpolate(
                            org.maplibre.android.style.expressions.Expression.linear(),
                            org.maplibre.android.style.expressions.Expression.zoom(),
                            org.maplibre.android.style.expressions.Expression.stop(0.0, 1.15),
                            org.maplibre.android.style.expressions.Expression.stop(2.0, 1.55),
                            org.maplibre.android.style.expressions.Expression.stop(3.46, 2.05)
                        )
                    ),
                    org.maplibre.android.style.layers.PropertyFactory.lineOpacity(continentOpacity)
                )
            }
            if (firstSymbolLayerId != null) style.addLayerBelow(outline, firstSymbolLayerId) else style.addLayer(outline)
        }

        if (style.getSource("horus-continent-labels") == null) {
            val labelsJson = """{"type":"FeatureCollection","features":[
                {"type":"Feature","properties":{"name_ar":"أمريكا الشمالية","name_en":"NORTH AMERICA"},"geometry":{"type":"Point","coordinates":[-102,43]}},
                {"type":"Feature","properties":{"name_ar":"أمريكا الجنوبية","name_en":"SOUTH AMERICA"},"geometry":{"type":"Point","coordinates":[-61,-17]}},
                {"type":"Feature","properties":{"name_ar":"أوروبا","name_en":"EUROPE"},"geometry":{"type":"Point","coordinates":[17,53]}},
                {"type":"Feature","properties":{"name_ar":"أفريقيا","name_en":"AFRICA"},"geometry":{"type":"Point","coordinates":[20,2]}},
                {"type":"Feature","properties":{"name_ar":"آسيا","name_en":"ASIA"},"geometry":{"type":"Point","coordinates":[91,47]}},
                {"type":"Feature","properties":{"name_ar":"أوقيانوسيا","name_en":"OCEANIA"},"geometry":{"type":"Point","coordinates":[135,-24]}},
                {"type":"Feature","properties":{"name_ar":"القارة القطبية الجنوبية","name_en":"ANTARCTICA"},"geometry":{"type":"Point","coordinates":[0,-78]}}
            ]}"""
            style.addSource(
                org.maplibre.android.style.sources.GeoJsonSource(
                    "horus-continent-labels",
                    labelsJson
                )
            )
        }

        if (style.getLayer("horus-continent-labels-layer") == null) {
            val useArabic = (if (configuredLanguage == "device") {
                java.util.Locale.getDefault().language
            } else configuredLanguage).startsWith("ar", ignoreCase = true)
            val continentText = if (useArabic) {
                org.maplibre.android.style.expressions.Expression.get("name_ar")
            } else {
                org.maplibre.android.style.expressions.Expression.get("name_en")
            }
            val labels = org.maplibre.android.style.layers.SymbolLayer(
                "horus-continent-labels-layer",
                "horus-continent-labels"
            ).apply {
                minZoom = 0.0f
                maxZoom = 3.46f
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.textField(continentText),
                    org.maplibre.android.style.layers.PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    org.maplibre.android.style.layers.PropertyFactory.textSize(14.0f),
                    org.maplibre.android.style.layers.PropertyFactory.textColor("#24384D"),
                    org.maplibre.android.style.layers.PropertyFactory.textHaloColor("#FFFFFF"),
                    org.maplibre.android.style.layers.PropertyFactory.textHaloWidth(2.4f),
                    org.maplibre.android.style.layers.PropertyFactory.textHaloBlur(0.15f),
                    org.maplibre.android.style.layers.PropertyFactory.textMaxWidth(11.0f),
                    org.maplibre.android.style.layers.PropertyFactory.textAnchor("center"),
                    org.maplibre.android.style.layers.PropertyFactory.textJustify("center"),
                    org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap(true),
                    org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement(true),
                    org.maplibre.android.style.layers.PropertyFactory.textOpacity(continentOpacity)
                )
            }
            style.addLayer(labels)
        }
    }.onFailure { error ->
        android.util.Log.e("HorusMaps", "Unable to install dissolved continent overview", error)
    }

    // 3D building geometry remains installed in BOTH camera modes.
    // The 2D/3D control changes camera pitch only; it never removes extrusion layers.
    // Keep the old map's 3D building appearance without duplicating
    // an extrusion layer already supplied by the Liberty style.
    if (style.layers.none { it is org.maplibre.android.style.layers.FillExtrusionLayer }) {
        val sourceId = when {
            style.getSource("openmaptiles") != null -> "openmaptiles"
            style.getSource("openfreemap") != null -> "openfreemap"
            else -> null
        }

        if (sourceId != null) {
            val buildings = org.maplibre.android.style.layers.FillExtrusionLayer(
                "horus-3d-buildings",
                sourceId
            ).apply {
                sourceLayer = "building"
                minZoom = 15f
                setFilter(
                    org.maplibre.android.style.expressions.Expression.all(
                        org.maplibre.android.style.expressions.Expression.has("render_height"),
                        org.maplibre.android.style.expressions.Expression.has("render_min_height")
                    )
                )
                setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.fillExtrusionColor(
                        android.graphics.Color.parseColor("#C7D0DA")
                    ),
                    org.maplibre.android.style.layers.PropertyFactory.fillExtrusionHeight(
                        org.maplibre.android.style.expressions.Expression.get("render_height")
                    ),
                    org.maplibre.android.style.layers.PropertyFactory.fillExtrusionBase(
                        org.maplibre.android.style.expressions.Expression.get("render_min_height")
                    ),
                    org.maplibre.android.style.layers.PropertyFactory.fillExtrusionOpacity(0.92f)
                )
            }

            val firstLabel = style.layers
                .filterIsInstance<org.maplibre.android.style.layers.SymbolLayer>()
                .firstOrNull()

            if (firstLabel != null) {
                style.addLayerBelow(buildings, firstLabel.id)
            } else {
                style.addLayer(buildings)
            }
        }
    }
}

private data class HorusNavigationProgress(
    val remainingMeters: Int,
    val remainingSeconds: Int,
    val traveledMeters: Int
)
private data class HorusNavigationManeuverState(
    val current: HorusRouteStep,
    val next: HorusRouteStep?,
    val distanceToCurrentMeters: Int
)
private fun selectNavigationManeuver(route: HorusRouteResult, progress: HorusNavigationProgress): HorusNavigationManeuverState? {
    val maneuvers = route.steps.filterNot { it.type == "depart" || it.type == "arrive" }
    if (maneuvers.isEmpty()) return null
    val index = maneuvers.indexOfFirst { it.distanceFromStartMeters >= progress.traveledMeters - 20 }
        .let { if (it < 0) maneuvers.lastIndex else it }
    val current = maneuvers[index]
    return HorusNavigationManeuverState(current, maneuvers.getOrNull(index + 1),
        (current.distanceFromStartMeters - progress.traveledMeters).coerceAtLeast(0))
}

private fun calculateRouteProgress(
    routePoints: List<LatLng>,
    currentLocation: Location,
    route: HorusRouteResult
): HorusNavigationProgress? {
    if (routePoints.size < 2) return null

    val latitudeScale = 110540.0
    val longitudeScale = 111320.0 * kotlin.math.cos(Math.toRadians(currentLocation.latitude)).coerceAtLeast(0.01)
    var traversedMeters = 0.0
    var bestDistanceSquared = Double.POSITIVE_INFINITY
    var bestAlongRoute = 0.0

    for (index in 0 until routePoints.lastIndex) {
        val start = routePoints[index]
        val end = routePoints[index + 1]
        val ax = (start.longitude - currentLocation.longitude) * longitudeScale
        val ay = (start.latitude - currentLocation.latitude) * latitudeScale
        val bx = (end.longitude - currentLocation.longitude) * longitudeScale
        val by = (end.latitude - currentLocation.latitude) * latitudeScale
        val dx = bx - ax
        val dy = by - ay
        val segmentLengthSquared = dx * dx + dy * dy
        val fraction = if (segmentLengthSquared <= 0.0001) 0.0 else
            ((-(ax * dx + ay * dy)) / segmentLengthSquared).coerceIn(0.0, 1.0)
        val nearestX = ax + fraction * dx
        val nearestY = ay + fraction * dy
        val distanceSquared = nearestX * nearestX + nearestY * nearestY
        val segmentLength = FloatArray(1).also { output ->
            Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, output)
        }[0].toDouble()

        if (distanceSquared < bestDistanceSquared) {
            bestDistanceSquared = distanceSquared
            bestAlongRoute = traversedMeters + segmentLength * fraction
        }
        traversedMeters += segmentLength
    }

    if (traversedMeters <= 0.0) return null
    val remainingRatio = (1.0 - bestAlongRoute / traversedMeters).coerceIn(0.0, 1.0)
    return HorusNavigationProgress(
        remainingMeters = (route.distanceMeters * remainingRatio).toInt().coerceAtLeast(0),
        remainingSeconds = (route.durationSeconds * remainingRatio).toInt().coerceAtLeast(0),
        traveledMeters = (route.distanceMeters * (1.0 - remainingRatio)).toInt().coerceAtLeast(0)
    )
}

class MainActivity : ComponentActivity() {
    private val searchBoundaryHandler = Handler(Looper.getMainLooper())
    private var searchBoundaryAnimator: ValueAnimator? = null
    private var searchBoundaryClearRunnable: Runnable? = null
    private var searchBoundaryFinishRunnable: Runnable? = null
    private var searchMarkerClearRunnable: Runnable? = null
    private var searchMarkerFadeAnimator: ValueAnimator? = null
    private var searchMarker: Marker? = null
    private var placeFeatureClickListener: org.maplibre.android.maps.MapLibreMap.OnMapClickListener? = null

    private fun installPlaceFeatureClickListener(
        map: org.maplibre.android.maps.MapLibreMap,
        onPlaceSelected: (HorusNavigationDestination) -> Unit
    ) {
        placeFeatureClickListener?.let { old ->
            runCatching { map.removeOnMapClickListener(old) }
        }
        val listener = org.maplibre.android.maps.MapLibreMap.OnMapClickListener { tappedPoint ->
            val features: List<org.maplibre.geojson.Feature> = runCatching {
                map.queryRenderedFeatures(map.projection.toScreenLocation(tappedPoint))
            }.getOrDefault(emptyList())
            val selected: HorusNavigationDestination? = features.firstNotNullOfOrNull { feature ->
                val properties = feature.properties() ?: return@firstNotNullOfOrNull null
                fun value(key: String): String =
                    runCatching { feature.getStringProperty(key)?.trim().orEmpty().lowercase(Locale.ROOT) }
                        .getOrDefault("")
                val values = listOf(
                    "amenity", "shop", "healthcare", "railway", "public_transport",
                    "highway", "leisure", "tourism", "office", "craft", "aeroway",
                    "place", "class", "subclass", "type", "kind", "religion"
                ).associateWith(::value)
                val tags = values.values.filter(String::isNotBlank).toSet()
                val categoryName = when {
                    tags.any { it in setOf("place_of_worship", "mosque", "masjid", "religious_muslim") } ||
                        (value("religion") == "muslim" && value("amenity") == "place_of_worship") -> "مسجد"
                    tags.any { it in setOf("hospital", "clinic", "healthcare") } -> "منشأة طبية"
                    tags.any { it in setOf("pharmacy", "chemist") } -> "صيدلية"
                    tags.any { it in setOf("supermarket", "grocery", "convenience", "department_store") } -> "متجر"
                    tags.any { it in setOf("restaurant", "fast_food", "cafe") } -> "مطعم"
                    tags.any { it in setOf("fuel", "gas_station") } -> "محطة وقود"
                    tags.any { it in setOf("rail", "railway", "station", "subway", "tram", "light_rail") } -> "محطة قطار"
                    tags.any { it in setOf("airport", "aerodrome") } -> "مطار"
                    tags.any { it in setOf("bus", "bus_stop", "bus_station") } -> "محطة حافلات"
                    else -> null
                } ?: return@firstNotNullOfOrNull null
                val name = listOf("name", "name:ar", "name_ar", "name:en", "name_en", "name_int")
                    .firstNotNullOfOrNull { key ->
                        runCatching { feature.getStringProperty(key)?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()
                    } ?: categoryName
                val address = listOf("addr:street", "addr:suburb", "addr:city", "addr:state", "addr:country")
                    .mapNotNull { key ->
                        runCatching { feature.getStringProperty(key)?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()
                    }.distinct().joinToString("، ")
                HorusNavigationDestination(
                    name = name,
                    address = address,
                    latitude = tappedPoint.latitude,
                    longitude = tappedPoint.longitude
                )
            }
            if (selected == null) {
                false
            } else {
                showSearchMarker(map, tappedPoint.latitude, tappedPoint.longitude, selected.name)
                onPlaceSelected(selected)
                true
            }
        }
        placeFeatureClickListener = listener
        map.addOnMapClickListener(listener)
    }

    private fun bestKnownLocation(map: org.maplibre.android.maps.MapLibreMap): Location? {
        val componentLocation = runCatching {
            if (map.locationComponent.isLocationComponentActivated) map.locationComponent.lastKnownLocation else null
        }.getOrNull()
        if (componentLocation != null) return componentLocation
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        return providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 2 * 60 * 60 * 1000L }
            .maxByOrNull { it.time }
    }

    private fun showSearchMarker(map: org.maplibre.android.maps.MapLibreMap, latitude: Double, longitude: Double, title: String) {
        searchMarkerClearRunnable?.let(searchBoundaryHandler::removeCallbacks)
        searchMarkerClearRunnable = null
        searchMarkerFadeAnimator?.cancel()
        runCatching { searchMarker?.let { map.removeAnnotation(it) } }

        val marker = runCatching {
            map.addMarker(MarkerOptions().position(LatLng(latitude, longitude)).title(title))
        }.getOrNull()
        searchMarker = marker ?: return

        // Keep the marker fully visible for 8.5 seconds, then fade it out
        // smoothly during the final 1.5 seconds of its 10-second lifetime.
        val clearAfterFade = Runnable {
            val activeMarker = marker
            val originalBitmap = runCatching { activeMarker.icon?.bitmap }.getOrNull()
            if (originalBitmap == null) {
                runCatching { map.removeAnnotation(activeMarker) }
                if (searchMarker === activeMarker) searchMarker = null
                return@Runnable
            }
            searchMarkerFadeAnimator?.cancel()
            searchMarkerFadeAnimator = ValueAnimator.ofInt(255, 0).apply {
                duration = 1500L
                addUpdateListener { animation ->
                    val alpha = animation.animatedValue as Int
                    runCatching {
                        val fadedBitmap = Bitmap.createBitmap(
                            originalBitmap.width,
                            originalBitmap.height,
                            Bitmap.Config.ARGB_8888
                        )
                        Canvas(fadedBitmap).drawBitmap(
                            originalBitmap,
                            0f,
                            0f,
                            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
                        )
                        activeMarker.icon = IconFactory.getInstance(this@MainActivity).fromBitmap(fadedBitmap)
                    }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        runCatching { map.removeAnnotation(activeMarker) }
                        if (searchMarker === activeMarker) searchMarker = null
                        searchMarkerFadeAnimator = null
                        searchMarkerClearRunnable = null
                    }
                })
                start()
            }
        }
        searchMarkerClearRunnable = clearAfterFade
        searchBoundaryHandler.postDelayed(clearAfterFade, 8500L)
    }

    private fun showSearchBoundary(map: org.maplibre.android.maps.MapLibreMap, featureGeoJson: String) {
        searchBoundaryAnimator?.cancel()
        searchBoundaryClearRunnable?.let(searchBoundaryHandler::removeCallbacks)
        searchBoundaryFinishRunnable?.let(searchBoundaryHandler::removeCallbacks)
        searchBoundaryClearRunnable = null
        searchBoundaryFinishRunnable = null
        map.getStyle { style ->
            val source = (style.getSource("horus-search-boundary") as? GeoJsonSource)
                ?: GeoJsonSource("horus-search-boundary", featureGeoJson).also { style.addSource(it) }
            source.setGeoJson(featureGeoJson)
            val layer = (style.getLayer("horus-search-boundary-line") as? LineLayer)
                ?: LineLayer("horus-search-boundary-line", "horus-search-boundary").apply {
                    setProperties(PropertyFactory.lineColor("#F02035"), PropertyFactory.lineWidth(3.2f), PropertyFactory.lineOpacity(1.0f))
                }.also { style.addLayer(it) }
            layer.setProperties(PropertyFactory.lineColor("#F02035"), PropertyFactory.lineWidth(3.2f), PropertyFactory.lineOpacity(1.0f))
            val clear = Runnable {
                searchBoundaryAnimator = ValueAnimator.ofFloat(1.0f, 0.0f).apply {
                    duration = 1500L
                    addUpdateListener { animation ->
                        runCatching { layer.setProperties(PropertyFactory.lineOpacity(animation.animatedValue as Float)) }
                    }
                    start()
                }
                val finishFade = Runnable {
                    runCatching { source.setGeoJson("""{"type":"FeatureCollection","features":[]}""") }
                    searchBoundaryFinishRunnable = null
                }
                searchBoundaryFinishRunnable = finishFade
                searchBoundaryHandler.postDelayed(finishFade, 1550L)
            }
            searchBoundaryClearRunnable = clear
            searchBoundaryHandler.postDelayed(clear, 8500L)
        }
    }

    private fun focusSearchResult(map: org.maplibre.android.maps.MapLibreMap, result: HorusGeoSearchResult) {
        val bounds = result.bounds
        if (bounds != null) {
            val crossesAntimeridian = bounds.west > bounds.east
            if (crossesAntimeridian) {
                // LatLngBounds.Builder treats west/east as an ordinary numeric
                // range. For a polygon crossing +/-180°, that can produce a
                // nearly-worldwide box whose apparent center is around Africa.
                // The search API now supplies the center of the shortest
                // longitude arc, so use it directly for this camera transition.
                val longitudeSpan = (180.0 - bounds.west) + (bounds.east + 180.0)
                val latitudeSpan = bounds.north - bounds.south
                val extent = maxOf(longitudeSpan, latitudeSpan)
                val zoom = when {
                    extent > 150.0 -> 2.2
                    extent > 110.0 -> 2.7
                    extent > 75.0 -> 3.3
                    extent > 45.0 -> 4.0
                    extent > 25.0 -> 4.8
                    extent > 12.0 -> 5.8
                    else -> 6.8
                }
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(result.latitude, result.longitude), zoom),
                    1050
                )
            } else {
                runCatching {
                    val fitBounds = LatLngBounds.Builder()
                        .include(LatLng(bounds.north, bounds.west))
                        .include(LatLng(bounds.south, bounds.east))
                        .build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(fitBounds, (112 * resources.displayMetrics.density).toInt()), 1050)
                }.onFailure {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(result.latitude, result.longitude), 7.0), 900)
                }
            }
        } else {
            val kind = result.kind.lowercase()
            val zoom = when {
                kind in setOf(
                    "village", "hamlet", "suburb", "neighbourhood", "quarter", "locality",
                    "isolated_dwelling", "residential", "primary", "secondary", "tertiary",
                    "unclassified", "service", "pedestrian", "living_street", "road"
                ) -> 15.0
                kind in setOf("city", "town") -> 12.5
                kind in setOf("state", "county", "province", "administrative") -> 7.5
                else -> 12.0
            }
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(result.latitude, result.longitude), zoom), 900)
        }
        showSearchMarker(map, result.latitude, result.longitude, result.name)
        showSearchBoundary(map, result.geometryGeoJson)
    }

    private fun enableBlueLocationDot(map: org.maplibre.android.maps.MapLibreMap, style: Style) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return

        try {
            val component = map.locationComponent
            if (!component.isLocationComponentActivated) {
                component.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, style).build()
                )
            }
            component.isLocationComponentEnabled = true
            component.cameraMode = CameraMode.NONE
            component.renderMode = RenderMode.NORMAL
        } catch (error: Exception) {
            android.util.Log.e("HorusMaps", "Unable to enable blue location indicator", error)
        }
    }

    private fun centerOnCurrentLocation(map: org.maplibre.android.maps.MapLibreMap) {
        val componentLocation = runCatching {
            if (map.locationComponent.isLocationComponentActivated) map.locationComponent.lastKnownLocation else null
        }.getOrNull()
        if (componentLocation != null) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(componentLocation.latitude, componentLocation.longitude), 16.0
                ), 900
            )
            return
        }

        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        fun move(location: Location) {
            val target = LatLng(location.latitude, location.longitude)
            runOnUiThread { map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 15.5), 900) }
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null) move(last)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { move(location); runCatching { manager.removeUpdates(this) } }
        }
        try {
            providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { manager.removeUpdates(listener) } }, 15000L)
        } catch (_: SecurityException) {
            Toast.makeText(this, "لم يتم منح إذن الموقع", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            if (last == null) Toast.makeText(this, "تعذر تحديد موقعك. تأكد من تشغيل خدمة الموقع.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(applicationContext)

        setContent {
            MaterialTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                val mapView = remember { MapView(this@MainActivity) }
                val mapInitializationRequested = remember { mutableStateOf(false) }
                var mapLibreMap by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
                var remoteConfig by remember { mutableStateOf(HorusRemoteConfig.loadCached(this@MainActivity)) }
                val accountPrefs = remember { getSharedPreferences("horus_account", MODE_PRIVATE) }
                var accountEmail by remember { mutableStateOf(accountPrefs.getString("email", null)) }
                var savedAccounts by remember { mutableStateOf(HorusAccountStore.accounts(accountPrefs)) }
                var pendingLoginEmail by remember { mutableStateOf("") }
                var authReturnToProfile by remember { mutableStateOf(false) }
                val accountScope = rememberCoroutineScope()
                var hasCompletedOnboarding by remember {
                    mutableStateOf(
                        accountPrefs.getBoolean("onboarding_completed", false) ||
                            !accountEmail.isNullOrBlank()
                    )
                }
                var isGuestMode by remember {
                    mutableStateOf(
                        accountEmail.isNullOrBlank() &&
                            accountPrefs.getBoolean("onboarding_completed", false)
                    )
                }
                var showWelcomeSheet by remember {
                    mutableStateOf(!hasCompletedOnboarding && remoteConfig.showWelcomePanel)
                }
                var showRegistration by remember { mutableStateOf(false) }
                var showLogin by remember { mutableStateOf(false) }
                var showProfile by remember { mutableStateOf(false) }
                var profileImageUri by remember { mutableStateOf(accountPrefs.getString("profile_image_uri", null)) }
                var searchQuery by remember { mutableStateOf("") }
                var activeSearchJob by remember { mutableStateOf<Job?>(null) }
                var searchGeneration by remember { mutableStateOf(0) }
                var searchExpanded by remember { mutableStateOf(false) }
                var searchLoading by remember { mutableStateOf(false) }
                var searchHasRun by remember { mutableStateOf(false) }
                var searchError by remember { mutableStateOf<String?>(null) }
                var selectedSearchCategory by remember { mutableStateOf<HorusSearchCategory?>(null) }
                var geographySearchResults by remember { mutableStateOf<List<HorusGeoSearchResult>>(emptyList()) }
                var nearbySearchResults by remember { mutableStateOf<List<HorusNearbyPlace>>(emptyList()) }
                var navigationDestination by remember { mutableStateOf<HorusNavigationDestination?>(null) }
                var selectedTravelMode by remember { mutableStateOf("driving") }
                var calculatedRoute by remember { mutableStateOf<HorusRouteResult?>(null) }
                var routeCoordinates by remember { mutableStateOf<List<LatLng>>(emptyList()) }
                var navigationProgress by remember { mutableStateOf<HorusNavigationProgress?>(null) }
                var navigationManeuver by remember { mutableStateOf<HorusNavigationManeuverState?>(null) }
                var routePolyline by remember { mutableStateOf<Polyline?>(null) }
                var routeLoading by remember { mutableStateOf(false) }
                var navigationStarted by remember { mutableStateOf(false) }
                var navigationError by remember { mutableStateOf<String?>(null) }

                fun activateAccount(
                    email: String,
                    firstName: String,
                    lastName: String,
                    phone: String,
                    token: String?,
                    profileImageData: String? = null
                ) {
                    val cloudImageUri = profileImageData?.let {
                        HorusProfileImageStore.saveBase64(this@MainActivity, email, it)
                    }
                    val oldImage = cloudImageUri
                        ?: HorusAccountStore.profileImage(accountPrefs, email)
                        ?: savedAccounts.firstOrNull { it.email.equals(email, true) }?.imageUri
                    val account = HorusSavedAccount(email, firstName, lastName, phone, oldImage)
                    HorusAccountStore.saveAccount(accountPrefs, account, token)
                    savedAccounts = HorusAccountStore.accounts(accountPrefs)
                    accountPrefs.edit()
                        .putString("email", email)
                        .putString("first_name", firstName)
                        .putString("last_name", lastName)
                        .putString("phone", phone)
                        .putString("profile_image_uri", oldImage)
                        .putBoolean("onboarding_completed", true)
                        .apply()
                    accountEmail = email
                    profileImageUri = oldImage
                    isGuestMode = false
                    hasCompletedOnboarding = true
                    showRegistration = false
                    showLogin = false
                    showWelcomeSheet = false
                    showProfile = false
                    pendingLoginEmail = ""
                    authReturnToProfile = false

                    // Older installs may have a local-only image. Upload it once after
                    // login if the account does not yet have a cloud copy.
                    if (!token.isNullOrBlank() && profileImageData.isNullOrBlank()) {
                        accountScope.launch {
                            val remote = runCatching { HorusAuthApi.currentUser(token) }.getOrNull()
                            if (remote?.success == true && accountEmail.equals(email, true)) {
                                if (!remote.profileImageData.isNullOrBlank()) {
                                    val restored = HorusProfileImageStore.saveBase64(
                                        this@MainActivity, email, remote.profileImageData
                                    )
                                    if (restored != null) {
                                        HorusAccountStore.updateImage(accountPrefs, email, restored)
                                        accountPrefs.edit().putString("profile_image_uri", restored).apply()
                                        profileImageUri = restored
                                        savedAccounts = HorusAccountStore.accounts(accountPrefs)
                                    }
                                } else if (!oldImage.isNullOrBlank()) {
                                    val encoded = HorusProfileImageStore.encodeJpeg(
                                        this@MainActivity, android.net.Uri.parse(oldImage)
                                    )
                                    if (!encoded.isNullOrBlank()) {
                                        runCatching { HorusAuthApi.uploadProfileImage(token, encoded) }
                                    }
                                }
                            }
                        }
                    }
                }

                fun openAddAccount() {
                    authReturnToProfile = true
                    pendingLoginEmail = ""
                    showProfile = false
                    showWelcomeSheet = false
                    showRegistration = false
                    showLogin = true
                }

                fun switchToAccount(email: String) {
                    if (email.equals(accountEmail, true)) return
                    val token = HorusAccountStore.token(accountPrefs, email)
                    if (token.isNullOrBlank()) {
                        authReturnToProfile = true
                        pendingLoginEmail = email
                        showProfile = false
                        showLogin = true
                        return
                    }
                    accountScope.launch {
                        val result = runCatching { HorusAuthApi.currentUser(token) }.getOrNull()
                        if (result?.success == true) {
                            activateAccount(result.email.orEmpty(), result.firstName, result.lastName, result.phone, token, result.profileImageData)
                            Toast.makeText(this@MainActivity, "تم التبديل إلى ${result.email}", Toast.LENGTH_SHORT).show()
                        } else {
                            HorusAccountStore.clearToken(accountPrefs, email)
                            authReturnToProfile = true
                            pendingLoginEmail = email
                            showProfile = false
                            showLogin = true
                            Toast.makeText(this@MainActivity, "سجّل الدخول إلى هذا الحساب مرة أخرى.", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                suspend fun activateNextAvailableAccount(excludedEmail: String): Boolean {
                    val candidates = HorusAccountStore.accounts(accountPrefs)
                        .filterNot { it.email.equals(excludedEmail, true) }

                    // Prefer another account whose saved server session is still valid.
                    for (candidate in candidates) {
                        val savedToken = HorusAccountStore.token(accountPrefs, candidate.email)
                        if (savedToken.isNullOrBlank()) continue
                        val result = runCatching { HorusAuthApi.currentUser(savedToken) }.getOrNull()
                        if (result?.success == true) {
                            activateAccount(
                                result.email.orEmpty(), result.firstName, result.lastName,
                                result.phone, savedToken, result.profileImageData
                            )
                            Toast.makeText(
                                this@MainActivity,
                                "تم فتح الحساب الآخر: ${result.email}",
                                Toast.LENGTH_SHORT
                            ).show()
                            return true
                        }
                        HorusAccountStore.clearToken(accountPrefs, candidate.email)
                    }

                    // If another account exists but needs authentication, open its login
                    // instead of silently dropping the user into guest mode.
                    val remaining = HorusAccountStore.accounts(accountPrefs)
                    if (remaining.isNotEmpty()) {
                        pendingLoginEmail = remaining.first().email
                        authReturnToProfile = false
                        showProfile = false
                        showWelcomeSheet = false
                        showRegistration = false
                        showLogin = true
                        Toast.makeText(
                            this@MainActivity,
                            "الحساب الآخر موجود؛ سجّل الدخول إليه للمتابعة.",
                            Toast.LENGTH_LONG
                        ).show()
                        return true
                    }

                    accountEmail = null
                    profileImageUri = null
                    isGuestMode = true
                    hasCompletedOnboarding = true
                    showProfile = false
                    showLogin = false
                    showRegistration = false
                    showWelcomeSheet = false
                    return false
                }

                fun logoutCurrentAccount() {
                    val email = accountEmail ?: return
                    val token = HorusAccountStore.token(accountPrefs, email)
                    accountScope.launch {
                        if (!token.isNullOrBlank()) {
                            val success = runCatching { HorusAuthApi.logout(token) }.getOrDefault(false)
                            if (!success) {
                                Toast.makeText(this@MainActivity, "تعذر الاتصال بالخادم. لم يتم تسجيل الخروج.", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                        }
                        HorusAccountStore.removeAccount(accountPrefs, email, deleteProfile = false)
                        accountPrefs.edit().remove("email").remove("first_name").remove("last_name")
                            .remove("phone").remove("profile_image_uri")
                            .putBoolean("onboarding_completed", true).apply()
                        accountEmail = null
                        profileImageUri = null
                        savedAccounts = HorusAccountStore.accounts(accountPrefs)
                        showProfile = false
                        val switched = activateNextAvailableAccount(email)
                        if (!switched) {
                            Toast.makeText(
                                this@MainActivity,
                                "تم تسجيل الخروج. لا توجد حسابات أخرى، أنت الآن في وضع الضيف.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                fun deleteCurrentAccount(password: String) {
                    val email = accountEmail ?: return
                    val token = HorusAccountStore.token(accountPrefs, email)
                    if (token.isNullOrBlank()) {
                        authReturnToProfile = true
                        pendingLoginEmail = email
                        showProfile = false
                        showLogin = true
                        Toast.makeText(this@MainActivity, "سجّل الدخول مرة أخرى لتأكيد ملكية الحساب قبل الحذف.", Toast.LENGTH_LONG).show()
                        return
                    }
                    accountScope.launch {
                        val result = runCatching { HorusAuthApi.deleteAccountResult(token, password) }.getOrNull()
                        var deletionConfirmed = result?.first == true
                        // A response can be lost after the server committed the deletion.
                        if (!deletionConfirmed) {
                            val availability = runCatching {
                                HorusAuthApi.checkEmail(email.substringBefore("@"))
                            }.getOrNull()
                            deletionConfirmed = availability?.available == true
                        }
                        if (!deletionConfirmed) {
                            val message = result?.second?.takeIf { it.isNotBlank() }
                                ?: "تعذر تأكيد حذف الحساب. تحقق من الاتصال وحاول مجددًا."
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        HorusProfileImageStore.deleteLocal(this@MainActivity, email)
                        HorusAccountStore.removeAccount(accountPrefs, email, deleteProfile = true)
                        accountPrefs.edit().remove("email").remove("first_name").remove("last_name")
                            .remove("phone").remove("profile_image_uri")
                            .putBoolean("onboarding_completed", true).apply()
                        accountEmail = null
                        profileImageUri = null
                        savedAccounts = HorusAccountStore.accounts(accountPrefs)
                        showProfile = false
                        val switched = activateNextAvailableAccount(email)
                        if (!switched) {
                            Toast.makeText(
                                this@MainActivity,
                                "تم حذف الحساب نهائيًا. لا توجد حسابات أخرى، أنت الآن في وضع الضيف.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                LaunchedEffect(accountEmail) {
                    if (!accountEmail.isNullOrBlank() && savedAccounts.none { it.email.equals(accountEmail, true) }) {
                        HorusAccountStore.saveAccount(
                            accountPrefs,
                            HorusSavedAccount(
                                accountEmail!!,
                                accountPrefs.getString("first_name", "").orEmpty(),
                                accountPrefs.getString("last_name", "").orEmpty(),
                                accountPrefs.getString("phone", "").orEmpty(),
                                profileImageUri
                            ),
                            HorusAccountStore.token(accountPrefs, accountEmail!!)
                        )
                        savedAccounts = HorusAccountStore.accounts(accountPrefs)
                    }
                }
                var is3DMode by remember { mutableStateOf(remoteConfig.showBuildings3D && remoteConfig.initialZoom >= 14.0) }
                val profileImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        val email = accountEmail
                        val token = email?.let { HorusAccountStore.token(accountPrefs, it) }
                        if (email.isNullOrBlank() || token.isNullOrBlank()) {
                            Toast.makeText(this@MainActivity, "سجّل الدخول لحفظ صورة حسابك على السحابة.", Toast.LENGTH_LONG).show()
                        } else {
                            accountScope.launch {
                                val encoded = HorusProfileImageStore.encodeJpeg(this@MainActivity, uri)
                                if (encoded.isNullOrBlank()) {
                                    Toast.makeText(this@MainActivity, "تعذر قراءة الصورة. اختر صورة أخرى.", Toast.LENGTH_LONG).show()
                                } else {
                                    val uploaded = runCatching {
                                        HorusAuthApi.uploadProfileImage(token, encoded)
                                    }.getOrNull()
                                    if (uploaded?.first != true) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            uploaded?.second?.takeIf { it.isNotBlank() }
                                                ?: "تعذر حفظ الصورة على السحابة. حاول مرة أخرى.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        val savedUri = HorusProfileImageStore.saveBase64(
                                            this@MainActivity, email, "data:image/jpeg;base64,$encoded"
                                        )
                                        if (savedUri != null && accountEmail.equals(email, true)) {
                                            profileImageUri = savedUri
                                            accountPrefs.edit().putString("profile_image_uri", savedUri).apply()
                                            HorusAccountStore.updateImage(accountPrefs, email, savedUri)
                                            savedAccounts = HorusAccountStore.accounts(accountPrefs)
                                            Toast.makeText(this@MainActivity, "تم حفظ صورة حسابك على السحابة.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                    val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (granted) mapLibreMap?.getStyle { style ->
                        enableBlueLocationDot(mapLibreMap!!, style)
                        centerOnCurrentLocation(mapLibreMap!!)
                    }
                    else Toast.makeText(this@MainActivity, "يلزم السماح بإذن الموقع لتحديد موقعك الحالي.", Toast.LENGTH_LONG).show()
                }
                fun changeMapMode(enable3D: Boolean) {
                    if (is3DMode == enable3D) return
                    is3DMode = enable3D
                    mapLibreMap?.let { map ->
                        val current = map.cameraPosition
                        val nextCamera = org.maplibre.android.camera.CameraPosition.Builder()
                            .target(current.target)
                            .zoom(current.zoom)
                            .bearing(current.bearing)
                            .tilt(if (enable3D) 50.0 else 0.0)
                            .build()
                        map.animateCamera(CameraUpdateFactory.newCameraPosition(nextCamera), 650)
                    }
                }
                fun requestOrLocate() {
                    if (!remoteConfig.currentLocation) return
                    val fine = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) mapLibreMap?.let { centerOnCurrentLocation(it) }
                    else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }

                fun runMapSearch() {
                    activeSearchJob?.cancel()
                    searchGeneration += 1
                    val requestGeneration = searchGeneration
                    val rawQuery = searchQuery.trim()
                    val category = selectedSearchCategory ?: HorusSearchCategory.detect(rawQuery)
                    if (rawQuery.length < 2 && category == null) {
                        searchError = "اكتب اسم دولة أو قارة أو مكان للبحث."
                        searchExpanded = true
                        return
                    }
                    val map = mapLibreMap
                    if (map == null) {
                        searchError = "الخريطة ما زالت تُحمّل. حاول بعد لحظات."
                        return
                    }
                    selectedSearchCategory = category
                    searchLoading = true
                    searchHasRun = true
                    searchError = null
                    geographySearchResults = emptyList()
                    nearbySearchResults = emptyList()
                    searchExpanded = true
                    activeSearchJob = accountScope.launch {
                        try {
                            if (category != null) {
                                val location = bestKnownLocation(map)
                                if (location == null) {
                                    val fine = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    val coarse = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (!fine && !coarse) {
                                        searchError = "للعثور على أقرب فرع داخل دولتك، اسمح لـ HorusMaps باستخدام موقعك."
                                        locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    } else {
                                        searchError = "لم نتمكن من تحديد موقعك الحالي. تأكد من تشغيل GPS ثم انتظر لحظات وحاول مجددًا."
                                        centerOnCurrentLocation(map)
                                    }
                                    return@launch
                                }
                                val country = HorusMapSearchApi.reverseCountry(location.latitude, location.longitude)
                                val branchQuery = HorusSearchCategory.queryFor(rawQuery, category)
                                val results = HorusMapSearchApi.nearbyPlaces(branchQuery, category, location.latitude, location.longitude, country.code, rawQuery)
                                nearbySearchResults = results
                                if (results.isEmpty()) {
                                    searchError = "لم نعثر على نتائج مطابقة داخل " + country.name.ifBlank { "دولتك" } + " ضمن نطاق البحث."
                                }
                            } else {
                                val target = map.cameraPosition.target
                                val localContinent = HorusMapSearchApi.localContinent(this@MainActivity, rawQuery)
                                val results = if (localContinent != null) listOf(localContinent)
                                    else HorusMapSearchApi.geocode(rawQuery, target?.latitude, target?.longitude)
                                geographySearchResults = results
                                if (results.isEmpty()) searchError = "لم نعثر على مكان مطابق. جرّب كتابة الاسم بطريقة أخرى."
                            }
                        } catch (cancelled: CancellationException) {
                            // A query change, a newer request, or closing the panel can
                            // legitimately cancel this request. Never show cancellation
                            // as a user-facing search failure.
                            throw cancelled
                        } catch (error: Exception) {
                            if (requestGeneration == searchGeneration) {
                                searchError = error.message?.takeIf { it.isNotBlank() }
                                    ?: "حدث خطأ أثناء البحث. تحقق من اتصال الإنترنت وحاول مرة أخرى."
                            }
                        } finally {
                            if (requestGeneration == searchGeneration) {
                                searchLoading = false
                                activeSearchJob = null
                            }
                        }
                    }
                }

                LaunchedEffect(searchQuery, searchExpanded, searchHasRun) {
                    // Debounce only schedules a search. It must not cancel a request
                    // already started by the search button or keyboard action.
                    // Including searchHasRun as a key cancels the pending debounce as
                    // soon as a manual search begins.
                    val typedQuery = searchQuery.trim()
                    if (searchExpanded && typedQuery.length >= 2 && !searchHasRun) {
                        delay(1000L)
                        if (typedQuery == searchQuery.trim() && !searchHasRun) {
                            runMapSearch()
                        }
                    }
                }

                fun clearCalculatedRoute() {
                    mapLibreMap?.let { map -> routePolyline?.let { runCatching { map.removeAnnotation(it) } } }
                    routePolyline = null
                    calculatedRoute = null
                    routeCoordinates = emptyList()
                    navigationProgress = null
                    navigationManeuver = null
                    navigationStarted = false
                }

                fun cancelNavigation() {
                    clearCalculatedRoute()
                    navigationDestination = null
                    navigationError = null
                    routeLoading = false
                }

                fun selectGeographyResult(result: HorusGeoSearchResult) {
                    clearCalculatedRoute()
                    navigationDestination = HorusNavigationDestination(
                        name = result.name,
                        address = result.displayName,
                        latitude = result.latitude,
                        longitude = result.longitude
                    )
                    mapLibreMap?.let { focusSearchResult(it, result) }
                    searchExpanded = false
                    searchError = null
                    navigationError = null
                }

                fun selectNearbyResult(result: HorusNearbyPlace) {
                    clearCalculatedRoute()
                    navigationDestination = HorusNavigationDestination(
                        name = result.name,
                        address = listOf(result.address, result.region, result.country).filter { it.isNotBlank() }.distinct().joinToString("، "),
                        latitude = result.latitude,
                        longitude = result.longitude
                    )
                    mapLibreMap?.let { map ->
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(result.latitude, result.longitude), 16.5), 900)
                        showSearchMarker(map, result.latitude, result.longitude, result.name)
                    }
                    searchExpanded = false
                    searchError = null
                    navigationError = null
                }

                fun calculateSelectedRoute() {
                    val destination = navigationDestination ?: return
                    val map = mapLibreMap ?: return
                    val origin = bestKnownLocation(map)
                    if (origin == null) {
                        navigationError = "اسمح للتطبيق بالوصول إلى موقعك الحالي لتحديد نقطة البداية."
                        requestOrLocate()
                        return
                    }
                    routeLoading = true
                    navigationError = null
                    accountScope.launch {
                        try {
                            val result = HorusMapSearchApi.route(
                                origin.latitude,
                                origin.longitude,
                                destination.latitude,
                                destination.longitude,
                                selectedTravelMode
                            )
                            val coordinates = org.json.JSONObject(result.geometryGeoJson)
                                .getJSONArray("coordinates")
                            val points = buildList {
                                for (index in 0 until coordinates.length()) {
                                    val pair = coordinates.optJSONArray(index) ?: continue
                                    if (pair.length() < 2) continue
                                    add(LatLng(pair.getDouble(1), pair.getDouble(0)))
                                }
                            }
                            if (points.size < 2) throw IllegalStateException("لم يصل مسار صالح من خدمة الملاحة.")
                            routePolyline?.let { runCatching { map.removeAnnotation(it) } }
                            routePolyline = map.addPolyline(
                                PolylineOptions()
                                    .addAll(points)
                                    .color(android.graphics.Color.rgb(25, 118, 210))
                                    .width(6f)
                            )
                            routeCoordinates = points
                            calculatedRoute = result
                            bestKnownLocation(map)?.let { location ->
                                val progress = calculateRouteProgress(points, location, result)
                                navigationProgress = progress
                                navigationManeuver = progress?.let { selectNavigationManeuver(result, it) }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            navigationError = error.message ?: "تعذر حساب الطريق. حاول مرة أخرى."
                            calculatedRoute = null
                        } finally {
                            routeLoading = false
                        }
                    }
                }

                fun beginNavigation() {
                    if (calculatedRoute == null) return
                    navigationStarted = true
                    val activeRoute = calculatedRoute
                    val activeLocation = mapLibreMap?.let { bestKnownLocation(it) }
                    if (activeRoute != null && activeLocation != null) {
                        val progress = calculateRouteProgress(routeCoordinates, activeLocation, activeRoute)
                        navigationProgress = progress
                        navigationManeuver = progress?.let { selectNavigationManeuver(activeRoute, it) }
                    }
                    mapLibreMap?.let { map ->
                        runCatching {
                            if (map.locationComponent.isLocationComponentActivated) {
                                map.locationComponent.cameraMode = CameraMode.TRACKING
                            }
                        }
                    }
                }
                LaunchedEffect(navigationStarted, calculatedRoute, routeCoordinates) {
                    if (!navigationStarted || calculatedRoute == null || routeCoordinates.size < 2) {
                        navigationProgress = null
                        return@LaunchedEffect
                    }
                    val fine = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (!fine && !coarse) {
                        navigationError = "انقطع إذن الموقع؛ اسمح بالوصول للموقع لمتابعة تحديث المسافة المتبقية."
                        return@LaunchedEffect
                    }
                    val manager = getSystemService(LOCATION_SERVICE) as LocationManager
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            val activeRoute = calculatedRoute ?: return
                            val progress = calculateRouteProgress(routeCoordinates, location, activeRoute)
                            navigationProgress = progress
                            navigationManeuver = progress?.let { selectNavigationManeuver(activeRoute, it) }
                        }
                    }
                    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
                    try {
                        bestKnownLocation(mapLibreMap ?: return@LaunchedEffect)?.let { location ->
                            val progress = calculateRouteProgress(routeCoordinates, location, calculatedRoute!!)
                            navigationProgress = progress
                            navigationManeuver = progress?.let { selectNavigationManeuver(calculatedRoute!!, it) }
                        }
                        providers.forEach { provider ->
                            manager.requestLocationUpdates(provider, 1000L, 3f, listener, Looper.getMainLooper())
                        }
                    } catch (security: SecurityException) {
                        navigationError = "يلزم السماح بإذن الموقع لتحديث الملاحة."
                    } catch (error: Exception) {
                        android.util.Log.w("HorusMaps", "Navigation location updates unavailable", error)
                    }
                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        runCatching { manager.removeUpdates(listener) }
                    }
                }

                LaunchedEffect(Unit) {
                    HorusRemoteConfig.refresh(this@MainActivity) { updated ->
                        remoteConfig = updated
                        if (updated.currentLocation) requestOrLocate()
                    }
                }

                LaunchedEffect(remoteConfig.refreshIntervalSeconds) {
                    while (true) {
                        delay(remoteConfig.refreshIntervalSeconds * 1000L)
                        HorusRemoteConfig.refresh(this@MainActivity) { updated -> remoteConfig = updated }
                    }
                }

                LaunchedEffect(remoteConfig) {
                    if (!hasCompletedOnboarding) showWelcomeSheet = remoteConfig.showWelcomePanel
                    val map = mapLibreMap
                    if (map != null) {
                        accountScope.launch {
                            val globeStyleJson = fetchGlobeStyleJson(remoteConfig.styleUrl)
                            val styleBuilder = if (globeStyleJson != null) {
                                Style.Builder().fromJson(globeStyleJson)
                            } else {
                                Style.Builder().fromUri(remoteConfig.styleUrl)
                            }
                            map.setStyle(styleBuilder) { style ->
                                runCatching { installMapAppearance(this@MainActivity, style, remoteConfig.language) }
                                installPlaceFeatureClickListener(map) { destination ->
                                    clearCalculatedRoute()
                                    navigationDestination = destination
                                    navigationError = null
                                }
                                map.animateCamera(
                                CameraUpdateFactory.newCameraPosition(
                                    org.maplibre.android.camera.CameraPosition.Builder()
                                        .target(map.cameraPosition.target)
                                        .zoom(remoteConfig.initialZoom)
                                        .bearing(if (is3DMode) map.cameraPosition.bearing else 0.0)
                                        .tilt(if (is3DMode) 50.0 else 0.0)
                                        .build()
                                ), 500
                                )
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    delay(450)
                    if (!hasCompletedOnboarding) showWelcomeSheet = remoteConfig.showWelcomePanel
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { mapView },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                if (!mapInitializationRequested.value) {
                                    mapInitializationRequested.value = true
                                    view.getMapAsync { map ->
                                        mapLibreMap = map
                                         // Replace the MapLibre logo and native attribution info button with
                                         // a plain HorusMaps wordmark and a persistent, readable source credit.
                                         map.uiSettings.setLogoEnabled(false)
                                          map.uiSettings.setAttributionEnabled(false)
                                          // Keep the compass clear of the profile control in the top-right.
                                          // Place it in the upper-left corner with a comfortable inset.
                                          map.uiSettings.setCompassGravity(android.view.Gravity.TOP or android.view.Gravity.LEFT)
                                          val compassInset = (16 * resources.displayMetrics.density).toInt()
                                          val compassTop = (112 * resources.displayMetrics.density).toInt()
                                          map.uiSettings.setCompassMargins(compassInset, compassTop, 0, 0)
                                        val mapViewportWidthPx = resources.displayMetrics.widthPixels.toDouble()
                                        val singleWorldMinZoom = (
                                            kotlin.math.ln(mapViewportWidthPx / 512.0) / kotlin.math.ln(2.0) + 0.12
                                        ).coerceAtLeast(0.0)
                                        // The globe can fit the complete planet at zoom 0; Mercator's
                                        // one-world minimum would unnecessarily crop the spherical view.
                                        map.setMinZoomPreference(0.0)
                                        accountScope.launch {
                                            val globeStyleJson = fetchGlobeStyleJson(remoteConfig.styleUrl)
                                            val styleBuilder = if (globeStyleJson != null) {
                                                Style.Builder().fromJson(globeStyleJson)
                                            } else {
                                                Style.Builder().fromUri(remoteConfig.styleUrl)
                                            }
                                            map.setStyle(styleBuilder) { style ->
                                            installPlaceFeatureClickListener(map) { destination ->
                                                clearCalculatedRoute()
                                                navigationDestination = destination
                                                navigationError = null
                                            }
                                            // The old repository used this same Liberty vector
                                            // basemap, with localized labels and 3D buildings.
                                            runCatching { installMapAppearance(this@MainActivity, style, remoteConfig.language) }
                                                .onFailure { error ->
                                                    android.util.Log.e(
                                                        "HorusMaps",
                                                        "Map appearance customization failed",
                                                        error
                                                    )
                                                }
                                            map.cameraPosition =
                                                org.maplibre.android.camera.CameraPosition.Builder()
                                                    .zoom(remoteConfig.initialZoom)
                                                    .tilt(if (is3DMode) 50.0 else 0.0)
                                                    .build()
                                            val fine = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                            val coarse = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                            if (remoteConfig.currentLocation && (fine || coarse)) {
                                                enableBlueLocationDot(map, style)
                                                centerOnCurrentLocation(map)
                                            }
                                            }
                                        }
                                    }
                                }
                            }
                        )

                        AnimatedVisibility(
                            visible = navigationDestination != null && !navigationStarted,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 18.dp),
                            enter = slideInVertically(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                initialOffsetY = { it }
                            ) + fadeIn(tween(300)),
                            exit = slideOutVertically(
                                animationSpec = tween(320, easing = FastOutSlowInEasing),
                                targetOffsetY = { it }
                            ) + fadeOut(tween(220))
                        ) {
                            navigationDestination?.let { destination ->
                                HorusNavigationPanel(
                                    destination = destination,
                                    selectedMode = selectedTravelMode,
                                    route = calculatedRoute,
                                    calculating = routeLoading,
                                    navigating = navigationStarted,
                                    error = navigationError,
                                    onModeSelected = { mode ->
                                        if (selectedTravelMode != mode) {
                                            selectedTravelMode = mode
                                            clearCalculatedRoute()
                                            navigationError = when (mode) {
                                                "transit" -> "مسارات القطارات ستظهر عند توفر بيانات النقل العام للمنطقة."
                                                "flight" -> "الطيران يُعرض كتقدير للمسافة الجوية، وليس مسار ملاحة أرضي."
                                                else -> null
                                            }
                                        }
                                    },
                                    onCalculate = { calculateSelectedRoute() },
                                    onStart = { beginNavigation() },
                                    onCancel = { cancelNavigation() }
                                )
                            }
                        }

                        HorusMapSearchPanel(
                            query = searchQuery,
                            onQueryChange = { value ->
                                searchQuery = value
                                searchHasRun = false
                                searchError = null
                            },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                            loading = searchLoading,
                            hasSearched = searchHasRun,
                            error = searchError,
                            selectedCategory = selectedSearchCategory,
                            geographyResults = geographySearchResults,
                            nearbyResults = nearbySearchResults,
                            onCategorySelected = { category ->
                                selectedSearchCategory = category
                                searchQuery = ""
                                searchHasRun = false
                                searchError = null
                                geographySearchResults = emptyList()
                                nearbySearchResults = emptyList()
                                searchExpanded = true
                            },
                            onSearch = { runMapSearch() },
                            onSelectGeography = { selectGeographyResult(it) },
                            onSelectPlace = { selectNearbyResult(it) },
                            onClose = {
                                searchExpanded = false
                                searchQuery = ""
                                searchHasRun = false
                                searchError = null
                                selectedSearchCategory = null
                                geographySearchResults = emptyList()
                                nearbySearchResults = emptyList()
                            },
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
                                .padding(start = 12.dp, end = 78.dp, top = 8.dp)
                        )

                        if (navigationStarted && navigationProgress != null) {
                            HorusNavigationActiveHud(
                                remainingMeters = navigationProgress!!.remainingMeters,
                                remainingSeconds = navigationProgress!!.remainingSeconds,
                                maneuver = navigationManeuver?.current,
                                distanceToManeuverMeters = navigationManeuver?.distanceToCurrentMeters,
                                onStop = { cancelNavigation() },
                                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
                                    .padding(top = 74.dp)
                            )
                        }

                        // MapLibre-style plain wordmark in the lower-right corner (no badge or info icon).
                        Text(
                            text = "HorusMaps",
                            modifier = Modifier
                                .align(androidx.compose.ui.AbsoluteAlignment.BottomRight)
                                .navigationBarsPadding()
                                .padding(end = 8.dp, bottom = 2.dp),
                            color = Color(0xFF263238),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // Required source attribution stays discreet in the opposite corner
                        // from the HorusMaps wordmark, with both source names remaining tappable.
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .navigationBarsPadding()
                                .padding(start = 8.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "© OpenStreetMap",
                                modifier = Modifier.clickable {
                                    startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://openstreetmap.org/copyright")
                                        )
                                    )
                                },
                                color = Color(0xFF667085).copy(alpha = 0.72f),
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                            Text(" · ", color = Color(0xFF667085).copy(alpha = 0.72f), fontSize = 7.sp)
                            Text(
                                text = "OpenMapTiles",
                                modifier = Modifier.clickable {
                                    startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://openmaptiles.org/")
                                        )
                                    )
                                },
                                color = Color(0xFF667085).copy(alpha = 0.72f),
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(end = 18.dp, bottom = if (remoteConfig.currentLocation) 96.dp else 28.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { changeMapMode(!is3DMode) },
                                modifier = Modifier.height(46.dp),
                                shape = RoundedCornerShape(15.dp),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF26364A)
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 7.dp)
                            ) {
                                Text(
                                    text = if (is3DMode) "3D" else "2D",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        if (isGuestMode || !accountEmail.isNullOrBlank()) {
                            if (isGuestMode) {
                                GuestAccessProfileButton(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .statusBarsPadding()
                                        .padding(top = 12.dp, end = 18.dp),
                                    onClick = { showProfile = true }
                                )
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .statusBarsPadding()
                                        .padding(top = 12.dp, end = 18.dp)
                                        .size(50.dp),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = Color.White,
                                    shadowElevation = 8.dp
                                ) {
                                    androidx.compose.material3.IconButton(
                                        onClick = { showProfile = true },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        ProfileAvatar(
                                            imageUri = profileImageUri,
                                            firstName = accountPrefs.getString("first_name", "").orEmpty(),
                                            lastName = accountPrefs.getString("last_name", "").orEmpty(),
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (remoteConfig.currentLocation) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .navigationBarsPadding()
                                    .padding(end = 18.dp, bottom = 28.dp)
                                    .size(54.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = Color.White,
                                shadowElevation = 7.dp
                            ) {
                                androidx.compose.material3.IconButton(
                                    onClick = { requestOrLocate() },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MyLocation,
                                        contentDescription = "تحديد موقعي الحالي",
                                        tint = Color(0xFF287FE8),
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = showProfile,
                            modifier = Modifier.fillMaxSize(),
                            enter = fadeIn(tween(220)),
                            exit = fadeOut(tween(180))
                        ) {
                            ProfilePanel(
                                firstName = accountPrefs.getString("first_name", "").orEmpty(),
                                lastName = accountPrefs.getString("last_name", "").orEmpty(),
                                email = accountEmail.orEmpty(),
                                phone = accountPrefs.getString("phone", "").orEmpty(),
                                imageUri = profileImageUri,
                                isGuest = isGuestMode,
                                is3DMode = is3DMode,
                                accounts = savedAccounts,
                                currentEmail = accountEmail,
                                onDismiss = { showProfile = false },
                                onPickImage = { profileImagePicker.launch(arrayOf("image/*")) },
                                onOpenWelcome = {
                                    showProfile = false
                                    showWelcomeSheet = true
                                },
                                onAddAccount = { openAddAccount() },
                                onSwitchAccount = { email -> switchToAccount(email) },
                                onLogout = { logoutCurrentAccount() },
                                onDeleteAccount = { password -> deleteCurrentAccount(password) },
                                onToggle3D = { enabled -> changeMapMode(enabled) }
                            )
                        }

                        AnimatedVisibility(
                            visible = showWelcomeSheet,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            enter = slideInVertically(
                                animationSpec = tween(
                                    durationMillis = 650,
                                    easing = FastOutSlowInEasing
                                ),
                                initialOffsetY = { fullHeight -> fullHeight }
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = 500)
                            ),
                            exit = slideOutVertically(
                                animationSpec = tween(
                                    durationMillis = 550,
                                    easing = FastOutSlowInEasing
                                ),
                                targetOffsetY = { fullHeight -> fullHeight }
                            ) + fadeOut(
                                animationSpec = tween(durationMillis = 350)
                            )
                        ) {
                            WelcomeBottomSheet(
                                onCreateAccount = {
                                    showWelcomeSheet = false
                                    showRegistration = true
                                },
                                onSignIn = {
                                    showWelcomeSheet = false
                                    showLogin = true
                                },
                                onContinueAsGuest = {
                                    accountPrefs.edit().putBoolean("onboarding_completed", true).apply()
                                    hasCompletedOnboarding = true
                                    isGuestMode = accountEmail.isNullOrBlank()
                                    showWelcomeSheet = false
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = showRegistration,
                            modifier = Modifier.fillMaxSize(),
                            enter = slideInVertically(
                                animationSpec = tween(520, easing = FastOutSlowInEasing),
                                initialOffsetY = { it / 3 }
                            ) + fadeIn(tween(420)),
                            exit = slideOutVertically(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                targetOffsetY = { it / 3 }
                            ) + fadeOut(tween(300))
                        ) {
                            HorusRegistrationFlow(
                                onBack = {
                                    showRegistration = false
                                    if (authReturnToProfile) {
                                        showProfile = true
                                        authReturnToProfile = false
                                        pendingLoginEmail = ""
                                    } else showWelcomeSheet = true
                                },
                                onLoginRequested = {
                                    showRegistration = false
                                    showLogin = true
                                },
                                onAccountCreated = { email, firstName, lastName, phone, token ->
                                    activateAccount(email, firstName, lastName, phone, token)
                                    Toast.makeText(this@MainActivity, "تم إنشاء حسابك: $email", Toast.LENGTH_LONG).show()
                                }
                            )
                        }

                        AnimatedVisibility(
                            visible = showLogin,
                            modifier = Modifier.fillMaxSize(),
                            enter = slideInVertically(
                                animationSpec = tween(520, easing = FastOutSlowInEasing),
                                initialOffsetY = { it / 3 }
                            ) + fadeIn(tween(420)),
                            exit = slideOutVertically(
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                targetOffsetY = { it / 3 }
                            ) + fadeOut(tween(300))
                        ) {
                            HorusLoginFlow(
                                initialEmail = pendingLoginEmail,
                                onBack = {
                                    showLogin = false
                                    if (authReturnToProfile) {
                                        showProfile = true
                                        authReturnToProfile = false
                                        pendingLoginEmail = ""
                                    } else showWelcomeSheet = true
                                },
                                onCreateAccount = {
                                    showLogin = false
                                    showRegistration = true
                                },
                                onLoginSuccess = { email, firstName, lastName, phone, token ->
                                    activateAccount(email, firstName, lastName, phone, token)
                                }
                            )
                        }
                    }
                }

                DisposableEffect(lifecycleOwner, mapView) {
                    mapView.onCreate(savedInstanceState)

                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> mapView.onStart()
                            Lifecycle.Event.ON_RESUME -> mapView.onResume()
                            Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                            Lifecycle.Event.ON_STOP -> mapView.onStop()
                            else -> Unit
                        }
                    }

                    lifecycleOwner.lifecycle.addObserver(observer)

                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        mapView.onDestroy()
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun GuestAccessProfileButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "guestProfileGlow")
    val glow by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050),
            repeatMode = RepeatMode.Reverse
        ),
        label = "guestProfileGlowAlpha"
    )

    Box(
        modifier = modifier
            .size(56.dp)
            .border(
                width = 2.dp,
                color = Color(0xFF36A5FF).copy(alpha = glow),
                shape = androidx.compose.foundation.shape.CircleShape
            )
            .padding(3.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = Color(0xFF102D4A),
            shadowElevation = 10.dp
        ) {
            androidx.compose.material3.IconButton(
                onClick = onClick,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "خيارات الحساب",
                    tint = Color(0xFF8ED0FF),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WelcomeBottomSheet(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(
            topStart = 30.dp,
            topEnd = 30.dp
        ),
        color = Color.White,
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFD9DEE7))
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "أهلاً بك في HorusMaps",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF172033),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "اكتشف الأماكن من حولك، واعثر على طريقك بسهولة.",
                fontSize = 14.sp,
                color = Color(0xFF697386),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onCreateAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF087FE5),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "إنشاء حساب",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF087FE5)
                )
            ) {
                Text(
                    text = "تسجيل الدخول",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onContinueAsGuest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF0F5FA),
                    contentColor = Color(0xFF26364A)
                )
            ) {
                Text(
                    text = "استخدام بدون حساب",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
