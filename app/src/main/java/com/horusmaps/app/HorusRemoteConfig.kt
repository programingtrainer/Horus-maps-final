package com.horusmaps.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HorusRemoteConfig(
    val styleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
    val initialZoom: Double = 2.0,
    val showBuildings3D: Boolean = true,
    val language: String = "device",
    val currentLocation: Boolean = true,
    val showWelcomePanel: Boolean = true,
    val refreshIntervalSeconds: Long = 300L
) {
    companion object {
        private const val ENDPOINT =
            "https://horus-maps-final.horus-egypt-company.workers.dev/v1/config"
        private const val PREFS = "horus_remote_config"
        private const val CONFIG_KEY = "config_json"

        fun loadCached(context: Context): HorusRemoteConfig {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(CONFIG_KEY, null) ?: return HorusRemoteConfig()
            return parse(raw) ?: HorusRemoteConfig()
        }

        fun refresh(context: Context, onComplete: (HorusRemoteConfig) -> Unit) {
            Thread {
                var result = loadCached(context)
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5000
                        readTimeout = 5000
                        setRequestProperty("Accept", "application/json")
                    }
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        val parsed = parse(body)
                        if (parsed != null) {
                            val configJson = JSONObject(body).getJSONObject("config").toString()
                            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .edit().putString(CONFIG_KEY, configJson).apply()
                            result = parsed
                        }
                    }
                } catch (_: Exception) {
                    // Keep the last known good configuration or built-in defaults.
                } finally {
                    connection?.disconnect()
                }
                Handler(Looper.getMainLooper()).post { onComplete(result) }
            }.start()
        }

        private fun parse(raw: String): HorusRemoteConfig? = try {
            val root = JSONObject(raw)
            val config = if (root.has("config")) root.getJSONObject("config") else root
            val app = config.optJSONObject("app") ?: JSONObject()
            val map = config.optJSONObject("map") ?: JSONObject()
            val features = config.optJSONObject("features") ?: JSONObject()
            val ui = config.optJSONObject("ui") ?: JSONObject()
            val refresh = config.optJSONObject("refresh") ?: JSONObject()

            if (app.optString("name", "HorusMaps") != "HorusMaps") return null
            HorusRemoteConfig(
                styleUrl = map.optString(
                    "styleUrl",
                    "https://tiles.openfreemap.org/styles/liberty"
                ).takeIf { it.startsWith("https://") }
                    ?: "https://tiles.openfreemap.org/styles/liberty",
                initialZoom = map.optDouble("initialZoom", 2.0).coerceIn(0.0, 20.0),
                showBuildings3D = map.optBoolean("showBuildings3D", true),
                language = map.optString("language", "device"),
                currentLocation = features.optBoolean("currentLocation", true),
                showWelcomePanel = ui.optBoolean("showWelcomePanel", true),
                refreshIntervalSeconds = refresh.optLong("intervalSeconds", 300L)
                    .coerceIn(60L, 86400L)
            )
        } catch (_: Exception) {
            null
        }
    }
}
