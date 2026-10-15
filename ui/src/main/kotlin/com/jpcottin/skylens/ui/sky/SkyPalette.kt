package com.jpcottin.skylens.ui.sky

import androidx.compose.ui.graphics.Color
import com.jpcottin.skylens.astro.BodyKind

/** Colours the overlay draws with; the night-vision palette keeps everything red. */
data class SkyPalette(
    val horizon: Color,
    val grid: Color,
    val cardinal: Color,
    val label: Color,
    val labelShadow: Color,
    val sun: Color,
    val sunGlow: Color,
    val moon: Color,
    val moonShadow: Color,
    val planet: Color,
    val satellite: Color,
    val satelliteShadowed: Color,
    val track: Color,
    val selection: Color,
    val target: Color,
    val ground: Color,
    val nightVision: Boolean,
) {
    /** Star colour from its B−V colour index, or red in night-vision mode. */
    fun star(colorIndex: Double): Color = when {
        nightVision -> satellite
        colorIndex < 0.0 -> Color(0xFFBFD4FF)
        colorIndex < 0.4 -> Color(0xFFF0F4FF)
        colorIndex < 0.8 -> Color(0xFFFFF4E0)
        colorIndex < 1.3 -> Color(0xFFFFD9A0)
        else -> Color(0xFFFFB070)
    }

    fun planet(name: String): Color = when {
        nightVision -> planet
        name == "Mars" -> Color(0xFFFF7A5C)
        name == "Jupiter" -> Color(0xFFF2D3A7)
        name == "Saturn" -> Color(0xFFF0E1A8)
        name == "Venus" -> Color(0xFFFFFCE8)
        name == "Mercury" -> Color(0xFFD8D0C8)
        name == "Uranus" -> Color(0xFFA8E8F0)
        name == "Neptune" -> Color(0xFF7FA8FF)
        else -> planet
    }

    fun forKind(kind: BodyKind): Color = when (kind) {
        BodyKind.SUN -> sun
        BodyKind.MOON -> moon
        BodyKind.PLANET -> planet
        BodyKind.STAR -> label
        BodyKind.SATELLITE -> satellite
    }

    companion object {
        val Default = SkyPalette(
            horizon = Color(0xCCFFFFFF),
            grid = Color(0x33FFFFFF),
            cardinal = Color(0xFFFFFFFF),
            label = Color(0xFFFFFFFF),
            labelShadow = Color(0xAA000000),
            sun = Color(0xFFFFD54F),
            sunGlow = Color(0x55FFD54F),
            moon = Color(0xFFF5F2E7),
            moonShadow = Color(0xFF2B2B33),
            planet = Color(0xFFFFE0B2),
            satellite = Color(0xFF80F0FF),
            satelliteShadowed = Color(0xFF9FA8B5),
            track = Color(0xAA80F0FF),
            selection = Color(0xFFFFFFFF),
            target = Color(0xFFFFC046),
            ground = Color(0x66182028),
            nightVision = false,
        )

        val NightVision = SkyPalette(
            horizon = Color(0xCCFF3B30),
            grid = Color(0x33FF3B30),
            cardinal = Color(0xFFFF3B30),
            label = Color(0xFFFF3B30),
            labelShadow = Color(0xAA000000),
            sun = Color(0xFFFF3B30),
            sunGlow = Color(0x33FF3B30),
            moon = Color(0xFFFF6B60),
            moonShadow = Color(0xFF220505),
            planet = Color(0xFFFF8A80),
            satellite = Color(0xFFFF5A50),
            satelliteShadowed = Color(0xFF8A1C16),
            track = Color(0x99FF3B30),
            selection = Color(0xFFFF3B30),
            target = Color(0xFFFF3B30),
            ground = Color(0x66200000),
            nightVision = true,
        )
    }
}
