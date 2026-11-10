package hunoia.luno.gesture.settings.style

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.gesture.R
import hunoia.luno.config.defaults.GestureSettingsDefaults
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SurfacePlacementSliders
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlin.math.roundToInt

/** 样式项数量：6 个滑杆 + 3 个边距滑杆 + 1 个振动开关。 */
private const val STYLE_ITEM_COUNT = 10

/**
 * 动作面板样式与手感：图标大小 / 图标间距 / 弧线铺开程度 / 最大角度 / 长按判定时间 / 屏幕边距。
 * 全局一份，在动作设置里改。内容可滚动（边距项加进来后已超过一屏高度）。
 */
@Composable
fun ActionPanelStyleConfigContent(
    currentStyle: ArcStyle,
    actionPanelVibrate: Boolean,
    actionPanelLongPressMs: Long,
    onStyleChanged: (ArcStyle) -> Unit,
    onActionPanelVibrateChanged: (Boolean) -> Unit,
    onActionPanelLongPressChanged: (Long) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    val style = currentStyle
    val density = LocalDensity.current
    var itemSizeDp by remember(style.itemSize) { mutableFloatStateOf(with(density) { style.itemSize.toDp().value }) }
    var appIconScale by remember(style.appIconScale) { mutableFloatStateOf(style.appIconScale) }
    var itemGapDp by remember(style.itemGapPx) { mutableFloatStateOf(with(density) { style.itemGapPx.toDp().value }) }
    var spreadSpacing by remember(style.spreadSpacing) { mutableFloatStateOf(style.spreadSpacing) }
    var maxAngleDegrees by remember(style.maxAngleDegrees) { mutableFloatStateOf(style.maxAngleDegrees) }
    var longPressMs by remember(actionPanelLongPressMs) {
        mutableFloatStateOf(actionPanelLongPressMs.toFloat())
    }

    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(scrollState)
            .padding(horizontal = PageGutter)
            .padding(top = PageGutter, bottom = ContentBottom),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        LunoGroup {
            LunoTextSlider(
                value = itemSizeDp,
                onValueChange = { itemSizeDp = it },
                onValueChangeFinished = {
                    onStyleChanged(style.copy(itemSize = with(density) { itemSizeDp.dp.roundToPx() }))
                },
                text = stringResource(R.string.action_panel_item_size),
                valueDisplay = { "${it.roundToInt()}dp" },
                valueRange = 40f..64f,
                shape = segmentedShape(0, STYLE_ITEM_COUNT),
            )
            LunoTextSlider(
                value = appIconScale,
                onValueChange = { appIconScale = it },
                onValueChangeFinished = {
                    onStyleChanged(style.copy(appIconScale = appIconScale))
                },
                text = stringResource(R.string.action_panel_app_icon_size),
                valueDisplay = { "${(it * 100).roundToInt()}%" },
                valueRange = 0.6f..1.2f,
                shape = segmentedShape(1, STYLE_ITEM_COUNT),
            )
            LunoTextSlider(
                value = itemGapDp,
                onValueChange = { itemGapDp = it },
                onValueChangeFinished = {
                    onStyleChanged(style.copy(itemGapPx = with(density) { itemGapDp.dp.roundToPx() }))
                },
                text = stringResource(R.string.action_panel_item_gap),
                valueDisplay = { "${it.roundToInt()}dp" },
                valueRange = 0f..24f,
                shape = segmentedShape(2, STYLE_ITEM_COUNT),
            )
            LunoTextSlider(
                value = spreadSpacing,
                onValueChange = { spreadSpacing = it },
                onValueChangeFinished = {
                    onStyleChanged(style.copy(spreadSpacing = spreadSpacing))
                },
                text = stringResource(R.string.action_panel_spread_spacing),
                valueDisplay = { "${(it * 100).roundToInt()}%" },
                valueRange = 0.85f..1.35f,
                shape = segmentedShape(3, STYLE_ITEM_COUNT),
            )
            LunoTextSlider(
                value = maxAngleDegrees,
                onValueChange = { maxAngleDegrees = it },
                onValueChangeFinished = {
                    onStyleChanged(style.copy(maxAngleDegrees = maxAngleDegrees))
                },
                text = stringResource(R.string.action_panel_max_angle),
                valueDisplay = { "${it.roundToInt()}°" },
                valueRange = 90f..360f,
                shape = segmentedShape(4, STYLE_ITEM_COUNT),
            )
            // 扇菜单离屏幕左右 / 上下的最小边距，与网格面板、快捷启动同一套滑杆。
            SurfacePlacementSliders(
                placement = style.placement,
                shapeAt = { index -> segmentedShape(5 + index, STYLE_ITEM_COUNT) },
                onCommit = { next -> onStyleChanged(style.copy(placement = next)) },
            )
            // 长按判定：按住多久算长按（也是「长按变体是面板时无需抬手」的点火时间）。
            LunoTextSlider(
                value = longPressMs,
                onValueChange = { longPressMs = it },
                onValueChangeFinished = {
                    onActionPanelLongPressChanged(
                        longPressMs.roundToInt().toLong().coerceIn(
                            GestureSettingsDefaults.MinActionPanelLongPressMs,
                            GestureSettingsDefaults.MaxActionPanelLongPressMs,
                        ),
                    )
                },
                text = stringResource(R.string.action_panel_long_press),
                valueDisplay = { "${it.roundToInt()}ms" },
                valueRange = GestureSettingsDefaults.MinActionPanelLongPressMs.toFloat()..
                    GestureSettingsDefaults.MaxActionPanelLongPressMs.toFloat(),
                shape = segmentedShape(8, STYLE_ITEM_COUNT),
            )
            LunoSwitchRow(
                icon = Icons.Default.Vibration,
                title = stringResource(R.string.action_panel_vibration),
                checked = actionPanelVibrate,
                onCheckedChange = onActionPanelVibrateChanged,
                shape = segmentedShape(9, STYLE_ITEM_COUNT),
            )
        }
    }
}
