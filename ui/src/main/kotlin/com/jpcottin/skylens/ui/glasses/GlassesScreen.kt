package com.jpcottin.skylens.ui.glasses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.Icon
import androidx.xr.glimmer.ListItem
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.TitleChip
import androidx.xr.glimmer.TitleChipDefaults
import androidx.xr.glimmer.list.GlimmerLazyColumn
import com.jpcottin.skylens.R
import com.jpcottin.skylens.astro.BodyKind
import com.jpcottin.skylens.astro.MoonObject
import com.jpcottin.skylens.astro.PlanetObject
import com.jpcottin.skylens.astro.SatelliteObject
import com.jpcottin.skylens.astro.SatellitePass
import com.jpcottin.skylens.astro.SkyPosition
import com.jpcottin.skylens.astro.StarObject
import com.jpcottin.skylens.astro.SunObject
import com.jpcottin.skylens.astro.compassPoint
import com.jpcottin.skylens.domain.SkyUiState
import com.jpcottin.skylens.ui.objects.PassesState
import com.jpcottin.skylens.ui.preview.PreviewSky
import com.jpcottin.skylens.ui.sky.SkyFormat
import com.jpcottin.skylens.ui.sky.SkyOverlay
import com.jpcottin.skylens.ui.sky.SkyOverlayOptions
import com.jpcottin.skylens.ui.sky.SkyPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The projected display of the Display AI Glasses emulator is 450×450 dp at 160 dpi. */
@Preview(name = "Glasses", device = "spec:width=450dp,height=450dp,dpi=160", showBackground = true, backgroundColor = 0xFF000000)
annotation class GlassesPreview

/** Lens palette: bright on transparent black, no ground shading, high-contrast labels. */
val LensPalette: SkyPalette = SkyPalette.Default.copy(
    horizon = Color(0xDDFFFFFF),
    grid = Color(0x22FFFFFF),
    ground = Color.Transparent,
    track = Color(0xDD9BBFFF),
    satellite = Color(0xFF9BBFFF),
    target = Color(0xFFFFD68A),
)

/**
 * The lens UI, built with Jetpack Compose Glimmer. Pure black root (transparent on the additive
 * display), the sky window drawn where the wearer looks, and one bottom-aligned card: what is in
 * front of the wearer. A tap on the card switches to the list of upcoming passes; system back
 * returns to the sky.
 */
@Composable
fun GlassesScreen(
    state: SkyUiState,
    mode: GlassesMode,
    focus: SkyPosition?,
    passes: PassesState,
    onToggleMode: () -> Unit,
    onSelectPass: (SatellitePass) -> Unit,
    modifier: Modifier = Modifier,
    onViewSizeChanged: (IntSize) -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged(onViewSizeChanged),
        contentAlignment = Alignment.BottomCenter,
    ) {
        when (mode) {
            GlassesMode.SKY -> SkyWindow(state, focus, onToggleMode)
            GlassesMode.PASSES -> PassList(state, passes, onSelectPass, zone)
        }
    }
}

@Composable
private fun SkyWindow(state: SkyUiState, focus: SkyPosition?, onToggleMode: () -> Unit) {
    SkyOverlay(
        snapshot = state.snapshot,
        camera = state.camera,
        palette = LensPalette,
        options = SkyOverlayOptions(
            showLabels = true,
            showTrajectories = state.settings.showTrajectories,
            drawGround = false,
            drawGrid = false,
            starLabelMagnitude = 1.0,
            labelSizeSp = 18f,
            markerScale = 1.4f,
        ),
        selectedId = focus?.obj?.id,
        targetId = state.targetId,
        modifier = Modifier.fillMaxSize().testTag("glasses_overlay"),
    )
    val centre = state.camera.centre
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TitleChip(leadingIcon = { Icon(Icons.Rounded.Explore, contentDescription = null) }) {
            Text(
                "${compassPoint(centre.azimuthDeg)} ${SkyFormat.degrees(centre.azimuthDeg)}° · ${SkyFormat.degrees(centre.elevationDeg)}°",
                maxLines = 1,
                modifier = Modifier.testTag("glasses_heading"),
            )
        }
        Spacer(Modifier.height(TitleChipDefaults.associatedContentSpacing))
        val observer = state.observer
        Card(
            onClick = onToggleMode,
            modifier = Modifier.fillMaxWidth().testTag("glasses_card"),
            title = {
                Text(
                    when {
                        observer == null -> stringResource(R.string.glasses_waiting_position)
                        focus != null -> focus.obj.name
                        else -> stringResource(R.string.glasses_nothing_here)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (observer != null && focus != null) {
                    Text(focusLine(focus), style = GlimmerTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else if (observer != null) {
                    Text(
                        stringResource(R.string.glasses_satellites_up, state.snapshot?.ofKind(BodyKind.SATELLITE)?.count { it.isAboveHorizon } ?: 0),
                        style = GlimmerTheme.typography.bodySmall,
                    )
                }
                Text(stringResource(R.string.glasses_tap_for_passes), style = GlimmerTheme.typography.caption, color = GlimmerTheme.colors.primary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun focusLine(p: SkyPosition): String {
    val h = p.horizontal
    val where = "${compassPoint(h.azimuthDeg)} ${SkyFormat.degrees(h.azimuthDeg)}° · ${if (h.elevationDeg < 0) stringResource(R.string.below_horizon) else "alt ${SkyFormat.degrees(h.elevationDeg)}°"}"
    val extra = when (val o = p.obj) {
        is SatelliteObject -> listOfNotNull(
            p.distanceKm?.let { "${SkyFormat.km(it)} km" },
            p.sunlit?.let { stringResource(if (it) R.string.sunlit else R.string.in_shadow) },
        ).joinToString(" · ")
        is MoonObject -> p.illuminatedFraction?.let { stringResource(R.string.illuminated, Math.round(it * 100).toInt()) } ?: ""
        is PlanetObject -> p.magnitude?.let { stringResource(R.string.magnitude, SkyFormat.magnitude(it)) } ?: ""
        is StarObject -> o.star.constellation.takeIf { it.isNotBlank() }?.let { stringResource(R.string.constellation, it) } ?: ""
        is SunObject -> ""
    }
    return if (extra.isBlank()) where else "$where · $extra"
}

@Composable
private fun PassList(state: SkyUiState, passes: PassesState, onSelectPass: (SatellitePass) -> Unit, zone: ZoneId) {
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TitleChip(leadingIcon = { Icon(Icons.Rounded.SatelliteAlt, contentDescription = null) }) {
            Text(stringResource(R.string.passes_title), maxLines = 1)
        }
        Spacer(Modifier.height(TitleChipDefaults.associatedContentSpacing))
        when (passes) {
            PassesState.NotApplicable, PassesState.Loading -> Card(Modifier.fillMaxWidth().testTag("glasses_passes_loading")) {
                Text(stringResource(R.string.passes_computing), style = GlimmerTheme.typography.bodySmall)
            }
            is PassesState.Ready -> if (passes.passes.isEmpty()) {
                Card(Modifier.fillMaxWidth()) { Text(stringResource(R.string.glasses_no_passes), style = GlimmerTheme.typography.bodySmall) }
            } else {
                GlimmerLazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("glasses_passes"),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(passes.passes.size) { index ->
                        val pass = passes.passes[index]
                        ListItem(
                            onClick = { onSelectPass(pass) },
                            modifier = Modifier.testTag("glasses_pass_${pass.satellite.catalogNumber}_${pass.riseMillis}"),
                            leadingIcon = {
                                Icon(
                                    if (pass.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                    contentDescription = stringResource(if (pass.visible) R.string.pass_visible else R.string.pass_daylight),
                                )
                            },
                            supportingLabel = {
                                Text(
                                    "${Instant.ofEpochMilli(pass.riseMillis).atZone(zone).format(timeFormat)} ${compassPoint(pass.riseAzimuthDeg)} · ${SkyFormat.degrees(pass.maxElevationDeg)}° ${compassPoint(pass.culminationAzimuthDeg)}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        ) {
                            Text("${pass.satellite.name} · ${GlassesModel.countdown(state.nowMillis, pass.riseMillis)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// ---- Previews -------------------------------------------------------------------------------

private val previewPasses: List<SatellitePass> by lazy {
    GlassesModel.orderForLens(
        PreviewSky.tles.flatMap { com.jpcottin.skylens.astro.PassPredictor().nextPasses(it, PreviewSky.vancouver, PreviewSky.NIGHT_MILLIS, hours = 12, minElevationDeg = 10.0, maxPasses = 2) },
    )
}

@GlassesPreview
@Composable
fun GlassesSkyPreview() {
    val state = PreviewSky.state(night = true).copy(fov = com.jpcottin.skylens.domain.FieldOfView(40.0, 40.0))
    val focus = GlassesModel.focus(state.snapshot, state.camera.centre, null)
    GlimmerTheme { GlassesScreen(state, GlassesMode.SKY, focus, PassesState.NotApplicable, onToggleMode = {}, onSelectPass = {}) }
}

@GlassesPreview
@Composable
fun GlassesTargetPreview() {
    val state = PreviewSky.state(night = true, targetId = "sat:25544").copy(fov = com.jpcottin.skylens.domain.FieldOfView(40.0, 40.0))
    GlimmerTheme { GlassesScreen(state, GlassesMode.SKY, state.target, PassesState.NotApplicable, onToggleMode = {}, onSelectPass = {}) }
}

@GlassesPreview
@Composable
fun GlassesPassesPreview() {
    GlimmerTheme { GlassesScreen(PreviewSky.state(night = true), GlassesMode.PASSES, null, PassesState.Ready(previewPasses), onToggleMode = {}, onSelectPass = {}, zone = ZoneId.of("America/Vancouver")) }
}

@GlassesPreview
@Composable
fun GlassesPassesLoadingPreview() {
    GlimmerTheme { GlassesScreen(PreviewSky.state(night = true), GlassesMode.PASSES, null, PassesState.Loading, onToggleMode = {}, onSelectPass = {}) }
}

@GlassesPreview
@Composable
fun GlassesNoLocationPreview() {
    GlimmerTheme { GlassesScreen(PreviewSky.state(night = true).copy(snapshot = null, location = com.jpcottin.skylens.domain.LocationState.Unknown), GlassesMode.SKY, null, PassesState.NotApplicable, onToggleMode = {}, onSelectPass = {}) }
}
