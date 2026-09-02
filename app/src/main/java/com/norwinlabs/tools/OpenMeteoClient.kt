package com.norwinlabs.tools

import android.os.Handler
import android.os.Looper
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Open-Meteo (open-meteo.com) is a free, keyless weather API - its non-commercial tier is a fit
 * for this kind of on-demand, user-triggered lookup. Everything is requested pre-converted to
 * Fahrenheit/mph/mm so no unit math has to happen on-device.
 */
object OpenMeteoClient {

    private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    interface Callback {
        fun onSuccess(weather: WeatherSnapshot)
        fun onError(message: String)
    }

    fun fetchWeather(lat: Double, lon: Double, callback: Callback) {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", lat.toString())
            .addQueryParameter("longitude", lon.toString())
            .addQueryParameter(
                "current",
                "temperature_2m,dew_point_2m,relative_humidity_2m,wind_speed_10m,wind_direction_10m," +
                    "cloud_cover,surface_pressure,precipitation"
            )
            // Just enough hourly history to derive a short-term pressure trend below.
            .addQueryParameter("hourly", "surface_pressure")
            .addQueryParameter("past_hours", "6")
            .addQueryParameter("forecast_hours", "1")
            .addQueryParameter("temperature_unit", "fahrenheit")
            .addQueryParameter("wind_speed_unit", "mph")
            .addQueryParameter("precipitation_unit", "mm")
            .addQueryParameter("timezone", "auto")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NorwinLabsTools-HuntingInsights")
            .build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    val weather = if (response.isSuccessful && body != null) parseWeather(body) else null
                    if (weather != null) {
                        mainHandler.post { callback.onSuccess(weather) }
                    } else {
                        mainHandler.post { callback.onError("Weather service returned ${response.code}") }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post { callback.onError(e.message ?: "Network error") }
            }
        }.start()
    }

    private fun parseWeather(json: String): WeatherSnapshot? {
        val root = JSONObject(json)
        val current = root.optJSONObject("current") ?: return null
        val pressureNow = current.optDouble("surface_pressure", Double.NaN)
        if (pressureNow.isNaN()) return null

        return WeatherSnapshot(
            tempF = current.optDouble("temperature_2m", 60.0),
            dewPointF = current.optDouble("dew_point_2m", 50.0),
            humidityPct = current.optDouble("relative_humidity_2m", 50.0),
            windMph = current.optDouble("wind_speed_10m", 0.0),
            windDirDeg = current.optDouble("wind_direction_10m", 0.0),
            cloudCoverPct = current.optDouble("cloud_cover", 0.0),
            pressureHpa = pressureNow,
            pressureChange3hHpa = pressureTrend(root, current, pressureNow),
            precipitationMm = current.optDouble("precipitation", 0.0)
        )
    }

    /**
     * Finds the hourly surface-pressure reading from ~3 hours ago (matched by timestamp, since
     * the hourly array's alignment to "now" isn't guaranteed by position alone) and compares it
     * to the current reading. Falls back to "no trend" (0.0) if the match can't be made.
     */
    private fun pressureTrend(root: JSONObject, current: JSONObject, pressureNow: Double): Double {
        val hourly = root.optJSONObject("hourly") ?: return 0.0
        val times = hourly.optJSONArray("time") ?: return 0.0
        val pressures = hourly.optJSONArray("surface_pressure") ?: return 0.0
        if (times.length() != pressures.length() || times.length() == 0) return 0.0

        var currentIndex = -1
        val currentTime = current.optString("time")
        for (i in 0 until times.length()) {
            if (times.optString(i) == currentTime) { currentIndex = i; break }
        }
        if (currentIndex == -1) currentIndex = times.length() - 1 // best-effort fallback

        val pastIndex = currentIndex - 3
        if (pastIndex < 0) return 0.0
        val pastPressure = pressures.optDouble(pastIndex, Double.NaN)
        return if (pastPressure.isNaN()) 0.0 else pressureNow - pastPressure
    }
}
