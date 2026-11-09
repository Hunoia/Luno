package hunoia.luno.ui.component.row

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.SurfacePlacement
import hunoia.luno.config.model.SurfacePlacementDefaults
import hunoia.luno.config.model.placementStep
import hunoia.luno.design.R
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.theme.LargeShape
import kotlin.math.roundToInt

/**
 * 浮层跟随触发点的三个边距滑杆（左右 / 上 / 下）。
 *
 * 网格面板、快捷启动、小窗三个设置页共用这一组控件与同一份口径（[SurfacePlacement]），
 * 避免三处各写一套滑杆与文案。调用方决定落盘到哪个朝向的那份配置。
 *
 * - [onPreview]：拖动中实时回调（面板正开着的页面可拿它做实时预览）。
 * - [onCommit]：松手时回调，由调用方落盘。
 */
@Composable
fun SurfacePlacementSliders(
    placement: SurfacePlacement,
    onCommit: (SurfacePlacement) -> Unit,
    enabled: Boolean = true,
    shapeAt: (Int) -> Shape = { LargeShape },
    onPreview: (SurfacePlacement) -> Unit = {},
) {
    var draft by remember(placement) { mutableStateOf(placement) }
    val range = SurfacePlacementDefaults.MinMarginDp..SurfacePlacementDefaults.MaxMarginDp

    LunoTextSlider(
        value = draft.sideMarginDp,
        onValueChange = { value ->
            draft = draft.copy(sideMarginDp = value.placementStep())
            onPreview(draft)
        },
        onValueChangeFinished = { onCommit(draft) },
        text = stringResource(R.string.surface_side_margin),
        valueDisplay = { "${it.roundToInt()}dp" },
        valueRange = range,
        steps = 0,
        shape = shapeAt(0),
        enabled = enabled,
    )
    LunoTextSlider(
        value = draft.topGapDp,
        onValueChange = { value ->
            draft = draft.copy(topGapDp = value.placementStep())
            onPreview(draft)
        },
        onValueChangeFinished = { onCommit(draft) },
        text = stringResource(R.string.surface_top_gap),
        valueDisplay = { "${it.roundToInt()}dp" },
        valueRange = range,
        steps = 0,
        shape = shapeAt(1),
        enabled = enabled,
    )
    LunoTextSlider(
        value = draft.bottomGapDp,
        onValueChange = { value ->
            draft = draft.copy(bottomGapDp = value.placementStep())
            onPreview(draft)
        },
        onValueChangeFinished = { onCommit(draft) },
        text = stringResource(R.string.surface_bottom_gap),
        valueDisplay = { "${it.roundToInt()}dp" },
        valueRange = range,
        steps = 0,
        shape = shapeAt(2),
        enabled = enabled,
    )
}
