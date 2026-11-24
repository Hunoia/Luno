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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreenLazy
import kotlinx.coroutines.launch

/**
 * 到达提示·预设选择页：单选（内置单层素材 + 我的组合），选完即落盘并返回。
 *
 * 组合 / 参数编辑在预设库，本页只做选择。
 */
@Composable
fun AlertPresetPickerScreen(
    onBack: () -> Unit,
    onNavToPresetLibrary: () -> Unit = {},
) {
    val editor = rememberAlertEditor()
    val currentId = editor.alert.presetId
    val all = editor.library.pickerPresets()
    val mine = all.filter { !it.builtin }
    val builtin = all.filter { it.builtin }
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }

    fun selectAndBack(presetId: String?) {
        if (leaving) return
        leaving = true
        scope.launch {
            editor.commitAlert { it.copy(presetId = presetId) }
            onBack()
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_alert_preset_picker),
        onBack = onBack,
    ) {
        item(key = "none") {
            LunoGroup(title = stringResource(R.string.float_ball_alert_preset_group_choice)) {
                SegmentedRows(
                    segmentedRows {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_alert_preset_none),
                                subtitle = stringResource(R.string.float_ball_alert_preset_none_subtitle),
                                shape = shape,
                                trailing = if (currentId == null) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                                onClick = { selectAndBack(null) },
                            )
                        }
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_alert_preset_manage),
                                subtitle = stringResource(R.string.float_ball_alert_preset_manage_subtitle),
                                shape = shape,
                                onClick = onNavToPresetLibrary,
                            )
                        }
                    },
                )
            }
        }
        if (mine.isNotEmpty()) {
            item(key = "mine") {
                LunoGroup(
                    title = stringResource(R.string.float_ball_preset_group_mine),
                    subtitle = stringResource(R.string.float_ball_preset_group_mine_hint),
                ) {
                    val rows = segmentedRows {
                        mine.forEach { preset ->
                            item { shape ->
                                AlertPresetRow(
                                    preset = preset,
                                    selected = preset.id == currentId,
                                    shape = shape,
                                    onClick = { selectAndBack(preset.id) },
                                )
                            }
                        }
                    }
                    SegmentedRows(rows)
                }
            }
        }
        item(key = "builtin") {
            LunoGroup(
                title = stringResource(R.string.float_ball_preset_group_builtin),
                subtitle = stringResource(R.string.float_ball_preset_group_builtin_hint),
            ) {
                val rows = segmentedRows {
                    builtin.forEach { preset ->
                        item { shape ->
                            AlertPresetRow(
                                preset = preset,
                                selected = preset.id == currentId,
                                shape = shape,
                                onClick = { selectAndBack(preset.id) },
                            )
                        }
                    }
                }
                SegmentedRows(rows)
            }
        }
    }
}

/** 单行预设：名称 + 层数 / 类别 + 选中勾。 */
@Composable
internal fun AlertPresetRow(
    preset: AlertPreset,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val categoryText = preset.categories()
        .map { stringResource(it.titleRes()) }
        .joinToString(" · ")
    LunoSettingsRow(
        title = preset.displayName(),
        subtitle = if (categoryText.isBlank()) {
            preset.layerSummary()
        } else {
            "${preset.layerSummary()}  $categoryText"
        },
        shape = shape,
        trailing = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else {
            null
        },
        onClick = onClick,
    )
}
