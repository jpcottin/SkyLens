package com.jpcottin.skylens.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jpcottin.skylens.theme.SkyLensTheme
import com.jpcottin.skylens.ui.preview.PreviewSky
import com.jpcottin.skylens.ui.sky.SkyActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun actions(onSelect: (String?) -> Unit = {}, onTarget: (String?) -> Unit = {}) =
        ShellActions(SkyActions(), onUpdateSettings = {}, onRefreshTles = {}, passesFor = { emptyList() }, onSelect = onSelect, onSetTarget = onTarget)

    @Test
    fun tabs_switchBetweenSkyObjectsAndSettings() {
        rule.setContent { SkyLensTheme(dynamicColor = false) { SkyLensNavigation(PreviewSky.state(night = true), actions()) } }
        rule.onNodeWithTag("hud_pointing").assertIsDisplayed()
        rule.onNodeWithTag("nav_objects").performClick()
        rule.onNodeWithTag("objects_list").assertIsDisplayed()
        rule.onNodeWithTag("nav_settings").performClick()
        rule.onNodeWithTag("settings_list").assertIsDisplayed()
        rule.onNodeWithTag("nav_sky").performClick()
        rule.onNodeWithTag("hud_pointing").assertIsDisplayed()
    }

    @Test
    fun objectRow_opensDetail_andPointMeReturnsToTheSky() {
        var selected: String? = null
        var target: String? = null
        rule.setContent { SkyLensTheme(dynamicColor = false) { SkyLensNavigation(PreviewSky.state(night = true), actions(onSelect = { selected = it }, onTarget = { target = it })) } }
        rule.onNodeWithTag("nav_objects").performClick()
        rule.onNodeWithTag("object_row_sat:25544").performClick()
        assertEquals("sat:25544", selected)
        rule.onNodeWithTag("detail_list").assertIsDisplayed()
        rule.onNodeWithText("NORAD 25544", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("detail_point_me").performClick()
        assertEquals("sat:25544", target)
        rule.onNodeWithTag("hud_pointing").assertIsDisplayed()
    }

    @Test
    fun startKey_opensThatTabFirst() {
        rule.setContent { SkyLensTheme(dynamicColor = false) { SkyLensNavigation(PreviewSky.state(night = true), actions(), startKey = ObjectsKey) } }
        rule.onNodeWithTag("objects_list").assertIsDisplayed()
        rule.onNodeWithText("Satellites").assertIsDisplayed()
    }

    @Test
    fun selectedCard_detailsButtonOpensTheDetail() {
        rule.setContent { SkyLensTheme(dynamicColor = false) { SkyLensNavigation(PreviewSky.state(night = true, selectedId = "moon"), actions()) } }
        rule.onNodeWithText("Details").performClick()
        rule.onNodeWithTag("detail_list").assertIsDisplayed()
        rule.onNodeWithText("illuminated", substring = true).assertIsDisplayed()
    }
}
