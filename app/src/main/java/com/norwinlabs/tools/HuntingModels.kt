package com.norwinlabs.tools

import org.osmdroid.util.GeoPoint

/** The three species Hunting Insights estimates activity for. */
enum class Species(val label: String, val emoji: String) {
    TURKEY("Turkey", "🦃"),
    DEER("Deer", "🦌"),
    BEAR("Bear", "🐻")
}

/**
 * Coarse time-of-day bands, used instead of real sunrise/sunset times since computing those
 * needs either an astronomy library or another external API this app doesn't have. Dawn/dusk
 * are widened a bit either side of typical sunrise/sunset to absorb that imprecision.
 */
enum class TimeOfDay(val label: String) {
    DAWN("Dawn"),
    MIDDAY("Midday"),
    DUSK("Dusk"),
    NIGHT("Night");

    companion object {
        fun fromCurrentHour(hour24: Int): TimeOfDay = when (hour24) {
            in 5..7 -> DAWN
            in 8..15 -> MIDDAY
            in 16..18 -> DUSK
            else -> NIGHT
        }
    }
}

data class WeatherSnapshot(
    val tempF: Double,
    val dewPointF: Double,
    val humidityPct: Double,
    val windMph: Double,
    val windDirDeg: Double,
    val cloudCoverPct: Double,
    val pressureHpa: Double,
    /** Change in surface pressure over the last ~3 hours; negative = falling (front approaching). */
    val pressureChange3hHpa: Double,
    val precipitationMm: Double
)

/**
 * Representative points for nearby terrain features, from Overpass's `out center` (a way's or
 * relation's centroid rather than its full outline - see [OverpassClient.fetchHuntingTerrain]).
 */
data class TerrainFeatures(
    val forestPoints: List<GeoPoint>,
    val waterPoints: List<GeoPoint>,
    val foodPoints: List<GeoPoint> // farmland, orchards, meadows
)

/** One cell of the analysis grid, covering a small rectangle of the map. */
data class GridCell(
    val latSouth: Double,
    val latNorth: Double,
    val lonWest: Double,
    val lonEast: Double,
    val elevationM: Double? = null
) {
    val center: GeoPoint get() = GeoPoint((latSouth + latNorth) / 2.0, (lonWest + lonEast) / 2.0)
}

data class ActivityScore(val cell: GridCell, val score: Int, val reasons: List<String>)
