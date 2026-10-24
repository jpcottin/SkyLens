package com.jpcottin.skylens.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.NearMeDisabled
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jpcottin.skylens.R
import com.jpcottin.skylens.astro.BodyKind
import com.jpcottin.skylens.astro.Horizontal
import com.jpcottin.skylens.astro.MoonObject
import com.jpcottin.skylens.astro.PlanetObject
import com.jpcottin.skylens.astro.SatelliteObject
import com.jpcottin.skylens.astro.SkyPosition
import com.jpcottin.skylens.astro.SkySnapshot
import com.jpcottin.skylens.astro.StarObject
import com.jpcottin.skylens.astro.SunObject
import com.jpcottin.skylens.astro.compassPoint
import com.jpcottin.skylens.domain.LocationState
import com.jpcottin.skylens.domain.SkyUiState
import com.jpcottin.skylens.domain.ViewMode
import com.jpcottin.skylens.theme.SkyLensTheme
import com.jpcottin.skylens.ui.common.FormFactorPreviews
import com.jpcottin.skylens.ui.common.icon
import com.jpcottin.skylens.ui.common.ThemePreviews
import com.jpcottin.skylens.ui.preview.PreviewSky
import androidx.compose.ui.tooling.preview.Preview

/** Callbacks from the sky screen; kept as a bundle so previews and tests can pass no-ops. */
data class SkyActions(
    val onTap: (Horizontal, Double) -> Unit = { _, _ -> },
    val onPan: (Double, Double) -> Unit = { _, _ -> },
    val onToggleMode: () -> Unit = {},
    val onSelect: (String?) -> Unit = {},
    val onSetTarget: (String?) -> Unit = {},
    /** Compass calibration: the camera is pointed at this object right now. */
    val onAlignHeading: (String) -> Unit = {},
    val onLaunchOnGlasses: () -> Unit = {},
    val onOpenDetails: (String) -> Unit = {},
    val onRequestLocation: () -> Unit = {},
    val onRequestCamera: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onViewSizeChanged: (IntSize) -> Unit = {},
)

/**
 * The sky: camera or drawn background, the [SkyOverlay] on top, a HUD with where the device
 * points, and a card for the selected object. [background] is the camera viewfinder slot.
 */
@Composable
fun SkyScreen(
    state: SkyUiState,
    actions: SkyActions,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    background: @Composable (Modifier) -> Unit = {},
) {
    val palette = if (state.settings.nightMode) SkyPalette.NightVision else SkyPalette.Default
    val camera = state.camera
    // The gesture detectors below must not restart on every state change (a clock tick, a
    // sensor reading at 50 Hz, the pan itself): a restarted detector drops the gesture in
    // progress. They are keyed on the mode only and read the current camera through this.
    val currentCamera = rememberUpdatedState(camera)
    val usesCamera = state.mode == ViewMode.AR && state.cameraPermissionGranted && state.settings.cameraPassthrough
    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (usesCamera) {
            background(Modifier.fillMaxSize())
        } else {
            DrawnSkyBackground(state.snapshot?.sunElevationDeg ?: -20.0, state.settings.nightMode)
        }
        val density = LocalDensity.current
        SkyOverlay(
            snapshot = state.snapshot,
            camera = camera,
            palette = palette,
            options = SkyOverlayOptions(
                showLabels = state.settings.showLabels,
                showTrajectories = state.settings.showTrajectories,
                drawGround = !usesCamera,
                drawGrid = state.mode == ViewMode.MAP,
            ),
            selectedId = state.selectedId,
            targetId = state.targetId,
            modifier = Modifier
                .fillMaxSize()
                .testTag("sky_overlay")
                .onSizeChanged(actions.onViewSizeChanged)
                .pointerInput(state.mode) {
                    detectTapGestures { offset ->
                        val cam = currentCamera.value
                        val size = androidx.compose.ui.geometry.Size(this.size.width.toFloat(), this.size.height.toFloat())
                        val direction = cam.unproject(SkyProjection.toScreenPoint(offset, size))
                        val radiusDeg = SkyProjection.degreesPerPixel(cam, size) * with(density) { 28.dp.toPx() }
                        actions.onTap(direction, radiusDeg)
                    }
                }
                .pointerInput(state.mode) {
                    if (state.mode != ViewMode.MAP) return@pointerInput
                    detectDragGestures { _, drag ->
                        val degPerPx = currentCamera.value.horizontalFovDeg / this.size.width
                        actions.onPan(-drag.x * degPerPx, drag.y * degPerPx)
                    }
                },
        )
        SkyHud(state, actions, compact, Modifier.align(Alignment.TopCenter).safeDrawingPadding())
        Column(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (state.location) {
                LocationState.PermissionNeeded, LocationState.Unknown -> LocationCard(state.location, actions)
                else -> Unit
            }
            if (state.mode == ViewMode.AR && !state.cameraPermissionGranted && state.settings.cameraPassthrough && state.location.observer != null) {
                CameraCard(actions)
            }
            state.selected?.let { SelectedObjectCard(it, state, actions) }
        }
    }
}

@Composable
private fun DrawnSkyBackground(sunElevationDeg: Double, nightMode: Boolean) {
    val colors = when {
        nightMode -> listOf(Color.Black, Color.Black)
        sunElevationDeg > 5 -> listOf(Color(0xFF1E4FA8), Color(0xFF7FB2E8))
        sunElevationDeg > -6 -> listOf(Color(0xFF1A1F4E), Color(0xFFD98A4B))
        sunElevationDeg > -12 -> listOf(Color(0xFF070A24), Color(0xFF2B2F6A))
        else -> listOf(Color(0xFF02030C), Color(0xFF0B1030))
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors)))
}

@Composable
private fun SkyHud(state: SkyUiState, actions: SkyActions, compact: Boolean, modifier: Modifier) {
    val centre = state.camera.centre
    Column(modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), modifier = Modifier.testTag("hud_pointing")) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Explore, contentDescription = null, Modifier.padding(end = 6.dp))
                    Text(
                        stringResource(R.string.hud_pointing, compassPoint(centre.azimuthDeg), SkyFormat.degrees(centre.azimuthDeg), SkyFormat.degrees(centre.elevationDeg)),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.glassesConnected) {
                    FilledTonalIconButton(onClick = actions.onLaunchOnGlasses, modifier = Modifier.testTag("launch_glasses")) {
                        Icon(Icons.Rounded.Visibility, contentDescription = stringResource(R.string.launch_on_glasses))
                    }
                }
                FilledTonalIconButton(onClick = actions.onToggleMode, modifier = Modifier.testTag("mode_toggle")) {
                    if (state.mode == ViewMode.AR) {
                        Icon(Icons.Rounded.Map, contentDescription = stringResource(R.string.switch_to_map))
                    } else {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = stringResource(R.string.switch_to_ar))
                    }
                }
            }
        }
        if (!compact) {
            Spacer(Modifier.height(6.dp))
            val tle = state.tle
            val status = when {
                tle.refreshing -> stringResource(R.string.tle_refreshing)
                tle.error != null -> stringResource(R.string.tle_error, tle.error)
                tle.source == com.jpcottin.skylens.data.TleSource.NETWORK -> stringResource(R.string.tle_network)
                tle.source == com.jpcottin.skylens.data.TleSource.CACHE -> stringResource(R.string.tle_cached)
                else -> stringResource(R.string.tle_bundled)
            }
            // The sky behind is dark whatever the theme: keep the status on its own translucent chip.
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.satellites_loaded, state.satelliteCount) + " · " + status,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("hud_status"),
                    )
                    if (!state.sensorsAvailable && state.mode == ViewMode.MAP) {
                        Text(stringResource(R.string.no_sensors), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (state.mode == ViewMode.AR && state.compassAccuracy.needsCalibration) {
            Spacer(Modifier.height(6.dp))
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f), modifier = Modifier.testTag("compass_warning")) {
                Text(
                    stringResource(R.string.compass_low_accuracy),
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun LocationCard(location: LocationState, actions: SkyActions) {
    Card(Modifier.widthIn(max = 480.dp).padding(bottom = 8.dp).testTag("location_card"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.location_needed_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.location_needed_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onRequestLocation) { Text(stringResource(R.string.grant_location)) }
                OutlinedButton(onClick = actions.onOpenSettings) { Text(stringResource(R.string.open_settings)) }
            }
        }
    }
}

@Composable
private fun CameraCard(actions: SkyActions) {
    Card(Modifier.widthIn(max = 480.dp).padding(bottom = 8.dp).testTag("camera_card"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.camera_permission_body), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = actions.onRequestCamera) { Text(stringResource(R.string.grant_camera)) }
        }
    }
}

/** Card describing the selected object, with the "Point me" guidance toggle. */
@Composable
fun SelectedObjectCard(position: SkyPosition, state: SkyUiState, actions: SkyActions, modifier: Modifier = Modifier) {
    val isTarget = state.targetId == position.obj.id
    Card(modifier.widthIn(max = 520.dp).testTag("selected_card"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(position.obj.kind.icon(), contentDescription = null, Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                Text(position.obj.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { actions.onSelect(null) }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.deselect)) }
            }
            Text(describePosition(position), style = MaterialTheme.typography.bodyMedium)
            Text(describeDetails(position), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (state.mode == ViewMode.AR && state.sensorsAvailable) {
                    // Calibration: the user has the camera on this object, so its azimuth is the truth.
                    TextButton(onClick = { actions.onAlignHeading(position.obj.id) }, modifier = Modifier.testTag("align_heading")) {
                        Icon(Icons.Rounded.Explore, contentDescription = null, Modifier.padding(end = 4.dp))
                        Text(stringResource(R.string.align_heading))
                    }
                }
                TextButton(onClick = { actions.onOpenDetails(position.obj.id) }) { Text(stringResource(R.string.details)) }
                Spacer(Modifier.width(4.dp))
                AssistChip(
                    onClick = { actions.onSetTarget(if (isTarget) null else position.obj.id) },
                    label = { Text(stringResource(if (isTarget) R.string.stop_guiding else R.string.point_me)) },
                    leadingIcon = { Icon(if (isTarget) Icons.Rounded.NearMeDisabled else Icons.Rounded.NearMe, contentDescription = null) },
                    modifier = Modifier.testTag("point_me"),
                )
            }
        }
    }
}

@Composable
fun describePosition(p: SkyPosition): String {
    val h = p.horizontal
    val base = stringResource(R.string.direction, compassPoint(h.azimuthDeg), SkyFormat.degrees(h.azimuthDeg), SkyFormat.degrees(h.elevationDeg))
    return if (h.elevationDeg < 0) "$base · ${stringResource(R.string.below_horizon)}" else base
}

@Composable
fun describeDetails(p: SkyPosition): String = when (val o = p.obj) {
    is SatelliteObject -> listOfNotNull(
        p.distanceKm?.let { stringResource(R.string.range_km, SkyFormat.km(it)) },
        p.speedKmPerSec?.let { stringResource(R.string.speed_kms, "%.1f".format(it)) },
        p.sunlit?.let { stringResource(if (it) R.string.sunlit else R.string.in_shadow) },
    ).joinToString(" · ")
    is StarObject -> listOfNotNull(
        p.magnitude?.let { stringResource(R.string.magnitude, SkyFormat.magnitude(it)) },
        o.star.constellation.takeIf { it.isNotBlank() }?.let { stringResource(R.string.constellation, it) },
    ).joinToString(" · ")
    is PlanetObject -> listOfNotNull(
        p.magnitude?.let { stringResource(R.string.magnitude, SkyFormat.magnitude(it)) },
        p.distanceKm?.let { stringResource(R.string.distance_au, SkyFormat.au(it)) },
    ).joinToString(" · ")
    is MoonObject -> listOfNotNull(
        p.illuminatedFraction?.let { stringResource(R.string.illuminated, Math.round(it * 100).toInt()) },
        p.distanceKm?.let { stringResource(R.string.distance_km, SkyFormat.km(it)) },
    ).joinToString(" · ")
    is SunObject -> p.distanceKm?.let { stringResource(R.string.distance_au, SkyFormat.au(it)) } ?: ""
}

// ---- Previews -------------------------------------------------------------------------------

@FormFactorPreviews
@Composable
fun SkyScreenNightPreview() {
    SkyLensTheme(dynamicColor = false) { SkyScreen(PreviewSky.state(night = true), SkyActions()) }
}

@ThemePreviews
@Composable
fun SkyScreenDayPreview() {
    SkyLensTheme(dynamicColor = false) { SkyScreen(PreviewSky.state(night = false), SkyActions()) }
}

@Preview(name = "Selected satellite", showBackground = true)
@Composable
fun SkyScreenSelectedPreview() {
    SkyLensTheme(dynamicColor = false) { SkyScreen(PreviewSky.state(night = true, selectedId = "sat:25544", targetId = "sat:25544"), SkyActions()) }
}

@Preview(name = "Night vision", showBackground = true)
@Composable
fun SkyScreenNightVisionPreview() {
    val settings = com.jpcottin.skylens.data.Settings(nightMode = true)
    SkyLensTheme(dynamicColor = false, nightMode = true) { SkyScreen(PreviewSky.state(night = true, settings = settings, selectedId = "moon"), SkyActions()) }
}

@Preview(name = "Glasses connected", showBackground = true)
@Composable
fun SkyScreenGlassesConnectedPreview() {
    SkyLensTheme(dynamicColor = false) { SkyScreen(PreviewSky.state(night = true).copy(glassesConnected = true), SkyActions()) }
}

@Preview(name = "AR, compass needs calibration", showBackground = true)
@Composable
fun SkyScreenCompassWarningPreview() {
    SkyLensTheme(dynamicColor = false) {
        SkyScreen(PreviewSky.state(night = true, mode = ViewMode.AR, selectedId = "planet:saturn").copy(compassAccuracy = com.jpcottin.skylens.data.CompassAccuracy.LOW), SkyActions())
    }
}

@Preview(name = "Needs location", showBackground = true)
@Composable
fun SkyScreenNeedsLocationPreview() {
    SkyLensTheme(dynamicColor = false) {
        SkyScreen(PreviewSky.state(night = true).copy(location = LocationState.PermissionNeeded, snapshot = null), SkyActions())
    }
}

@Preview(name = "Overlay only, 400x400", widthDp = 400, heightDp = 400, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SkyOverlayPreview() {
    val state = PreviewSky.state(night = true)
    SkyOverlay(state.snapshot, state.camera, SkyPalette.Default, SkyOverlayOptions(drawGround = true, drawGrid = true), selectedId = "planet:jupiter", targetId = "sat:25544", modifier = Modifier.fillMaxSize())
}
