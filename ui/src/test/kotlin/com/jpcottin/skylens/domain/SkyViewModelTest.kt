package com.jpcottin.skylens.domain

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.jpcottin.skylens.astro.BodyKind
import com.jpcottin.skylens.astro.Horizontal
import com.jpcottin.skylens.astro.Observer
import com.jpcottin.skylens.data.Clock
import com.jpcottin.skylens.data.DevicePose
import com.jpcottin.skylens.data.LocationFix
import com.jpcottin.skylens.data.SatelliteGroup
import com.jpcottin.skylens.data.SettingsRepository
import com.jpcottin.skylens.data.TleRepository
import com.jpcottin.skylens.ui.preview.PreviewSky
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** In-memory Preferences DataStore for the settings repository. */
private class FakeDataStore : DataStore<Preferences> {
    private val flow = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> get() = flow
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(flow.value)
        flow.value = next
        return next
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SkyViewModelTest {
    @get:Rule val folder = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val now = PreviewSky.NIGHT_MILLIS
    private val fixes = MutableSharedFlow<LocationFix>(replay = 1)
    private val poses = MutableSharedFlow<DevicePose>(replay = 1)
    private var permission = true

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun TestScope.viewModel(debug: DebugOverrides = DebugOverrides(), sensors: Boolean = true): SkyViewModel {
        val tles = TleRepository(
            cacheDir = folder.root,
            bundled = { if (it == SatelliteGroup.VISUAL) PreviewSky.tleText else null },
            client = OkHttpClient(),
            clock = Clock { now },
            // An unroutable address: the refresh fails fast and the bundled data stays.
            baseUrl = "http://127.0.0.1:9/gp.php",
            ioDispatcher = dispatcher,
        )
        return SkyViewModel(
            settingsRepository = SettingsRepository(FakeDataStore()),
            tleRepository = tles,
            catalogLoader = { PreviewSky.catalog },
            locationFixes = fixes,
            hasLocationPermission = { permission },
            sensorPoses = if (sensors) poses else null,
            clock = Clock { now },
            debug = debug,
            computeDispatcher = dispatcher,
            tickMillis = 500L,
        )
    }

    @Test
    fun withoutALocation_noSnapshotAndPermissionIsAsked() = runTest(dispatcher) {
        permission = false
        val vm = viewModel()
        settle()
        val s = vm.state.value
        assertEquals(LocationState.PermissionNeeded, s.location)
        assertNull(s.snapshot)
        assertEquals(3, s.tle.tles.size)
        vm.viewModelScopeCancel()
    }

    @Test
    fun aFix_producesASnapshotWithEveryLayer() = runTest(dispatcher) {
        val vm = viewModel()
        fixes.emit(LocationFix(PreviewSky.vancouver, 10f, now))
        advanceTimeBy(1_000)
        settle()
        val s = vm.state.value
        assertTrue(s.location is LocationState.Fix)
        val snapshot = s.snapshot
        assertNotNull(snapshot)
        assertEquals(now, snapshot!!.epochMillis)
        assertEquals(3, snapshot.ofKind(BodyKind.SATELLITE).size)
        assertTrue(snapshot.ofKind(BodyKind.STAR).size > 500)
        assertEquals(3, s.satelliteCount)
        vm.viewModelScopeCancel()
    }

    @Test
    fun debugLocation_overridesEverything() = runTest(dispatcher) {
        permission = false
        val paris = Observer(48.8566, 2.3522, 35.0)
        val vm = viewModel(DebugOverrides(location = paris, timeMillis = now, mapMode = true))
        advanceTimeBy(1_000)
        settle()
        val s = vm.state.value
        assertEquals(LocationState.Manual(paris), s.location)
        assertEquals(ViewMode.MAP, s.mode)
        assertNotNull(s.snapshot)
        vm.viewModelScopeCancel()
    }

    @Test
    fun sensorPoses_driveTheCameraOnlyInArMode() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(ViewMode.AR, vm.state.value.mode)
        poses.emit(DevicePose.fromAngles(90.0, 10.0))
        settle()
        assertEquals(90.0, vm.state.value.pose.centre.azimuthDeg, 1e-6)
        vm.setMode(ViewMode.MAP)
        poses.emit(DevicePose.fromAngles(270.0, 10.0))
        settle()
        assertEquals("map mode ignores the sensors", 90.0, vm.state.value.pose.centre.azimuthDeg, 1e-6)
        vm.panMap(-30.0, 20.0)
        assertEquals(60.0, vm.state.value.pose.centre.azimuthDeg, 1e-6)
        assertEquals(30.0, vm.state.value.pose.centre.elevationDeg, 1e-6)
        vm.viewModelScopeCancel()
    }

    @Test
    fun noSensors_startsInMapMode() = runTest(dispatcher) {
        val vm = viewModel(sensors = false)
        assertEquals(ViewMode.MAP, vm.state.value.mode)
        assertFalse(vm.state.value.sensorsAvailable)
        vm.viewModelScopeCancel()
    }

    @Test
    fun panMap_clampsElevationAndWrapsAzimuth() = runTest(dispatcher) {
        val vm = viewModel(sensors = false)
        vm.lookAt(Horizontal(350.0, 80.0))
        vm.panMap(20.0, 30.0)
        assertEquals(10.0, vm.state.value.pose.centre.azimuthDeg, 1e-6)
        assertEquals(89.0, vm.state.value.pose.centre.elevationDeg, 1e-6)
        vm.panMap(0.0, -200.0)
        assertEquals(-20.0, vm.state.value.pose.centre.elevationDeg, 1e-6)
        vm.viewModelScopeCancel()
    }

    @Test
    fun selectNearest_picksTheMoonAndClearsWhenFar() = runTest(dispatcher) {
        val vm = viewModel(DebugOverrides(location = PreviewSky.vancouver, timeMillis = now))
        advanceTimeBy(1_000)
        settle()
        val moon = vm.state.value.snapshot!!.find("moon")!!
        vm.selectNearest(moon.horizontal, 1.0)
        assertEquals("moon", vm.state.value.selectedId)
        assertEquals("moon", vm.state.value.selected?.obj?.id)
        // Nothing within a hundredth of a degree of an arbitrary spot.
        vm.selectNearest(Horizontal(moon.horizontal.azimuthDeg + 10.0, moon.horizontal.elevationDeg), 0.01)
        assertNull(vm.state.value.selectedId)
        vm.viewModelScopeCancel()
    }

    @Test
    fun debugSelectionAndNightMode_applyAtLaunch() = runTest(dispatcher) {
        val vm = viewModel(DebugOverrides(selectId = "moon", nightMode = true), sensors = false)
        settle()
        assertEquals("moon", vm.state.value.selectedId)
        assertTrue(vm.state.value.settings.nightMode)
        vm.viewModelScopeCancel()
    }

    @Test
    fun target_alsoSelects() = runTest(dispatcher) {
        val vm = viewModel(sensors = false)
        vm.setTarget("sat:25544")
        assertEquals("sat:25544", vm.state.value.targetId)
        assertEquals("sat:25544", vm.state.value.selectedId)
        vm.setTarget(null)
        assertNull(vm.state.value.targetId)
        assertEquals("sat:25544", vm.state.value.selectedId)
        vm.viewModelScopeCancel()
    }

    @Test
    fun settingsUpdate_changesLayersInTheNextSnapshot() = runTest(dispatcher) {
        val vm = viewModel(DebugOverrides(location = PreviewSky.vancouver, timeMillis = now))
        advanceTimeBy(1_000)
        settle()
        assertTrue(vm.state.value.snapshot!!.ofKind(BodyKind.STAR).isNotEmpty())
        vm.updateSettings { it.copy(showStars = false) }
        advanceTimeBy(1_000)
        settle()
        assertFalse(vm.state.value.settings.showStars)
        assertTrue(vm.state.value.snapshot!!.ofKind(BodyKind.STAR).isEmpty())
        vm.viewModelScopeCancel()
    }

    @Test
    fun manualLocation_isUsedWhenEnabled() = runTest(dispatcher) {
        permission = false
        val vm = viewModel()
        val sydney = Observer(-33.8688, 151.2093, 20.0)
        vm.updateSettings { it.copy(manualLocation = sydney, useManualLocation = true) }
        advanceTimeBy(1_000)
        settle()
        assertEquals(LocationState.Manual(sydney), vm.state.value.location)
        assertNotNull(vm.state.value.snapshot)
        vm.viewModelScopeCancel()
    }

    @Test
    fun passes_areComputedForTheObserver() = runTest(dispatcher) {
        val vm = viewModel(DebugOverrides(location = PreviewSky.vancouver, timeMillis = now))
        settle()
        val passes = vm.passes(PreviewSky.tles.first(), hours = 24)
        assertTrue(passes.isNotEmpty())
        assertTrue(passes.all { it.riseMillis >= now })
        vm.viewModelScopeCancel()
    }

    @Test
    fun fieldOfView_onlyUpdatesOnChange() = runTest(dispatcher) {
        val vm = viewModel(sensors = false)
        val before = vm.state.value
        vm.setFieldOfView(before.fov)
        assertTrue(before === vm.state.value)
        vm.setFieldOfView(FieldOfView(30.0, 40.0))
        assertEquals(30.0, vm.state.value.fov.horizontalDeg, 1e-9)
        vm.viewModelScopeCancel()
    }

    /** Runs everything that is due and one compute tick; the loop never idles, so never advanceUntilIdle. */
    private fun TestScope.settle() {
        runCurrent()
        advanceTimeBy(600)
        runCurrent()
    }

    /** The compute loop would keep the test scope alive forever: cancel the ViewModel's scope. */
    private fun SkyViewModel.viewModelScopeCancel() = viewModelScope.cancel()
}
