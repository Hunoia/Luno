package hunoia.luno.gesture.settings.style

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.gesture.R
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlin.math.roundToInt

/** 动作面板样式参数：图标大小 / 图标间距 / 弧线铺开程度。全局一份，在动作设置里改。 */
@Composable
fun ActionPanelStyleConfigContent(
    currentStyle: ArcStyle,
    actionPanelVibrate: Boolean,
    onStyleChanged: (ArcStyle) -> Unit,
    onActionPanelVibrateChanged: (Boolean) -> Unit,
) {
    val style = currentStyle
    val density = LocalDensity.current
    var itemSizeDp by remember(style.itemSize) { mutableFloatStateOf(with(density) { style.itemSize.toDp().value }) }
    var appIconScale by remember(style.appIconScale) { mutableFloatStateOf(style.appIconScale) }
    var itemGapDp by remember(style.itemGapPx) { mutableFloatStateOf(with(density) { style.itemGapPx.toDp().value }) }
    var spreadSpacing by remember(style.spreadSpacing) { mutableFloatStateOf(style.spreadSpacing) }
    var maxAngleDegrees by remember(style.maxAngleDegrees) { mutableFloatStateOf(style.maxAngleDegrees) }

    // 普通 Column：LunoColumn 的 verticalScroll 会抢 ModalBottomSheet 的下滑关闭手势。
    Column(
        modifier = Modifier
            .padding(PageGutter)
            .padding(bottom = ContentBottom),
        verticalArrangement = Arrangement.spacedBy(ListSpacing),
    ) {
        LunoSegmentedGroup {
            item { shape -> LunoTextSlider(
                value = itemSizeDp,
                onValueChange = { itemSizeDp = it },
                onValueChangeFinished = {
                    onStyleChanged(style.copy(itemSize = with(density) { itemSizeDp.dp.roundToPx() }))
                },
                text = stringResource(R.string.action_panel_item_size),
                valueDisplay = { "${it.roundToInt()}dp" },
                valueRange = 40f..64f,
                shape = shape,
            ) }
            item { shape -> LunoTextSlider(
                value = appIconScale,
                onValueChange = { appIconScale = it },
                onValueChangeFinished = {
                    onStyleChanged(style.copy(appIconScale = appIconScale))
                },
                text = stringResource(R.string.action_panel_app_icon_size),
                valueDisplay = { "${(it * 100).roundToInt()}%" },
                valueRange = 0.6f..1.2f,
                shape = shape,
            ) }
            item { shape -> LunoTextSlider(
                value = itemGapDp,
                onValueChange = { itemGapDp = it },
                onValueChangeFinished = {
                    onStyleChanged(style.copy(itemGapPx = with(density) { itemGapDp.dp.roundToPx() }))
                },
                text = stringResource(R.string.action_panel_item_gap),
                valueDisplay = { "${it.roundToInt()}dp" },
                valueRange = 0f..24f,
                shape = shape,
            ) }
            item { shape -> LunoTextSlider(
                value = spreadSpacing,
                onValueChange = { spreadSpacing = it },
                onValueChangeFinished = {
                    onStyleChanged(style.copy(spreadSpacing = spreadSpacing))
                },
                text = stringResource(R.string.action_panel_spread_spacing),
                valueDisplay = { "${(it * 100).roundToInt()}%" },
                valueRange = 0.85f..1.35f,
                shape = shape,
            ) }
            item { shape -> LunoTextSlider(
                value = maxAngleDegrees,
                onValueChange = { maxAngleDegrees = it },
                onValueChangeFinished = {
                    onStyleChanged(style.copy(maxAngleDegrees = maxAngleDegrees))
                },
                text = stringResource(R.string.action_panel_max_angle),
                valueDisplay = { "${it.roundToInt()}°" },
                valueRange = 90f..360f,
                shape = shape,
            ) }
            item { shape -> LunoSwitchRow(
                icon = Icons.Default.Vibration,
                title = stringResource(R.string.action_panel_vibration),
                checked = actionPanelVibrate,
                onCheckedChange = onActionPanelVibrateChanged,
                shape = shape,
            ) }
        }
    }
}
