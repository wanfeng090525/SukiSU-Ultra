package com.sukisu.ultra.ui.screen.uiedit

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.ui.component.bottombar.BottomBarDestination
import com.sukisu.ultra.ui.util.UiCustomizationData
import com.sukisu.ultra.ui.util.UiCustomizationIds
import com.sukisu.ultra.ui.util.UiCustomizationStore
import com.sukisu.ultra.ui.util.UiLayoutKeys
import com.sukisu.ultra.ui.util.UiSlots
import com.sukisu.ultra.ui.util.customText

private data class SlotToggle(
    val slot: String,
    @StringRes val label: Int,
)

private val homeSlots = listOf(
    SlotToggle(UiSlots.HOME_STATUS, R.string.ui_component_home_status),
    SlotToggle(UiSlots.HOME_UPDATE, R.string.ui_component_home_update),
    SlotToggle(UiSlots.HOME_WARNINGS, R.string.ui_component_home_warnings),
    SlotToggle(UiSlots.HOME_AI, R.string.ui_component_home_ai),
    SlotToggle(UiSlots.HOME_STATS, R.string.ui_component_home_stats),
    SlotToggle(UiSlots.HOME_INFO, R.string.ui_component_home_info),
    SlotToggle(UiSlots.HOME_SUPPORT, R.string.ui_component_home_support),
)

private val settingsSlots = listOf(
    SlotToggle("settings.general", R.string.ui_card_general),
    SlotToggle("settings.profile_templates", R.string.ui_card_profile_templates),
    SlotToggle("settings.extensions", R.string.ui_card_extensions),
    SlotToggle("settings.tools", R.string.ui_card_tools),
    SlotToggle("settings.temporary_root", R.string.ui_card_temporary_root),
    SlotToggle("settings.security", R.string.ui_card_security),
    SlotToggle("settings.module_behaviour", R.string.ui_card_module_behaviour),
    SlotToggle("settings.uninstall", R.string.ui_card_uninstall),
    SlotToggle("settings.diagnostics", R.string.ui_card_diagnostics),
)

private val extensionSlots = listOf(
    SlotToggle(UiSlots.EXTENSIONS_TERMINAL, R.string.ui_component_terminal),
    SlotToggle(UiSlots.EXTENSIONS_LIST, R.string.ui_component_extension_list),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiEditorScreen(onBack: () -> Unit) {
    val repository = remember { SettingsRepositoryImpl() }
    var data by remember {
        mutableStateOf(UiCustomizationIds.normalize(UiCustomizationStore.load(repository)))
    }

    fun apply(updated: UiCustomizationData) {
        val normalized = UiCustomizationIds.normalize(updated)
        data = normalized
        UiCustomizationStore.save(repository, normalized)
    }

    fun toggleComponent(slot: String, visible: Boolean) {
        val hidden = data.hiddenComponents.toMutableSet()
        if (visible) hidden.remove(slot) else hidden.add(slot)
        apply(data.copy(hiddenComponents = hidden.toList()))
    }

    fun toggleScreen(screen: String, visible: Boolean) {
        val hidden = data.hiddenScreens.toMutableSet()
        if (visible) {
            hidden.remove(screen)
        } else {
            val remaining = UiCustomizationIds.screens - hidden - screen
            if (remaining.isEmpty()) return
            hidden.add(screen)
        }
        apply(data.copy(hiddenScreens = hidden.toList()))
    }

    fun moveScreen(screen: String, offset: Int) {
        val order = data.navOrder.toMutableList()
        val index = order.indexOf(screen)
        val target = index + offset
        if (index < 0 || target !in order.indices) return
        order[index] = order[target]
        order[target] = screen
        apply(data.copy(navOrder = order))
    }

    fun updateLayout(key: String, value: Float) {
        apply(data.copy(layout = data.layout + (key to value)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.ui_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.close),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        apply(UiCustomizationData())
                    }) {
                        Icon(
                            Icons.Rounded.Restore,
                            contentDescription = stringResource(id = R.string.ui_editor_reset),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 32.dp,
            ),
        ) {
            item {
                Text(
                    text = stringResource(id = R.string.ui_editor_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            item { SectionTitle(R.string.ui_editor_section_pages) }

            item {
                ToggleRow(
                    title = stringResource(id = R.string.ui_editor_hide_bottom_bar),
                    checked = !data.isVisible(UiSlots.CHROME_BOTTOM_BAR),
                    onCheckedChange = { toggleComponent(UiSlots.CHROME_BOTTOM_BAR, !it) },
                )
            }
            item {
                ToggleRow(
                    title = stringResource(id = R.string.ui_editor_hide_top_bar),
                    checked = !data.isVisible(UiSlots.CHROME_TOP_BAR),
                    onCheckedChange = { toggleComponent(UiSlots.CHROME_TOP_BAR, !it) },
                )
            }

            items(count = data.navOrder.size) { index ->
                val screen = data.navOrder[index]
                val destination = BottomBarDestination.entries.firstOrNull { it.key == screen } ?: return@items
                val visible = data.isScreenVisible(screen)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = visible,
                        onCheckedChange = { toggleScreen(screen, it) },
                    )
                    Text(
                        text = destinationLabel(destination),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    IconButton(
                        enabled = index > 0,
                        onClick = { moveScreen(screen, -1) },
                    ) {
                        Icon(
                            Icons.Rounded.ExpandLess,
                            contentDescription = stringResource(id = R.string.ui_editor_move_up),
                        )
                    }
                    IconButton(
                        enabled = index < data.navOrder.lastIndex,
                        onClick = { moveScreen(screen, 1) },
                    ) {
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = stringResource(id = R.string.ui_editor_move_down),
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(id = R.string.ui_editor_pages_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }

            item { SectionTitle(R.string.ui_editor_section_home) }
            items(count = homeSlots.size) { index ->
                val slot = homeSlots[index]
                ToggleRow(
                    title = stringResource(id = slot.label),
                    checked = slot.slot !in data.hiddenComponents,
                    onCheckedChange = { toggleComponent(slot.slot, it) },
                )
            }

            item { SectionTitle(R.string.ui_editor_section_settings) }
            items(count = settingsSlots.size) { index ->
                val slot = settingsSlots[index]
                ToggleRow(
                    title = stringResource(id = slot.label),
                    checked = slot.slot !in data.hiddenComponents,
                    onCheckedChange = { toggleComponent(slot.slot, it) },
                )
            }

            item { SectionTitle(R.string.ui_editor_section_extensions) }
            items(count = extensionSlots.size) { index ->
                val slot = extensionSlots[index]
                ToggleRow(
                    title = stringResource(id = slot.label),
                    checked = slot.slot !in data.hiddenComponents,
                    onCheckedChange = { toggleComponent(slot.slot, it) },
                )
            }

            item { SectionTitle(R.string.ui_editor_section_layout) }
            item {
                LayoutSlider(
                    label = stringResource(id = R.string.ui_editor_home_padding),
                    value = data.layout[UiLayoutKeys.HOME_PADDING] ?: 12f,
                    range = UiLayoutKeys.ranges.getValue(UiLayoutKeys.HOME_PADDING),
                    onValueChange = { updateLayout(UiLayoutKeys.HOME_PADDING, it) },
                )
            }
            item {
                LayoutSlider(
                    label = stringResource(id = R.string.ui_editor_home_spacing),
                    value = data.layout[UiLayoutKeys.HOME_SPACING] ?: 12f,
                    range = UiLayoutKeys.ranges.getValue(UiLayoutKeys.HOME_SPACING),
                    onValueChange = { updateLayout(UiLayoutKeys.HOME_SPACING, it) },
                )
            }
            item {
                LayoutSlider(
                    label = stringResource(id = R.string.ui_editor_home_top),
                    value = data.layout[UiLayoutKeys.HOME_TOP_PADDING] ?: 12f,
                    range = UiLayoutKeys.ranges.getValue(UiLayoutKeys.HOME_TOP_PADDING),
                    onValueChange = { updateLayout(UiLayoutKeys.HOME_TOP_PADDING, it) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Column {
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Text(
            text = stringResource(id = title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LayoutSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "$label · ${value.toInt()} dp",
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = 23,
        )
    }
}

@Composable
private fun destinationLabel(destination: BottomBarDestination): String = when (destination) {
    BottomBarDestination.Home -> customText(com.sukisu.ultra.ui.util.CustomTextKey.PAGE_HOME)
    BottomBarDestination.SuperUser -> customText(com.sukisu.ultra.ui.util.CustomTextKey.PAGE_SUPERUSER)
    BottomBarDestination.Module -> customText(com.sukisu.ultra.ui.util.CustomTextKey.PAGE_MODULES)
    BottomBarDestination.Setting -> customText(com.sukisu.ultra.ui.util.CustomTextKey.PAGE_SETTINGS)
}

private fun UiCustomizationData.isVisible(slot: String): Boolean = slot !in hiddenComponents

private fun UiCustomizationData.isScreenVisible(screen: String): Boolean = screen !in hiddenScreens
