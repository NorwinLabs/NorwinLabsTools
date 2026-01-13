package com.example.norwinlabstools

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient()
    // Using a more general endpoint to debug or checking if repo exists
    private val GITHUB_API_URL = "https://api.github.com/repos/NorwinLabsTools/NorwinLabsTools/releases/latest"

    interface UpdateCallback {
        fun onUpdateAvailable(latestVersion: String, downloadUrl: String)
        fun onNoUpdate()
        fun onError(error: String)
    }

    fun checkForUpdates(callback: UpdateCallback) {
        val request = Request.Builder()
            .url(GITHUB_API_URL)
            .header("User-Agent", "NorwinLabsTools-App")
            .build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonData = response.body?.string()
                    if (jsonData != null) {
                        val jsonObject = JSONObject(jsonData)
                        val latestVersion = jsonObject.getString("tag_name")
                        val assets = jsonObject.getJSONArray("assets")
                        
                        var downloadUrl = ""
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url")
                                break
                            }
                        }

                        val currentVersion = getCurrentVersion()
                        if (isNewerVersion(latestVersion, currentVersion)) {
                            callback.onUpdateAvailable(latestVersion, downloadUrl)
                        } else {
                            callback.onNoUpdate()
                        }
                    }
                } else {
                    // If 404, it might mean no releases have been created yet
                    if (response.code == 404) {
                        callback.onError("No releases found on GitHub (404).")
                    } else {
                        callback.onError("Server error: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                callback.onError(e.message ?: "Network error")
            }
        }.start()
    }

    private fun getCurrentVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestClean = latest.replace(Regex("[^0-9.]"), "")
        val currentClean = current.replace(Regex("[^0-9.]"), "")
        
        val latestParts = latestClean.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = currentClean.split(".").map { it.toIntOrNull() ?: 0 }
        
        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}