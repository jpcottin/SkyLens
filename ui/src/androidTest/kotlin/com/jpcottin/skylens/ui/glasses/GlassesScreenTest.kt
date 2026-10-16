package com.jpcottin.skylens.ui.glasses

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.xr.glimmer.GlimmerTheme
import com.jpcottin.skylens.astro.PassPredictor
import com.jpcottin.skylens.astro.SatellitePass
import com.jpcottin.skylens.domain.FieldOfView
import com.jpcottin.skylens.domain.LocationState
import com.jpcottin.skylens.ui.objects.PassesState
import com.jpcottin.skylens.ui.preview.PreviewSky
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/** The Glimmer screen is plain Compose, so it runs on any device, not only glasses. */
class GlassesScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val state = PreviewSky.state(night = true).copy(fov = FieldOfView(40.0, 40.0))
    private val passes: List<SatellitePass> by lazy {
        GlassesModel.orderForLens(PreviewSky.tles.flatMap { PassPredictor().nextPasses(it, PreviewSky.vancouver, PreviewSky.NIGHT_MILLIS, hours = 12, minElevationDeg = 10.0, maxPasses = 2) })
    }

    @Test
    fun skyMode_showsHeadingAndTheFocusedObject_tapTogglesMode() {
        var toggles = 0
        val target = state.snapshot!!.find("sat:25544")!!
        rule.setContent {
            GlimmerTheme { GlassesScreen(state.copy(targetId = "sat:25544"), GlassesMode.SKY, target, PassesState.NotApplicable, onToggleMode = { toggles++ }, onSelectPass = {}) }
        }
        rule.onNodeWithTag("glasses_heading").assertIsDisplayed()
        rule.onNodeWithText("E 100° · 35°").assertIsDisplayed()
        rule.onNodeWithText("ISS (ZARYA)").assertIsDisplayed()
        rule.onNodeWithTag("glasses_overlay").assertIsDisplayed()
        rule.onNodeWithTag("glasses_card").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun skyMode_withoutFocus_countsSatellitesUp() {
        rule.setContent { GlimmerTheme { GlassesScreen(state, GlassesMode.SKY, null, PassesState.NotApplicable, onToggleMode = {}, onSelectPass = {}) } }
        rule.onNodeWithText("Sky").assertIsDisplayed()
        rule.onNodeWithText("satellites above the horizon", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Tap for next passes").assertIsDisplayed()
    }

    @Test
    fun skyMode_withoutLocation_saysSo() {
        rule.setContent {
            GlimmerTheme { GlassesScreen(state.copy(snapshot = null, location = LocationState.Unknown), GlassesMode.SKY, null, PassesState.NotApplicable, onToggleMode = {}, onSelectPass = {}) }
        }
        rule.onNodeWithText("Waiting for position").assertIsDisplayed()
    }

    @Test
    fun passesMode_listsPasses_andSelectingOneCallsBack() {
        var selected: SatellitePass? = null
        rule.setContent {
            GlimmerTheme { GlassesScreen(state, GlassesMode.PASSES, null, PassesState.Ready(passes), onToggleMode = {}, onSelectPass = { selected = it }, zone = ZoneId.of("UTC")) }
        }
        rule.onNodeWithText("Next passes").assertIsDisplayed()
        val first = passes.first()
        rule.onNodeWithTag("glasses_pass_${first.satellite.catalogNumber}_${first.riseMillis}").assertIsDisplayed().performClick()
        assertEquals(first, selected)
    }

    @Test
    fun passesMode_loadingAndEmpty() {
        rule.setContent { GlimmerTheme { GlassesScreen(state, GlassesMode.PASSES, null, PassesState.Loading, onToggleMode = {}, onSelectPass = {}) } }
        rule.onNodeWithTag("glasses_passes_loading").assertIsDisplayed()
        rule.onNodeWithText("Computing passes…").assertIsDisplayed()
    }

    @Test
    fun passesMode_empty() {
        rule.setContent { GlimmerTheme { GlassesScreen(state, GlassesMode.PASSES, null, PassesState.Ready(emptyList()), onToggleMode = {}, onSelectPass = {}) } }
        rule.onNodeWithText("No pass in the next 12 hours").assertIsDisplayed()
    }
}
