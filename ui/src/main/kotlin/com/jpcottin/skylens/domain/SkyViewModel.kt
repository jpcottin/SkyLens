package com.jpcottin.skylens.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpcottin.skylens.astro.Horizontal
import com.jpcottin.skylens.astro.Observer
import com.jpcottin.skylens.astro.PassPredictor
import com.jpcottin.skylens.astro.SatellitePass
import com.jpcottin.skylens.astro.SkyCamera
import com.jpcottin.skylens.astro.SkyEngine
import com.jpcottin.skylens.astro.SkyPosition
import com.jpcottin.skylens.astro.SkySnapshot
import com.jpcottin.skylens.astro.StarCatalog
import com.jpcottin.skylens.astro.Tle
import com.jpcottin.skylens.astro.angularSeparationDegrees
import com.jpcottin.skylens.astro.angularDifferenceDegrees
import com.jpcottin.skylens.data.Clock
import com.jpcottin.skylens.data.CompassAccuracy
import com.jpcottin.skylens.data.PoseReading
import com.jpcottin.skylens.data.DevicePose
import com.jpcottin.skylens.data.LocationFix
import com.jpcottin.skylens.data.Settings
import com.jpcottin.skylens.data.SettingsRepository
import com.jpcottin.skylens.data.TleRepository
import com.jpcottin.skylens.data.TleState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How the sky is shown. */
enum class ViewMode {
    /** Overlay on the camera, driven by the device sensors. */
    AR,

    /** Drawn sky the user pans by hand; also what the emulator and previews use. */
    MAP,
}

/** Where the observer position comes from. */
sealed interface LocationState {
    /** The position to compute the sky for, when one is known. */
    val observer: Observer? get() = null

    data object Unknown : LocationState
    data object PermissionNeeded : LocationState
    data class Manual(override val observer: Observer) : LocationState
    data class Fix(override val observer: Observer, val accuracyM: Float) : LocationState
}

data class SkyUiState(
    val nowMillis: Long = 0L,
    val settings: Settings = Settings(),
    val tle: TleState = TleState(),
    val location: LocationState = LocationState.Unknown,
    val snapshot: SkySnapshot? = null,
    /** Live device pose in AR mode, the virtual pose in map mode. */
    val pose: DevicePose = DevicePose.fromAngles(0.0, 30.0),
    val fov: FieldOfView = FieldOfView.DEFAULT,
    val mode: ViewMode = ViewMode.AR,
    val sensorsAvailable: Boolean = true,
    val cameraPermissionGranted: Boolean = false,
    val selectedId: String? = null,
    /** Object the user asked to be guided to ("Point me"). */
    val targetId: String? = null,
    val satelliteCount: Int = 0,
    /** Accuracy the platform reports for the magnetic heading (AR mode only). */
    val compassAccuracy: CompassAccuracy = CompassAccuracy.UNKNOWN,
) {
    val camera: SkyCamera get() = pose.toCamera(fov.horizontalDeg, fov.verticalDeg)
    val selected: SkyPosition? get() = selectedId?.let { snapshot?.find(it) }
    val target: SkyPosition? get() = targetId?.let { snapshot?.find(it) }
    val observer: Observer? get() = location.observer
}

class SkyViewModel(
    private val settingsRepository: SettingsRepository,
    private val tleRepository: TleRepository,
    private val catalogLoader: suspend () -> StarCatalog,
    private val locationFixes: Flow<LocationFix>,
    private val hasLocationPermission: () -> Boolean,
    private val sensorPoses: Flow<PoseReading>?,
    private val clock: Clock,
    private val debug: DebugOverrides = DebugOverrides(),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** How often the sky is recomputed. Satellites move ≈ 1°/s at the zenith, so twice a second. */
    private val tickMillis: Long = 500L,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SkyUiState(
            nowMillis = clock.nowMillis(),
            mode = if (debug.mapMode == true || sensorPoses == null) ViewMode.MAP else ViewMode.AR,
            sensorsAvailable = sensorPoses != null,
            pose = debug.pose ?: DevicePose.fromAngles(0.0, 30.0),
            selectedId = debug.selectId,
        ),
    )
    val state: StateFlow<SkyUiState> = _state.asStateFlow()

    private var engine: SkyEngine? = null
    private var catalog: StarCatalog? = null
    private var locationJob: Job? = null
    private var sensorJob: Job? = null
    private var engineKey: Pair<List<Tle>, Int>? = null

    /** Last pose straight from the sensors, before the compass correction. */
    private var rawSensorPose: DevicePose? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s -> _state.update { it.copy(settings = s) } }
        }
        viewModelScope.launch {
            tleRepository.state.collect { t -> _state.update { it.copy(tle = t) } }
        }
        viewModelScope.launch {
            catalog = catalogLoader()
            val groups = state.value.settings.satelliteGroups
            tleRepository.loadLocal(groups)
            tleRepository.refresh(groups)
        }
        // Reload element sets when the chosen groups change.
        viewModelScope.launch {
            settingsRepository.settings.map { it.satelliteGroups }.distinctUntilChanged().collectLatest { groups ->
                tleRepository.loadLocal(groups)
                tleRepository.refresh(groups)
            }
        }
        // Location: debug override, manual setting, or the platform fix.
        viewModelScope.launch {
            settingsRepository.settings.map { it.useManualLocation to it.manualLocation }.distinctUntilChanged().collect { (useManual, manual) ->
                restartLocation(useManual, manual)
            }
        }
        debug.nightMode?.let { night -> viewModelScope.launch { settingsRepository.update { it.copy(nightMode = night) } } }
        startSensors()
        viewModelScope.launch { computeLoop() }
    }

    private fun restartLocation(useManual: Boolean, manual: Observer?) {
        locationJob?.cancel()
        debug.location?.let { fixed ->
            _state.update { it.copy(location = LocationState.Manual(fixed)) }
            return
        }
        if (useManual && manual != null) {
            _state.update { it.copy(location = LocationState.Manual(manual)) }
            return
        }
        if (!hasLocationPermission()) {
            _state.update { it.copy(location = if (manual != null) LocationState.Manual(manual) else LocationState.PermissionNeeded) }
            return
        }
        locationJob = viewModelScope.launch {
            locationFixes
                .catch { }
                .collect { fix -> _state.update { it.copy(location = LocationState.Fix(fix.observer, fix.accuracyM)) } }
        }
    }

    private fun startSensors() {
        sensorJob?.cancel()
        if (debug.pose != null || sensorPoses == null) return
        sensorJob = viewModelScope.launch {
            sensorPoses.catch { }.collect { reading ->
                rawSensorPose = reading.pose
                _state.update { s ->
                    if (s.mode != ViewMode.AR) s
                    else s.copy(pose = reading.pose.yawed(s.settings.headingOffsetDeg), compassAccuracy = reading.accuracy)
                }
            }
        }
    }

    private suspend fun computeLoop() {
        while (true) {
            val s = state.value
            val observer = s.observer
            val cat = catalog
            if (observer != null && cat != null) {
                val tles = s.tle.tles
                val span = s.settings.trajectorySeconds
                val eng = engine?.takeIf { engineKey == (tles to span) } ?: withContext(computeDispatcher) {
                    SkyEngine(cat, tles, trackSpanSeconds = span).also { engine = it; engineKey = tles to span }
                }
                val now = clock.nowMillis()
                val snapshot = withContext(computeDispatcher) { eng.compute(observer, now, s.settings.toLayers()) }
                _state.update { it.copy(snapshot = snapshot, nowMillis = now, satelliteCount = eng.satelliteCount) }
            } else {
                _state.update { it.copy(nowMillis = clock.nowMillis()) }
            }
            delay(tickMillis)
        }
    }

    /** The user granted or refused location: re-evaluate the source. */
    fun onLocationPermissionResult() {
        val s = state.value.settings
        restartLocation(s.useManualLocation, s.manualLocation)
    }

    fun onCameraPermission(granted: Boolean) = _state.update { it.copy(cameraPermissionGranted = granted) }

    fun setFieldOfView(fov: FieldOfView) = _state.update { if (it.fov == fov) it else it.copy(fov = fov) }

    fun setMode(mode: ViewMode) {
        _state.update { it.copy(mode = mode) }
        if (mode == ViewMode.AR) startSensors()
    }

    fun toggleMode() = setMode(if (state.value.mode == ViewMode.AR) ViewMode.MAP else ViewMode.AR)

    /** Map mode: pan the virtual camera by screen deltas expressed in degrees. */
    fun panMap(deltaAzimuthDeg: Double, deltaElevationDeg: Double) {
        _state.update { s ->
            if (s.mode != ViewMode.MAP) return@update s
            val c = s.pose.centre
            val az = (c.azimuthDeg + deltaAzimuthDeg + 360.0) % 360.0
            val el = (c.elevationDeg + deltaElevationDeg).coerceIn(-20.0, 89.0)
            s.copy(pose = DevicePose.fromAngles(az, el, 0.0))
        }
    }

    /** Map mode: look straight at a sky direction. */
    fun lookAt(direction: Horizontal) {
        _state.update { s -> s.copy(mode = ViewMode.MAP, pose = DevicePose.fromAngles(direction.azimuthDeg, direction.elevationDeg.coerceIn(-20.0, 89.0), 0.0)) }
    }

    fun select(id: String?) = _state.update { it.copy(selectedId = id) }

    fun setTarget(id: String?) = _state.update { it.copy(targetId = id, selectedId = id ?: it.selectedId) }

    /**
     * Compass calibration: the user points the camera at [objectId] and the correction becomes
     * whatever turns the raw sensor heading onto that object's azimuth. Returns the new offset,
     * or null when there is no sensor pose or no such object.
     */
    fun alignHeadingTo(objectId: String): Double? {
        val raw = rawSensorPose ?: return null
        val target = state.value.snapshot?.find(objectId) ?: return null
        val offset = angularDifferenceDegrees(target.horizontal.azimuthDeg, raw.centre.azimuthDeg)
        setHeadingOffset(offset)
        return offset
    }

    /** Sets the compass correction (wrapped to (-180, 180]) and re-applies it to the current pose. */
    fun setHeadingOffset(degrees: Double) {
        val wrapped = angularDifferenceDegrees(degrees, 0.0)
        rawSensorPose?.let { raw -> _state.update { if (it.mode == ViewMode.AR) it.copy(pose = raw.yawed(wrapped)) else it } }
        updateSettings { it.copy(headingOffsetDeg = wrapped) }
    }

    /** Picks the object nearest to a sky direction within `maxSeparationDeg`, or clears the selection. */
    fun selectNearest(direction: Horizontal, maxSeparationDeg: Double) {
        val snapshot = state.value.snapshot ?: return
        val nearest = snapshot.positions
            .filter { it.isAboveHorizon || it.obj.kind != com.jpcottin.skylens.astro.BodyKind.STAR }
            .minByOrNull { angularSeparationDegrees(it.horizontal, direction) }
        select(nearest?.takeIf { angularSeparationDegrees(it.horizontal, direction) <= maxSeparationDeg }?.obj?.id)
    }

    fun refreshTles() {
        viewModelScope.launch { tleRepository.refresh(state.value.settings.satelliteGroups, force = true) }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    /** Next passes of a satellite for the current observer, computed off the main thread. */
    suspend fun passes(tle: Tle, hours: Int = 48): List<SatellitePass> {
        val observer = state.value.observer ?: return emptyList()
        val minEl = state.value.settings.minPassElevationDeg
        return withContext(computeDispatcher) { PassPredictor().nextPasses(tle, observer, clock.nowMillis(), hours, minEl) }
    }
}
