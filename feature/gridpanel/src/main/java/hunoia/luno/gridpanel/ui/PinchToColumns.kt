package hunoia.luno.gridpanel.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import hunoia.luno.config.model.GridPanelDefaults

/** 两指间距相对上次落档变化多少才走一档（1.25 = 25%）。 */
private const val ColumnStepRatio = 1.25f

/**
 * **反向**退档需要的间距变化比（1.25²）。同方向走档只要 25%，往回转要 56%：
 * 捏合时手腕扭转会让两指直线距离小幅回缩，若正反向用同一阈值，同方向连捏会突然
 * 回跳一档。反向门槛放宽后，扭动落在死区里不动档，真要回退得明显回收手指。
 */
private const val ColumnReverseRatio = ColumnStepRatio * ColumnStepRatio

/**
 * 两档之间至少间隔多久（毫秒）。**输入事件可能比屏幕帧更密**：一帧之内连走两档时，
 * 中间那档根本没被画出来，真机表现就是「5 被跳过」。按时间派档保证每档至少落一帧。
 */
internal const val ColumnStepMinIntervalMillis = 90L

/**
 * 间距比值 → 列数档位（-1 / 0 / +1）。[lastDirection] 是上一档的方向（0 = 还没走过档）：
 * 反向要跨 [ColumnReverseRatio]，同向只要 [ColumnStepRatio]。抽成纯函数，单测直接打这张表。
 */
internal fun columnStepFor(ratio: Float, lastDirection: Int = 0): Int = when {
    ratio >= (if (lastDirection > 0) ColumnReverseRatio else ColumnStepRatio) -> -1
    ratio <= 1f / (if (lastDirection < 0) ColumnReverseRatio else ColumnStepRatio) -> 1
    else -> 0
}

/**
 * 捏合调列数的状态机：两指间距 → 列数，一次一档，且两档之间至少 [minIntervalMillis]。
 *
 * **定档用阈值位，不用当前间距**（滞回）：走一档后基准只前进一个阈值（`× 1.25`），
 * 不跳到当前间距。否则快速捏合时基准被推到超调位，手指稍微回缩 / 扭动就会立刻反向
 * 再走一档——真机表现就是"捏到 5 继续扭又跳回 6"。用阈值位后，要退档必须回落到
 * 上一个阈值，扭动造成的小幅距离抖动落在滞回带里，不动档位。
 *
 * 快到一帧要跨两档时，第二档攒到下一个间隔再走（而不是被吞掉）。
 * 夹在 `GridPanelDefaults` 的上下限内，到顶 / 到底后不再发。
 */
internal class ColumnStepper(
    private val minColumns: Int = GridPanelDefaults.MinColumnCount,
    private val maxColumns: Int = GridPanelDefaults.MaxColumnCount,
    private val minIntervalMillis: Long = ColumnStepMinIntervalMillis,
) {
    var columns: Int = minColumns
        private set

    private var anchorSpan = 0f
    private var lastStepAtMillis = 0L
    /** 上一档的方向（-1 = 列数变少，+1 = 列数变多，0 = 还没走过）。 */
    private var lastDirection = 0

    /** 手势起手：记住当前列数与基准间距。首档不设等待，起手就能响应。 */
    fun start(columns: Int, span: Float, atMillis: Long) {
        this.columns = columns.coerceIn(minColumns, maxColumns)
        anchorSpan = span
        lastStepAtMillis = atMillis - minIntervalMillis
        lastDirection = 0
    }

    /** 喂入当前间距，返回新列数；没到档 / 还在间隔内返回 null。 */
    fun update(span: Float, atMillis: Long): Int? {
        if (anchorSpan <= 0f) return null
        // 放大手势 = 格子变大 = 列数变少；缩小手势 = 列数变多。
        val ratio = span / anchorSpan
        val step = columnStepFor(ratio, lastDirection)
        if (step == 0) return null
        // 间隔没到就原样保留（基准不动），下个事件继续判定——不丢档，只是晚一帧。
        if (atMillis - lastStepAtMillis < minIntervalMillis) return null
        val target = (columns + step).coerceIn(minColumns, maxColumns)
        // 到顶 / 到底：基准也不前进，反向时才不会被"攒下的行程"拖住。
        if (target == columns) return null
        // 只前进到阈值位，不跟到当前间距（见类 KDoc 的滞回说明）。
        val crossing = if (lastDirection != 0 && step != lastDirection) ColumnReverseRatio else ColumnStepRatio
        anchorSpan = if (step < 0) anchorSpan * crossing else anchorSpan / crossing
        lastStepAtMillis = atMillis
        lastDirection = step
        columns = target
        return target
    }
}

/**
 * 编辑态双指调列数：**只在出现第二根手指后接管**，单指事件完全不消费，所以不会抢滚动 / 点击。
 *
 * 两个使用方（面板卡片调自身列数、单功能应用抽屉调图标列数）共用这一份实现；
 * 档位推进全在 [ColumnStepper] 里，手势这里只做取点与节流。
 *
 * 手势期间只回调 `final = false`（宿主改本地预览），松手才 `final = true`（宿主落盘）。
 *
 * **回调与列数读取一律走 `rememberUpdatedState`**：`pointerInput(Unit)` 的协程不会随重组重启，
 * 直接捕获 lambda 会把宿主第一次组合里的 `baseColumns` / `params` 冻住——拿旧基准判断预览
 * （`target != base`）会在撞上旧值时把预览清成 null，列数瞬掉回持久化值再跳回来，
 * 真机表现就是「捏合过程中突然回跳一档」。
 */
@Composable
internal fun Modifier.pinchToColumns(
    enabled: Boolean,
    currentColumns: () -> Int,
    onColumns: (target: Int, final: Boolean) -> Unit,
): Modifier {
    if (!enabled) return this
    val columnsState = rememberUpdatedState(currentColumns)
    val onColumnsState = rememberUpdatedState(onColumns)
    return pointerInput(Unit) {
        val stepper = ColumnStepper()
        awaitEachGesture {
            val first = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var firstId: PointerId = first.id
            var secondId: PointerId? = null
            var pinching = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (!pinching) {
                    if (pressed.size >= 2) {
                        val a = pressed[0]
                        val b = pressed[1]
                        firstId = a.id
                        secondId = b.id
                        val span = (a.position - b.position).getDistance()
                        stepper.start(columnsState.value(), span, a.uptimeMillis)
                        pinching = span > 0f
                    } else if (pressed.isEmpty()) {
                        break
                    }
                }
                if (pinching) {
                    val a = event.changes.firstOrNull { it.id == firstId && it.pressed }
                    val b = event.changes.firstOrNull { it.id == secondId && it.pressed }
                    if (a == null || b == null) {
                        event.changes.forEach { it.consume() }
                        break
                    }
                    val span = (a.position - b.position).getDistance()
                    stepper.update(span, a.uptimeMillis)?.let { onColumnsState.value(it, false) }
                    event.changes.forEach { it.consume() }
                }
            }
            if (pinching) onColumnsState.value(stepper.columns, true)
        }
    }
}
