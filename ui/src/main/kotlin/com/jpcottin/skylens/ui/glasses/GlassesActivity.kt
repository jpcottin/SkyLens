package com.jpcottin.skylens.ui.glasses

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import com.jpcottin.skylens.ui.PASS_OBSERVER_STEP_DEG
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.googlefonts.createGoogleSansFlexTypography
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import com.jpcottin.skylens.appContainer
import com.jpcottin.skylens.data.LocationSource
import com.jpcottin.skylens.data.OrientationSource
import com.jpcottin.skylens.data.PoseReading
import com.jpcottin.skylens.data.PoseSources
import com.jpcottin.skylens.domain.DebugOverrides
import com.jpcottin.skylens.domain.FovFit
import com.jpcottin.skylens.domain.SkyViewModel
import com.jpcottin.skylens.domain.clock
import com.jpcottin.skylens.ui.objects.PassesState
import kotlinx.coroutines.flow.Flow

/**
 * Projected activity shown on Display AI Glasses. It runs on the phone, but as an `xr_projected`
 * activity its context is the glasses' context: the rotation-vector sensor it reads is the one
 * in the glasses, so the sky window follows the wearer's head. Location comes from the phone.
 *
 * The same [SkyViewModel] as the phone computes the sky; the target chosen with "Point me" on
 * the phone is shared through the container so the lens highlights it too.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalProjectedApi::class)
class GlassesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Temporary requirement: let the system focus the first focusable element (the card).
        ComposeUiFlags.isInitialFocusOnFocusableAvailable = true
        super.onCreate(savedInstanceState)
        // Watching the sky is the whole point: keep the lens on while the activity runs.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val container = appContainer
        val debug = if (container.isDebuggable) DebugOverrides.fromIntent(intent) else DebugOverrides()

        setContent {
            GlimmerTheme(typography = createGoogleSansFlexTypography()) {
                val viewModel: SkyViewModel = viewModel {
                    val clock = debug.clock()
                    val declination = {
                        val observer = lastObserver
                        if (observer != null) LocationSource.declinationDeg(observer, clock.nowMillis()) else 0.0
                    }
                    // The glasses' own sensors when they report, else the phone's (the emulator's
                    // glasses advertise a rotation vector that never delivers a reading).
                    val glasses = OrientationSource(this@GlassesActivity).takeIf { it.isAvailable }
                    val host = runCatching { OrientationSource(ProjectedContext.createHostDeviceContext(this@GlassesActivity)) }.getOrNull()?.takeIf { it.isAvailable }
                    val poses: Flow<PoseReading>? = PoseSources.withFallback(
                        glasses?.poses(displayRotation = { 0 }, declinationDeg = declination),
                        host?.poses(displayRotation = { 0 }, declinationDeg = declination),
                    )
                    SkyViewModel(
                        settingsRepository = container.settings,
                        tleRepository = container.tles,
                        catalogLoader = { container.starCatalog() },
                        locationFixes = container.location.fixes(),
                        hasLocationPermission = { container.location.hasPermission() },
                        sensorPoses = poses,
                        clock = clock,
                        debug = debug,
                        sharedTarget = container.sharedTarget,
                    )
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                lastObserver = state.observer
                // On the lens a debug selection is a target: it gets the card and the guide arrow.
                // Sensors, location and the sky computation run only while the screen is visible.
                LifecycleStartEffect(Unit) {
                    viewModel.setForeground(true)
                    onStopOrDispose { viewModel.setForeground(false) }
                }
                LaunchedEffect(Unit) { debug.selectId?.let(viewModel::setTarget) }

                var mode by rememberSaveable { mutableStateOf(GlassesMode.SKY) }
                var passes by remember { mutableStateOf<PassesState>(PassesState.NotApplicable) }
                LaunchedEffect(mode, state.observer?.roundedTo(PASS_OBSERVER_STEP_DEG), state.satelliteCount) {
                    if (mode == GlassesMode.PASSES && state.observer != null) {
                        passes = PassesState.Loading
                        passes = PassesState.Ready(GlassesModel.orderForLens(viewModel.upcomingPasses()))
                    }
                }
                BackHandler(enabled = mode == GlassesMode.PASSES) { mode = GlassesMode.SKY }

                val focus = GlassesModel.focus(state.snapshot, state.camera.centre, state.targetId)
                GlassesScreen(
                    state = state,
                    mode = mode,
                    focus = focus,
                    passes = passes,
                    onToggleMode = { mode = if (mode == GlassesMode.SKY) GlassesMode.PASSES else GlassesMode.SKY },
                    onSelectPass = { pass ->
                        viewModel.setTarget("sat:${pass.satellite.catalogNumber}")
                        mode = GlassesMode.SKY
                    },
                    // The lens is a small window on the sky: a 40° wide view keeps labels readable.
                    onViewSizeChanged = { size -> viewModel.setFieldOfView(FovFit.forView(LENS_FOV_DEG, size.width, size.height)) },
                )
            }
        }
    }

    @Volatile
    private var lastObserver: com.jpcottin.skylens.astro.Observer? = null

    private companion object {
        const val LENS_FOV_DEG = 40.0
    }
}
