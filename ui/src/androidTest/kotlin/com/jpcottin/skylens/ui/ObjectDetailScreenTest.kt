package com.jpcottin.skylens.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jpcottin.skylens.astro.PassPredictor
import com.jpcottin.skylens.theme.SkyLensTheme
import com.jpcottin.skylens.ui.objects.ObjectDetailScreen
import com.jpcottin.skylens.ui.objects.PassesState
import com.jpcottin.skylens.ui.preview.PreviewSky
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

class ObjectDetailScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val passes by lazy { PassPredictor().nextPasses(PreviewSky.tles.first(), PreviewSky.vancouver, PreviewSky.NIGHT_MILLIS, hours = 48, minElevationDeg = 10.0) }

    @Test
    fun satellite_showsOrbitFactsAndPasses() {
        rule.setContent {
            SkyLensTheme(dynamicColor = false) {
                ObjectDetailScreen(PreviewSky.nightSnapshot.find("sat:25544"), PassesState.Ready(passes), 10.0, onPointMe = {}, onBack = {}, zone = ZoneId.of("UTC"))
            }
        }
        rule.onNodeWithText("ISS (ZARYA)").assertIsDisplayed()
        rule.onNodeWithText("NORAD 25544", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Next passes").assertIsDisplayed()
        assertTrue(passes.isNotEmpty())
        rule.onAllNodes(hasTestTag("pass_row")).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    }

    @Test
    fun loading_showsSpinnerText() {
        rule.setContent { SkyLensTheme(dynamicColor = false) { ObjectDetailScreen(PreviewSky.nightSnapshot.find("sat:20580"), PassesState.Loading, 10.0, onPointMe = {}, onBack = {}) } }
        rule.onNodeWithText("Computing passes…").assertIsDisplayed()
    }

    @Test
    fun noPasses_explainsTheThreshold() {
        rule.setContent { SkyLensTheme(dynamicColor = false) { ObjectDetailScreen(PreviewSky.nightSnapshot.find("sat:20580"), PassesState.Ready(emptyList()), 25.0, onPointMe = {}, onBack = {}) } }
        rule.onNodeWithText("No pass above 25° in the next two days.").assertIsDisplayed()
    }

    @Test
    fun pointMe_andBack_callBack() {
        var pointed = false
        var back = false
        rule.setContent { SkyLensTheme(dynamicColor = false) { ObjectDetailScreen(PreviewSky.nightSnapshot.find("moon"), PassesState.NotApplicable, 10.0, onPointMe = { pointed = true }, onBack = { back = true }) } }
        rule.onNodeWithTag("detail_point_me").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        assertTrue(pointed && back)
        rule.onAllNodes(hasTestTag("pass_row")).assertCountEquals(0)
    }

    @Test
    fun missingObject_showsAMessage() {
        rule.setContent { SkyLensTheme(dynamicColor = false) { ObjectDetailScreen(null, PassesState.NotApplicable, 10.0, onPointMe = {}, onBack = null) } }
        rule.onNodeWithText("This object is no longer in the catalogue.").assertIsDisplayed()
    }
}
