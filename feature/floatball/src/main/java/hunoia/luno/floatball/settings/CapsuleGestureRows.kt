package hunoia.luno.floatball.settings

import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.CapsuleGestureBehavior
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoDropdownRow

/**
 * 胶囊手势行为单选下拉（两颗胶囊的手势组共用）：
 * 选项集按槽位给（球长按 / 行长按 / 两向滑动各自的合法集合不同），
 * 当前值读设置层的归一化属性——非法组合显示为出厂默认而不是原值。
 */
@Composable
internal fun GestureBehaviorRow(
    title: String,
    current: CapsuleGestureBehavior,
    options: List<CapsuleGestureBehavior>,
    onSelect: (CapsuleGestureBehavior) -> Unit,
    shape: Shape,
) {
    LunoDropdownRow(
        title = title,
        options = options.map { stringResource(it.labelRes()) },
        selectedIndex = options.indexOf(current).coerceAtLeast(0),
        onSelect = { index -> onSelect(options[index]) },
        shape = shape,
    )
}

/** 行为展示名（选项顺序由调用方决定，这里只管文案）。 */
internal fun CapsuleGestureBehavior.labelRes(): Int = when (this) {
    CapsuleGestureBehavior.NONE -> R.string.float_ball_gesture_none
    CapsuleGestureBehavior.IGNORE_ROW -> R.string.float_ball_gesture_ignore_row
    CapsuleGestureBehavior.COLLAPSE -> R.string.float_ball_gesture_collapse
    CapsuleGestureBehavior.MENU -> R.string.float_ball_gesture_menu
    CapsuleGestureBehavior.CLEAR_QUEUE -> R.string.float_ball_gesture_clear_queue
}
