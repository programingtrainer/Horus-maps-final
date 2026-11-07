package com.horusmaps.app

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode

private fun installMapAppearance(context: Context, style: Style, configuredLanguage: String, showBuildings3D: Boolean) {
    // Replace dense political details at world scale with a clean land outline and continent labels.
    runCatching {
        style.getLayer("label_country_1")?.minZoom = 1.8f
        style.getLayer("label_country_2")?.minZoom = 1.8f
        (style.getLayer("boundary_2") as? LineLayer)?.minZoom = 2.0f
        if (style.getSource("horus-continent-land") == null) {
            val landGeoJson = context.assets.open("continents_land.geojson").bufferedReader().use { it.readText() }
            style.addSource(GeoJsonSource("horus-continent-land", landGeoJson))
            val outline = LineLayer("horus-continent-outline", "horus-continent-land").apply {
                minZoom = 0.0f
                maxZoom = 1.95f
                setProperties(PropertyFactory.lineColor("#8194A9"), PropertyFactory.lineWidth(1.15f), PropertyFactory.lineOpacity(0.82f))
            }
            val firstLabel = style.layers.firstOrNull { it is SymbolLayer }
            if (firstLabel != null) style.addLayerBelow(outline, firstLabel.id) else style.addLayer(outline)
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
            style.addSource(GeoJsonSource("horus-continent-labels", labelsJson))
            val useArabic = (if (configuredLanguage == "device") java.util.Locale.getDefault().language else configuredLanguage).startsWith("ar", ignoreCase = true)
            val text = if (useArabic) Expression.get("name_ar") else Expression.get("name_en")
            val continentLabels = SymbolLayer("horus-continent-labels-layer", "horus-continent-labels").apply {
                minZoom = 0.0f
                maxZoom = 1.95f
                setProperties(PropertyFactory.textField(text), PropertyFactory.textSize(13f), PropertyFactory.textColor("#34465C"), PropertyFactory.textHaloColor("#FFFFFF"), PropertyFactory.textHaloWidth(1.6f), PropertyFactory.textAllowOverlap(true), PropertyFactory.textIgnorePlacement(true))
            }
            style.addLayer(continentLabels)
        }
    }.onFailure { android.util.Log.e("HorusMaps", "Unable to install continent overview layers", it) }
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

    // Let the remote setting explicitly enable or disable 3D building layers.
    if (!showBuildings3D) {
        style.layers
            .filterIsInstance<org.maplibre.android.style.layers.FillExtrusionLayer>()
            .forEach {
                it.setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.visibility(
                        org.maplibre.android.style.layers.Property.NONE
                    )
                )
            }
    }

    // Keep the old map's 3D building appearance without duplicating
    // an extrusion layer already supplied by the Liberty style.
    if (showBuildings3D && style.layers.none { it is org.maplibre.android.style.layers.FillExtrusionLayer }) {
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

private fun applyMapDimension(
    map: org.maplibre.android.maps.MapLibreMap,
    style: Style,
    is3D: Boolean
) {
    // Keep 3D building extrusions visible in both camera modes.
    // The 2D/3D toggle changes only the camera tilt.

    val current = map.cameraPosition
    val target = org.maplibre.android.camera.CameraPosition.Builder()
        .target(current.target)
        .zoom(current.zoom)
        .bearing(current.bearing)
        .tilt(if (is3D) 55.0 else 0.0)
        .build()

    map.animateCamera(CameraUpdateFactory.newCameraPosition(target), 550)
}

class MainActivity : ComponentActivity() {
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
                var isMap3D by remember { mutableStateOf(false) }
                var remoteConfig by remember { mutableStateOf(HorusRemoteConfig.loadCached(this@MainActivity)) }
                var showWelcomeSheet by remember { mutableStateOf(remoteConfig.showWelcomePanel) }
                val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                    val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (granted) mapLibreMap?.getStyle { style ->
                        enableBlueLocationDot(mapLibreMap!!, style)
                        centerOnCurrentLocation(mapLibreMap!!)
                    }
                    else Toast.makeText(this@MainActivity, "يلزم السماح بإذن الموقع لتحديد موقعك الحالي.", Toast.LENGTH_LONG).show()
                }
                fun requestOrLocate() {
                    if (!remoteConfig.currentLocation) return
                    val fine = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) mapLibreMap?.let { centerOnCurrentLocation(it) }
                    else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
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
                    showWelcomeSheet = remoteConfig.showWelcomePanel
                    val map = mapLibreMap
                    if (map != null) {
                        map.setStyle(Style.Builder().fromUri(remoteConfig.styleUrl)) { style ->
                            runCatching {
                                installMapAppearance(this@MainActivity, style, remoteConfig.language, remoteConfig.showBuildings3D)
                                applyMapDimension(map, style, isMap3D)
                            }
                            map.animateCamera(CameraUpdateFactory.zoomTo(remoteConfig.initialZoom), 500)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    delay(450)
                    showWelcomeSheet = remoteConfig.showWelcomePanel
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
                                        // Fit one world across the viewport at maximum zoom-out; keep horizontal wrapping enabled.
                                        val mapWidthPx = view.width.toDouble().takeIf { it > 0.0 } ?: resources.displayMetrics.widthPixels.toDouble()
                                        val oneWorldMinZoom = (kotlin.math.ln(mapWidthPx / 512.0) / kotlin.math.ln(2.0) + 0.08).coerceIn(0.0, 2.0)
                                        map.setMinZoomPreference(oneWorldMinZoom)
                                        map.setStyle(
                                            Style.Builder().fromUri(remoteConfig.styleUrl)
                                        ) { style ->
                                            // The old repository used this same Liberty vector
                                            // basemap, with localized labels and 3D buildings.
                                            runCatching {
                                                installMapAppearance(this@MainActivity, style, remoteConfig.language, remoteConfig.showBuildings3D)
                                                applyMapDimension(map, style, isMap3D)
                                            }
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
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .padding(end = 18.dp, bottom = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(54.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = Color.White,
                                shadowElevation = 7.dp
                            ) {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        isMap3D = !isMap3D
                                        mapLibreMap?.getStyle { style ->
                                            applyMapDimension(mapLibreMap!!, style, isMap3D)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = if (isMap3D) "2D" else "3D",
                                        color = Color(0xFF287FE8),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (remoteConfig.currentLocation) {
                                Surface(
                                    modifier = Modifier.size(54.dp),
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
                                    Toast.makeText(
                                        this@MainActivity,
                                        "إنشاء الحساب سيتم تفعيله في مرحلة المصادقة",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onSignIn = {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "تسجيل الدخول سيتم تفعيله في مرحلة المصادقة",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onContinueAsGuest = {
                                    showWelcomeSheet = false
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
