package com.example.norwinlabstools

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.norwinlabstools.databinding.FragmentFlockCameraMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

class FlockCameraMapFragment : Fragment() {

    private var _binding: FragmentFlockCameraMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var mapTheme: MapThemeController
    private val cameraMarkers = mutableListOf<Marker>()

    // Below this zoom level the visible area is too large for a responsible, bounded
    // Overpass query (its public instance disallows bulk/unbounded queries).
    private val minSearchZoom = 10.0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        Configuration.getInstance().userAgentValue = "NorwinLabsTools-FlockCameraMap/${requireContext().packageName}"

        _binding = FragmentFlockCameraMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()

        binding.btnSearchArea.setOnClickListener { searchThisArea() }
        binding.fabToggleSatellite.setOnClickListener { mapTheme.toggleSatellite() }
        binding.fabToggleDarkMode.setOnClickListener {
            val isDark = mapTheme.toggleDarkMode()
            binding.fabToggleDarkMode.setImageResource(
                if (isDark) android.R.drawable.ic_menu_day else android.R.drawable.ic_menu_recent_history
            )
        }
    }

    private fun setupMap() {
        binding.mapView.setTileSource(MapTileSources.DEFAULT)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(12.0)
        mapTheme = MapThemeController(binding.mapView, MapTileSources.DEFAULT)

        val rotationGestureOverlay = RotationGestureOverlay(binding.mapView)
        rotationGestureOverlay.isEnabled = true
        binding.mapView.overlays.add(rotationGestureOverlay)
    }

    private fun searchThisArea() {
        if (binding.mapView.zoomLevelDouble < minSearchZoom) {
            Toast.makeText(context, "Zoom in further to search this area", Toast.LENGTH_SHORT).show()
            return
        }

        val bbox = binding.mapView.boundingBox
        binding.btnSearchArea.isEnabled = false
        binding.progressLoading.visibility = View.VISIBLE
        binding.tvCameraStatus.text = "Searching this area…"

        OverpassClient.fetchAlprCameras(bbox, object : OverpassClient.Callback {
            override fun onSuccess(cameras: List<FlockCamera>) {
                if (_binding == null) return
                showCameras(cameras)
                binding.btnSearchArea.isEnabled = true
                binding.progressLoading.visibility = View.GONE
                binding.tvCameraStatus.text = if (cameras.isEmpty()) {
                    "No known ALPR cameras found in this area"
                } else {
                    "${cameras.size} known ALPR camera${if (cameras.size == 1) "" else "s"} in this area"
                }
            }

            override fun onError(message: String) {
                if (_binding == null) return
                binding.btnSearchArea.isEnabled = true
                binding.progressLoading.visibility = View.GONE
                binding.tvCameraStatus.text = "Search failed"
                Toast.makeText(context, "Search failed: $message", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun showCameras(cameras: List<FlockCamera>) {
        cameraMarkers.forEach { binding.mapView.overlays.remove(it) }
        cameraMarkers.clear()

        for (camera in cameras) {
            val marker = Marker(binding.mapView)
            marker.position = GeoPoint(camera.lat, camera.lon)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = camera.manufacturer ?: "ALPR camera"
            marker.snippet = camera.direction?.let { "Facing: $it" }
            marker.icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_camera)
            binding.mapView.overlays.add(marker)
            cameraMarkers.add(marker)
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
