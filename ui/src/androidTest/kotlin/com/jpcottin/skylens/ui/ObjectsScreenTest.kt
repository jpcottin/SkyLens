package com.jpcottin.skylens.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertCountEquals
import com.jpcottin.skylens.theme.SkyLensTheme
import com.jpcottin.skylens.ui.objects.ObjectSections
import com.jpcottin.skylens.ui.objects.ObjectsScreen
import com.jpcottin.skylens.ui.preview.PreviewSky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ObjectsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sections_listSatellitesSolarSystemAndBrightStars() {
        val sections = ObjectSections.from(PreviewSky.nightSnapshot, "")
        assertEquals(3, sections.satellites.size)
        assertEquals(9, sections.solarSystem.size) // Sun, Moon, 7 planets
        assertTrue(sections.stars.isNotEmpty())
        assertTrue(sections.stars.all { it.isAboveHorizon && (it.magnitude ?: 9.0) <= 1.6 })
        // Satellites above the horizon sort first.
        val above = sections.satellites.map { it.isAboveHorizon }
        assertEquals(above.sortedDescending(), above)
    }

    @Test
    fun sections_queryFiltersByName() {
        val sections = ObjectSections.from(PreviewSky.nightSnapshot, "jup")
        assertEquals(listOf("Jupiter"), sections.solarSystem.map { it.obj.name })
        assertTrue(sections.satellites.isEmpty())
        val sirius = ObjectSections.from(PreviewSky.nightSnapshot, "sirius")
        assertEquals(listOf("Sirius"), sirius.stars.map { it.obj.name })
        assertTrue(ObjectSections.from(PreviewSky.nightSnapshot, "zzzz").isEmpty)
    }

    @Test
    fun list_showsRowsAndSelects() {
        var selected: String? = null
        rule.setContent {
            SkyLensTheme(dynamicColor = false) { ObjectsScreen(PreviewSky.nightSnapshot, hasLocation = true, selectedId = null, onSelect = { selected = it }) }
        }
        rule.onNodeWithText("Satellites").assertIsDisplayed()
        rule.onNodeWithTag("object_row_sat:25544").assertIsDisplayed().performClick()
        assertEquals("sat:25544", selected)
        rule.onNodeWithTag("objects_list").performScrollToNode(hasTestTag("object_row_moon"))
        rule.onNodeWithTag("object_row_moon").performClick()
        assertEquals("moon", selected)
    }

    @Test
    fun search_narrowsTheList() {
        rule.setContent { SkyLensTheme(dynamicColor = false) { ObjectsScreen(PreviewSky.nightSnapshot, hasLocation = true, selectedId = null, onSelect = {}) } }
        rule.onNodeWithTag("objects_search").performTextInput("Saturn")
        rule.onNodeWithTag("object_row_planet:saturn").assertIsDisplayed()
        rule.onAllNodes(hasTestTag("object_row_sat:25544")).assertCountEquals(0)
        rule.onNodeWithTag("objects_search").performTextInput("xyz")
        rule.onNodeWithText("Nothing matches").assertIsDisplayed()
    }

    @Test
    fun noLocation_showsTheWaitingMessage() {
        rule.setContent { SkyLensTheme(dynamicColor = false) { ObjectsScreen(null, hasLocation = false, selectedId = null, onSelect = {}) } }
        rule.onNodeWithText("Waiting for your position…").assertIsDisplayed()
    }
}
