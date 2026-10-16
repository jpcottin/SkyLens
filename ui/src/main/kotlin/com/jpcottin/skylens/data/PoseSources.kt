package com.jpcottin.skylens.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/** Combinators for orientation streams. */
object PoseSources {
    /**
     * Uses [primary] if it delivers a reading within [timeoutMillis], else [secondary]. The
     * glasses emulator advertises a rotation-vector sensor that never reports, so the lens
     * falls back to the phone's sensors there; real glasses keep their own.
     */
    fun withFallback(primary: Flow<PoseReading>?, secondary: Flow<PoseReading>?, timeoutMillis: Long = 3_000L): Flow<PoseReading>? {
        if (primary == null) return secondary
        if (secondary == null) return primary
        return flow {
            val first = withTimeoutOrNull(timeoutMillis) { primary.firstOrNull() }
            if (first != null) {
                emit(first)
                emitAll(primary)
            } else {
                emitAll(secondary)
            }
        }
    }
}
