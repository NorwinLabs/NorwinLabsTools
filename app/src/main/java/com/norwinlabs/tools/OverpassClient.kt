package com.norwinlabs.tools

import android.os.Handler
import android.os.Looper
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.util.concurrent.TimeUnit

/**
 * DeFlock's ALPR camera map is built on crowdsourced OpenStreetMap data (nodes tagged
 * man_made=surveillance + surveillance:type=ALPR, per the OSM wiki convention DeFlock
 * contributors use), rather than a bespoke REST API. This queries the public Overpass API
 * directly for that same data, scoped to a bounding box (a global query would violate
 * Overpass's fair-use policy against bulk/unbounded queries).
 */
object OverpassClient {

    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
    private const val RESULT_LIMIT = 500
    private const val TERRAIN_RESULT_LIMIT = 300

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    interface Callback {
        fun onSuccess(cameras: List<FlockCamera>)
        fun onError(message: String)
    }

    fun fetchAlprCameras(bbox: BoundingBox, callback: Callback) {
        val query = """
            [out:json][timeout:25][bbox:${bbox.latSouth},${bbox.lonWest},${bbox.latNorth},${bbox.lonEast}];
            node["man_made"="surveillance"]["surveillance:type"="ALPR"];
            out body $RESULT_LIMIT;
        """.trimIndent()

        val url = OVERPASS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("data", query)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NorwinLabsTools-FlockCameraMap")
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val cameras = parseCameras(responseBody)
                        mainHandler.post { callback.onSuccess(cameras) }
                    } else {
                        mainHandler.post { callback.onError("Overpass returned ${response.code}") }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError(e.message ?: "Network error") }
            }
        }.start()
    }

    interface TerrainCallback {
        fun onSuccess(terrain: TerrainFeatures)
        fun onError(message: String)
    }

    /**
     * Forest, water, and open-food (farmland/orchard/meadow) features within [bbox], used by
     * Hunting Insights as habitat proxies. `out center` gives a representative point for ways
     * and relations (their centroid) rather than a full outline - good enough for "how close is
     * this grid cell to forest/water/food", not for true edge/boundary geometry.
     */
    fun fetchHuntingTerrain(bbox: BoundingBox, callback: TerrainCallback) {
        val query = """
            [out:json][timeout:25][bbox:${bbox.latSouth},${bbox.lonWest},${bbox.latNorth},${bbox.lonEast}];
            (
              way["natural"="wood"];
              way["landuse"="forest"];
              way["natural"="water"];
              way["waterway"="river"];
              way["waterway"="stream"];
              node["natural"="spring"];
              way["landuse"="farmland"];
              way["landuse"="orchard"];
              way["landuse"="meadow"];
            );
            out center $TERRAIN_RESULT_LIMIT;
        """.trimIndent()

        val url = OVERPASS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("data", query)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NorwinLabsTools-HuntingInsights")
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val terrain = parseTerrain(responseBody)
                        mainHandler.post { callback.onSuccess(terrain) }
                    } else {
                        mainHandler.post { callback.onError("Overpass returned ${response.code}") }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError(e.message ?: "Network error") }
            }
        }.start()
    }

    private fun parseTerrain(json: String): TerrainFeatures {
        val forest = mutableListOf<GeoPoint>()
        val water = mutableListOf<GeoPoint>()
        val food = mutableListOf<GeoPoint>()

        val elements = JSONObject(json).optJSONArray("elements") ?: return TerrainFeatures(forest, water, food)
        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            val point = elementPoint(element) ?: continue
            val tags = element.optJSONObject("tags") ?: continue

            when {
                tags.optString("natural") == "wood" || tags.optString("landuse") == "forest" -> forest.add(point)
                tags.optString("natural") == "water" || tags.optString("natural") == "spring" ||
                    tags.optString("waterway") in listOf("river", "stream") -> water.add(point)
                tags.optString("landuse") in listOf("farmland", "orchard", "meadow") -> food.add(point)
            }
        }
        return TerrainFeatures(forest, water, food)
    }

    /** Nodes carry lat/lon directly; ways/relations queried with `out center` carry a `center` object instead. */
    private fun elementPoint(element: JSONObject): GeoPoint? {
        val lat = element.optDouble("lat", Double.NaN)
        val lon = element.optDouble("lon", Double.NaN)
        if (!lat.isNaN() && !lon.isNaN()) return GeoPoint(lat, lon)

        val center = element.optJSONObject("center") ?: return null
        val centerLat = center.optDouble("lat", Double.NaN)
        val centerLon = center.optDouble("lon", Double.NaN)
        if (centerLat.isNaN() || centerLon.isNaN()) return null
        return GeoPoint(centerLat, centerLon)
    }

    private fun parseCameras(json: String): List<FlockCamera> {
        val result = mutableListOf<FlockCamera>()
        val elements = JSONObject(json).optJSONArray("elements") ?: return result
        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            val lat = element.optDouble("lat", Double.NaN)
            val lon = element.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue

            val tags = element.optJSONObject("tags")
            val manufacturer = tags?.optString("manufacturer")?.takeIf { it.isNotBlank() }
            val direction = tags?.optString("direction")?.takeIf { it.isNotBlank() }

            result.add(FlockCamera(element.optLong("id"), lat, lon, manufacturer, direction))
        }
        return result
    }
}
