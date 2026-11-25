package hunoia.luno.floatball.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.toMarkColor

/**
 * 胶囊外观的颜色行：球色与胶囊背景色，两颗胶囊共用（同一语义组件只留一份）。
 *
 * 行为对齐主球取色：色板行点击开取色板（sheet 由宿主挂在 `LunoPage` 的 sheets 槽，
 * 这里只回调 [onBallClick] / [onBackgroundClick]）。副标题实时显示来源；色板显示
 * **当前生效色**（未设置时即 [defaultColor]，渲染端回落主题主色，与悬浮球解耦）。
 */
@Composable
internal fun ColumnScope.CapsuleColorRows(
    ballSpec: ColorSpec,
    backgroundSpec: ColorSpec,
    defaultColor: androidx.compose.ui.graphics.Color,
    startIndex: Int,
    rowCount: Int,
    onBallClick: () -> Unit,
    onBackgroundClick: () -> Unit,
) {
    var row = startIndex
    LunoSettingsRow(
        title = stringResource(R.string.float_ball_capsule_ball_color),
        icon = Icons.Default.Palette,
        subtitle = stringResource(
            if (ballSpec.isUnset) {
                R.string.float_ball_capsule_color_default
            } else {
                R.string.float_ball_capsule_color_custom
            },
        ),
        shape = segmentedShape(row++, rowCount),
        leading = { ColorSwatch(color = ballSpec.effectiveColor(defaultColor)) },
        onClick = onBallClick,
    )
    LunoSettingsRow(
        title = stringResource(R.string.float_ball_capsule_background_color),
        icon = Icons.Default.Palette,
        subtitle = stringResource(
            if (backgroundSpec.isUnset) {
                R.string.float_ball_capsule_color_default
            } else {
                R.string.float_ball_capsule_color_custom
            },
        ),
        shape = segmentedShape(row++, rowCount),
        leading = { ColorSwatch(color = backgroundSpec.effectiveColor(defaultColor)) },
        onClick = onBackgroundClick,
    )
}

/** 颜色行数：球色 + 背景色两行。 */
internal fun capsuleColorRowCount(): Int = 2

/** 行尾色板：圆形色块，默认态显示 [defaultColor]（主题主色），自定义态显示所选色。 */
@Composable
private fun ColorSwatch(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(MarkColorSize)
            .background(color = color, shape = CircleShape),
    )
}

/** 当前生效色：未设置（默认主色）→ [defaultColor]，否则自定义 / 主题色。 */
@Composable
private fun ColorSpec.effectiveColor(defaultColor: androidx.compose.ui.graphics.Color) =
    if (isUnset) defaultColor else toMarkColor()
