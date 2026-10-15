package com.jpcottin.skylens.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Sgp4Test {
    @Test
    fun propagate_matchesReferenceImplementationToMillimetres() {
        for (case in Sgp4Fixtures.cases) {
            val tle = Tle.parse(case.name, case.line1, case.line2) ?: error("fixture TLE did not parse")
            val sgp4 = Sgp4(tle)
            for (row in case.rows) {
                val s = sgp4.propagate(row.tsinceMin)
                assertEquals("${case.name} x @${row.tsinceMin}", row.r.x, s.position.x, 1e-6)
                assertEquals("${case.name} y @${row.tsinceMin}", row.r.y, s.position.y, 1e-6)
                assertEquals("${case.name} z @${row.tsinceMin}", row.r.z, s.position.z, 1e-6)
                assertEquals("${case.name} vx @${row.tsinceMin}", row.v.x, s.velocity.x, 1e-9)
                assertEquals("${case.name} vy @${row.tsinceMin}", row.v.y, s.velocity.y, 1e-9)
                assertEquals("${case.name} vz @${row.tsinceMin}", row.v.z, s.velocity.z, 1e-9)
            }
        }
    }

    @Test
    fun propagateAt_usesTheTleEpoch() {
        val case = Sgp4Fixtures.cases.first { it.name == "ISS (ZARYA)" }
        val tle = Tle.parse(case.name, case.line1, case.line2)!!
        val sgp4 = Sgp4(tle)
        val epochMillis = JulianDate.toEpochMillis(tle.epochJd)
        val atEpoch = sgp4.propagateAt(epochMillis)
        val row0 = case.rows.first { it.tsinceMin == 0.0 }
        // Epoch millis are rounded to the millisecond: 1 ms of ISS motion is ≈ 8 m.
        assertEquals(row0.r.x, atEpoch.position.x, 0.01)
        val later = sgp4.propagateAt(epochMillis + 30 * 60_000L)
        val row30 = case.rows.first { it.tsinceMin == 30.0 }
        assertEquals(row30.r.x, later.position.x, 0.01)
    }

    @Test
    fun issAltitude_isAboutFourHundredKilometres() {
        val case = Sgp4Fixtures.cases.first { it.name == "ISS (ZARYA)" }
        val sgp4 = Sgp4(Tle.parse(case.name, case.line1, case.line2)!!)
        val r = sgp4.propagate(0.0).position.length - 6378.135
        assertTrue("altitude $r", r in 380.0..440.0)
        assertEquals(7.66, sgp4.propagate(0.0).velocity.length, 0.1)
    }

    @Test
    fun deepSpace_isRejected() {
        val line1 = "1 41866U 16071A   26286.50000000  .00000100  00000+0  00000+0 0  9995"
        val line2 = "2 41866   0.0200  90.0000 0001000 180.0000 180.0000  1.00270000 36000"
        val tle = Tle.parse("GOES", fix(line1), fix(line2))!!
        try {
            Sgp4(tle)
            fail("expected Sgp4Exception")
        } catch (e: Sgp4Exception) {
            assertTrue(e.message!!.contains("Deep-space"))
        }
    }

    private fun fix(line: String): String {
        var sum = 0
        for (c in line.substring(0, 68)) if (c.isDigit()) sum += c - '0' else if (c == '-') sum += 1
        return line.substring(0, 68) + (sum % 10)
    }
}
