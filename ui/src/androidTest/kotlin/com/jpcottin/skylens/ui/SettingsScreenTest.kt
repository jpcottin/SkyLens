package com.jpcottin.skylens.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jpcottin.skylens.astro.Observer
import com.jpcottin.skylens.data.SatelliteGroup
import com.jpcottin.skylens.data.Settings
import com.jpcottin.skylens.data.TleSource
import com.jpcottin.skylens.data.TleState
import com.jpcottin.skylens.theme.SkyLensTheme
import com.jpcottin.skylens.ui.preview.PreviewSky
import com.jpcottin.skylens.ui.settings.SettingsScreen
import com.jpcottin.skylens.ui.settings.parseObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun content(initial: Settings = Settings(), tle: TleState = TleState(PreviewSky.tles, TleSource.BUNDLED), onRefresh: () -> Unit = {}, observe: (Settings) -> Unit = {}) {
        rule.setContent {
            var settings by androidx.compose.runtime.remember { mutableStateOf(initial) }
            SkyLensTheme(dynamicColor = false) {
                SettingsScreen(settings, tle, onUpdate = { t -> settings = t(settings); observe(settings) }, onRefreshTles = onRefresh, onBack = null)
            }
        }
    }

    @Test
    fun layerSwitches_toggle() {
        var latest = Settings()
        content(observe = { latest = it })
        rule.onNodeWithTag("switch_stars").assertIsOn().performClick()
        rule.onNodeWithTag("switch_stars").assertIsOff()
        assertFalse(latest.showStars)
        rule.onNodeWithTag("switch_night").performScrollTo().performClick()
        assertTrue(latest.nightMode)
    }

    @Test
    fun satelliteGroups_neverEmpty() {
        var latest = Settings()
        content(observe = { latest = it })
        rule.onNodeWithTag("group_STATIONS").performClick()
        assertEquals(setOf(SatelliteGroup.VISUAL), latest.satelliteGroups)
        // Removing the last group is refused.
        rule.onNodeWithTag("group_VISUAL").performClick()
        assertEquals(setOf(SatelliteGroup.VISUAL), latest.satelliteGroups)
        rule.onNodeWithTag("group_STARLINK").performClick()
        assertTrue(SatelliteGroup.STARLINK in latest.satelliteGroups)
    }

    @Test
    fun manualLocation_validatesAndSaves() {
        var latest = Settings()
        content(observe = { latest = it })
        rule.onNodeWithTag("field_lat").performScrollTo().performTextInput("95")
        rule.onNodeWithTag("field_lon").performTextInput("2.35")
        rule.onNodeWithText("Latitude must be within ±90 and longitude within ±180.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("save_location").assertIsNotEnabled()
        rule.onNodeWithTag("field_lat").performTextClearance()
        rule.onNodeWithTag("field_lat").performTextInput("48.85")
        rule.onNodeWithTag("save_location").assertIsEnabled().performClick()
        assertEquals(Observer(48.85, 2.35, 0.0), latest.manualLocation)
        assertTrue(latest.useManualLocation)
    }

    @Test
    fun orbitStatus_andRefresh() {
        var refreshed = false
        content(tle = TleState(PreviewSky.tles, TleSource.CACHE, fetchedAtMillis = PreviewSky.NIGHT_MILLIS, error = "HTTP 503 for visual"), onRefresh = { refreshed = true })
        rule.onNodeWithTag("tle_status").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Orbit update failed: HTTP 503 for visual", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("refresh_tle").performScrollTo().performClick()
        assertTrue(refreshed)
    }

    @Test
    fun refresh_disabledWhileRefreshing() {
        content(tle = TleState(PreviewSky.tles, TleSource.NETWORK, refreshing = true))
        rule.onNodeWithTag("refresh_tle").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun compassCorrection_resetsToZero() {
        var latest = Settings(headingOffsetDeg = 12.0)
        content(initial = latest, observe = { latest = it })
        rule.onNodeWithTag("heading_offset_label").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Heading correction: +12°").assertIsDisplayed()
        rule.onNodeWithTag("reset_heading").performScrollTo().assertIsEnabled().performClick()
        assertEquals(0.0, latest.headingOffsetDeg, 1e-9)
        rule.onNodeWithTag("reset_heading").assertIsNotEnabled()
    }

    @Test
    fun parseObserver_rules() {
        assertEquals(Observer(1.0, 2.0, 3.0), parseObserver("1", "2", "3"))
        assertEquals(Observer(1.0, 2.0, 0.0), parseObserver("1", "2", ""))
        assertNull(parseObserver("91", "2", ""))
        assertNull(parseObserver("1", "-181", ""))
        assertNull(parseObserver("", "", ""))
    }
}
