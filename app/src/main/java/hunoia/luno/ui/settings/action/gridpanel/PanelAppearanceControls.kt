package hunoia.luno.ui.settings.action.gridpanel

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.GridPanelDefaults
import hunoia.luno.config.model.PanelAppearance
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SurfacePlacementSliders
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlin.math.roundToInt

/** 面板外观滑杆：卡透明度、背景模糊、跟随触发点的三个边距。全局与单面板编辑共用。 */
@Composable
internal fun PanelAppearanceSliders(
    appearance: PanelAppearance,
    landscape: Boolean,
    shapeAt: (Int) -> Shape,
    onCommit: (PanelAppearance) -> Unit,
) {
    var draft by remember(appearance) { mutableStateOf(appearance) }
    val blurOffLabel = stringResource(R.string.grid_panel_blur_off)

    LunoTextSlider(
        value = draft.cardAlpha,
        onValueChange = { value -> draft = draft.copy(cardAlpha = value.percentStep()) },
        onValueChangeFinished = { onCommit(draft) },
        text = stringResource(R.string.grid_panel_card_alpha),
        valueDisplay = { "${(it * 100).roundToInt()}%" },
        valueRange = GridPanelDefaults.MinCardAlpha..GridPanelDefaults.MaxCardAlpha,
        steps = 0,
        shape = shapeAt(0),
    )
    LunoTextSlider(
        value = draft.blurRadiusDp,
        onValueChange = { value -> draft = draft.copy(blurRadiusDp = value.roundToInt().toFloat()) },
        onValueChangeFinished = { onCommit(draft) },
        text = stringResource(R.string.grid_panel_blur_radius),
        valueDisplay = { v -> if (v <= 0f) blurOffLabel else "${v.roundToInt()}dp" },
        valueRange = GridPanelDefaults.MinBlurRadiusDp..GridPanelDefaults.MaxBlurRadiusDp,
        steps = 0,
        shape = shapeAt(1),
    )
    SurfacePlacementSliders(
        placement = draft.placement(landscape),
        shapeAt = { index -> shapeAt(index + 2) },
        onCommit = { next ->
            val updated = draft.withPlacement(landscape, next)
            draft = updated
            onCommit(updated)
        },
    )
}

/**
 * 单面板外观：顶部「跟随全局」开关 + 自定义时的滑杆。
 * [appearance] 为 `null` = 跟随全局；关闭开关时调用方把当前全局值写成本面板覆盖。
 */
@Composable
internal fun PanelAppearanceControls(
    appearance: PanelAppearance?,
    global: PanelAppearance,
    landscape: Boolean,
    shapeAt: (Int) -> Shape,
    onFollowGlobalChange: (Boolean) -> Unit,
    onCommit: (PanelAppearance) -> Unit,
) {
    val following = appearance == null
    LunoSwitchRow(
        title = stringResource(R.string.panel_appearance_follow_global),
        subtitle = stringResource(
            if (following) {
                R.string.panel_appearance_follow_global_hint
            } else {
                R.string.panel_appearance_custom_hint
            },
        ),
        checked = following,
        onCheckedChange = onFollowGlobalChange,
        shape = shapeAt(0),
    )
    if (appearance != null) {
        PanelAppearanceSliders(
            appearance = appearance,
            landscape = landscape,
            shapeAt = { shapeAt(it + 1) },
            onCommit = onCommit,
        )
    }
}

/** 全局面板外观设置内容：可滚动，供面板列表页顶部的 sheet 使用。 */
@Composable
internal fun PanelAppearanceConfigContent(
    appearance: PanelAppearance,
    onCommit: (PanelAppearance) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
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
            PanelAppearanceSliders(
                appearance = appearance,
                landscape = landscape,
                shapeAt = { index -> segmentedShape(index, 5) },
                onCommit = onCommit,
            )
        }
    }
}

/** 百分比滑杆：连续拖动、按 1% 量化后落盘。 */
internal fun Float.percentStep(): Float = (this * 100f).roundToInt() / 100f
