package hunoia.luno.floatball.settings.alert

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.AlertSlot
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreenLazy
import kotlinx.coroutines.launch

/**
 * 到达提示·预设选择页：指定槽位可选的全部预设（内置 + 我的）。
 *
 * 只列**提供该槽可用宿主变体**的预设；选完即落盘并返回。预设本体编辑 / 新建在预设库
 * （Step 3），本页只做单选。
 */
@Composable
fun AlertPresetPickerScreen(
    slot: AlertSlot,
    onBack: () -> Unit,
) {
    val editor = rememberAlertEditor()
    val settings = editor.alert.slotSettings(slot)
    val presets = presetsForSlot(slot, editor.library)
    val currentId = settings.presetId
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }

    fun selectAndBack(presetId: String?) {
        if (leaving) return
        leaving = true
        scope.launch {
            persistAlertNow { it.withSlot(slot) { s -> s.copy(presetId = presetId) } }
            TuneConfig.syncActionSnapshot()
            onBack()
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_alert_preset_picker),
        onBack = onBack,
    ) {
        item(key = "presets") {
            LunoGroup(
                title = stringResource(slot.titleRes()),
                subtitle = stringResource(R.string.float_ball_alert_preset_picker_hint),
            ) {
                val rows = segmentedRows {
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_alert_preset_default),
                            subtitle = stringResource(R.string.float_ball_alert_preset_default_subtitle),
                            shape = shape,
                            trailing = if (currentId == null) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = { selectAndBack(null) },
                        )
                    }
                    presets.forEach { preset ->
                        item { shape ->
                            AlertPresetRow(
                                preset = preset,
                                selected = preset.id == currentId,
                                shape = shape,
                                onClick = { selectAndBack(preset.id) },
                            )
                        }
                    }
                    if (presets.isEmpty()) {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_alert_preset_empty),
                                shape = shape,
                            )
                        }
                    }
                }
                SegmentedRows(rows)
            }
        }
    }
}

/** 单行预设：名称 + 内置/我的徽标 + 选中勾。 */
@Composable
private fun AlertPresetRow(
    preset: AlertPreset,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    LunoSettingsRow(
        title = preset.displayName(),
        subtitle = stringResource(
            if (preset.builtin) {
                R.string.float_ball_alert_preset_builtin
            } else {
                R.string.float_ball_alert_preset_custom
            },
        ),
        shape = shape,
        trailing = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else {
            null
        },
        onClick = onClick,
    )
}