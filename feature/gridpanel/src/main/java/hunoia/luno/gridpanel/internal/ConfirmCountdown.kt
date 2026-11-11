package hunoia.luno.gridpanel.internal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import hunoia.luno.ui.theme.MotionConfirmCountdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 部件「双击确认」的进度环：key 非空期间跑一个固定 [MotionConfirmCountdown] 的确认窗口，
 * 窗口结束回调 [onTimeout]。进度环动画直接走 `tween(MotionConfirmCountdown)`：Compose
 * 已通过 MotionDurationScale 自动跟随系统动画时长缩放，不得手算后双乘；**窗口时长不变**——
 * 系统关闭动画也要留出第二次点击的时间。习惯打卡与代办完成共用，避免两份裸 `tween(数字)`
 * 与两份倒计时常量（ui-guidelines §2.4 / §5.2）。
 *
 * @param key 待确认对象；从 null 变为非 null 时起窗口，变为 null（确认 / 取消）时立即收口。
 */
@Composable
internal fun rememberConfirmProgress(key: Any?, onTimeout: () -> Unit): Float {
    val progress = remember { Animatable(0f) }
    val timeout = rememberUpdatedState(onTimeout)
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        progress.snapTo(0f)
        launch {
            progress.animateTo(1f, tween(MotionConfirmCountdown))
        }
        delay(MotionConfirmCountdown.toLong())
        timeout.value()
    }
    return progress.value
}

/** 确认环：弱色轨道 + 从 -90° 起的进度弧；习惯打卡与待办完成共用。 */
internal fun DrawScope.drawConfirmRing(
    progress: Float,
    accent: Color,
    track: Color,
    strokeWidth: Float,
) {
    val inset = strokeWidth / 2f
    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    drawArc(
        color = track,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
    drawArc(
        color = accent,
        startAngle = -90f,
        sweepAngle = 360f * progress,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
}
