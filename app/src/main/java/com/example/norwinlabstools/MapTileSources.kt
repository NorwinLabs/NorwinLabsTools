package com.example.norwinlabstools

import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * OpenStreetMap's raw tile.openstreetmap.org endpoint actively blocks apps that hit it directly
 * without following its production-usage policy (osm.wiki/Blocked). CARTO's free basemap tiles
 * are policy-compliant for this kind of moderate, non-commercial usage and use OSM data/attribution
 * underneath. Shared by every osmdroid MapView in the app so there's one place to swap providers.
 */
object MapTileSources {
    val DEFAULT: XYTileSource = XYTileSource(
        "CartoVoyager",
        0, 20, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
        )
    )
}
