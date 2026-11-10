package com.sukisu.ultra.ui.util

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.data.repository.SettingsRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * User/theme supplied edits for the manager shell.
 *
 * Everything is stored as stable string ids so a theme package stays readable and a newer
 * manager can keep applying a package written by an older one. Unknown ids are dropped on load.
 */
@Immutable
@Serializable
data class UiCustomizationData(
    /** Bottom navigation destinations that are completely hidden ("home", "superuser", ...). */
    val hiddenScreens: List<String> = emptyList(),
    /** Individual widgets/sections addressed by [UiSlots]. */
    val hiddenComponents: List<String> = emptyList(),
    /** Bottom navigation order, destination keys. Missing entries are appended in default order. */
    val navOrder: List<String> = emptyList(),
    /** Layout values in dp, addressed by [UiLayoutKeys]. */
    val layout: Map<String, Float> = emptyMap(),
)

object UiSlots {
    const val CHROME_BOTTOM_BAR = "chrome.bottomBar"
    const val CHROME_TOP_BAR = "chrome.topBar"

    const val HOME_STATUS = "home.status"
    const val HOME_UPDATE = "home.update"
    const val HOME_WARNINGS = "home.warnings"
    const val HOME_AI = "home.ai"
    const val HOME_STATS = "home.stats"
    const val HOME_INFO = "home.info"
    const val HOME_SUPPORT = "home.support"

    const val EXTENSIONS_TERMINAL = "extensions.terminal"
    const val EXTENSIONS_LIST = "extensions.list"

    fun settingsCard(section: String): String = "settings.$section"
}

object UiLayoutKeys {
    const val HOME_PADDING = "home.horizontalPadding"
    const val HOME_SPACING = "home.itemSpacing"
    const val HOME_TOP_PADDING = "home.topPadding"

    val ranges: Map<String, ClosedFloatingPointRange<Float>> = mapOf(
        HOME_PADDING to 0f..48f,
        HOME_SPACING to 0f..32f,
        HOME_TOP_PADDING to 0f..48f,
    )
}

object UiCustomizationIds {
    const val HOME = "home"
    const val SUPERUSER = "superuser"
    const val MODULE = "module"
    const val SETTING = "setting"

    val screens: List<String> = listOf(HOME, SUPERUSER, MODULE, SETTING)

    val components: Set<String> = setOf(
        UiSlots.CHROME_BOTTOM_BAR,
        UiSlots.CHROME_TOP_BAR,
        UiSlots.HOME_STATUS,
        UiSlots.HOME_UPDATE,
        UiSlots.HOME_WARNINGS,
        UiSlots.HOME_AI,
        UiSlots.HOME_STATS,
        UiSlots.HOME_INFO,
        UiSlots.HOME_SUPPORT,
        UiSlots.EXTENSIONS_TERMINAL,
        UiSlots.EXTENSIONS_LIST,
        "settings.general",
        "settings.profile_templates",
        "settings.extensions",
        "settings.tools",
        "settings.temporary_root",
        "settings.security",
        "settings.module_behaviour",
        "settings.uninstall",
        "settings.diagnostics",
    )

    fun normalize(data: UiCustomizationData): UiCustomizationData {
        val hiddenScreens = data.hiddenScreens.filter { it in screens }.distinct()
        // Keep at least one destination reachable even if a theme hides everything.
        val visibleScreens = screens - hiddenScreens.toSet()
        val safeHiddenScreens = if (visibleScreens.isEmpty()) {
            hiddenScreens - HOME
        } else {
            hiddenScreens
        }
        val order = buildList {
            data.navOrder.filter { it in screens }.distinct().forEach(::add)
            screens.forEach { if (it !in this) add(it) }
        }
        val layout = data.layout.mapNotNull { (key, value) ->
            val range = UiLayoutKeys.ranges[key] ?: return@mapNotNull null
            key to value.coerceIn(range.start, range.endInclusive)
        }.toMap()
        return UiCustomizationData(
            hiddenScreens = safeHiddenScreens,
            hiddenComponents = data.hiddenComponents.filter { it in components }.distinct(),
            navOrder = order,
            layout = layout,
        )
    }
}

val LocalUiCustomization = staticCompositionLocalOf { UiCustomizationData() }

fun UiCustomizationData.isVisible(slot: String): Boolean = slot !in hiddenComponents

fun UiCustomizationData.isScreenVisible(screen: String): Boolean = screen !in hiddenScreens

fun UiCustomizationData.layoutDp(key: String, default: Dp): Dp = layout[key]?.dp ?: default

object UiCustomizationStore {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun decode(raw: String): UiCustomizationData {
        if (raw.isBlank()) return UiCustomizationData()
        return runCatching {
            UiCustomizationIds.normalize(json.decodeFromString<UiCustomizationData>(raw))
        }.getOrDefault(UiCustomizationData())
    }

    fun encode(data: UiCustomizationData): String {
        val normalized = UiCustomizationIds.normalize(data)
        if (normalized == UiCustomizationData()) return ""
        return json.encodeToString(normalized)
    }

    fun load(repository: SettingsRepository): UiCustomizationData =
        decode(repository.uiCustomizationJson)

    fun save(repository: SettingsRepository, data: UiCustomizationData) {
        repository.uiCustomizationJson = encode(data)
    }

    fun clear(repository: SettingsRepository) {
        repository.uiCustomizationJson = ""
    }
}
