package hunoia.luno.gridpanel.habit

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.config.model.HabitDayState
import hunoia.luno.gridpanel.R

/** 点阵配色角色：实际色由部件 / 页面提供，视觉语义只在这里定义。 */
enum class HabitDayRole { PRIMARY, NEUTRAL, ERROR }

/** 点内标记：已打 = 勾、跳过 = 横杠；部件打卡圆、历史点阵与编辑页日历共用。 */
enum class HabitDayMark { NONE, CHECK, MINUS }

/** 标记颜色角色：勾固定白色，横杠跟随主色，无标记透明。 */
enum class HabitDayMarkTint { WHITE, PRIMARY, NONE }

/**
 * 一天的状态 → 视觉语义的**唯一**映射。部件打卡圆、历史点阵与编辑页近 30 天日历共用，
 * 保证「已打 / 待打 / 跳过 / 断签」四处口径一致（ui-guidelines §1.1）。
 *
 * - 已打 = 实心主色 + 勾；
 * - 待打 = 空心主色描边；
 * - 跳过 = 主色半透明 + 横杠（与部件一致，编辑页也用）；
 * - 断签 = 弱灰 + 错误色描边（仍可补打，但 streak 已断）；
 * - 未开始 = 弱灰。
 */
data class HabitDayVisual(
    val fillRole: HabitDayRole?,
    val fillAlpha: Float,
    val borderRole: HabitDayRole?,
    val borderAlpha: Float,
    val mark: HabitDayMark,
    val markTint: HabitDayMarkTint,
)

fun HabitDayState.visual(): HabitDayVisual = when (this) {
    HabitDayState.CHECKED -> HabitDayVisual(
        fillRole = HabitDayRole.PRIMARY,
        fillAlpha = 1f,
        borderRole = HabitDayRole.PRIMARY,
        borderAlpha = 1f,
        mark = HabitDayMark.CHECK,
        markTint = HabitDayMarkTint.WHITE,
    )
    HabitDayState.DUE -> HabitDayVisual(
        fillRole = null,
        fillAlpha = 0f,
        borderRole = HabitDayRole.PRIMARY,
        borderAlpha = 1f,
        mark = HabitDayMark.NONE,
        markTint = HabitDayMarkTint.NONE,
    )
    HabitDayState.SKIPPED -> HabitDayVisual(
        fillRole = HabitDayRole.PRIMARY,
        fillAlpha = 0.3f,
        borderRole = HabitDayRole.PRIMARY,
        borderAlpha = 0.15f,
        mark = HabitDayMark.MINUS,
        markTint = HabitDayMarkTint.PRIMARY,
    )
    HabitDayState.OVERDUE -> HabitDayVisual(
        fillRole = HabitDayRole.NEUTRAL,
        fillAlpha = 0.12f,
        borderRole = HabitDayRole.ERROR,
        borderAlpha = 0.7f,
        mark = HabitDayMark.NONE,
        markTint = HabitDayMarkTint.NONE,
    )
    HabitDayState.INACTIVE -> HabitDayVisual(
        fillRole = HabitDayRole.NEUTRAL,
        fillAlpha = 0.12f,
        borderRole = HabitDayRole.NEUTRAL,
        borderAlpha = 0.15f,
        mark = HabitDayMark.NONE,
        markTint = HabitDayMarkTint.NONE,
    )
}

/** 每个 Surface 提供自己的实际色：主色 = 习惯色，中性 = 卡面 / 描边色，错误 = 断签。 */
internal data class HabitDayPalette(val primary: Color, val neutral: Color, val error: Color)

@Composable
internal fun habitDayPalette(primary: Color, neutral: Color): HabitDayPalette =
    HabitDayPalette(primary, neutral, MaterialTheme.colorScheme.error)

internal fun HabitDayVisual.fillColor(palette: HabitDayPalette): Color =
    fillRole?.color(palette)?.copy(alpha = fillAlpha) ?: Color.Transparent

internal fun HabitDayVisual.borderColor(palette: HabitDayPalette): Color =
    borderRole?.color(palette)?.copy(alpha = borderAlpha) ?: Color.Transparent

/** 标记色：勾压在实心主色上用白色；横杠压在半透明主色上用不透明主色，保证对比度（§6）。 */
internal fun HabitDayVisual.markTint(palette: HabitDayPalette): Color = when (markTint) {
    HabitDayMarkTint.WHITE -> Color.White
    HabitDayMarkTint.PRIMARY -> palette.primary
    HabitDayMarkTint.NONE -> Color.Transparent
}

/**
 * 点内标记的自绘实现：打卡圆、历史点阵与编辑页日历共用一份（ui-guidelines §1.1）。
 * 用 Canvas 而非矢量图标：历史点阵的点径会缩到几 dp，图标无法跟着等比缩放。
 */
@Composable
internal fun HabitDayMarkIcon(
    mark: HabitDayMark,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (mark == HabitDayMark.NONE) return
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (minOf(w, h) * 0.16f).coerceAtLeast(1.dp.toPx())
        val path = Path().apply {
            when (mark) {
                HabitDayMark.CHECK -> {
                    moveTo(w * 0.20f, h * 0.52f)
                    lineTo(w * 0.43f, h * 0.74f)
                    lineTo(w * 0.80f, h * 0.27f)
                }
                HabitDayMark.MINUS -> {
                    moveTo(w * 0.22f, h * 0.50f)
                    lineTo(w * 0.78f, h * 0.50f)
                }
                HabitDayMark.NONE -> Unit
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private fun HabitDayRole.color(palette: HabitDayPalette): Color = when (this) {
    HabitDayRole.PRIMARY -> palette.primary
    HabitDayRole.NEUTRAL -> palette.neutral
    HabitDayRole.ERROR -> palette.error
}

/** 状态文案：部件与编辑页日历的无障碍描述共用同一套说法。 */
@Composable
internal fun HabitDayState.label(): String = stringResource(
    when (this) {
        HabitDayState.CHECKED -> R.string.habit_day_checked
        HabitDayState.DUE -> R.string.habit_day_due
        HabitDayState.SKIPPED -> R.string.habit_day_skipped
        HabitDayState.OVERDUE -> R.string.habit_day_overdue
        HabitDayState.INACTIVE -> R.string.habit_day_inactive
    },
)
