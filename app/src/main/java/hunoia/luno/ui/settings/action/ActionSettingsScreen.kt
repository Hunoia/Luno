package hunoia.luno.ui.settings.action

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import hunoia.luno.ui.theme.ConnectionRadius
import hunoia.luno.ui.theme.ContainerRadius
import hunoia.luno.ui.theme.SegmentedGap
import hunoia.luno.ui.theme.ListSpacing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.search.SearchCenter
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.GestureButtonActionSettingsOverride
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.QuickSearchSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.config.model.defaultEngine
import hunoia.luno.config.model.miniWindowSettings
import hunoia.luno.config.model.withMiniWindowSettings
import hunoia.luno.ui.component.MiniWindowControls
import hunoia.luno.ui.component.MyColumn
import hunoia.luno.ui.component.SegmentedGroup
import hunoia.luno.ui.component.SegmentedSettingsRow
import hunoia.luno.ui.component.SegmentedSwitchRow
import hunoia.luno.ui.component.segmentedShape
import hunoia.luno.ui.component.TopBar
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.RowIconSize
import hunoia.luno.ui.theme.liquidGlassBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

import kotlinx.coroutines.launch
import hunoia.luno.ui.component.gestureButtonMarkColor
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.ui.platform.LocalDensity
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.ui.component.sheets.OptimizedBottomSheet
import hunoia.luno.ui.settings.gesture.style.ActionPanelStyleConfigContent
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSettingsScreen(
    onBack: () -> Unit,
    onNavToSearchEngines: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var showActionPanelStyle by remember { mutableStateOf(false) }
    val actionSettings by TuneConfig.action.collectAsStateWithLifecycle(initialValue = ActionSettings())
    val advancedSettings by TuneConfig.advanced.collectAsStateWithLifecycle(initialValue = AdvancedSettings())
    val buttons by GestureConfig.buttons.collectAsStateWithLifecycle(initialValue = emptyList())
    val subGestureSettings by GestureConfig.subGestures.collectAsStateWithLifecycle(initialValue = SubGestureSettings())
    val quickSearchSettings by SearchCenter.search.collectAsStateWithLifecycle(initialValue = QuickSearchSettings())

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = liquidGlassBackdrop()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(),
        topBar = { TopBar(onBack = onBack, title = stringResource(R.string.action_settings), scrollBehavior = scrollBehavior, backdrop = backdrop) }
    ) { padding ->
        if (showActionPanelStyle) {
            OptimizedBottomSheet(onDismissRequest = { showActionPanelStyle = false }) {
                ActionPanelStyleConfigContent(
                    currentStyle = actionSettings.actionPanelStyle,
                    onStyleChanged = { next ->
                        scope.launch { TuneConfig.updateAction { it.copy(actionPanelStyle = next) } }
                    },
                )
            }
        }
        MyColumn(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topPadding = padding.calculateTopPadding(),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            SegmentedGroup(contentSpacing = SegmentedGap) {
                SegmentedSettingsRow(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.search_engine_settings),
                    subtitle = quickSearchSettings.defaultEngine().name,
                    onClick = onNavToSearchEngines,
                )
            }

            SegmentedGroup(contentSpacing = SegmentedGap) {
                SegmentedSettingsRow(
                    icon = Icons.Default.Gesture,
                    title = stringResource(R.string.action_panel_style),
                    subtitle = actionPanelStyleSummary(actionSettings.actionPanelStyle),
                    onClick = { showActionPanelStyle = true },
                )
            }

            SegmentedGroup(contentSpacing = SegmentedGap) {
                SegmentedSettingsRow(
                    icon = Icons.Default.Crop,
                    title = stringResource(R.string.mini_window_position_short),
                    subtitle = stringResource(R.string.mini_window_position_hint),
                )
                MiniWindowControls(
                    settings = advancedSettings.miniWindowSettings(),
                    onChange = { next -> scope.launch { TuneConfig.updateAdvanced { it.withMiniWindowSettings(next) } } },
                )
            }

            buttons.sortedBy { it.id }.forEachIndexed { index, button ->
                OverrideCard(
                    label = button.name.ifBlank { stringResource(R.string.gesture_button_name, index + 1) },
                    color = button.color,
                    actionSettingsOverride = button.actionSettingsOverride,
                    onUpdateOverride = { updated ->
                        scope.launch {
                            GestureConfig.updateButtons { list ->
                                list.map { if (it.id == button.id) it.copy(actionSettingsOverride = updated) else it }
                            }
                        }
                    },
                    globalMiniWindow = advancedSettings.miniWindowSettings(),
                )
            }

            subGestureSettings.subGestures.sortedBy { it.name.ifBlank { it.id } }.forEach { subGesture ->
                OverrideCard(
                    label = subGesture.name.ifBlank { stringResource(R.string.action_sub_gesture) },
                    color = subGesture.color,
                    actionSettingsOverride = subGesture.actionSettingsOverride,
                    onUpdateOverride = { updated ->
                        scope.launch {
                            GestureConfig.updateSubGestures { settings ->
                                settings.copy(
                                    subGestures = settings.subGestures.map { if (it.id == subGesture.id) it.copy(actionSettingsOverride = updated) else it }
                                )
                            }
                        }
                    },
                    globalMiniWindow = advancedSettings.miniWindowSettings(),
                )
            }
        }
    }
}

/** 副标题摘要：图标大小 · 图标间距 · 弧线铺开。 */
@Composable
private fun actionPanelStyleSummary(style: ArcStyle): String {
    val density = LocalDensity.current
    val itemDp = with(density) { style.itemSize.toDp().value }.roundToInt()
    val gapDp = with(density) { style.itemGapPx.toDp().value }.roundToInt()
    return "$itemDp dp · $gapDp dp · ${(style.spreadSpacing * 100).roundToInt()}%"
}

@Composable
private fun OverrideCard(
    label: String,
    color: Int,
    actionSettingsOverride: GestureButtonActionSettingsOverride,
    onUpdateOverride: (GestureButtonActionSettingsOverride) -> Unit,
    globalMiniWindow: MiniWindowSettings,
) {
    var expanded by remember { mutableStateOf(false) }
    val dotColor = gestureButtonMarkColor(color)
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "OverrideCardArrowRotation",
    )
    val headerBottomRadius by animateDpAsState(
        targetValue = if (expanded) ConnectionRadius else ContainerRadius,
        label = "OverrideCardHeaderBottomRadius",
    )
    val headerShape = RoundedCornerShape(
        topStart = ContainerRadius,
        topEnd = ContainerRadius,
        bottomStart = headerBottomRadius,
        bottomEnd = headerBottomRadius,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ContainerRadius)),
    ) {
        SegmentedSettingsRow(
            title = label,
            onClick = { expanded = !expanded },
            shape = headerShape,
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(MarkColorSize)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.7f)),
                )
            },
            trailingContent = {
                Icon(
                    modifier = Modifier
                        .size(RowIconSize)
                        .graphicsLayer { rotationZ = rotation },
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(segmentedShape(1, 2)),
                verticalArrangement = Arrangement.spacedBy(SegmentedGap),
            ) {
                    OverrideSection(
                        title = stringResource(R.string.mini_window_position_short),
                        enabled = actionSettingsOverride.miniWindow != null,
                        onEnabledChange = { enabled ->
                            onUpdateOverride(actionSettingsOverride.copy(
                                miniWindow = if (enabled) globalMiniWindow else null
                            ))
                        },
                    ) {
                        MiniWindowControls(
                            settings = actionSettingsOverride.miniWindow ?: globalMiniWindow,
                            onChange = { next -> onUpdateOverride(actionSettingsOverride.copy(miniWindow = next)) },
                        )
                    }
                }
            }
        }
    }

@Composable
private fun OverrideSection(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
        SegmentedSwitchRow(
            title = title,
            subtitle = if (enabled) stringResource(R.string.custom_action_setting) else stringResource(R.string.follow_global_action_setting),
            checked = enabled,
            onCheckedChange = onEnabledChange,
            shape = segmentedShape(0, 1),
        )
        if (enabled) {
            content()
            TextButton(onClick = { onEnabledChange(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.restore_follow_global))
            }
        }
    }
}
