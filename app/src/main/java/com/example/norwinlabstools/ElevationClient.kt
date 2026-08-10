package com.example.norwinlabstools

import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.util.concurrent.TimeUnit

/**
 * Open-elevation.com is a free, keyless, community-run elevation lookup backed by SRTM data.
 * Being community-run and rate-limited, it's used here for one small batch (one grid's worth of
 * points, tens not hundreds) per "Analyze This Area" tap, not per-frame or per-pan.
 */
object ElevationClient {

    private const val URL = "https://api.open-elevation.com/api/v1/lookup"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    interface Callback {
        /** Elevations in meters, same order/length as the requested points; null entries mean that one point failed to parse. */
        fun onSuccess(elevationsM: List<Double?>)
        fun onError(message: String)
    }

    fun fetchElevations(points: List<GeoPoint>, callback: Callback) {
        if (points.isEmpty()) { callback.onSuccess(emptyList()); return }

        val locations = JSONArray()
        points.forEach { point ->
            locations.put(JSONObject().apply {
                put("latitude", point.latitude)
                put("longitude", point.longitude)
            })
        }
        val body = JSONObject().put("locations", locations).toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url(URL)
            .header("User-Agent", "NorwinLabsTools-HuntingInsights")
            .post(body)
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        val elevations = parseElevations(responseBody, points.size)
                        mainHandler.post { callback.onSuccess(elevations) }
                    } else {
                        mainHandler.post { callback.onError("Elevation service returned ${response.code}") }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError(e.message ?: "Network error") }
            }
        }.start()
    }

    private fun parseElevations(json: String, expectedCount: Int): List<Double?> {
        val results = JSONObject(json).optJSONArray("results") ?: return List(expectedCount) { null }
        return (0 until expectedCount).map { i ->
            if (i < results.length()) {
                val elevation = results.getJSONObject(i).optDouble("elevation", Double.NaN)
                elevation.takeUnless { it.isNaN() }
            } else {
                null
            }
        }
    }
}
