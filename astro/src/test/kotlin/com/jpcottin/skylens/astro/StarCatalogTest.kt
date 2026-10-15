package com.jpcottin.skylens.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarCatalogTest {
    private val catalog = StarCatalog.loadBundled()

    @Test
    fun bundledCatalog_hasEveryStarToMagnitudeFive() {
        assertEquals(1637, catalog.stars.size)
        assertTrue(catalog.stars.all { it.magnitude <= 5.0 })
        assertTrue(catalog.stars.all { it.raDeg in 0.0..360.0 && it.decDeg in -90.0..90.0 })
    }

    @Test
    fun sirius_isTheBrightest() {
        val first = catalog.stars.first()
        assertEquals("Sirius", first.name)
        assertEquals(-1.44, first.magnitude, 1e-9)
        assertEquals("CMa", first.constellation)
        assertEquals("α", first.bayer)
    }

    @Test
    fun namedStars_haveLabels() {
        assertNotNull(catalog.byName("Vega"))
        assertNotNull(catalog.byName("polaris"))
        assertTrue(catalog.named().size > 300)
        val unnamedWithBayer = catalog.stars.first { it.name.isBlank() && it.bayer.isNotBlank() }
        assertEquals("${unnamedWithBayer.bayer} ${unnamedWithBayer.constellation}", unnamedWithBayer.label)
    }

    @Test
    fun parse_skipsCommentsAndBadLines() {
        val text = """
            # comment
            1|Test|1.5|10.0|2.0|0.1|Ori|α
            bad|line
            2||3.0|-5.0|4.5||Tau|
        """.trimIndent()
        val c = StarCatalog.parse(text)
        assertEquals(2, c.stars.size)
        assertEquals(22.5, c.stars[0].raDeg, 1e-9)
        assertEquals("", c.stars[1].label)
        assertEquals(1, c.brighterThan(3.0).size)
    }
}
