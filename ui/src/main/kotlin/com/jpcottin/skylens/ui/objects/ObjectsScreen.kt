package com.jpcottin.skylens.ui.objects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jpcottin.skylens.R
import com.jpcottin.skylens.astro.BodyKind
import com.jpcottin.skylens.astro.SkyPosition
import com.jpcottin.skylens.astro.SkySnapshot
import com.jpcottin.skylens.astro.compassPoint
import com.jpcottin.skylens.theme.SkyLensTheme
import com.jpcottin.skylens.ui.common.FormFactorPreviews
import com.jpcottin.skylens.ui.common.SectionHeader
import com.jpcottin.skylens.ui.common.icon
import com.jpcottin.skylens.ui.preview.PreviewSky
import com.jpcottin.skylens.ui.sky.SkyFormat

/** Rows of the objects list, grouped the way the screen shows them. */
data class ObjectSections(
    val satellites: List<SkyPosition>,
    val solarSystem: List<SkyPosition>,
    val stars: List<SkyPosition>,
) {
    val isEmpty: Boolean get() = satellites.isEmpty() && solarSystem.isEmpty() && stars.isEmpty()

    companion object {
        /** Satellites above the horizon first; stars only the bright ones that are up; a query filters by name. */
        fun from(snapshot: SkySnapshot?, query: String, starMagnitude: Double = 1.6): ObjectSections {
            if (snapshot == null) return ObjectSections(emptyList(), emptyList(), emptyList())
            val q = query.trim()
            fun matches(p: SkyPosition) = q.isEmpty() || p.obj.name.contains(q, ignoreCase = true)
            val satellites = snapshot.ofKind(BodyKind.SATELLITE).filter(::matches)
                .sortedWith(compareByDescending<SkyPosition> { it.isAboveHorizon }.thenByDescending { it.horizontal.elevationDeg })
            val solar = snapshot.positions.filter { it.obj.kind == BodyKind.SUN || it.obj.kind == BodyKind.MOON || it.obj.kind == BodyKind.PLANET }
                .filter(::matches).sortedByDescending { it.horizontal.elevationDeg }
            val stars = snapshot.ofKind(BodyKind.STAR)
                .filter { (q.isNotEmpty() && matches(it)) || (q.isEmpty() && (it.magnitude ?: 9.0) <= starMagnitude && it.isAboveHorizon) }
                .filter { it.obj.name.isNotBlank() && !it.obj.name.startsWith("HIP") }
                .sortedBy { it.magnitude ?: 9.0 }
            return ObjectSections(satellites, solar, stars)
        }
    }
}

@Composable
fun ObjectsScreen(
    snapshot: SkySnapshot?,
    hasLocation: Boolean,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val sections = ObjectSections.from(snapshot, query)
    // Hosted directly by the navigation suite (no Scaffold): keep the search field below the status bar.
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("objects_search"),
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.objects_search)) },
            singleLine = true,
        )
        when {
            !hasLocation -> Message(stringResource(R.string.objects_no_location))
            sections.isEmpty -> Message(stringResource(R.string.objects_empty))
            else -> LazyColumn(Modifier.fillMaxSize().testTag("objects_list")) {
                if (sections.satellites.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.objects_satellites)) }
                    items(sections.satellites, key = { it.obj.id }) { ObjectRow(it, it.obj.id == selectedId, onSelect) }
                }
                if (sections.solarSystem.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.objects_solar_system)) }
                    items(sections.solarSystem, key = { it.obj.id }) { ObjectRow(it, it.obj.id == selectedId, onSelect) }
                }
                if (sections.stars.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.objects_stars)) }
                    items(sections.stars, key = { it.obj.id }) { ObjectRow(it, it.obj.id == selectedId, onSelect) }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ObjectRow(p: SkyPosition, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val h = p.horizontal
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .clickable { onSelect(p.obj.id) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("object_row_${p.obj.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            p.obj.kind.icon(),
            contentDescription = null,
            Modifier.padding(end = 12.dp),
            tint = if (p.isAboveHorizon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(p.obj.name, style = MaterialTheme.typography.bodyLarge)
            val detail = buildString {
                append(compassPoint(h.azimuthDeg))
                append(' ').append(SkyFormat.degrees(h.azimuthDeg)).append("° · ")
                if (h.elevationDeg < 0) append(stringResource(R.string.below_horizon)) else append("alt ").append(SkyFormat.degrees(h.elevationDeg)).append('°')
                p.distanceKm?.takeIf { p.obj.kind == BodyKind.SATELLITE }?.let { append(" · ").append(SkyFormat.km(it)).append(" km") }
                p.magnitude?.takeIf { p.obj.kind == BodyKind.STAR || p.obj.kind == BodyKind.PLANET }?.let { append(" · mag ").append(SkyFormat.magnitude(it)) }
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val sunlit = p.sunlit
        if (p.obj.kind == BodyKind.SATELLITE && sunlit != null) {
            Text(
                stringResource(if (sunlit) R.string.sunlit else R.string.in_shadow),
                style = MaterialTheme.typography.labelSmall,
                color = if (sunlit) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@FormFactorPreviews
@Composable
fun ObjectsScreenPreview() {
    SkyLensTheme(dynamicColor = false) {
        androidx.compose.material3.Surface { ObjectsScreen(PreviewSky.nightSnapshot, hasLocation = true, selectedId = "sat:25544", onSelect = {}) }
    }
}

@Preview(name = "No location", showBackground = true)
@Composable
fun ObjectsScreenNoLocationPreview() {
    SkyLensTheme(dynamicColor = false) { androidx.compose.material3.Surface { ObjectsScreen(null, hasLocation = false, selectedId = null, onSelect = {}) } }
}

@Preview(name = "Row", showBackground = true)
@Composable
fun ObjectRowPreview() {
    SkyLensTheme(dynamicColor = false) {
        androidx.compose.material3.Surface {
            Column {
                ObjectRow(PreviewSky.nightSnapshot.find("sat:25544")!!, selected = true, onSelect = {})
                ObjectRow(PreviewSky.nightSnapshot.find("moon")!!, selected = false, onSelect = {})
                ObjectRow(PreviewSky.nightSnapshot.find("planet:jupiter")!!, selected = false, onSelect = {})
            }
        }
    }
}
