package com.jpcottin.skylens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpcottin.skylens.data.LocationSource
import com.jpcottin.skylens.domain.DebugOverrides
import com.jpcottin.skylens.domain.FovFit
import com.jpcottin.skylens.domain.SkyViewModel
import com.jpcottin.skylens.domain.ViewMode
import com.jpcottin.skylens.domain.clock
import com.jpcottin.skylens.theme.SkyLensTheme
import com.jpcottin.skylens.ui.ShellActions
import com.jpcottin.skylens.ui.SkyLensNavigation
import com.jpcottin.skylens.ui.sky.SkyActions
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = appContainer
        // Deterministic inputs for screenshots and CI, honoured by debuggable builds only.
        val debug = if (container.isDebuggable) DebugOverrides.fromIntent(intent) else DebugOverrides()

        setContent {
            val viewModel: SkyViewModel = viewModel {
                val clock = debug.clock()
                val declination = {
                    // Updated lazily from the latest fix; the declination changes by well under a degree across a city.
                    val observer = lastObserver
                    if (observer != null) LocationSource.declinationDeg(observer, clock.nowMillis()) else 0.0
                }
                val poses: Flow<com.jpcottin.skylens.data.PoseReading>? = if (container.orientation.isAvailable) {
                    container.orientation.poses(displayRotation = { display?.rotation ?: 0 }, declinationDeg = declination)
                } else {
                    null
                }
                SkyViewModel(
                    settingsRepository = container.settings,
                    tleRepository = container.tles,
                    catalogLoader = { container.starCatalog() },
                    locationFixes = container.location.fixes(),
                    hasLocationPermission = { container.location.hasPermission() },
                    sensorPoses = poses,
                    clock = clock,
                    debug = debug,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            lastObserver = state.observer

            val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                viewModel.onLocationPermissionResult()
            }
            val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                viewModel.onCameraPermission(granted)
            }
            LaunchedEffect(Unit) {
                viewModel.onCameraPermission(checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                if (!container.location.hasPermission() && debug.location == null && !state.settings.useManualLocation) {
                    locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }

            val sensorFov = container.cameraFov
            val skyActions = SkyActions(
                onTap = viewModel::selectNearest,
                onPan = viewModel::panMap,
                onToggleMode = viewModel::toggleMode,
                onSelect = viewModel::select,
                onSetTarget = viewModel::setTarget,
                onAlignHeading = { id ->
                    val offset = viewModel.alignHeadingTo(id)
                    val name = state.snapshot?.find(id)?.obj?.name ?: id
                    if (offset != null) {
                        android.widget.Toast.makeText(this, getString(com.jpcottin.skylens.R.string.aligned_to, name, "%+.0f".format(offset)), android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onRequestLocation = { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                onRequestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                onViewSizeChanged = { size ->
                    val usesCamera = state.mode == ViewMode.AR && state.cameraPermissionGranted && state.settings.cameraPassthrough
                    viewModel.setFieldOfView(
                        if (usesCamera && sensorFov != null) FovFit.fit(sensorFov.longSideDeg, sensorFov.shortSideDeg, size.width, size.height)
                        else FovFit.forView(66.0, size.width, size.height),
                    )
                },
            )
            SkyLensTheme(nightMode = state.settings.nightMode) {
                SkyLensNavigation(
                    state = state,
                    actions = ShellActions(
                        sky = skyActions,
                        onUpdateSettings = viewModel::updateSettings,
                        onRefreshTles = viewModel::refreshTles,
                        passesFor = { tle -> viewModel.passes(tle) },
                        onSelect = viewModel::select,
                        onSetTarget = viewModel::setTarget,
                    ),
                    cameraBackground = { modifier -> CameraBackground(modifier) },
                    startKey = when (debug.startTab) {
                        "objects" -> com.jpcottin.skylens.ui.ObjectsKey
                        "settings" -> com.jpcottin.skylens.ui.SettingsKey
                        else -> com.jpcottin.skylens.ui.SkyKey
                    },
                )
            }
        }
    }

    @Volatile
    private var lastObserver: com.jpcottin.skylens.astro.Observer? = null
}

/** Binds a CameraX preview to the lifecycle for as long as it is composed and shows it full-bleed. */
@Composable
private fun CameraBackground(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    LaunchedEffect(lifecycleOwner) {
        val provider = runCatching { ProcessCameraProvider.awaitInstance(context) }.getOrNull() ?: return@LaunchedEffect
        val preview = Preview.Builder().build().apply { setSurfaceProvider { request -> surfaceRequest = request } }
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        if (!provider.hasCamera(selector)) return@LaunchedEffect
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview)
        }
        try {
            awaitCancellation()
        } finally {
            provider.unbindAll()
        }
    }
    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
}
