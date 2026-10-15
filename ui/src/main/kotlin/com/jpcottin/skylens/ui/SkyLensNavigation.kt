package com.jpcottin.skylens.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import com.jpcottin.skylens.R
import com.jpcottin.skylens.astro.SatelliteObject
import com.jpcottin.skylens.domain.SkyUiState
import com.jpcottin.skylens.theme.SkyLensTheme
import com.jpcottin.skylens.ui.common.FormFactorPreviews
import com.jpcottin.skylens.ui.objects.ObjectDetailScreen
import com.jpcottin.skylens.ui.objects.ObjectsScreen
import com.jpcottin.skylens.ui.objects.PassesState
import com.jpcottin.skylens.ui.preview.PreviewSky
import com.jpcottin.skylens.ui.settings.SettingsScreen
import com.jpcottin.skylens.ui.sky.SelectedObjectCard
import com.jpcottin.skylens.ui.sky.SkyActions
import com.jpcottin.skylens.ui.sky.SkyScreen
import com.jpcottin.skylens.astro.SatellitePass
import com.jpcottin.skylens.astro.Tle
import kotlinx.serialization.Serializable

@Serializable data object SkyKey : NavKey
@Serializable data object ObjectsKey : NavKey
@Serializable data class DetailKey(val id: String) : NavKey
@Serializable data object SettingsKey : NavKey

/** Everything the navigation shell needs from the activity. */
data class ShellActions(
    val sky: SkyActions,
    val onUpdateSettings: ((com.jpcottin.skylens.data.Settings) -> com.jpcottin.skylens.data.Settings) -> Unit,
    val onRefreshTles: () -> Unit,
    val passesFor: suspend (Tle) -> List<SatellitePass>,
    val onSelect: (String?) -> Unit,
    val onSetTarget: (String?) -> Unit,
)

/**
 * Navigation 3 shell. The navigation suite picks a bottom bar, a rail or a drawer from the
 * window class; Objects and Detail form a list-detail pair that shows side by side when the
 * window is wide (tablet, unfolded foldable, desktop); the sky in tabletop posture puts the
 * selected object's card on the lower half.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SkyLensNavigation(
    state: SkyUiState,
    actions: ShellActions,
    cameraBackground: @Composable (Modifier) -> Unit = {},
    startKey: NavKey = SkyKey,
) {
    val backStack = rememberNavBackStack(startKey)
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isTabletop = adaptiveInfo.windowPosture.isTabletop
    val isWide = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    fun popIfTop(key: NavKey) {
        if (backStack.size > 1 && backStack.lastOrNull() == key) backStack.removeLastOrNull()
    }
    fun switchTab(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }
    val currentTab = backStack.firstOrNull() ?: SkyKey
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            item(
                selected = currentTab == SkyKey,
                onClick = { switchTab(SkyKey) },
                icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_sky)) },
                modifier = Modifier.testTag("nav_sky"),
            )
            item(
                selected = currentTab == ObjectsKey,
                onClick = { switchTab(ObjectsKey) },
                icon = { Icon(Icons.Rounded.List, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_objects)) },
                modifier = Modifier.testTag("nav_objects"),
            )
            item(
                selected = currentTab == SettingsKey,
                onClick = { switchTab(SettingsKey) },
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_settings)) },
                modifier = Modifier.testTag("nav_settings"),
            )
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            sceneStrategies = listOf(listDetailStrategy),
            entryProvider = entryProvider {
                entry<SkyKey> {
                    val skyActions = actions.sky.copy(
                        onOpenDetails = { id -> backStack.add(DetailKey(id)) },
                        onOpenSettings = { switchTab(SettingsKey) },
                    )
                    if (isTabletop) {
                        TabletopSky(state, skyActions, cameraBackground)
                    } else {
                        SkyScreen(state, skyActions, background = cameraBackground)
                    }
                }
                entry<ObjectsKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { ObjectsPlaceholder() })) {
                    Surface(Modifier.fillMaxSize()) {
                        ObjectsScreen(
                            snapshot = state.snapshot,
                            hasLocation = state.observer != null,
                            selectedId = state.selectedId,
                            onSelect = { id ->
                                actions.onSelect(id)
                                if (backStack.lastOrNull() is DetailKey) backStack.removeLastOrNull()
                                backStack.add(DetailKey(id))
                            },
                        )
                    }
                }
                entry<DetailKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                    val position = state.snapshot?.find(key.id)
                    var passes by remember(key.id) { mutableStateOf<PassesState>(if (position?.obj is SatelliteObject) PassesState.Loading else PassesState.NotApplicable) }
                    val tle = (position?.obj as? SatelliteObject)?.tle
                    LaunchedEffect(tle?.catalogNumber, state.observer) {
                        if (tle != null) {
                            passes = PassesState.Loading
                            passes = PassesState.Ready(actions.passesFor(tle))
                        }
                    }
                    ObjectDetailScreen(
                        position = position,
                        passes = passes,
                        minPassElevationDeg = state.settings.minPassElevationDeg,
                        onPointMe = {
                            actions.onSetTarget(key.id)
                            switchTab(SkyKey)
                        },
                        onBack = if (isWide) null else ({ popIfTop(key) }),
                    )
                }
                entry<SettingsKey> {
                    SettingsScreen(
                        settings = state.settings,
                        tle = state.tle,
                        onUpdate = actions.onUpdateSettings,
                        onRefreshTles = actions.onRefreshTles,
                        onBack = null,
                    )
                }
            },
        )
    }
}

@Composable
private fun ObjectsPlaceholder() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.objects_select_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Foldable in tabletop posture (half open, hinge horizontal): the sky on the upper half, where
 * the camera can point at the sky, and the selected object (or a hint) on the flat lower half.
 */
@Composable
fun TabletopSky(state: SkyUiState, actions: SkyActions, cameraBackground: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        SkyScreen(state.copy(selectedId = null), actions, Modifier.weight(1f), compact = true, background = cameraBackground)
        Surface(Modifier.weight(1f).fillMaxWidth().testTag("tabletop_panel"), color = MaterialTheme.colorScheme.surface) {
            val selected = state.selected
            if (selected != null) {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                    SelectedObjectCard(selected, state, actions)
                }
            } else {
                ObjectsScreen(state.snapshot, state.observer != null, state.selectedId, onSelect = { id -> actions.onSelect(id) })
            }
        }
    }
}

@FormFactorPreviews
@Composable
fun SkyLensNavigationPreview() {
    SkyLensTheme(dynamicColor = false) {
        SkyLensNavigation(
            PreviewSky.state(night = true, selectedId = "sat:25544"),
            ShellActions(SkyActions(), {}, {}, { emptyList() }, {}, {}),
        )
    }
}

@Preview(name = "Tabletop", device = "spec:width=673dp,height=841dp", showBackground = true)
@Composable
fun TabletopSkyPreview() {
    SkyLensTheme(dynamicColor = false) {
        Row(Modifier.fillMaxSize()) { TabletopSky(PreviewSky.state(night = true, selectedId = "moon"), SkyActions(), {}) }
    }
}
