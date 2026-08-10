package com.example.norwinlabstools

import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.norwinlabstools.databinding.FragmentHuntingInsightsMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.util.Calendar
import java.util.Locale

/**
 * Estimates where turkey, deer, and bear are likely to be active right now (or at a chosen
 * time of day), by combining live weather (Open-Meteo), elevation (Open-Elevation), and nearby
 * terrain (forest/water/farmland, from OpenStreetMap via [OverpassClient]) into a per-species
 * heuristic score across a grid over the visible map area. See [HuntingActivityEngine] for the
 * actual scoring logic and its honest caveats - this is a decision-support hint, not a
 * guaranteed sighting predictor.
 */
class HuntingInsightsMapFragment : Fragment() {

    private var _binding: FragmentHuntingInsightsMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var mapTheme: MapThemeController
    private val gridOverlays = mutableListOf<Polygon>()

    // Below this zoom level the visible area is both too large for a responsible Overpass query
    // and too coarse for a 6x6 grid to say anything meaningful cell-to-cell.
    private val minSearchZoom = 13.0
    private val gridSize = 6

    // Scratch state for one in-flight analysis (weather/terrain/elevation fetch in parallel).
    private var fetchWeather: WeatherSnapshot? = null
    private var fetchTerrain: TerrainFeatures? = null
    private var fetchElevations: List<Double?>? = null
    private var fetchStepsDone = 0
    private var fetchStepsFailed = 0
    private var fetchCells: List<GridCell> = emptyList()

    // Most recent completed analysis - kept around so switching species/time-of-day re-scores
    // and re-renders instantly, without re-fetching anything.
    private var hasAnalysis = false
    private var lastWeather: WeatherSnapshot? = null
    private var lastTerrain: TerrainFeatures = TerrainFeatures(emptyList(), emptyList(), emptyList())
    private var lastCells: List<GridCell> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
        Configuration.getInstance().userAgentValue = "NorwinLabsTools-HuntingInsights/${requireContext().packageName}"

        _binding = FragmentHuntingInsightsMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()
        selectDefaultTimeOfDay()

        binding.btnAnalyzeArea.setOnClickListener { analyzeThisArea() }
        binding.chipGroupSpecies.setOnCheckedStateChangeListener { _, _ -> rescoreAndRender() }
        binding.chipGroupTime.setOnCheckedStateChangeListener { _, _ -> rescoreAndRender() }
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
        binding.mapView.controller.setZoom(14.0)
        mapTheme = MapThemeController(binding.mapView, MapTileSources.DEFAULT)

        val rotationGestureOverlay = RotationGestureOverlay(binding.mapView)
        rotationGestureOverlay.isEnabled = true
        binding.mapView.overlays.add(rotationGestureOverlay)
    }

    /** Pre-selects the time-of-day chip matching the device's current local hour. */
    private fun selectDefaultTimeOfDay() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val chipId = when (TimeOfDay.fromCurrentHour(hour)) {
            TimeOfDay.DAWN -> R.id.chip_dawn
            TimeOfDay.MIDDAY -> R.id.chip_midday
            TimeOfDay.DUSK -> R.id.chip_dusk
            TimeOfDay.NIGHT -> R.id.chip_night
        }
        binding.chipGroupTime.check(chipId)
    }

    private fun analyzeThisArea() {
        if (binding.mapView.zoomLevelDouble < minSearchZoom) {
            Toast.makeText(context, "Zoom in further to analyze this area", Toast.LENGTH_SHORT).show()
            return
        }

        val bbox = binding.mapView.boundingBox
        val cells = buildGrid(bbox)
        val cellCenters = cells.map { it.center }
        val bboxCenter = GeoPoint((bbox.latNorth + bbox.latSouth) / 2.0, (bbox.lonEast + bbox.lonWest) / 2.0)

        fetchWeather = null
        fetchTerrain = null
        fetchElevations = null
        fetchStepsDone = 0
        fetchStepsFailed = 0
        fetchCells = cells

        binding.btnAnalyzeArea.isEnabled = false
        binding.progressLoading.visibility = View.VISIBLE
        binding.tvHuntingStatus.text = "Analyzing this area…"

        OpenMeteoClient.fetchWeather(bboxCenter.latitude, bboxCenter.longitude, object : OpenMeteoClient.Callback {
            override fun onSuccess(weather: WeatherSnapshot) {
                fetchWeather = weather
                onFetchStepDone()
            }
            override fun onError(message: String) {
                fetchStepsFailed++
                onFetchStepDone()
            }
        })

        OverpassClient.fetchHuntingTerrain(bbox, object : OverpassClient.TerrainCallback {
            override fun onSuccess(terrain: TerrainFeatures) {
                fetchTerrain = terrain
                onFetchStepDone()
            }
            override fun onError(message: String) {
                fetchStepsFailed++
                onFetchStepDone()
            }
        })

        ElevationClient.fetchElevations(cellCenters, object : ElevationClient.Callback {
            override fun onSuccess(elevationsM: List<Double?>) {
                fetchElevations = elevationsM
                onFetchStepDone()
            }
            override fun onError(message: String) {
                fetchStepsFailed++
                onFetchStepDone()
            }
        })
    }

    private fun buildGrid(bbox: BoundingBox): List<GridCell> {
        val latStep = (bbox.latNorth - bbox.latSouth) / gridSize
        val lonStep = (bbox.lonEast - bbox.lonWest) / gridSize
        val cells = mutableListOf<GridCell>()
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val latSouth = bbox.latSouth + latStep * row
                val lonWest = bbox.lonWest + lonStep * col
                cells.add(GridCell(latSouth, latSouth + latStep, lonWest, lonWest + lonStep))
            }
        }
        return cells
    }

    private fun onFetchStepDone() {
        if (_binding == null) return
        fetchStepsDone++
        if (fetchStepsDone < 3) return

        binding.progressLoading.visibility = View.GONE
        binding.btnAnalyzeArea.isEnabled = true

        if (fetchStepsFailed == 3) {
            binding.tvHuntingStatus.text = "Analysis failed - check your connection and try again"
            Toast.makeText(context, "Couldn't reach the weather, elevation, or terrain services", Toast.LENGTH_LONG).show()
            return
        }

        val elevations = fetchElevations
        lastCells = if (elevations != null && elevations.size == fetchCells.size) {
            fetchCells.mapIndexed { i, cell -> cell.copy(elevationM = elevations[i]) }
        } else {
            fetchCells
        }
        lastWeather = fetchWeather
        lastTerrain = fetchTerrain ?: TerrainFeatures(emptyList(), emptyList(), emptyList())
        hasAnalysis = true

        updateWeatherSummary(lastWeather)
        rescoreAndRender()

        val note = if (fetchStepsFailed > 0) " (some data unavailable)" else ""
        binding.tvHuntingStatus.text = "Analyzed ${lastCells.size} cells$note - tap a shaded area for details"
    }

    private fun rescoreAndRender() {
        if (!hasAnalysis || _binding == null) return
        val species = selectedSpecies()
        val scores = HuntingActivityEngine.computeScores(species, selectedTimeOfDay(), lastWeather, lastTerrain, lastCells)
        renderScores(species, scores)
    }

    private fun selectedSpecies(): Species = when (binding.chipGroupSpecies.checkedChipId) {
        R.id.chip_turkey -> Species.TURKEY
        R.id.chip_bear -> Species.BEAR
        else -> Species.DEER
    }

    private fun selectedTimeOfDay(): TimeOfDay = when (binding.chipGroupTime.checkedChipId) {
        R.id.chip_midday -> TimeOfDay.MIDDAY
        R.id.chip_dusk -> TimeOfDay.DUSK
        R.id.chip_night -> TimeOfDay.NIGHT
        else -> TimeOfDay.DAWN
    }

    private fun renderScores(species: Species, scores: List<ActivityScore>) {
        gridOverlays.forEach { binding.mapView.overlays.remove(it) }
        gridOverlays.clear()

        for (score in scores) {
            val polygon = Polygon(binding.mapView)
            polygon.points = listOf(
                GeoPoint(score.cell.latSouth, score.cell.lonWest),
                GeoPoint(score.cell.latSouth, score.cell.lonEast),
                GeoPoint(score.cell.latNorth, score.cell.lonEast),
                GeoPoint(score.cell.latNorth, score.cell.lonWest)
            )
            val color = scoreColor(score.score)
            polygon.fillColor = color
            polygon.strokeColor = color
            polygon.strokeWidth = 0.5f
            polygon.setOnClickListener { _, _, _ ->
                showScoreDetails(species, score)
                true
            }
            binding.mapView.overlays.add(polygon)
            gridOverlays.add(polygon)
        }
        binding.mapView.invalidate()
    }

    /** Red (low) through yellow to green (high), matching the legend gradient in the layout. */
    private fun scoreColor(score: Int): Int {
        val hue = (score.coerceIn(0, 100) / 100f) * 120f
        return Color.HSVToColor(160, floatArrayOf(hue, 0.85f, 0.85f))
    }

    private fun showScoreDetails(species: Species, score: ActivityScore) {
        if (context == null) return
        val message = score.reasons.joinToString("\n") { "• $it" }
        AlertDialog.Builder(requireContext())
            .setTitle("${species.emoji} ${species.label}: ${score.score}/100")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateWeatherSummary(weather: WeatherSnapshot?) {
        if (weather == null) {
            binding.tvWeatherSummary.visibility = View.GONE
            return
        }
        val trend = when {
            weather.pressureChange3hHpa <= -1.0 -> "falling"
            weather.pressureChange3hHpa >= 1.0 -> "rising"
            else -> "steady"
        }
        binding.tvWeatherSummary.text = String.format(
            Locale.US,
            "%.0f°F · Dew point %.0f°F · Wind %.0f mph · Pressure %s",
            weather.tempF, weather.dewPointF, weather.windMph, trend
        )
        binding.tvWeatherSummary.visibility = View.VISIBLE
    }

    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { super.onPause(); binding.mapView.onPause() }
    override fun onDestroyView() {
        super.onDestroyView()
        gridOverlays.clear()
        _binding = null
    }
}
