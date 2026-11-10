package com.sukisu.ultra.ui.component.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sukisu.ultra.ui.util.LocalUiCustomization
import com.sukisu.ultra.ui.util.isScreenVisible
import com.sukisu.ultra.ui.util.isVisible

/**
 * Destinations that should be shown in the bottom bar / navigation rail, in the user's order.
 * The customization layer guarantees that at least one destination survives.
 */
@Composable
fun rememberVisibleDestinations(): List<BottomBarDestination> {
    val customization = LocalUiCustomization.current
    return remember(customization) {
        val byKey = BottomBarDestination.entries.associateBy { it.key }
        // An empty order means "never customized": fall back to the full default order so the
        // default state never collapses to a single page.
        val ordered = customization.navOrder.mapNotNull(byKey::get)
            .ifEmpty { BottomBarDestination.entries }
        ordered
            .filter { customization.isScreenVisible(it.key) }
            .ifEmpty { listOf(BottomBarDestination.Home) }
    }
}

@Composable
fun rememberBottomBarVisible(): Boolean {
    val customization = LocalUiCustomization.current
    return customization.isVisible("chrome.bottomBar")
}
