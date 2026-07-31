package com.example.norwinlabstools

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.norwinlabstools.databinding.FragmentDataCenterMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class DataCenterMapFragment : Fragment() {

    private var _binding: FragmentDataCenterMapBinding? = null
    private val binding get() = _binding!!

    private var isSatellite = false
    private var isDarkMode = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        Configuration.getInstance().userAgentValue = "NorwinLabsTools-DataCenterMap/${requireContext().packageName}"

        _binding = FragmentDataCenterMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()
        addDataCenterMarkers()

        binding.tvDataCenterCount.text = "${DataCenters.ALL.size} large data centers worldwide"

        binding.fabFitAll.setOnClickListener { fitAllMarkers() }
        binding.fabToggleSatellite.setOnClickListener { toggleSatellite() }
        binding.fabToggleDarkMode.setOnClickListener { toggleDarkMode() }

        binding.mapView.post { fitAllMarkers() }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(MapTileSources.DEFAULT)
        binding.mapView.setMultiTouchControls(true)

        val rotationGestureOverlay = RotationGestureOverlay(binding.mapView)
        rotationGestureOverlay.isEnabled = true
        binding.mapView.overlays.add(rotationGestureOverlay)
    }

    private fun addDataCenterMarkers() {
        for (site in DataCenters.ALL) {
            val marker = Marker(binding.mapView)
            marker.position = GeoPoint(site.lat, site.lng)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = site.name
            marker.snippet = "${site.operator} — ${site.location}"
            marker.icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_mapmode)
            binding.mapView.overlays.add(marker)
        }
        binding.mapView.invalidate()
    }

    private fun fitAllMarkers() {
        val sites = DataCenters.ALL
        if (sites.isEmpty()) return

        val north = sites.maxOf { it.lat }
        val south = sites.minOf { it.lat }
        val east = sites.maxOf { it.lng }
        val west = sites.minOf { it.lng }
        val boundingBox = BoundingBox(north, east, south, west).increaseByScale(1.2f)
        binding.mapView.zoomToBoundingBox(boundingBox, true)
    }

    private fun toggleSatellite() {
        isSatellite = !isSatellite
        if (isSatellite) {
            binding.mapView.setTileSource(TileSourceFactory.USGS_SAT)
        } else {
            binding.mapView.setTileSource(MapTileSources.DEFAULT)
        }
        updateMapTheme()
    }

    private fun toggleDarkMode() {
        isDarkMode = !isDarkMode
        if (isDarkMode) {
            binding.fabToggleDarkMode.setImageResource(android.R.drawable.ic_menu_day)
        } else {
            binding.fabToggleDarkMode.setImageResource(android.R.drawable.ic_menu_recent_history)
        }
        updateMapTheme()
    }

    private fun updateMapTheme() {
        if (isDarkMode) {
            val matrix = ColorMatrix(floatArrayOf(
                -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
            ))
            binding.mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
        } else {
            binding.mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }
        binding.mapView.invalidate()
    }

    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
