package com.jpcottin.skylens.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.click
import com.jpcottin.skylens.astro.Horizontal
import com.jpcottin.skylens.domain.LocationState
import com.jpcottin.skylens.domain.ViewMode
import com.jpcottin.skylens.theme.SkyLensTheme
import com.jpcottin.skylens.ui.preview.PreviewSky
import com.jpcottin.skylens.ui.sky.SkyActions
import com.jpcottin.skylens.ui.sky.SkyScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SkyScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hud_showsWhereTheCameraPoints() {
        rule.setContent { SkyLensTheme(dynamicColor = false) { SkyScreen(PreviewSky.state(night = true), SkyActions()) } }
        // PreviewSky.nightPose looks at azimuth 100° (E), elevation 35°.
        rule.onNodeWithTag("hud_pointing").assertIsDisplayed()
        rule.onNodeWithText("E 100° · alt 35°", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("hud_status").assertTextContains("3 satellites", substring = true)
    }

    @Test
    fun overlay_describesHowManyObjectsAreOnScreen() {
        rule.setContent { SkyLensTheme(dynamicColor = false) { SkyScreen(PreviewSky.state(night = true), SkyActions()) } }
        rule.onNodeWithContentDescription("Sky view", substring = true).assertIsDisplayed()
        val node = rule.onNodeWithContentDescription("objects on screen", substring = true)
        node.assertIsDisplayed()
    }

    @Test
    fun selectedCard_showsTheObjectAndPointMeTogglesTheTarget() {
        var target: String? = "none"
        rule.setContent {
            SkyLensTheme(dynamicColor = false) {
                SkyScreen(PreviewSky.state(night = true, selectedId = "sat:25544"), SkyActions(onSetTarget = { target = it }))
            }
        }
        rule.onNodeWithTag("selected_card").assertIsDisplayed()
        rule.onNodeWithText("ISS (ZARYA)").assertIsDisplayed()
        rule.onNodeWithText("km away", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("point_me").performClick()
        assertEquals("sat:25544", target)
    }

    @Test
    fun selectedCard_stopGuidingWhenAlreadyTargeted() {
        var target: String? = "sat:25544"
        rule.setContent {
            SkyLensTheme(dynamicColor = false) {
                SkyScreen(PreviewSky.state(night = true, selectedId = "sat:25544", targetId = "sat:25544"), SkyActions(onSetTarget = { target = it }))
            }
        }
        rule.onNodeWithText("Stop guiding").assertIsDisplayed().performClick()
        assertEquals(null, target)
    }

    @Test
    fun dismiss_clearsTheSelection() {
        var selected: String? = "moon"
        rule.setContent {
            SkyLensTheme(dynamicColor = false) {
                SkyScreen(PreviewSky.state(night = true, selectedId = "moon"), SkyActions(onSelect = { selected = it }))
            }
        }
        rule.onNodeWithText("Moon").assertIsDisplayed()
        rule.onNodeWithText("illuminated", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(null, selected)
    }

    @Test
    fun tappingTheSky_reportsTheDirectionUnderTheFinger() {
        var tapped: Horizontal? = null
        var radius = 0.0
        val state = PreviewSky.state(night = true)
        rule.setContent { SkyLensTheme(dynamicColor = false) { SkyScreen(state, SkyActions(onTap = { h, r -> tapped = h; radius = r })) } }
        rule.onNodeWithTag("sky_overlay").performTouchInput { click(center) }
        rule.waitForIdle()
        assertNotNull(tapped)
        // The centre of the view is where the camera points.
        assertEquals(state.pose.centre.azimuthDeg, tapped!!.azimuthDeg, 0.5)
        assertEquals(state.pose.centre.elevationDeg, tapped!!.elevationDeg, 0.5)
        assertTrue("hit radius $radius", radius > 0.5 && radius < 10.0)
    }

    @Test
    fun alignChip_onlyInArMode_andCallsBackWithTheObject() {
        var aligned: String? = null
        rule.setContent {
            SkyLensTheme(dynamicColor = false) {
                SkyScreen(PreviewSky.state(night = true, mode = ViewMode.AR, selectedId = "planet:saturn"), SkyActions(onAlignHeading = { aligned = it }))
            }
        }
        rule.onNodeWithTag("align_heading").assertIsDisplayed().performClick()
        assertEquals("planet:saturn", aligned)
    }

    @Test
    fun alignChip_absentInMapMode() {
        rule.setContent { SkyLensTheme(dynamicColor = false) { SkyScreen(PreviewSky.state(night = true, mode = ViewMode.MAP, selectedId = "moon"), SkyActions()) } }
        rule.onAllNodes(hasTestTag("align_heading")).assertCountEquals(0)
    }

    @Test
    fun lowCompassAccuracy_showsTheFigureEightHint() {
        rule.setContent {
            SkyLensTheme(dynamicColor = false) {
                SkyScreen(PreviewSky.state(night = true, mode = ViewMode.AR).copy(compassAccuracy = com.jpcottin.skylens.data.CompassAccuracy.LOW), SkyActions())
            }
        }
        rule.onNodeWithTag("compass_warning").assertIsDisplayed()
        rule.onNodeWithText("figure eight", substring = true).assertIsDisplayed()
    }

    @Test
    fun modeToggle_callsBack() {
        var toggled = 0
        rule.setContent { SkyLensTheme(dynamicColor = false) { SkyScreen(PreviewSky.state(night = true, mode = ViewMode.MAP), SkyActions(onToggleMode = { toggled++ })) } }
        rule.onNodeWithContentDescription("Switch to camera view").performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun withoutLocation_theLocationCardAsksForIt() {
        var asked = false
        rule.setContent {
            SkyLensTheme(dynamicColor = false) {
                SkyScreen(PreviewSky.state(night = true).copy(location = LocationState.PermissionNeeded, snapshot = null), SkyActions(onRequestLocation = { asked = true }))
            }
        }
        rule.onNodeWithTag("location_card").assertIsDisplayed()
        rule.onNodeWithText("Use my location").performClick()
        assertTrue(asked)
        rule.onNodeWithContentDescription("waiting for position", substring = true).assertIsDisplayed()
    }

    @Test
    fun arModeWithoutCameraPermission_offersTheCamera() {
        var asked = false
        rule.setContent {
            SkyLensTheme(dynamicColor = false) {
                SkyScreen(PreviewSky.state(night = true, mode = ViewMode.AR).copy(cameraPermissionGranted = false), SkyActions(onRequestCamera = { asked = true }))
            }
        }
        rule.onNodeWithTag("camera_card").assertIsDisplayed()
        rule.onNodeWithText("Allow camera").performClick()
        assertTrue(asked)
    }
}
