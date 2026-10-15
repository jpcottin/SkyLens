package com.jpcottin.skylens.data

/** Source of "now", replaceable in tests and frozen by the debug launch extras. */
fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM: Clock = Clock { System.currentTimeMillis() }

        /** A clock stuck at one instant, for screenshots and deterministic CI runs. */
        fun frozen(millis: Long): Clock = Clock { millis }
    }
}
