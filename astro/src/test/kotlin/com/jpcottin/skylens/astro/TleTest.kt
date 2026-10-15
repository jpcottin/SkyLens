package com.jpcottin.skylens.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TleTest {
    private val iss = Sgp4Fixtures.cases.first { it.name == "ISS (ZARYA)" }

    @Test
    fun parse_readsEveryField() {
        val tle = Tle.parse("ISS (ZARYA)", iss.line1, iss.line2)
        assertNotNull(tle)
        tle!!
        assertEquals("ISS (ZARYA)", tle.name)
        assertEquals("25544", tle.catalogNumber)
        assertEquals("98067A", tle.internationalDesignator)
        assertEquals(51.6325, tle.inclinationDeg, 1e-9)
        assertEquals(0.0006710, tle.eccentricity, 1e-12)
        assertEquals(15.48855783, tle.meanMotionRevPerDay, 1e-8)
        assertEquals(iss.epochJd, tle.epochJd, 1e-6)
        assertEquals(0.12831e-3, tle.bstar, 1e-12)
        assertFalse(tle.isDeepSpace)
    }

    @Test
    fun parseAll_handlesNamesAndSkipsGarbage() {
        val hst = Sgp4Fixtures.cases.first { it.name == "HST" }
        val text = """
            ISS (ZARYA)
            ${iss.line1}
            ${iss.line2}
            this is not a tle
            0 HST
            ${hst.line1}
            ${hst.line2}
            1 broken line that is not long enough
            2 neither
        """.trimIndent()
        val all = Tle.parseAll(text)
        assertEquals(2, all.size)
        assertEquals("ISS (ZARYA)", all[0].name)
        // Space-Track style "0 NAME" line: the marker is stripped.
        assertEquals("HST", all[1].name)
    }

    @Test
    fun parseAll_withoutNameLines_fallsBackToCatalogueNumber() {
        val all = Tle.parseAll(iss.line1 + "\n" + iss.line2)
        assertEquals(1, all.size)
        assertEquals("25544", all[0].name)
    }

    @Test
    fun parse_rejectsBadChecksum() {
        val corrupted = iss.line1.substring(0, 68) + "0"
        assertNull(Tle.parse("ISS", corrupted, iss.line2))
    }

    @Test
    fun assumedDecimal_formats() {
        assertEquals(0.12831e-3, Tle.assumedDecimal(" 12831-3"), 1e-12)
        assertEquals(-0.11606e-4, Tle.assumedDecimal("-11606-4"), 1e-14)
        assertEquals(0.0, Tle.assumedDecimal(" 00000+0"), 1e-14)
        assertEquals(0.0, Tle.assumedDecimal("        "), 1e-14)
    }

    @Test
    fun deepSpace_isDetectedFromMeanMotion() {
        // A geostationary-like element set: 1.0027 rev/day.
        val line1 = "1 41866U 16071A   26286.50000000  .00000100  00000+0  00000+0 0  9995"
        val line2 = "2 41866   0.0200  90.0000 0001000 180.0000 180.0000  1.00270000 36000"
        val fixed1 = fixChecksum(line1)
        val fixed2 = fixChecksum(line2)
        val tle = Tle.parse("GOES", fixed1, fixed2)
        assertNotNull(tle)
        assertTrue(tle!!.isDeepSpace)
    }

    private fun fixChecksum(line: String): String {
        var sum = 0
        for (c in line.substring(0, 68)) if (c.isDigit()) sum += c - '0' else if (c == '-') sum += 1
        return line.substring(0, 68) + (sum % 10)
    }
}
