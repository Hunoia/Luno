package hunoia.luno.ui.settings.gesture.style

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.ArcStyle
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlin.math.roundToInt

/** 动作面板样式参数：图标大小 / 图标间距 / 弧线铺开程度。全局一份，在动作设置里改。 */
@Composable
fun ActionPanelStyleConfigContent(
    currentStyle: ArcStyle,
    onStyleChanged: (ArcStyle) -> Unit,
) {
    val style = currentStyle
    val density = LocalDensity.current
    var itemSizeDp by remember(style.itemSize) { mutableFloatStateOf(with(density) { style.itemSize.toDp().value }) }
    var itemGapDp by remember(style.itemGapPx) { mutableFloatStateOf(with(density) { style.itemGapPx.toDp().value }) }
    var spreadSpacing by remember(style.spreadSpacing) { mutableFloatStateOf(style.spreadSpacing) }

    // 普通 Column：LunoColumn 的 verticalScroll 会抢 ModalBottomSheet 的下滑关闭手势。
    Column(
        modifier = Modifier
            .padding(PageGutter)
            .padding(bottom = ContentBottom),
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
                shape = segmentedShape(0, 3),
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
                shape = segmentedShape(1, 3),
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
                shape = segmentedShape(2, 3),
            )
        }
    }
}