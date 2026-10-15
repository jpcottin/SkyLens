package com.jpcottin.skylens.astro

/**
 * A NORAD two-line element set. Angles are kept in degrees and the mean motion in
 * revolutions per day, exactly as printed; [Sgp4] converts them.
 */
data class Tle(
    val name: String,
    val catalogNumber: String,
    val internationalDesignator: String,
    val epochJd: Double,
    val meanMotionDot: Double,
    val meanMotionDdot: Double,
    val bstar: Double,
    val inclinationDeg: Double,
    val raanDeg: Double,
    val eccentricity: Double,
    val argPerigeeDeg: Double,
    val meanAnomalyDeg: Double,
    val meanMotionRevPerDay: Double,
    val line1: String,
    val line2: String,
) {
    /** Orbital period in minutes. */
    val periodMinutes: Double get() = 1440.0 / meanMotionRevPerDay

    /** Deep-space objects (period ≥ 225 min) need SDP4, which this engine does not implement. */
    val isDeepSpace: Boolean get() = periodMinutes >= 225.0

    companion object {
        /**
         * Parses a Celestrak-style text block: optional name lines followed by line 1 and line 2.
         * Malformed sets are skipped, never thrown, because a live feed must not take the app down.
         */
        fun parseAll(text: String): List<Tle> {
            val lines = text.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            val result = ArrayList<Tle>()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.startsWith("1 ") && i + 1 < lines.size && lines[i + 1].startsWith("2 ")) {
                    val previous = if (i > 0) lines[i - 1] else ""
                    val name = if (previous.isNotEmpty() && !previous.startsWith("1 ") && !previous.startsWith("2 ")) {
                        previous.trim().removePrefix("0 ").trim()
                    } else {
                        ""
                    }
                    parse(name, line, lines[i + 1])?.let(result::add)
                    i += 2
                } else {
                    i += 1
                }
            }
            return result
        }

        fun parse(name: String, line1: String, line2: String): Tle? {
            if (line1.length < 69 || line2.length < 69) return null
            if (!checksumOk(line1) || !checksumOk(line2)) return null
            return try {
                val epochYear2 = line1.substring(18, 20).trim().toInt()
                val epochYear = if (epochYear2 < 57) 2000 + epochYear2 else 1900 + epochYear2
                val epochDay = line1.substring(20, 32).trim().toDouble()
                val epochJd = JulianDate.fromCalendar(epochYear, 1, 1.0) + epochDay - 1.0
                Tle(
                    name = name.ifBlank { line1.substring(2, 7).trim() },
                    catalogNumber = line1.substring(2, 7).trim(),
                    internationalDesignator = line1.substring(9, 17).trim(),
                    epochJd = epochJd,
                    meanMotionDot = line1.substring(33, 43).trim().toDouble(),
                    meanMotionDdot = assumedDecimal(line1.substring(44, 52)),
                    bstar = assumedDecimal(line1.substring(53, 61)),
                    inclinationDeg = line2.substring(8, 16).trim().toDouble(),
                    raanDeg = line2.substring(17, 25).trim().toDouble(),
                    eccentricity = ("0." + line2.substring(26, 33).trim()).toDouble(),
                    argPerigeeDeg = line2.substring(34, 42).trim().toDouble(),
                    meanAnomalyDeg = line2.substring(43, 51).trim().toDouble(),
                    meanMotionRevPerDay = line2.substring(52, 63).trim().toDouble(),
                    line1 = line1,
                    line2 = line2,
                )
            } catch (e: NumberFormatException) {
                null
            } catch (e: IndexOutOfBoundsException) {
                null
            }
        }

        /** Fields such as `12831-3` mean 0.12831e-3. */
        internal fun assumedDecimal(field: String): Double {
            val f = field.trim()
            if (f.isEmpty()) return 0.0
            val sign = if (f.startsWith("-")) -1.0 else 1.0
            val body = f.trimStart('+', '-')
            val expIndex = body.indexOfFirst { (it == '+' || it == '-') }
            if (expIndex < 0) return sign * ("0.$body").toDouble()
            val mantissa = body.substring(0, expIndex)
            val exponent = body.substring(expIndex).toInt()
            return sign * ("0.$mantissa").toDouble() * Math.pow(10.0, exponent.toDouble())
        }

        /** Modulo-10 checksum of the first 68 characters: digits count, '-' counts one. */
        internal fun checksumOk(line: String): Boolean {
            if (line.length < 69) return false
            var sum = 0
            for (c in line.substring(0, 68)) {
                if (c.isDigit()) sum += c - '0' else if (c == '-') sum += 1
            }
            val expected = line[68]
            return expected.isDigit() && (sum % 10) == (expected - '0')
        }
    }
}
