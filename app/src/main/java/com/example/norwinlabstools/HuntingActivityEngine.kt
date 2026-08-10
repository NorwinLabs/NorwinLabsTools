package com.example.norwinlabstools

import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns weather + terrain + time-of-day into a 0-100 "likely activity" score per grid cell, for
 * one species at a time.
 *
 * This is a heuristic decision-support tool built from well-established, broadly-cited
 * hunting/wildlife-behavior rules of thumb - crepuscular activity patterns, wind and rain
 * suppressing daytime movement, the barometric-pressure "front" folklore hunters have used for
 * decades, dew point as a proxy for muggy/uncomfortable conditions, turkeys roosting off the
 * ground at night, and the outsized value of forest/field edges and water near food. It is *not*
 * a scientifically validated predictive model trained on real sighting data - this app has none.
 * The exact point values below are this app's own reasonable calibration of those well-known
 * qualitative effects, not sourced from a specific study; treat the output as a "where to start
 * looking" hint, not a guarantee.
 */
object HuntingActivityEngine {

    private const val EARTH_RADIUS_M = 6371000.0

    private data class Factor(val points: Int, val reason: String)

    fun computeScores(
        species: Species,
        timeOfDay: TimeOfDay,
        weather: WeatherSnapshot?,
        terrain: TerrainFeatures,
        cells: List<GridCell>
    ): List<ActivityScore> {
        val elevations = cells.mapNotNull { it.elevationM }
        val elevationRange = if (elevations.isNotEmpty()) elevations.min() to elevations.max() else null
        return cells.map { cell -> scoreCell(species, timeOfDay, weather, terrain, cell, elevationRange) }
    }

    private fun scoreCell(
        species: Species,
        timeOfDay: TimeOfDay,
        weather: WeatherSnapshot?,
        terrain: TerrainFeatures,
        cell: GridCell,
        elevationRange: Pair<Double, Double>?
    ): ActivityScore {
        val baseline = baselineFor(species, timeOfDay)
        val factors = mutableListOf<Factor>()

        if (weather != null) {
            factors += windFactor(species, weather)
            factors += precipitationFactor(species, weather)
            factors += temperatureFactor(species, timeOfDay, weather)
            factors += dewPointFactor(species, weather)
            factors += pressureTrendFactor(species, weather)
            factors += cloudCoverFactor(species, weather)
        }
        factors += waterFactor(species, cell, terrain)
        factors += foodFactor(cell, terrain)
        factors += edgeFactor(species, cell, terrain)
        factors += elevationBandFactor(species, cell, elevationRange)

        val total = (baseline + factors.sumOf { it.points }).coerceIn(0, 100)

        val reasons = mutableListOf("${timeOfDay.label} baseline for ${species.label.lowercase(Locale.US)}: $baseline/100")
        reasons += factors.filter { it.points != 0 }
            .sortedByDescending { abs(it.points) }
            .take(4)
            .map { it.reason }

        return ActivityScore(cell, total, reasons)
    }

    private fun baselineFor(species: Species, timeOfDay: TimeOfDay): Int = when (species) {
        // Deer: heaviest movement at dawn/dusk (classic crepuscular pattern), bedded midday,
        // meaningful nocturnal movement too - especially common in areas under hunting pressure.
        Species.DEER -> when (timeOfDay) {
            TimeOfDay.DAWN -> 85
            TimeOfDay.MIDDAY -> 25
            TimeOfDay.DUSK -> 90
            TimeOfDay.NIGHT -> 55
        }
        // Turkey: peak right after fly-down at dawn, a midday feeding lull, some dusk activity
        // heading back toward the roost, and essentially none at night once roosted in trees.
        Species.TURKEY -> when (timeOfDay) {
            TimeOfDay.DAWN -> 90
            TimeOfDay.MIDDAY -> 40
            TimeOfDay.DUSK -> 65
            TimeOfDay.NIGHT -> 5
        }
        // Bear: crepuscular/nocturnal-leaning, with a strong dusk feeding push (more so in fall
        // hyperphagia), but still forages through midday more readily than deer or turkey do.
        Species.BEAR -> when (timeOfDay) {
            TimeOfDay.DAWN -> 70
            TimeOfDay.MIDDAY -> 45
            TimeOfDay.DUSK -> 85
            TimeOfDay.NIGHT -> 65
        }
    }

    private fun windFactor(species: Species, weather: WeatherSnapshot): Factor {
        val wind = weather.windMph
        val windStr = wind.roundedStr()
        return when {
            wind < 5 -> Factor(5, "Calm wind ($windStr mph) favors movement")
            wind < 10 -> Factor(0, "Light wind ($windStr mph)")
            wind < 15 -> Factor(if (species == Species.BEAR) -4 else -10, "Breezy ($windStr mph) - animals more cautious")
            else -> Factor(if (species == Species.BEAR) -8 else -20, "Strong wind ($windStr mph) suppresses daytime movement")
        }
    }

    private fun precipitationFactor(species: Species, weather: WeatherSnapshot): Factor {
        val precip = weather.precipitationMm
        return when {
            precip <= 0.0 -> Factor(5, "No rain")
            precip <= 2.0 -> if (species == Species.TURKEY) {
                Factor(-15, "Light rain - turkeys tend to hold in cover")
            } else {
                Factor(-5, "Light rain lightly suppresses movement")
            }
            else -> if (species == Species.TURKEY) {
                Factor(-35, "Steady rain - turkeys mostly hunker down")
            } else {
                Factor(-15, "Steady rain suppresses movement")
            }
        }
    }

    private fun temperatureFactor(species: Species, timeOfDay: TimeOfDay, weather: WeatherSnapshot): Factor {
        val temp = weather.tempF
        val tempStr = temp.roundedStr()
        return when (species) {
            Species.DEER -> when {
                temp < 45 -> Factor(10, "Cool temps ($tempStr°F) favor daytime deer movement")
                temp > 75 -> Factor(
                    if (timeOfDay == TimeOfDay.MIDDAY) -25 else -15,
                    "Warm temps ($tempStr°F) push deer toward dawn/dusk/night"
                )
                else -> Factor(0, "Mild temps ($tempStr°F)")
            }
            Species.TURKEY -> when {
                temp < 20 -> Factor(-5, "Very cold ($tempStr°F) may shorten feeding activity")
                else -> Factor(0, "Temperature ($tempStr°F) in a normal range")
            }
            Species.BEAR -> when {
                temp < 50 -> Factor(8, "Cool temps ($tempStr°F) favor extended foraging")
                temp > 80 -> Factor(
                    if (timeOfDay == TimeOfDay.MIDDAY) -10 else -5,
                    "Hot temps ($tempStr°F) push bears toward shade/twilight"
                )
                else -> Factor(0, "Mild temps ($tempStr°F)")
            }
        }
    }

    private fun dewPointFactor(species: Species, weather: WeatherSnapshot): Factor {
        val dew = weather.dewPointF
        val dewStr = dew.roundedStr()
        return when (species) {
            Species.DEER -> when {
                dew > 65 -> Factor(-12, "High dew point ($dewStr°F, muggy) tends to suppress daytime movement")
                dew < 45 -> Factor(8, "Low dew point ($dewStr°F, crisp air) favors movement")
                else -> Factor(0, "Moderate dew point ($dewStr°F)")
            }
            Species.TURKEY -> if (dew > 68) {
                Factor(-8, "High dew point ($dewStr°F) - muggy conditions")
            } else {
                Factor(0, "Dew point ($dewStr°F) not a strong factor")
            }
            Species.BEAR -> Factor(0, "Dew point not a strong factor for bear")
        }
    }

    private fun pressureTrendFactor(species: Species, weather: WeatherSnapshot): Factor {
        val change = weather.pressureChange3hHpa
        return when {
            change <= -3 -> Factor(
                when (species) { Species.DEER -> 12; Species.BEAR -> 5; Species.TURKEY -> 3 },
                "Falling pressure (front approaching) often triggers a feeding push"
            )
            change >= 3 -> Factor(
                when (species) { Species.DEER -> 8; Species.BEAR -> 5; Species.TURKEY -> 5 },
                "Rising pressure after a front tends to boost movement"
            )
            else -> Factor(0, "Stable pressure")
        }
    }

    private fun cloudCoverFactor(species: Species, weather: WeatherSnapshot): Factor {
        return if (species == Species.DEER && weather.cloudCoverPct in 30.0..85.0 && weather.precipitationMm <= 0.0) {
            Factor(5, "Overcast skies can make deer feel more secure moving in daylight")
        } else {
            Factor(0, "Cloud cover not a strong factor here")
        }
    }

    private fun waterFactor(species: Species, cell: GridCell, terrain: TerrainFeatures): Factor {
        val dist = nearestDistanceM(cell.center, terrain.waterPoints) ?: return Factor(0, "No mapped water nearby")
        val bonus = when {
            dist < 150 -> if (species == Species.BEAR) 20 else 15
            dist < 400 -> if (species == Species.BEAR) 10 else 7
            else -> 0
        }
        return Factor(bonus, "${dist.roundedStr()}m from water")
    }

    private fun foodFactor(cell: GridCell, terrain: TerrainFeatures): Factor {
        val dist = nearestDistanceM(cell.center, terrain.foodPoints) ?: return Factor(0, "No mapped food source nearby")
        val bonus = when {
            dist < 150 -> 15
            dist < 400 -> 7
            else -> 0
        }
        return Factor(bonus, "${dist.roundedStr()}m from a food source (field/orchard)")
    }

    private fun edgeFactor(species: Species, cell: GridCell, terrain: TerrainFeatures): Factor {
        val distForest = nearestDistanceM(cell.center, terrain.forestPoints)
        val distFood = nearestDistanceM(cell.center, terrain.foodPoints)
        if (distForest == null || distFood == null || distForest >= 200 || distFood >= 200) {
            return Factor(0, "Not near a mapped forest/field edge")
        }
        val bonus = if (species == Species.BEAR) 8 else 15
        return Factor(bonus, "Near a forest/field edge - a classic travel and feeding corridor")
    }

    private fun elevationBandFactor(species: Species, cell: GridCell, elevationRange: Pair<Double, Double>?): Factor {
        val elevation = cell.elevationM ?: return Factor(0, "No elevation data for this cell")
        val (min, max) = elevationRange ?: return Factor(0, "No elevation data for this area")
        if (max - min < 5.0 || species == Species.BEAR) {
            // Locally flat, or bear (whose elevation preference operates at a much larger
            // regional scale than one map view, so a local mid-band bonus doesn't apply).
            return Factor(0, "Elevation not a strong factor here")
        }
        val position = (elevation - min) / (max - min)
        return if (position in 0.3..0.7) {
            val bonus = if (species == Species.DEER) 8 else 5
            val reason = if (species == Species.DEER) {
                "Mid-slope bench - a common deer travel/bedding zone"
            } else {
                "Moderate elevation, away from low wet ground"
            }
            Factor(bonus, reason)
        } else {
            Factor(0, "Ridge top or low bottom, not a mid-slope bench")
        }
    }

    private fun nearestDistanceM(from: GeoPoint, points: List<GeoPoint>): Double? {
        if (points.isEmpty()) return null
        return points.minOf { distanceMeters(from, it) }
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private fun Double.roundedStr(): String = String.format(Locale.US, "%.0f", this)
}
