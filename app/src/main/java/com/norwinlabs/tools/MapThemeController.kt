package com.norwinlabs.tools

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView

/**
 * Satellite/dark-mode toggle behavior shared by every osmdroid MapView screen in the app (Circle
 * Share, Data Centers, Flock Cameras). Each screen still owns its own FAB icon swap since that's
 * tied to screen-specific view binding.
 */
class MapThemeController(
    private val mapView: MapView,
    private val defaultTileSource: ITileSource
) {
    private var isSatellite = false
    private var isDarkMode = false

    fun toggleSatellite() {
        isSatellite = !isSatellite
        mapView.setTileSource(if (isSatellite) TileSourceFactory.USGS_SAT else defaultTileSource)
        applyTheme()
    }

    /** Returns the new dark-mode state so the caller can flip its own FAB icon. */
    fun toggleDarkMode(): Boolean {
        isDarkMode = !isDarkMode
        applyTheme()
        return isDarkMode
    }

    private fun applyTheme() {
        if (isDarkMode) {
            val matrix = ColorMatrix(floatArrayOf(
                -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
            ))
            mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
        } else {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }
        mapView.invalidate()
    }
}
