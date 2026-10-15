package com.jpcottin.skylens.ui.objects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jpcottin.skylens.R
import com.jpcottin.skylens.astro.SatelliteObject
import com.jpcottin.skylens.astro.SatellitePass
import com.jpcottin.skylens.astro.SkyPosition
import com.jpcottin.skylens.astro.compassPoint
import com.jpcottin.skylens.theme.SkyLensTheme
import com.jpcottin.skylens.ui.common.FormFactorPreviews
import com.jpcottin.skylens.ui.common.SectionHeader
import com.jpcottin.skylens.ui.common.icon
import com.jpcottin.skylens.ui.preview.PreviewSky
import com.jpcottin.skylens.ui.sky.SkyFormat
import com.jpcottin.skylens.ui.sky.describeDetails
import com.jpcottin.skylens.ui.sky.describePosition
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pass list state for the detail screen. */
sealed interface PassesState {
    data object NotApplicable : PassesState
    data object Loading : PassesState
    data class Ready(val passes: List<SatellitePass>) : PassesState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectDetailScreen(
    position: SkyPosition?,
    passes: PassesState,
    minPassElevationDeg: Double,
    onPointMe: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(position?.obj?.name ?: "") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) }
                    }
                },
            )
        },
    ) { padding ->
        if (position == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.object_not_found), textAlign = TextAlign.Center)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("detail_list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(position.obj.kind.icon(), contentDescription = null, Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(position.obj.kind.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(describePosition(position), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("detail_position"))
                        val details = describeDetails(position)
                        if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        (position.obj as? SatelliteObject)?.let { sat ->
                            Text("NORAD ${sat.tle.catalogNumber} · ${sat.tle.internationalDesignator} · period ${"%.1f".format(sat.tle.periodMinutes)} min",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = onPointMe, modifier = Modifier.padding(top = 8.dp).testTag("detail_point_me")) {
                            Icon(Icons.Rounded.NearMe, contentDescription = null, Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.point_me))
                        }
                    }
                }
            }
            when (passes) {
                PassesState.NotApplicable -> Unit
                PassesState.Loading -> item {
                    SectionHeader(stringResource(R.string.passes_title))
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp))
                        Text(stringResource(R.string.passes_computing))
                    }
                }
                is PassesState.Ready -> {
                    item { SectionHeader(stringResource(R.string.passes_title)) }
                    if (passes.passes.isEmpty()) {
                        item { Text(stringResource(R.string.passes_none, minPassElevationDeg.toInt()), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(passes.passes, key = { it.riseMillis }) { PassRow(it, zone) }
                }
            }
        }
    }
}

@Composable
fun PassRow(pass: SatellitePass, zone: ZoneId, modifier: Modifier = Modifier) {
    val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM")
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    fun time(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(timeFormat)
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("pass_row"), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (pass.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            contentDescription = stringResource(if (pass.visible) R.string.pass_visible else R.string.pass_daylight),
            Modifier.padding(end = 12.dp),
            tint = if (pass.visible) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(Instant.ofEpochMilli(pass.riseMillis).atZone(zone).format(dayFormat) + " · " + stringResource(R.string.pass_duration, pass.durationSeconds / 60), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(
                    R.string.pass_summary,
                    time(pass.riseMillis) + " " + compassPoint(pass.riseAzimuthDeg),
                    SkyFormat.degrees(pass.maxElevationDeg),
                    compassPoint(pass.culminationAzimuthDeg),
                    time(pass.setMillis) + " " + compassPoint(pass.setAzimuthDeg),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Previews -------------------------------------------------------------------------------

private val previewPasses: List<SatellitePass> by lazy {
    val iss = PreviewSky.tles.first()
    com.jpcottin.skylens.astro.PassPredictor().nextPasses(iss, PreviewSky.vancouver, PreviewSky.NIGHT_MILLIS, hours = 48, minElevationDeg = 10.0)
}

@FormFactorPreviews
@Composable
fun ObjectDetailSatellitePreview() {
    SkyLensTheme(dynamicColor = false) {
        ObjectDetailScreen(PreviewSky.nightSnapshot.find("sat:25544"), PassesState.Ready(previewPasses), 10.0, onPointMe = {}, onBack = {}, zone = ZoneId.of("America/Vancouver"))
    }
}

@Preview(name = "Moon", showBackground = true)
@Composable
fun ObjectDetailMoonPreview() {
    SkyLensTheme(dynamicColor = false) { ObjectDetailScreen(PreviewSky.nightSnapshot.find("moon"), PassesState.NotApplicable, 10.0, onPointMe = {}, onBack = null) }
}

@Preview(name = "Computing passes", showBackground = true)
@Composable
fun ObjectDetailLoadingPreview() {
    SkyLensTheme(dynamicColor = false) { ObjectDetailScreen(PreviewSky.nightSnapshot.find("sat:20580"), PassesState.Loading, 10.0, onPointMe = {}, onBack = {}) }
}

@Preview(name = "Missing object", showBackground = true)
@Composable
fun ObjectDetailMissingPreview() {
    SkyLensTheme(dynamicColor = false) { ObjectDetailScreen(null, PassesState.NotApplicable, 10.0, onPointMe = {}, onBack = {}) }
}
