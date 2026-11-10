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
                listOf("مسجد", "مساجد", "جامع", "جوامع", "مصلى", "mosque", "mosques", "masjid", "prayer hall").any { matchesTerm(value, it) } -> MOSQUE
                listOf("سوبرماركت", "سوبر ماركت", "هايبر ماركت", "هايبرماركت", "ماركت", "متجر", "بقالة", "بقاله", "تموينات", "سوق غذائي", "supermarket", "super market", "hypermarket", "grocery", "grocery store", "market", "بنده", "panda", "كارفور", "carrefour", "لولو", "lulu", "دانوب", "danube", "العثيم", "othaim").any { matchesTerm(value, it) } -> SUPERMARKET
                listOf("مستشفى", "مستشفي", "مستشفيات", "مستشفيّات", "مركز طبي", "مركز صحي", "مراكز طبية", "hospital", "hospitals", "clinic", "medical center", "medical centre", "healthcare").any { matchesTerm(value, it) } -> HOSPITAL
                listOf("صيدلية", "صيدليه", "صيدليات", "صيدليات", "صيدليه", "pharmacy", "pharmacies", "chemist", "drugstore", "drug store").any { matchesTerm(value, it) } -> PHARMACY
                listOf("مطعم", "مطاعم", "مطعم وجبات سريعة", "restaurant", "restaurants", "fast food", "cafe", "coffee shop", "كافيه", "مقهى", "مقاهي").any { matchesTerm(value, it) } -> RESTAURANT
                listOf("محطة وقود", "محطات وقود", "محطة بنزين", "محطات بنزين", "محطة بترول", "محطات بترول", "محطة محروقات", "محطات المحروقات", "بنزين", "بنزينة", "بنزينه", "بترول", "وقود", "محروقات", "تعبئة وقود", "fuel", "fuel station", "gas station", "gas stations", "petrol station", "petrol stations", "filling station", "gasoline station", "service station", "petrol pump", "gasoline").any { matchesTerm(value, it) } -> FUEL
                else -> null
            }
        }

        fun queryFor(raw: String, category: HorusSearchCategory): String {
            val genericTokens = when (category) {
                MOSQUE -> setOf("مسجد", "مساجد", "جامع", "جوامع", "مصلى", "mosque", "mosques", "masjid", "prayer", "hall")
                SUPERMARKET -> setOf("سوبر", "ماركت", "سوبرماركت", "هايبر", "هايبرماركت", "متجر", "بقالة", "بقاله", "تموينات", "supermarket", "super", "market", "hypermarket", "grocery", "shop")
                HOSPITAL -> setOf("مستشفى", "مستشفي", "مستشفيات", "مركز", "طبي", "صحي", "hospital", "hospitals", "clinic", "medical", "center", "centre", "healthcare")
                PHARMACY -> setOf("صيدلية", "صيدليه", "صيدليات", "pharmacy", "pharmacies", "chemist", "drugstore", "drug", "store")
                RESTAURANT -> setOf("مطعم", "مطاعم", "restaurant", "restaurants", "fast", "food", "cafe", "coffee", "shop", "كافيه", "مقهى", "مقاهي")
                FUEL -> setOf("محطة", "محطات", "وقود", "بنزين", "بنزينة", "بنزينه", "بترول", "محروقات", "تعبئة", "fuel", "gas", "station", "stations", "petrol", "gasoline", "filling", "service", "pump")
            }.map(::normalize).toSet()
            val ignoredTokens = genericTokens + setOf(
                "في", "داخل", "in", "at", "near", "within", "بدولة", "دولة", "country",
                "محافظة", "المحافظة", "محافظه", "ولاية", "الولاية", "منطقة", "المنطقة",
                "مدينة", "المدينة", "حي", "الحي", "شارع", "الشارع", "طريق", "الطريق",
                "قرية", "القرية", "مركز", "المركز", "governorate", "province", "state",
                "city", "district", "neighborhood", "street", "road", "village", "county"
            ).map(::normalize)
            return normalize(raw).split(" ").filter { token -> token.isNotBlank() && ignoredTokens.none { generic -> token == generic || (token.length >= 4 && levenshtein(token, generic) <= 1) } }.joinToString(" ")
        }

        private fun matchesTerm(value: String, term: String): Boolean {
            val normalizedTerm = normalize(term)
            if (normalizedTerm.isBlank()) return false
            if (value.contains(normalizedTerm)) return true
            if (normalizedTerm.contains(" ")) {
                val parts = normalizedTerm.split(" ")
                return parts.all { part -> value.split(" ").any { token -> token == part || (token.length >= 4 && levenshtein(token, part) <= 1) } }
            }
            return value.split(" ").any { token -> token.length >= 4 && levenshtein(token, normalizedTerm) <= 1 }
        }

        private fun levenshtein(left: String, right: String): Int {
            if (kotlin.math.abs(left.length - right.length) > 1) return 2
            var previous = IntArray(right.length + 1) { it }
            for (i in left.indices) {
                val current = IntArray(right.length + 1)
                current[0] = i + 1
                for (j in right.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (left[i] == right[j]) 0 else 1)
                previous = current
            }
            return previous[right.length]
        }

        private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("[\\u0640]"), "")
            .replace(Regex("[أإآٱ]"), "ا")
            .replace("ى", "ي").replace("ة", "ه")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim().replace(Regex("\\s+"), " ")
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
    val openingHours: String,
    val region: String = "",
    val country: String = "",
    val countryCode: String = ""
)

data class HorusCountryInfo(val code: String, val name: String)

data class HorusRouteStep(
    val type: String,
    val modifier: String,
    val name: String,
    val instruction: String,
    val distanceFromStartMeters: Int,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val latitude: Double,
    val longitude: Double
)

data class HorusTransitStation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int
)

data class HorusAirport(
    val name: String,
    val iataCode: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int
)

data class HorusTransportSearchResult(
    val provider: String,
    val available: Boolean,
    val message: String?,
    val options: List<String>,
    val geometryGeoJson: String? = null,
    val originStations: List<HorusTransitStation> = emptyList(),
    val destinationStations: List<HorusTransitStation> = emptyList(),
    val originAirport: HorusAirport? = null,
    val destinationAirport: HorusAirport? = null
)

data class HorusRouteResult(
    val mode: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val geometryGeoJson: String,
    val provider: String,
    val steps: List<HorusRouteStep>
)

object HorusMapSearchApi {
    private const val BASE_URL = "${BuildConfig.HORUS_API_BASE_URL}/v1/search"
    private const val USER_AGENT = "HorusMaps-Android/0.1"

    private data class LongitudeArc(
        val west: Double,
        val east: Double,
        val center: Double,
        val span: Double
    )

    /**
     * Finds the smallest longitude interval containing every geometry point.
     * A plain min/max fails for polygons crossing the antimeridian: -179° and
     * +179° are only 2° apart, not 358° apart.
     */
    private fun longitudeArc(points: List<Pair<Double, Double>>): LongitudeArc {
        val longitudes = points.map { (longitude, _) ->
            ((longitude % 360.0) + 360.0) % 360.0
        }.sorted()

        if (longitudes.size == 1) {
            val value = normalizeLongitude(longitudes.first())
            return LongitudeArc(value, value, value, 0.0)
        }

        var largestGap = -1.0
        var gapIndex = 0
        for (index in longitudes.indices) {
            val current = longitudes[index]
            val next = if (index == longitudes.lastIndex) longitudes[0] + 360.0 else longitudes[index + 1]
            val gap = next - current
            if (gap > largestGap) {
                largestGap = gap
                gapIndex = index
            }
        }

        val start = longitudes[(gapIndex + 1) % longitudes.size]
        val span = (360.0 - largestGap).coerceIn(0.0, 360.0)
        val end = (start + span) % 360.0
        val center = (start + span / 2.0) % 360.0
        return LongitudeArc(
            west = normalizeLongitude(start),
            east = normalizeLongitude(end),
            center = normalizeLongitude(center),
            span = span
        )
    }

    private fun normalizeLongitude(longitude: Double): Double {
        val normalized = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return if (normalized == -180.0 && longitude > 0.0) 180.0 else normalized
    }

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
                val longitudeArc = longitudeArc(points)
                val minLat = points.minOf { it.second }
                val maxLat = points.maxOf { it.second }
                val displayName = properties.optString("name_ar", properties.optString("name_en", continent))
                return HorusGeoSearchResult(
                    id = "continent/$continent",
                    name = displayName,
                    displayName = displayName,
                    latitude = (minLat + maxLat) / 2.0,
                    longitude = longitudeArc.center,
                    geometryGeoJson = JSONObject()
                        .put("type", "Feature")
                        .put("geometry", geometry)
                        .put("properties", properties)
                        .toString(),
                    bounds = HorusGeoBounds(minLat, maxLat, longitudeArc.west, longitudeArc.east),
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
                    val longitudeArc = longitudeArc(points)
                    val minLat = points.minOf { it.second }
                    val maxLat = points.maxOf { it.second }
                    val centerLat = (minLat + maxLat) / 2.0
                    val centerLon = longitudeArc.center
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
                            bounds = if (maxLat - minLat > 0.0001 || longitudeArc.span > 0.0001) {
                                HorusGeoBounds(minLat, maxLat, longitudeArc.west, longitudeArc.east)
                            } else null,
                            kind = properties.optString("type", properties.optString("category", "place"))
                        )
                    )
                }
            }
        }


    suspend fun searchTransit(
        originLatitude: Double, originLongitude: Double,
        destinationLatitude: Double, destinationLongitude: Double,
        departureTime: String
    ): HorusTransportSearchResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("originLatitude", originLatitude).put("originLongitude", originLongitude)
            .put("destinationLatitude", destinationLatitude).put("destinationLongitude", destinationLongitude)
            .put("departureTime", departureTime)
        val root = JSONObject(post("$BASE_URL/transit", body))
        if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "تعذر البحث عن القطارات."))
        val options = root.optJSONArray("options") ?: JSONArray()
        fun parseStations(key: String): List<HorusTransitStation> {
            val stations = root.optJSONArray(key) ?: JSONArray()
            return buildList {
                for (stationIndex in 0 until stations.length()) {
                    val station = stations.optJSONObject(stationIndex) ?: continue
                    val latitude = station.optDouble("latitude", Double.NaN)
                    val longitude = station.optDouble("longitude", Double.NaN)
                    if (!latitude.isFinite() || !longitude.isFinite()) continue
                    add(HorusTransitStation(
                        name = station.optString("name", "محطة قطار"),
                        latitude = latitude,
                        longitude = longitude,
                        distanceMeters = station.optInt("distanceMeters")
                    ))
                }
            }
        }
        val originStations = parseStations("originStations")
        val destinationStations = parseStations("destinationStations")
        val lines = buildList {
            fun appendStations(stations: List<HorusTransitStation>, label: String) {
                stations.forEach { station ->
                    val distanceLabel = if (station.distanceMeters >= 1000) {
                        String.format(Locale.US, "%.1f كم", station.distanceMeters / 1000.0)
                    } else "${station.distanceMeters} متر"
                    add("$label: ${station.name} • $distanceLabel")
                }
            }
            appendStations(originStations, "أقرب محطة للبداية")
            appendStations(destinationStations, "أقرب محطة للوجهة")
            if (options.length() > 0) add("مواعيد القطارات المتاحة:")
            for (index in 0 until options.length()) {
                val option = options.optJSONObject(index) ?: continue
                val durationMinutes = (option.optInt("durationSeconds") / 60).coerceAtLeast(1)
                val distanceMeters = option.optInt("distanceMeters")
                val distanceLabel = if (distanceMeters > 0) " • ${String.format(Locale.US, "%.1f", distanceMeters / 1000.0)} كم" else ""
                add("الخيار ${index + 1} • ${durationMinutes} دقيقة • ${option.optInt("transfers")} تبديل${distanceLabel}")
                val legs = option.optJSONArray("legs") ?: JSONArray()
                for (legIndex in 0 until legs.length()) {
                    val leg = legs.optJSONObject(legIndex) ?: continue
                    val departure = leg.optString("departure").ifBlank { leg.optString("scheduledDeparture") }
                    val arrival = leg.optString("arrival").ifBlank { leg.optString("scheduledArrival") }
                    val status = when {
                        leg.optBoolean("cancelled") -> "ملغاة"
                        leg.optBoolean("realTime") -> "تحديث مباشر"
                        else -> "حسب الجدول"
                    }
                    add(listOf(
                        leg.optString("mode"),
                        leg.optString("agencyName"),
                        leg.optString("routeName"),
                        "${leg.optString("from")} → ${leg.optString("to")}",
                        "$departure - $arrival",
                        status
                    ).filter { it.isNotBlank() }.joinToString(" • "))
                }
            }
        }
        val firstGeometry = options.optJSONObject(0)?.optJSONObject("geometry")?.toString()
        HorusTransportSearchResult(
            provider = root.optString("provider", "Transitous / MOTIS"),
            available = root.optBoolean("available"),
            message = root.optString("message").takeIf { it.isNotBlank() && it != "null" },
            options = lines,
            geometryGeoJson = firstGeometry,
            originStations = originStations,
            destinationStations = destinationStations
        )
    }

    suspend fun searchFlights(
        originLatitude: Double, originLongitude: Double,
        destinationLatitude: Double, destinationLongitude: Double,
        date: String
    ): HorusTransportSearchResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("originLatitude", originLatitude).put("originLongitude", originLongitude)
            .put("destinationLatitude", destinationLatitude).put("destinationLongitude", destinationLongitude)
            .put("date", date)
        val root = JSONObject(post("$BASE_URL/flights", body))
        if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "تعذر البحث عن الطيران."))
        val options = root.optJSONArray("options") ?: JSONArray()
        fun parseAirport(value: JSONObject?): HorusAirport? {
            if (value == null) return null
            val latitude = value.optDouble("latitude", Double.NaN)
            val longitude = value.optDouble("longitude", Double.NaN)
            val code = value.optString("iataCode")
            if (code.isBlank()) return null
            return HorusAirport(
                name = value.optString("name", code),
                iataCode = code,
                latitude = latitude,
                longitude = longitude,
                distanceMeters = value.optInt("distanceMeters")
            )
        }
        val firstOption = options.optJSONObject(0)
        val originAirport = parseAirport(root.optJSONObject("originAirport"))
            ?: parseAirport(firstOption?.optJSONObject("origin"))
        val destinationAirport = parseAirport(root.optJSONObject("destinationAirport"))
            ?: parseAirport(firstOption?.optJSONObject("destination"))
        val lines = buildList {
            originAirport?.let { airport ->
                val distance = if (airport.distanceMeters >= 1000) String.format(Locale.US, "%.1f كم", airport.distanceMeters / 1000.0) else "${airport.distanceMeters} متر"
                add("مطار المغادرة: ${airport.name} (${airport.iataCode}) • يبعد $distance عن موقعك")
            }
            destinationAirport?.let { airport ->
                val distance = if (airport.distanceMeters >= 1000) String.format(Locale.US, "%.1f كم", airport.distanceMeters / 1000.0) else "${airport.distanceMeters} متر"
                add("مطار الوصول: ${airport.name} (${airport.iataCode}) • يبعد $distance عن الوجهة")
            }
            for (index in 0 until options.length()) {
                val option = options.optJSONObject(index)
                if (option == null) {
                    val legacyLine = options.optString(index).takeIf { it.isNotBlank() && it != "null" }
                    if (legacyLine != null) add(legacyLine)
                    continue
                }
                val flightNumber = option.optString("flightNumber")
                val airline = option.optString("airline")
                val origin = option.optJSONObject("origin") ?: JSONObject()
                val destination = option.optJSONObject("destination") ?: JSONObject()
                val from = origin.optString("iataCode")
                val to = destination.optString("iataCode")
                val departure = option.optString("departure")
                val timezone = option.optString("timezone")
                val arrival = option.optString("arrival")
                val timeZoneLabel = timezone.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                add("الرحلة ${index + 1} • $flightNumber • $airline")
                add("$from → $to • المغادرة: $departure$timeZoneLabel • الوصول: ${arrival.ifBlank { "غير متاح" }}")
                add("عدد التوقفات: ${option.optInt("stops")} • المصدر: ${option.optString("source", "Navo24")}")
                val durationSeconds = option.optLong("durationSeconds", 0L)
                if (durationSeconds > 0L) {
                    val totalMinutes = durationSeconds / 60
                    val durationLabel = if (totalMinutes >= 60) "${totalMinutes / 60} ساعة و ${totalMinutes % 60} دقيقة" else "${totalMinutes} دقيقة"
                    add("مدة الرحلة المجدولة: $durationLabel")
                }
                val via = option.optJSONArray("via") ?: JSONArray()
                if (via.length() > 0) {
                    add("محطات التوقف: " + (0 until via.length()).joinToString("، ") { via.optString(it) })
                }
                option.optString("status").takeIf { it.isNotBlank() }?.let { add("الحالة المنشورة: $it") }
                option.optString("updatedAt").takeIf { it.isNotBlank() }?.let { add("آخر تحديث للبيانات: $it") }
                add("السعر غير متاح من مصدر الجداول المجاني.")
            }
        }
        HorusTransportSearchResult(
            provider = root.optString("provider", "Open flight schedules"),
            available = root.optBoolean("available"),
            message = root.optString("message").takeIf { it.isNotBlank() && it != "null" },
            options = lines,
            originAirport = originAirport,
            destinationAirport = destinationAirport
        )
    }

    suspend fun route(
        originLatitude: Double,
        originLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double,
        mode: String
    ): HorusRouteResult = withContext(Dispatchers.IO) {
        require(mode == "driving" || mode == "walking") {
            "وسيلة الانتقال المطلوبة غير متاحة حاليًا."
        }
        val body = JSONObject()
            .put("originLatitude", originLatitude)
            .put("originLongitude", originLongitude)
            .put("destinationLatitude", destinationLatitude)
            .put("destinationLongitude", destinationLongitude)
            .put("mode", mode)
        val root = JSONObject(post("$BASE_URL/route", body))
        if (!root.optBoolean("ok")) {
            throw IllegalStateException(root.optString("error", "تعذر حساب الطريق."))
        }
        val geometry = root.optJSONObject("geometry")
            ?: throw IllegalStateException("لم تصل بيانات مسار صالحة.")
        val stepArray = root.optJSONArray("steps") ?: JSONArray()
        val routeSteps = buildList {
            for (index in 0 until stepArray.length()) {
                val item = stepArray.optJSONObject(index) ?: continue
                val latitude = item.optDouble("latitude", Double.NaN)
                val longitude = item.optDouble("longitude", Double.NaN)
                if (!latitude.isFinite() || !longitude.isFinite()) continue
                add(HorusRouteStep(
                    type = item.optString("type", "continue"),
                    modifier = item.optString("modifier"),
                    name = item.optString("name"),
                    instruction = item.optString("instruction"),
                    distanceFromStartMeters = item.optInt("distanceFromStartMeters", 0),
                    distanceMeters = item.optInt("distanceMeters", 0),
                    durationSeconds = item.optInt("durationSeconds", 0),
                    latitude = latitude,
                    longitude = longitude
                ))
            }
        }
        HorusRouteResult(
            mode = root.optString("mode", mode),
            distanceMeters = root.optInt("distanceMeters", 0),
            durationSeconds = root.optInt("durationSeconds", 0),
            geometryGeoJson = geometry.toString(),
            provider = root.optString("provider"),
            steps = routeSteps
        )
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
        countryCode: String,
        rawQuery: String = query
    ): List<HorusNearbyPlace> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("query", query)
            .put("rawQuery", rawQuery)
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
                        openingHours = item.optString("openingHours"),
                        region = item.optString("region"),
                        country = item.optString("country"),
                        countryCode = item.optString("countryCode")
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
