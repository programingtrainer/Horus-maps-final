package com.horusmaps.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

enum class HorusSearchCategory(val apiValue: String, val title: String) {
    MOSQUE("mosque", "مساجد"),
    SUPERMARKET("supermarket", "سوبر ماركت"),
    HOSPITAL("hospital", "مستشفيات"),
    PHARMACY("pharmacy", "صيدليات"),
    RESTAURANT("restaurant", "مطاعم"),
    FUEL("fuel", "محطات وقود");

    companion object {
        fun detect(raw: String): HorusSearchCategory? {
            val value = normalize(raw)
            return when {
                listOf("مسجد", "مساجد", "جامع", "جوامع", "mosque", "mosques", "masjid").any { value.contains(it) } -> MOSQUE
                listOf("سوبرماركت", "سوبر ماركت", "ماركت", "متجر", "supermarket", "grocery", "بنده", "panda", "كارفور", "carrefour", "لولو", "lulu", "دانوب", "danube", "العثيم", "othaim").any { value.contains(it) } -> SUPERMARKET
                listOf("مستشفى", "مستشفيات", "hospital", "hospitals").any { value.contains(it) } -> HOSPITAL
                listOf("صيدلية", "صيدليات", "pharmacy", "pharmacies").any { value.contains(it) } -> PHARMACY
                listOf("مطعم", "مطاعم", "restaurant", "restaurants", "cafe", "كافيه").any { value.contains(it) } -> RESTAURANT
                listOf("محطة وقود", "بنزين", "وقود", "fuel", "gas station", "petrol").any { value.contains(it) } -> FUEL
                else -> null
            }
        }

        fun queryFor(raw: String, category: HorusSearchCategory): String {
            val value = normalize(raw)
            val generic = when (category) {
                MOSQUE -> listOf("مسجد", "مساجد", "جامع", "جوامع", "mosque", "mosques", "masjid")
                SUPERMARKET -> listOf("سوبرماركت", "سوبر ماركت", "ماركت", "متجر", "supermarket", "grocery")
                HOSPITAL -> listOf("مستشفى", "مستشفيات", "hospital", "hospitals")
                PHARMACY -> listOf("صيدلية", "صيدليات", "pharmacy", "pharmacies")
                RESTAURANT -> listOf("مطعم", "مطاعم", "restaurant", "restaurants", "cafe", "كافيه")
                FUEL -> listOf("محطة وقود", "بنزين", "وقود", "fuel", "gas station", "petrol")
            }.map(::normalize)
            if (generic.any { it == value }) return ""
            return raw.replace(Regex("(?i)سوبر\\s*ماركت|ماركت|supermarket|grocery|mosques?|masjid|hospitals?|pharmacies?|restaurants?|gas\\s*station|petrol|fuel|مطاعم|مطعم|مساجد|مسجد|جوامع|جامع|مستشفيات|مستشفى|صيدليات|صيدلية|محطة\\s*وقود|وقود|بنزين|كافيه"), "")
                .trim()
        }

        private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي').replace("ـ", "")
            .replace(Regex("[\\u064B-\\u065F]"), "")
            .trim()
    }
}

data class HorusGeoBounds(
    val south: Double,
    val north: Double,
    val west: Double,
    val east: Double
)

data class HorusGeoSearchResult(
    val id: String,
    val name: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val geometryGeoJson: String,
    val bounds: HorusGeoBounds?,
    val kind: String
)

data class HorusNearbyPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
    val address: String,
    val brand: String,
    val openingHours: String
)

data class HorusCountryInfo(val code: String, val name: String)

object HorusMapSearchApi {
    private const val BASE_URL = "https://horus-maps-final.horus-egypt-company.workers.dev/v1/search"
    private const val USER_AGENT = "HorusMaps-Android/0.1"

    fun localContinent(context: android.content.Context, query: String): HorusGeoSearchResult? {
        val value = normalizeSearchText(query)
        val aliases = mapOf(
            "Africa" to listOf("أفريقيا", "افريقيا", "Africa"),
            "Asia" to listOf("آسيا", "اسيا", "Asia"),
            "Europe" to listOf("أوروبا", "اوروبا", "Europe"),
            "North America" to listOf("أمريكا الشمالية", "امريكا الشمالية", "North America"),
            "South America" to listOf("أمريكا الجنوبية", "امريكا الجنوبية", "South America"),
            "Oceania" to listOf("أوقيانوسيا", "اوقيانوسيا", "Oceania"),
            "Antarctica" to listOf("القارة القطبية الجنوبية", "انتاركتيكا", "Antarctica")
        )
        val continent = aliases.entries.firstOrNull { (_, names) ->
            names.any { normalizeSearchText(it) == value || value.contains(normalizeSearchText(it)) }
        }?.key ?: return null
        return runCatching {
            val root = JSONObject(context.assets.open("continents.geojson").bufferedReader().use { it.readText() })
            val features = root.optJSONArray("features") ?: return null
            for (index in 0 until features.length()) {
                val feature = features.optJSONObject(index) ?: continue
                val properties = feature.optJSONObject("properties") ?: continue
                if (!properties.optString("continent").equals(continent, true)) continue
                val geometry = feature.optJSONObject("geometry") ?: continue
                val points = mutableListOf<Pair<Double, Double>>()
                collectCoordinates(geometry.optJSONArray("coordinates"), points)
                if (points.isEmpty()) continue
                val minLon = points.minOf { it.first }
                val maxLon = points.maxOf { it.first }
                val minLat = points.minOf { it.second }
                val maxLat = points.maxOf { it.second }
                val displayName = properties.optString("name_ar", properties.optString("name_en", continent))
                return HorusGeoSearchResult(
                    id = "continent/$continent",
                    name = displayName,
                    displayName = displayName,
                    latitude = (minLat + maxLat) / 2.0,
                    longitude = (minLon + maxLon) / 2.0,
                    geometryGeoJson = JSONObject()
                        .put("type", "Feature")
                        .put("geometry", geometry)
                        .put("properties", properties)
                        .toString(),
                    bounds = HorusGeoBounds(minLat, maxLat, minLon, maxLon),
                    kind = "continent"
                )
            }
            null
        }.getOrNull()
    }

    private fun normalizeSearchText(value: String): String = value.lowercase(Locale.ROOT)
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace("ـ", "")
        .replace(Regex("[\\\\u064B-\\\\u065F]"), "")
        .replace("قارة", "")
        .trim()

    suspend fun geocode(query: String, latitude: Double?, longitude: Double?): List<HorusGeoSearchResult> =
        withContext(Dispatchers.IO) {
            val builder = Uri.parse("$BASE_URL/geocode").buildUpon()
                .appendQueryParameter("q", query.trim())
                .appendQueryParameter("lang", Locale.getDefault().toLanguageTag())
            if (latitude != null && longitude != null) {
                builder.appendQueryParameter("lat", latitude.toString())
                    .appendQueryParameter("lon", longitude.toString())
            }
            val root = JSONObject(get(builder.build().toString()))
            if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "تعذر تنفيذ البحث."))
            val features = root.optJSONArray("features") ?: JSONArray()
            buildList {
                for (index in 0 until features.length()) {
                    val feature = features.optJSONObject(index) ?: continue
                    val geometry = feature.optJSONObject("geometry") ?: continue
                    val properties = feature.optJSONObject("properties") ?: JSONObject()
                    val coordinates = geometry.optJSONArray("coordinates")
                    val points = mutableListOf<Pair<Double, Double>>()
                    collectCoordinates(coordinates, points)
                    if (points.isEmpty()) continue
                    val minLon = points.minOf { it.first }
                    val maxLon = points.maxOf { it.first }
                    val minLat = points.minOf { it.second }
                    val maxLat = points.maxOf { it.second }
                    val centerLat = (minLat + maxLat) / 2.0
                    val centerLon = (minLon + maxLon) / 2.0
                    val display = properties.optString("display_name", properties.optString("name", query))
                    add(
                        HorusGeoSearchResult(
                            id = properties.optString("osm_type") + "/" + properties.optString("osm_id", index.toString()),
                            name = properties.optString("name").takeIf { it.isNotBlank() } ?: display.substringBefore(","),
                            displayName = display,
                            latitude = centerLat,
                            longitude = centerLon,
                            geometryGeoJson = JSONObject()
                                .put("type", "Feature")
                                .put("geometry", geometry)
                                .put("properties", JSONObject().put("name", properties.optString("name")))
                                .toString(),
                            bounds = if (maxLat - minLat > 0.0001 || maxLon - minLon > 0.0001) {
                                HorusGeoBounds(minLat, maxLat, minLon, maxLon)
                            } else null,
                            kind = properties.optString("type", properties.optString("category", "place"))
                        )
                    )
                }
            }
        }

    suspend fun reverseCountry(latitude: Double, longitude: Double): HorusCountryInfo =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse("$BASE_URL/reverse").buildUpon()
                .appendQueryParameter("lat", latitude.toString())
                .appendQueryParameter("lon", longitude.toString())
                .build()
            val root = JSONObject(get(uri.toString()))
            if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "تعذر تحديد الدولة."))
            val code = root.optString("countryCode").lowercase(Locale.ROOT)
            if (code.length != 2) throw IllegalStateException("لم نتمكن من تحديد الدولة من موقعك الحالي.")
            HorusCountryInfo(code, root.optString("country"))
        }

    suspend fun nearbyPlaces(
        query: String,
        category: HorusSearchCategory,
        latitude: Double,
        longitude: Double,
        countryCode: String
    ): List<HorusNearbyPlace> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("query", query)
            .put("category", category.apiValue)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("countryCode", countryCode)
        val root = JSONObject(post("$BASE_URL/places", body))
        if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "تعذر تحميل الأماكن القريبة."))
        val results = root.optJSONArray("results") ?: JSONArray()
        buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                add(
                    HorusNearbyPlace(
                        id = item.optString("id", index.toString()),
                        name = item.optString("name", "مكان بدون اسم"),
                        latitude = item.optDouble("latitude", Double.NaN),
                        longitude = item.optDouble("longitude", Double.NaN),
                        distanceMeters = item.optInt("distanceMeters", 0),
                        address = item.optString("address"),
                        brand = item.optString("brand"),
                        openingHours = item.optString("openingHours")
                    )
                )
            }
        }.filter { it.latitude.isFinite() && it.longitude.isFinite() }
            .sortedBy { it.distanceMeters }
    }

    private fun collectCoordinates(value: JSONArray?, output: MutableList<Pair<Double, Double>>) {
        if (value == null) return
        if (value.length() >= 2 && value.opt(0) is Number && value.opt(1) is Number) {
            val longitude = (value.opt(0) as Number).toDouble()
            val latitude = (value.opt(1) as Number).toDouble()
            if (longitude in -180.0..180.0 && latitude in -90.0..90.0) {
                output += longitude to latitude
            }
            return
        }
        for (index in 0 until value.length()) {
            collectCoordinates(value.optJSONArray(index), output)
        }
    }

    private fun get(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 22_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return readResponse(connection)
    }

    private fun post(address: String, body: JSONObject): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 35_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): String {
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "خطأ في الاتصال بمحرك البحث ($status).")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }
}
