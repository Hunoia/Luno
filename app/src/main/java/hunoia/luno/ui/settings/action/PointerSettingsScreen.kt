package hunoia.luno.ui.settings.action

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.GestureSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 指针设置页：起始位置、移动手感、光标外观。
 * 独立页面而非底部弹层：滑杆多，弹层会与下滑关闭抢手势、文案也挤。
 * 调整位置行按 [GestureSettings.Pointer.followTrigger] 打开「跟随偏移」或「初始位置」调整页。
 */
@Composable
fun PointerSettingsScreen(
    onBack: () -> Unit,
    onAdjustPosition: (follow: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val gesture by TuneConfig.gesture.collectAsStateWithLifecycle(initialValue = GestureSettings())
    val pointer = gesture.pointer
    var draft by remember(pointer) { mutableStateOf(pointer) }

    fun commit(next: GestureSettings.Pointer) {
        draft = next
        scope.launch { TuneConfig.updateGesture { it.copy(pointer = next) } }
    }

    LunoScreen(title = stringResource(R.string.pointer_settings_title), onBack = onBack) {
        LunoGroup(title = stringResource(R.string.pointer_group_start)) {
            LunoSwitchRow(
                title = stringResource(R.string.pointer_follow_trigger),
                subtitle = stringResource(R.string.pointer_follow_trigger_hint),
                checked = draft.followTrigger,
                onCheckedChange = { checked -> commit(draft.copy(followTrigger = checked)) },
                shape = segmentedShape(0, 2),
            )
            LunoSettingsRow(
                icon = Icons.Default.Adjust,
                title = stringResource(R.string.pointer_adjust_position),
                subtitle = if (draft.followTrigger) {
                    stringResource(
                        R.string.pointer_follow_offset_summary,
                        (draft.followOffsetXRatio * 100).roundToInt(),
                        (draft.followOffsetYRatio * 100).roundToInt(),
                    )
                } else {
                    stringResource(
                        R.string.pointer_initial_summary,
                        (draft.initialXRatio * 100).roundToInt(),
                        (draft.initialYRatio * 100).roundToInt(),
                    )
                },
                shape = segmentedShape(1, 2),
                onClick = { onAdjustPosition(draft.followTrigger) },
            )
        }
        LunoGroup(title = stringResource(R.string.pointer_group_motion)) {
            LunoTextSlider(
                value = draft.sensitivityX,
                onValueChange = { draft = draft.copy(sensitivityX = it) },
                onValueChangeFinished = { commit(draft) },
                text = stringResource(id = R.string.pointer_sensitivity_x_plain),
                valueDisplay = { stringResource(id = R.string.pointer_sensitivity_x, it) },
                valueRange = 0.5f..4f,
                shape = segmentedShape(0, 3),
            )
            LunoTextSlider(
                value = draft.sensitivityY,
                onValueChange = { draft = draft.copy(sensitivityY = it) },
                onValueChangeFinished = { commit(draft) },
                text = stringResource(id = R.string.pointer_sensitivity_y_plain),
                valueDisplay = { stringResource(id = R.string.pointer_sensitivity_y, it) },
                valueRange = 0.5f..4f,
                shape = segmentedShape(1, 3),
            )
            LunoTextSlider(
                value = draft.acceleration,
                onValueChange = { draft = draft.copy(acceleration = it) },
                onValueChangeFinished = { commit(draft) },
                text = stringResource(id = R.string.pointer_acceleration_plain),
                valueDisplay = { stringResource(id = R.string.pointer_acceleration, it) },
                valueRange = 0f..2f,
                shape = segmentedShape(2, 3),
            )
        }
        LunoGroup(title = stringResource(R.string.pointer_group_cursor)) {
            // 光标大小是 Int：本地浮点过渡，松手取整提交。
            var localCursorSize by remember(draft.cursorSizeDp) {
                mutableStateOf(draft.cursorSizeDp.toFloat())
            }
            LunoTextSlider(
                value = localCursorSize,
                onValueChange = { localCursorSize = it },
                onValueChangeFinished = {
                    commit(draft.copy(cursorSizeDp = localCursorSize.roundToInt()))
                },
                text = stringResource(id = R.string.pointer_cursor_size_plain),
                valueDisplay = { stringResource(id = R.string.pointer_cursor_size, it.roundToInt()) },
                valueRange = 12f..64f,
                shape = segmentedShape(0, 2),
            )
            LunoTextSlider(
                value = draft.cursorAlpha,
                onValueChange = { draft = draft.copy(cursorAlpha = it) },
                onValueChangeFinished = { commit(draft) },
                text = stringResource(id = R.string.pointer_cursor_alpha_plain),
                valueDisplay = { stringResource(id = R.string.pointer_cursor_alpha, (it * 100).roundToInt()) },
                valueRange = 0.2f..1f,
                shape = segmentedShape(1, 2),
            )
        }
    }
}
