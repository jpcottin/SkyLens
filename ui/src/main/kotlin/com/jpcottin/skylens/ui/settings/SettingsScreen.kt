package com.jpcottin.skylens.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jpcottin.skylens.R
import com.jpcottin.skylens.astro.Observer
import com.jpcottin.skylens.data.SatelliteGroup
import com.jpcottin.skylens.data.Settings
import com.jpcottin.skylens.data.TleSource
import com.jpcottin.skylens.data.TleState
import com.jpcottin.skylens.theme.SkyLensTheme
import com.jpcottin.skylens.ui.common.FormFactorPreviews
import com.jpcottin.skylens.ui.common.SectionHeader
import com.jpcottin.skylens.ui.common.SwitchRow
import com.jpcottin.skylens.ui.common.ThemePreviews
import com.jpcottin.skylens.ui.preview.PreviewSky
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    tle: TleState,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onRefreshTles: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).testTag("settings_list")) {
            SectionHeader(stringResource(R.string.settings_layers))
            SwitchRow(stringResource(R.string.settings_sun), settings.showSun, { v -> onUpdate { it.copy(showSun = v) } }, testTag = "switch_sun")
            SwitchRow(stringResource(R.string.settings_moon), settings.showMoon, { v -> onUpdate { it.copy(showMoon = v) } }, testTag = "switch_moon")
            SwitchRow(stringResource(R.string.settings_planets), settings.showPlanets, { v -> onUpdate { it.copy(showPlanets = v) } }, testTag = "switch_planets")
            SwitchRow(stringResource(R.string.settings_stars), settings.showStars, { v -> onUpdate { it.copy(showStars = v) } }, testTag = "switch_stars")
            SwitchRow(stringResource(R.string.settings_satellites), settings.showSatellites, { v -> onUpdate { it.copy(showSatellites = v) } }, testTag = "switch_satellites")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_star_limit, "%.1f".format(settings.starMagnitudeLimit)), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.starMagnitudeLimit.toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(starMagnitudeLimit = (Math.round(v * 2) / 2.0)) } },
                    valueRange = 1f..5f,
                    steps = 7,
                    modifier = Modifier.testTag("slider_magnitude"),
                )
            }

            SectionHeader(stringResource(R.string.settings_satellite_groups))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (group in SatelliteGroup.entries) {
                    val selected = group in settings.satelliteGroups
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onUpdate { s ->
                                val next = if (selected) s.satelliteGroups - group else s.satelliteGroups + group
                                if (next.isEmpty()) s else s.copy(satelliteGroups = next)
                            }
                        },
                        label = { Text(group.displayName) },
                        modifier = Modifier.testTag("group_${group.name}"),
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_display))
            SwitchRow(stringResource(R.string.settings_labels), settings.showLabels, { v -> onUpdate { it.copy(showLabels = v) } }, testTag = "switch_labels")
            SwitchRow(stringResource(R.string.settings_trajectories), settings.showTrajectories, { v -> onUpdate { it.copy(showTrajectories = v) } }, testTag = "switch_trajectories")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_trajectory_length, settings.trajectorySeconds), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.trajectorySeconds.toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(trajectorySeconds = (Math.round(v / 60f) * 60).coerceIn(60, 600)) } },
                    valueRange = 60f..600f,
                    steps = 8,
                    modifier = Modifier.testTag("slider_trajectory"),
                )
            }
            SwitchRow(stringResource(R.string.settings_night_mode), settings.nightMode, { v -> onUpdate { it.copy(nightMode = v) } }, testTag = "switch_night")
            SwitchRow(stringResource(R.string.settings_camera_passthrough), settings.cameraPassthrough, { v -> onUpdate { it.copy(cameraPassthrough = v) } }, testTag = "switch_camera")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.settings_min_pass, settings.minPassElevationDeg.toInt()), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.minPassElevationDeg.toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(minPassElevationDeg = (Math.round(v / 5f) * 5).toDouble()) } },
                    valueRange = 0f..45f,
                    steps = 8,
                    modifier = Modifier.testTag("slider_min_pass"),
                )
            }

            SectionHeader(stringResource(R.string.settings_compass))
            Column(Modifier.padding(horizontal = 16.dp)) {
                val offset = settings.headingOffsetDeg
                Text(stringResource(R.string.settings_heading_offset, (if (offset > 0) "+" else "") + "%.0f".format(offset)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("heading_offset_label"))
                Slider(
                    value = offset.toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(headingOffsetDeg = Math.round(v).toDouble()) } },
                    valueRange = -180f..180f,
                    modifier = Modifier.testTag("slider_heading"),
                )
                Text(stringResource(R.string.settings_heading_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onUpdate { it.copy(headingOffsetDeg = 0.0) } }, enabled = offset != 0.0, modifier = Modifier.padding(top = 8.dp).testTag("reset_heading")) {
                    Text(stringResource(R.string.settings_heading_reset))
                }
            }

            SectionHeader(stringResource(R.string.settings_location))
            SwitchRow(stringResource(R.string.settings_use_manual), settings.useManualLocation, { v -> onUpdate { it.copy(useManualLocation = v) } }, testTag = "switch_manual_location")
            ManualLocationEditor(current = settings.manualLocation, onSave = { observer -> onUpdate { it.copy(manualLocation = observer, useManualLocation = true) } })

            SectionHeader(stringResource(R.string.settings_orbits))
            Column(Modifier.padding(horizontal = 16.dp)) {
                val status = when {
                    tle.refreshing -> stringResource(R.string.tle_refreshing)
                    tle.error != null -> stringResource(R.string.tle_error, tle.error)
                    tle.source == TleSource.NETWORK -> stringResource(R.string.tle_network)
                    tle.source == TleSource.CACHE -> stringResource(R.string.tle_cached)
                    else -> stringResource(R.string.tle_bundled)
                }
                Text(stringResource(R.string.satellites_loaded, tle.tles.size) + " · " + status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("tle_status"))
                tle.fetchedAtMillis?.let {
                    Text(stringResource(R.string.settings_fetched_at, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm"))),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onRefreshTles, enabled = !tle.refreshing, modifier = Modifier.padding(top = 8.dp).testTag("refresh_tle")) { Text(stringResource(R.string.settings_refresh)) }
            }

            SectionHeader(stringResource(R.string.settings_about))
            Text(stringResource(R.string.settings_credits), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Latitude / longitude / altitude fields with validation; saves only when both coordinates parse. */
@Composable
fun ManualLocationEditor(current: Observer?, onSave: (Observer) -> Unit, modifier: Modifier = Modifier) {
    var lat by rememberSaveable(current) { mutableStateOf(current?.latitudeDeg?.toString() ?: "") }
    var lon by rememberSaveable(current) { mutableStateOf(current?.longitudeDeg?.toString() ?: "") }
    var alt by rememberSaveable(current) { mutableStateOf(current?.altitudeM?.toString() ?: "0") }
    val parsed = parseObserver(lat, lon, alt)
    val dirty = parsed != current
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lat, { lat = it }, Modifier.weight(1f).testTag("field_lat"), label = { Text(stringResource(R.string.settings_latitude)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = lat.isNotBlank() && lat.toDoubleOrNull()?.let { it in -90.0..90.0 } != true)
            OutlinedTextField(lon, { lon = it }, Modifier.weight(1f).testTag("field_lon"), label = { Text(stringResource(R.string.settings_longitude)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = lon.isNotBlank() && lon.toDoubleOrNull()?.let { it in -180.0..180.0 } != true)
            OutlinedTextField(alt, { alt = it }, Modifier.weight(0.8f).testTag("field_alt"), label = { Text(stringResource(R.string.settings_altitude)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        if ((lat.isNotBlank() || lon.isNotBlank()) && parsed == null) {
            Text(stringResource(R.string.settings_invalid_location), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { parsed?.let(onSave) }, enabled = parsed != null && dirty, modifier = Modifier.testTag("save_location")) {
            Text(stringResource(R.string.settings_save_location))
        }
    }
}

/** Parses the three fields; null unless latitude and longitude are both valid. */
fun parseObserver(lat: String, lon: String, alt: String): Observer? {
    val la = lat.trim().toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: return null
    val lo = lon.trim().toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: return null
    val al = alt.trim().toDoubleOrNull() ?: 0.0
    return Observer(la, lo, al)
}

@FormFactorPreviews
@Composable
fun SettingsScreenPreview() {
    SkyLensTheme(dynamicColor = false) {
        SettingsScreen(Settings(manualLocation = PreviewSky.vancouver), TleState(PreviewSky.tles, TleSource.CACHE, fetchedAtMillis = PreviewSky.NIGHT_MILLIS), onUpdate = {}, onRefreshTles = {}, onBack = {})
    }
}

@ThemePreviews
@Composable
fun SettingsScreenThemePreview() {
    SkyLensTheme(dynamicColor = false) { SettingsScreen(Settings(), TleState(PreviewSky.tles, TleSource.BUNDLED), onUpdate = {}, onRefreshTles = {}, onBack = null) }
}

@Preview(name = "Night mode", showBackground = true)
@Composable
fun SettingsScreenNightPreview() {
    SkyLensTheme(dynamicColor = false, nightMode = true) { SettingsScreen(Settings(nightMode = true), TleState(PreviewSky.tles, TleSource.NETWORK, refreshing = true), onUpdate = {}, onRefreshTles = {}, onBack = {}) }
}

@Preview(name = "Orbit error", showBackground = true)
@Composable
fun SettingsScreenErrorPreview() {
    SkyLensTheme(dynamicColor = false) { SettingsScreen(Settings(), TleState(PreviewSky.tles, TleSource.CACHE, error = "HTTP 503 for visual"), onUpdate = {}, onRefreshTles = {}, onBack = {}) }
}
