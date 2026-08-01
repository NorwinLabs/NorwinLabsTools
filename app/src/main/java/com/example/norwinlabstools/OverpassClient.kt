package com.example.norwinlabstools

import android.os.Handler
import android.os.Looper
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.util.BoundingBox
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
