package hunoia.luno.floatball.settings.alert

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertCategory
import hunoia.luno.config.model.CapsuleAlertStyle
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.paletteFor
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.toMarkColor

/**
 * 到达提示·胶囊本体类设置页：类目开关、类内单选样式（描边脉冲 / 边框流光 /
 * 呼吸描边 / 内部扫光）与旋钮。
 *
 * 这一类的效果画在胶囊窗口自己的 Compose 层（不新增窗口、不动几何）；
 * 试播时胶囊没显示会先弹一条演示胶囊。
 */
@Composable
fun AlertCapsuleSettingsScreen(onBack: () -> Unit, onNavToPalette: () -> Unit = {}) {
    val facades = LocalFacades.current
    val context = LocalContext.current
    // 本地即显状态：用户操作（切样式 / 旋钮 / 开关）立即更新 UI，再异步落盘；
    // resume 时从真实配置再同步一次，兜底调色板页返回 / 外部写入（见 rememberAlertEditor）。
    val editor = rememberAlertEditor()
    val alert = editor.alert
    // settings 提到页面顶层：LunoScreen 的 content 是普通 Column（非懒重组），
    // 重组时整个 content lambda 重跑，when(settings.style) 才能跟着换分支。
    val settings = alert.capsule

    // 本地即显 + 异步落盘：transform 先应用到本地（UI 立即反映），再写回配置。
    fun updateAlert(transform: (NotificationAlertSettings) -> NotificationAlertSettings) =
        editor.update(transform)

    LunoScreen(
        title = stringResource(AlertCategory.Capsule.titleRes()),
        onBack = onBack,
    ) {
        LunoGroup(
            title = stringResource(AlertCategory.Capsule.titleRes()),
            subtitle = stringResource(AlertCategory.Capsule.subtitleRes()),
        ) {
            LunoSegmentedGroup {
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_capsule_alert_category_enabled),
                        subtitle = stringResource(R.string.float_ball_capsule_alert_category_enabled_subtitle),
                        checked = settings.enabled,
                        onCheckedChange = { checked ->
                            updateAlert { it.copy(capsule = it.capsule.copy(enabled = checked)) }
                        },
                        shape = shape,
                    )
                }
                item { shape ->
                    val styles = CapsuleAlertStyle.entries
                    LunoDropdownRow(
                        title = stringResource(R.string.float_ball_capsule_alert_style),
                        options = styleLabels(styles),
                        selectedIndex = styles.indexOf(settings.style).coerceAtLeast(0),
                        onSelect = { index ->
                            updateAlert {
                                it.copy(capsule = it.capsule.copy(style = styles[index]))
                            }
                        },
                        shape = shape,
                    )
                }
                item { shape ->
                    AlertPaletteSettingsRow(
                        palette = alert.paletteFor(AlertCategory.Capsule),
                        fallbackColor = alert.color.toMarkColor(),
                        shape = shape,
                        onClick = onNavToPalette,
                    )
                }
            }
        }
        key(settings.style) {
            LunoGroup {
                LunoSegmentedGroup {
                    when (settings.style) {
                        CapsuleAlertStyle.Pulse -> {
                            val s = settings.pulse
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_glow_width),
                                    value = s.glowWidthDpTenths,
                                    range = FloatBallDefaults.AlertPulseGlowWidthTenthsMin..
                                        FloatBallDefaults.AlertPulseGlowWidthTenthsMax,
                                    display = { "${it / 10f} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                pulse = it.capsule.pulse.copy(glowWidthDpTenths = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_halo),
                                    value = s.haloScale,
                                    range = FloatBallDefaults.AlertHaloScaleMin..
                                        FloatBallDefaults.AlertHaloScaleMax,
                                    display = { if (it <= 0f) stringResource(R.string.float_ball_off) else "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                pulse = it.capsule.pulse.copy(haloScale = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_speed),
                                    value = s.speed,
                                    range = FloatBallDefaults.AlertSpeedPercentMin..
                                        FloatBallDefaults.AlertSpeedPercentMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                pulse = it.capsule.pulse.copy(speed = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_brightness),
                                    value = s.brightness,
                                    range = FloatBallDefaults.AlertBrightnessMin..
                                        FloatBallDefaults.AlertBrightnessMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                pulse = it.capsule.pulse.copy(brightness = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_cycles),
                                    value = s.cycles,
                                    range = FloatBallDefaults.AlertCyclesMin..
                                        FloatBallDefaults.AlertCyclesMax,
                                    display = { "${it.toInt()} ×" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                pulse = it.capsule.pulse.copy(cycles = v),
                                            ))
                                        }
                                    },
                                )
                            }
                        }
    
                        CapsuleAlertStyle.Shimmer -> {
                            val s = settings.shimmer
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_band_width),
                                    value = s.bandWidth,
                                    range = FloatBallDefaults.AlertShimmerBandWidthMin..
                                        FloatBallDefaults.AlertShimmerBandWidthMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                shimmer = it.capsule.shimmer.copy(bandWidth = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_trail_width),
                                    value = s.trailScale,
                                    range = FloatBallDefaults.AlertShimmerTrailScaleMin..
                                        FloatBallDefaults.AlertShimmerTrailScaleMax,
                                    display = { if (it <= 0f) stringResource(R.string.float_ball_off) else "×${it / 100f}" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                shimmer = it.capsule.shimmer.copy(trailScale = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_trail_alpha),
                                    value = s.trailAlpha,
                                    range = FloatBallDefaults.AlertShimmerTrailAlphaMin..
                                        FloatBallDefaults.AlertShimmerTrailAlphaMax,
                                    display = { if (it <= 0f) stringResource(R.string.float_ball_off) else "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                shimmer = it.capsule.shimmer.copy(trailAlpha = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                LunoSwitchRow(
                                    title = stringResource(R.string.float_ball_capsule_alert_reverse),
                                    subtitle = stringResource(R.string.float_ball_capsule_alert_reverse_subtitle),
                                    checked = s.reverse,
                                    onCheckedChange = { checked ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                shimmer = it.capsule.shimmer.copy(reverse = checked),
                                            ))
                                        }
                                    },
                                    shape = shape,
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_speed),
                                    value = s.speed,
                                    range = FloatBallDefaults.AlertSpeedPercentMin..
                                        FloatBallDefaults.AlertSpeedPercentMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                shimmer = it.capsule.shimmer.copy(speed = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_brightness),
                                    value = s.brightness,
                                    range = FloatBallDefaults.AlertBrightnessMin..
                                        FloatBallDefaults.AlertBrightnessMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                shimmer = it.capsule.shimmer.copy(brightness = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_cycles),
                                    value = s.cycles,
                                    range = FloatBallDefaults.AlertCyclesMin..
                                        FloatBallDefaults.AlertCyclesMax,
                                    display = { "${it.toInt()} ×" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                shimmer = it.capsule.shimmer.copy(cycles = v),
                                            ))
                                        }
                                    },
                                )
                            }
                        }
                        CapsuleAlertStyle.BreathOutline -> {
                            val s = settings.breathOutline
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_breath_stroke_width),
                                    value = s.strokeWidthDpTenths,
                                    range = FloatBallDefaults.AlertBreathStrokeWidthTenthsMin..
                                        FloatBallDefaults.AlertBreathStrokeWidthTenthsMax,
                                    display = { "${it / 10f} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                breathOutline = it.capsule.breathOutline.copy(strokeWidthDpTenths = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_breath_scale),
                                    value = s.breatheScale,
                                    range = FloatBallDefaults.AlertBreathScaleMin..
                                        FloatBallDefaults.AlertBreathScaleMax,
                                    display = { if (it <= 0f) stringResource(R.string.float_ball_off) else "×${it / 1000f}" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                breathOutline = it.capsule.breathOutline.copy(breatheScale = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            AlertSpeedBrightnessCycles(s.speed, s.brightness, s.cycles,
                                onSpeed = { v -> updateAlert { it.copy(capsule = it.capsule.copy(breathOutline = it.capsule.breathOutline.copy(speed = v))) } },
                                onBrightness = { v -> updateAlert { it.copy(capsule = it.capsule.copy(breathOutline = it.capsule.breathOutline.copy(brightness = v))) } },
                                onCycles = { v -> updateAlert { it.copy(capsule = it.capsule.copy(breathOutline = it.capsule.breathOutline.copy(cycles = v))) } },
                            )
                        }
                        CapsuleAlertStyle.InnerSweep -> {
                            val s = settings.innerSweep
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_inner_sweep_band),
                                    value = s.bandWidth,
                                    range = FloatBallDefaults.AlertInnerSweepBandWidthMin..
                                        FloatBallDefaults.AlertInnerSweepBandWidthMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                innerSweep = it.capsule.innerSweep.copy(bandWidth = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                LunoSwitchRow(
                                    title = stringResource(R.string.float_ball_capsule_alert_reverse),
                                    subtitle = stringResource(R.string.float_ball_capsule_alert_reverse_subtitle),
                                    checked = s.reverse,
                                    onCheckedChange = { checked ->
                                        updateAlert {
                                            it.copy(capsule = it.capsule.copy(
                                                innerSweep = it.capsule.innerSweep.copy(reverse = checked),
                                            ))
                                        }
                                    },
                                    shape = shape,
                                )
                            }
                            AlertSpeedBrightnessCycles(s.speed, s.brightness, s.cycles,
                                onSpeed = { v -> updateAlert { it.copy(capsule = it.capsule.copy(innerSweep = it.capsule.innerSweep.copy(speed = v))) } },
                                onBrightness = { v -> updateAlert { it.copy(capsule = it.capsule.copy(innerSweep = it.capsule.innerSweep.copy(brightness = v))) } },
                                onCycles = { v -> updateAlert { it.copy(capsule = it.capsule.copy(innerSweep = it.capsule.innerSweep.copy(cycles = v))) } },
                            )
                        }
                    }
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_preview),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_preview_subtitle),
                            icon = Icons.Default.PlayArrow,
                            shape = shape,
                            onClick = { facades.system.previewFloatBallAlert(context, AlertCategory.Capsule) },
                        )
                    }
                }
            }
        }
    }
}
