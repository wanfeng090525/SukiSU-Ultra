package com.sukisu.ultra.ui.component.bottombar

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sukisu.ultra.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.util.shouldShowSplitPane
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.utils.springAnimateToPage

class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
    private val animatePageChanges: Boolean,
    private val destinations: List<Int> = BottomBarDestination.entries.map { it.ordinal },
) {
    var selectedPage by mutableIntStateOf(destinationAt(pagerState.currentPage))
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    val firstDestination: Int
        get() = destinations.firstOrNull() ?: BottomBarDestination.Home.ordinal

    val selectedPosition: Int
        get() = destinations.indexOf(selectedPage).coerceAtLeast(0)

    fun destinationAt(position: Int): Int =
        destinations.getOrElse(position) { firstDestination }

    fun animateToPage(targetIndex: Int) {
        val targetPosition = destinations.indexOf(targetIndex)
        if (targetPosition < 0) return
        if (targetIndex == selectedPage) return

        navJob?.cancel()

        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                if (animatePageChanges) {
                    pagerState.springAnimateToPage(targetPosition)
                } else {
                    pagerState.scrollToPage(targetPosition)
                }
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetPosition) {
                        selectedPage = destinationAt(pagerState.currentPage)
                    }
                }
            }
        }
    }

    fun syncPage() {
        val destination = destinationAt(pagerState.currentPage)
        if (!isNavigating && selectedPage != destination) {
            selectedPage = destination
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    destinations: List<Int> = BottomBarDestination.entries.map { it.ordinal },
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    animatePageChanges: Boolean = true,
): MainPagerState {
    return remember(pagerState, destinations, coroutineScope, animatePageChanges) {
        MainPagerState(pagerState, coroutineScope, animatePageChanges, destinations)
    }
}

@Immutable
data class NavigationBadgeState(
    val superuserCount: Int = 0,
    val moduleEnabledCount: Int = 0,
    val moduleUpdatableCount: Int = 0,
)

internal enum class BadgeTone { Alert, Accent }

@Immutable
internal data class NavBadge(val count: Int, val tone: BadgeTone)

internal fun badgeFor(index: Int, state: NavigationBadgeState): NavBadge? = when (index) {
    BottomBarDestination.SuperUser.ordinal ->
        state.superuserCount.takeIf { it > 0 }?.let { NavBadge(it, BadgeTone.Accent) }

    BottomBarDestination.Module.ordinal -> when {
        state.moduleUpdatableCount > 0 -> NavBadge(state.moduleUpdatableCount, BadgeTone.Alert)
        state.moduleEnabledCount > 0 -> NavBadge(state.moduleEnabledCount, BadgeTone.Accent)
        else -> null
    }

    else -> null
}

@Composable
fun useNavigationRail(enableFloatingBottomBar: Boolean): Boolean {
    return shouldShowSplitPane() && !(LocalUiMode.current == UiMode.Miuix && enableFloatingBottomBar)
}

@Composable
internal fun rememberBottomBarIconPaths(): Map<String, String> {
    val state by viewModel<SettingsViewModel>().uiState.collectAsStateWithLifecycle()
    return state.bottomBarIcons
}

@Composable
fun BottomBar(
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    if (!rememberBottomBarVisible()) return
    when (LocalUiMode.current) {
        UiMode.Miuix -> BottomBarMiuix(blurBackdrop, backdrop, navigationBadge, modifier)
        UiMode.Material -> BottomBarMaterial(backdrop, navigationBadge, modifier)
    }
}

@Composable
fun SideRail(
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    if (!rememberBottomBarVisible()) return
    when (LocalUiMode.current) {
        UiMode.Miuix -> NavigationRailMiuix(navigationBadge, modifier)
        UiMode.Material -> NavigationRailMaterial(navigationBadge, modifier)
    }
}
