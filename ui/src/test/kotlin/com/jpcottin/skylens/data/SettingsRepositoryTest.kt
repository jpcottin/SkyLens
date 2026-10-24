package com.jpcottin.skylens.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.jpcottin.skylens.astro.Observer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** In-memory DataStore so the repository's mapping can be tested on the JVM. */
private class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> get() = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        state.value = transform(state.value)
        return state.value
    }
}

/** A store whose file cannot be read, as DataStore reports a corrupt or unreadable preferences file. */
private class BrokenDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw IOException("unreadable") }
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        throw IOException("unreadable")
}

class SettingsRepositoryTest {
    @Test
    fun emptyStoreGivesTheDefaults() = runTest {
        val repo = SettingsRepository(FakeDataStore())
        assertEquals(Settings(), repo.settings.first())
    }

    @Test
    fun everyFieldRoundTrips() = runTest {
        val repo = SettingsRepository(FakeDataStore())
        val changed = Settings(
            showSun = false, showMoon = false, showPlanets = false, showStars = false, showSatellites = false,
            starMagnitudeLimit = 6.0, showLabels = false, showTrajectories = false, trajectorySeconds = 900,
            satelliteGroups = setOf(SatelliteGroup.VISUAL), nightMode = true, cameraPassthrough = false,
            manualLocation = Observer(49.2827, -123.1207, 70.0), useManualLocation = true,
            minPassElevationDeg = 25.0, headingOffsetDeg = -12.5,
        )
        repo.update { changed }
        assertEquals(changed, repo.settings.first())
    }

    @Test
    fun clearingTheManualLocationRemovesItsKeys() = runTest {
        val store = FakeDataStore()
        val repo = SettingsRepository(store)
        repo.update { it.copy(manualLocation = Observer(1.0, 2.0, 3.0), useManualLocation = true) }
        assertEquals(Observer(1.0, 2.0, 3.0), repo.settings.first().manualLocation)

        repo.update { it.copy(manualLocation = null) }
        assertNull(repo.settings.first().manualLocation)
        assertTrue(repo.settings.first().useManualLocation)
        val keys = store.state.value.asMap().keys.map { it.name }
        assertFalse(keys.any { it.startsWith("manual_") && it != "use_manual_location" })
    }

    @Test
    fun unknownSatelliteGroupNamesAreDropped() = runTest {
        val seeded = mutablePreferencesOf(
            stringSetPreferencesKey("satellite_groups") to setOf("VISUAL", "NO_SUCH_GROUP"),
        )
        val repo = SettingsRepository(FakeDataStore(seeded))
        assertEquals(setOf(SatelliteGroup.VISUAL), repo.settings.first().satelliteGroups)
    }

    @Test
    fun anUnreadableStoreFallsBackToTheDefaults() = runTest {
        val repo = SettingsRepository(BrokenDataStore())
        assertEquals(Settings(), repo.settings.first())
    }

    @Test
    fun aFailedWriteDoesNotThrow() = runTest {
        SettingsRepository(BrokenDataStore()).update { it.copy(nightMode = true) }
    }
}
