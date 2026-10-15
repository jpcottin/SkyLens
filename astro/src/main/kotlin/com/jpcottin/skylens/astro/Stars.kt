package com.jpcottin.skylens.astro

import java.io.InputStream

/** A catalogue star with J2000 coordinates. */
data class Star(
    val hipparcos: Int,
    val name: String,
    val raDeg: Double,
    val decDeg: Double,
    val magnitude: Double,
    val colorIndex: Double,
    val constellation: String,
    val bayer: String,
) {
    /** Name shown on the sky: the proper name, else the Bayer letter and constellation, else nothing. */
    val label: String
        get() = when {
            name.isNotBlank() -> name
            bayer.isNotBlank() -> "$bayer $constellation"
            else -> ""
        }

    val j2000: Equatorial get() = Equatorial(raDeg, decDeg)
}

/**
 * The bright-star catalogue bundled with the app: the HYG database v4.1 (CC BY-SA 4.0,
 * https://github.com/astronexus/HYG-Database) cut at magnitude 5.0, 1637 stars.
 */
class StarCatalog(val stars: List<Star>) {
    fun brighterThan(magnitude: Double): List<Star> = stars.filter { it.magnitude <= magnitude }

    fun named(): List<Star> = stars.filter { it.name.isNotBlank() }

    fun byName(name: String): Star? = stars.firstOrNull { it.name.equals(name, ignoreCase = true) }

    companion object {
        /** Loads the bundled catalogue from the module's resources. */
        fun loadBundled(): StarCatalog {
            val stream = StarCatalog::class.java.getResourceAsStream("/stars.txt")
                ?: throw IllegalStateException("stars.txt is missing from the astro resources")
            return parse(stream)
        }

        fun parse(stream: InputStream): StarCatalog = stream.bufferedReader().use { parse(it.readText()) }

        /** Format: `hip|name|ra_hours|dec_deg|mag|color_index|constellation|bayer`, `#` comments. */
        fun parse(text: String): StarCatalog {
            val stars = ArrayList<Star>(1700)
            for (line in text.lineSequence()) {
                if (line.isBlank() || line.startsWith("#")) continue
                val f = line.split('|')
                if (f.size < 8) continue
                val hip = f[0].toIntOrNull() ?: 0
                val ra = f[2].toDoubleOrNull() ?: continue
                val dec = f[3].toDoubleOrNull() ?: continue
                val mag = f[4].toDoubleOrNull() ?: continue
                stars.add(Star(hip, f[1], ra * 15.0, dec, mag, f[5].toDoubleOrNull() ?: 0.0, f[6], f[7]))
            }
            return StarCatalog(stars)
        }
    }
}
