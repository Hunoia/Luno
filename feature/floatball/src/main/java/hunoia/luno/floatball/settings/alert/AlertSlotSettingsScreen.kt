package hunoia.luno.floatball.settings.alert

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertSlot
import hunoia.luno.config.model.paletteById
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.toMarkColor

/**
 * 到达提示 v3 的单槽设置页：启用、选预设、少量覆盖（颜色 / 强度 / 速度）、单槽试播。
 *
 * 槽位只决定配置分组；预设自带的宿主决定绘制面（球体槽也可能落到胶囊窗）。
 * 全参数编辑在预设库（Step 3），本页只保留「不改预设也能微调」的三项覆盖。
 */
@Composable
fun AlertSlotSettingsScreen(
    slot: AlertSlot,
    onBack: () -> Unit,
    onNavToPresetPicker: () -> Unit,
    onNavToPalette: () -> Unit,
) {
    val editor = rememberAlertEditor()
    val alert = editor.alert
    val settings = alert.slotSettings(slot)
    val overrides = settings.overrides
    val context = LocalContext.current
    val facades = LocalFacades.current
    val palette = editor.library.paletteById(overrides.paletteId)

    LunoScreenLazy(
        title = stringResource(slot.titleRes()),
        onBack = onBack,
    ) {
        item(key = "slot") {
            LunoGroup(
                title = stringResource(slot.titleRes()),
                subtitle = stringResource(slot.subtitleRes()),
            ) {
                val rows = segmentedRows {
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_alert_slot_enabled),
                            subtitle = stringResource(R.string.float_ball_alert_slot_enabled_subtitle),
                            checked = settings.enabled,
                            onCheckedChange = { checked ->
                                editor.update { it.withSlot(slot) { s -> s.copy(enabled = checked) } }
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_alert_preset),
                            subtitle = slotPresetName(slot, settings, editor.library),
                            shape = shape,
                            onClick = onNavToPresetPicker,
                        )
                    }
                    item { shape ->
                        AlertKnobSlider(
                            label = stringResource(R.string.float_ball_alert_intensity),
                            value = overrides.intensityPercent,
                            range = IntensityRange,
                            display = { "${it.toInt()}%" },
                            shape = shape,
                            onCommit = { value ->
                                editor.update {
                                    it.withSlot(slot) { s ->
                                        s.withOverrides { o -> o.copy(intensityPercent = value) }
                                    }
                                }
                            },
                        )
                    }
                    item { shape ->
                        AlertKnobSlider(
                            label = stringResource(R.string.float_ball_alert_speed),
                            value = overrides.speedPercent,
                            range = SpeedRange,
                            display = { "${it.toInt()}%" },
                            shape = shape,
                            onCommit = { value ->
                                editor.update {
                                    it.withSlot(slot) { s ->
                                        s.withOverrides { o -> o.copy(speedPercent = value) }
                                    }
                                }
                            },
                        )
                    }
                    item { shape ->
                        AlertPaletteSettingsRow(
                            palette = palette,
                            fallbackColor = if (alert.color.isUnset) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                alert.color.toMarkColor()
                            },
                            shape = shape,
                            onClick = onNavToPalette,
                        )
                    }
                    AlertResetOverridesRow {
                        editor.update { it.withSlot(slot) { s -> s.withDefaultOverrides() } }
                    }
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_alert_preview),
                            subtitle = stringResource(R.string.float_ball_alert_preview_subtitle),
                            icon = Icons.Default.PlayArrow,
                            shape = shape,
                            onClick = { facades.system.previewFloatBallAlert(context, slot) },
                        )
                    }
                }
                SegmentedRows(rows)
            }
        }
    }
}

/** 强度百分比：100 = 预设原样。 */
private val IntensityRange = 50..200

/** 速度百分比：100 = 预设基准；25–400 与渲染计划的夹取口径一致。 */
private val SpeedRange = 25..400