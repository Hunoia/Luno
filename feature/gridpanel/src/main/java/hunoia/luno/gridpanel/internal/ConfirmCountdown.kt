package hunoia.luno.gridpanel.internal

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import hunoia.luno.ui.theme.MotionConfirmCountdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 系统动画时长缩放（0 = 关闭动画）；读取失败回退 1，ui-guidelines §5.2。 */
@Composable
internal fun rememberAnimationScale(): Float {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f).coerceAtLeast(0f)
    }
}

/**
 * 部件「双击确认」的进度环：key 非空期间跑一个固定 [MotionConfirmCountdown] 的确认窗口，
 * 窗口结束回调 [onTimeout]。进度环的动画时长按系统动画缩放，但**窗口时长不变**——
 * 系统关闭动画也要留出第二次点击的时间。习惯打卡与代办完成共用，避免两份裸 `tween(数字)`
 * 与两份倒计时常量（ui-guidelines §2.4 / §5.2）。
 *
 * @param key 待确认对象；从 null 变为非 null 时起窗口，变为 null（确认 / 取消）时立即收口。
 */
@Composable
internal fun rememberConfirmProgress(key: Any?, onTimeout: () -> Unit): Float {
    val progress = remember { Animatable(0f) }
    val scale = rememberAnimationScale()
    val timeout = rememberUpdatedState(onTimeout)
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        progress.snapTo(0f)
        if (scale > 0f) {
            launch {
                progress.animateTo(
                    1f,
                    tween((MotionConfirmCountdown * scale).toInt().coerceAtLeast(1)),
                )
            }
        } else {
            progress.snapTo(1f)
        }
        delay(MotionConfirmCountdown.toLong())
        timeout.value()
    }
    return progress.value
}
