package com.horusmaps.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val mapView = remember { MapView(this@MainActivity) }

                    DisposableEffect(lifecycleOwner, mapView) {
                        mapView.onCreate(null)

                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_START -> mapView.onStart()
                                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                                Lifecycle.Event.ON_STOP -> mapView.onStop()
                                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                                else -> Unit
                            }
                        }

                        lifecycleOwner.lifecycle.addObserver(observer)

                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            mapView.onDestroy()
                        }
                    }

                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.getMapAsync { map ->
                                if (map.style == null) {
                                    map.setStyle(
                                        Style.Builder().fromUri(
                                            "https://tiles.openfreemap.org/styles/liberty"
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
