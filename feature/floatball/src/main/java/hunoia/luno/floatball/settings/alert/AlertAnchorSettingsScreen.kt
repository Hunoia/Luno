package hunoia.luno.floatball.settings.alert

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertCategory
import hunoia.luno.config.model.AnchorAlertStyle
import hunoia.luno.config.model.BallBurstAlertSettings
import hunoia.luno.config.model.BallHaloAlertSettings
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FullRippleAlertSettings
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.RingAlertSettings
import hunoia.luno.config.model.paletteFor
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.toMarkColor

/**
 * 到达提示·球心·全屏类设置页：类目开关、类内单选样式（球光环 / 全屏涟漪 /
 * 球心爆发 / 球心光晕）与旋钮。
 *
 * 球光环锚定通知胶囊的收起圆；全屏涟漪可选球心或屏幕中心。两者都画在整屏舞台窗上，
 * 试播时胶囊没显示会先弹一条演示胶囊（球心锚点需要真实胶囊矩形）。
 */
@Composable
fun AlertAnchorSettingsScreen(onBack: () -> Unit, onNavToPalette: () -> Unit = {}) {
    val facades = LocalFacades.current
    val context = LocalContext.current
    // 本地即显状态：用户操作（切样式 / 旋钮 / 开关）立即更新 UI，再异步落盘；
    // resume 时从真实配置再同步一次，兜底调色板页返回 / 外部写入（见 rememberAlertEditor）。
    val editor = rememberAlertEditor()
    val alert = editor.alert
    // settings 提到页面顶层：LunoScreen 的 content 是普通 Column（非懒重组），
    // 重组时整个 content lambda 重跑，when(settings.style) 才能跟着换分支。
    val settings = alert.anchor

    // 本地即显 + 异步落盘：transform 先应用到本地（UI 立即反映），再写回配置。
    fun updateAlert(transform: (NotificationAlertSettings) -> NotificationAlertSettings) =
        editor.update(transform)

    LunoScreen(
        title = stringResource(AlertCategory.Anchor.titleRes()),
        onBack = onBack,
    ) {
        LunoGroup(
            title = stringResource(AlertCategory.Anchor.titleRes()),
            subtitle = stringResource(AlertCategory.Anchor.subtitleRes()),
        ) {
            // 条件行（球心光晕不显示调色板）走裸行：注册和 emit 都在本卡组合里，
            // 和驱动它的样式下拉同批刷新，不会「选择已落盘但行不出现」
            // （见 ui-guidelines §4.1；不要塞进和驱动控件共用的 LunoSegmentedGroup）。
            val rows = segmentedRows {
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_capsule_alert_category_enabled),
                        subtitle = stringResource(R.string.float_ball_capsule_alert_category_enabled_subtitle),
                        checked = settings.enabled,
                        onCheckedChange = { checked ->
                            updateAlert { it.copy(anchor = it.anchor.copy(enabled = checked)) }
                        },
                        shape = shape,
                    )
                }
                item { shape ->
                    val styles = AnchorAlertStyle.entries
                    LunoDropdownRow(
                        title = stringResource(R.string.float_ball_capsule_alert_style),
                        options = styleLabels(styles),
                        selectedIndex = styles.indexOf(settings.style).coerceAtLeast(0),
                        onSelect = { index ->
                            updateAlert {
                                it.copy(anchor = it.anchor.copy(style = styles[index]))
                            }
                        },
                        shape = shape,
                    )
                }
                if (settings.style != AnchorAlertStyle.BallHalo) {
                    // 调色板：环 / 涟漪 / 爆发都按周长或序号取色；球心光晕只用首色（单色辉光），
                    // 多色调色板对它无效，选中时隐藏入口（避免「无效项仍显示」）。
                    item { shape ->
                        AlertPaletteSettingsRow(
                            palette = alert.paletteFor(AlertCategory.Anchor),
                            fallbackColor = alert.color.toMarkColor(),
                            shape = shape,
                            onClick = onNavToPalette,
                        )
                    }
                }
            }
            SegmentedRows(rows)
        }
        key(settings.style) {
            LunoGroup {
                val rows = segmentedRows {
                    when (settings.style) {
                        AnchorAlertStyle.Ring -> {
                            val s = settings.ring
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_travel),
                                    value = s.travel,
                                    range = FloatBallDefaults.AlertRingTravelMin..
                                        FloatBallDefaults.AlertRingTravelMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(anchor = it.anchor.copy(
                                                ring = it.anchor.ring.copy(travel = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_stroke_width),
                                    value = s.strokeWidthDp,
                                    range = FloatBallDefaults.AlertRingStrokeWidthDpMin..
                                        FloatBallDefaults.AlertRingStrokeWidthDpMax,
                                    display = { "${it.toInt()} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(anchor = it.anchor.copy(
                                                ring = it.anchor.ring.copy(strokeWidthDp = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_rings_per_cycle),
                                    value = s.ringsPerCycle,
                                    range = FloatBallDefaults.AlertRingRingsPerCycleMin..
                                        FloatBallDefaults.AlertRingRingsPerCycleMax,
                                    display = { "${it.toInt()}" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(anchor = it.anchor.copy(
                                                ring = it.anchor.ring.copy(ringsPerCycle = v),
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
                                            it.copy(anchor = it.anchor.copy(
                                                ring = it.anchor.ring.copy(haloScale = v),
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
                                            it.copy(anchor = it.anchor.copy(
                                                ring = it.anchor.ring.copy(speed = v),
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
                                            it.copy(anchor = it.anchor.copy(
                                                ring = it.anchor.ring.copy(brightness = v),
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
                                            it.copy(anchor = it.anchor.copy(
                                                ring = it.anchor.ring.copy(cycles = v),
                                            ))
                                        }
                                    },
                                )
                            }
                        }
    
                        AnchorAlertStyle.Ripple -> {
                            val s = settings.fullRipple
                            item { shape ->
                                LunoSwitchRow(
                                    title = stringResource(R.string.float_ball_capsule_alert_center_at_ball),
                                    subtitle = stringResource(R.string.float_ball_capsule_alert_center_at_ball_subtitle),
                                    checked = s.centerAtBall,
                                    onCheckedChange = { checked ->
                                        updateAlert {
                                            it.copy(anchor = it.anchor.copy(
                                                fullRipple = it.anchor.fullRipple.copy(centerAtBall = checked),
                                            ))
                                        }
                                    },
                                    shape = shape,
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_stroke_width),
                                    value = s.strokeWidthDp,
                                    range = FloatBallDefaults.AlertRippleStrokeWidthDpMin..
                                        FloatBallDefaults.AlertRippleStrokeWidthDpMax,
                                    display = { "${it.toInt()} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(anchor = it.anchor.copy(
                                                fullRipple = it.anchor.fullRipple.copy(strokeWidthDp = v),
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
                                            it.copy(anchor = it.anchor.copy(
                                                fullRipple = it.anchor.fullRipple.copy(haloScale = v),
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
                                            it.copy(anchor = it.anchor.copy(
                                                fullRipple = it.anchor.fullRipple.copy(speed = v),
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
                                            it.copy(anchor = it.anchor.copy(
                                                fullRipple = it.anchor.fullRipple.copy(brightness = v),
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
                                            it.copy(anchor = it.anchor.copy(
                                                fullRipple = it.anchor.fullRipple.copy(cycles = v),
                                            ))
                                        }
                                    },
                                )
                            }
                        }
                        AnchorAlertStyle.BallBurst -> {
                            val s = settings.ballBurst
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_count),
                                    value = s.count,
                                    range = FloatBallDefaults.AlertBallBurstCountMin..
                                        FloatBallDefaults.AlertBallBurstCountMax,
                                    display = { "${it.toInt()}" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(anchor = it.anchor.copy(
                                                ballBurst = it.anchor.ballBurst.copy(count = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_size),
                                    value = s.particleSizeDp,
                                    range = FloatBallDefaults.AlertBallBurstSizeDpMin..
                                        FloatBallDefaults.AlertBallBurstSizeDpMax,
                                    display = { "${it.toInt()} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(anchor = it.anchor.copy(
                                                ballBurst = it.anchor.ballBurst.copy(particleSizeDp = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_ball_burst_spread),
                                    value = s.spreadDp,
                                    range = FloatBallDefaults.AlertBallBurstSpreadDpMin..
                                        FloatBallDefaults.AlertBallBurstSpreadDpMax,
                                    display = { "${it.toInt()} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(anchor = it.anchor.copy(
                                                ballBurst = it.anchor.ballBurst.copy(spreadDp = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            AlertSpeedBrightnessCycles(s.speed, s.brightness, s.cycles,
                                onSpeed = { v -> updateAlert { it.copy(anchor = it.anchor.copy(ballBurst = it.anchor.ballBurst.copy(speed = v))) } },
                                onBrightness = { v -> updateAlert { it.copy(anchor = it.anchor.copy(ballBurst = it.anchor.ballBurst.copy(brightness = v))) } },
                                onCycles = { v -> updateAlert { it.copy(anchor = it.anchor.copy(ballBurst = it.anchor.ballBurst.copy(cycles = v))) } },
                            )
                        }
                        AnchorAlertStyle.BallHalo -> {
                            val s = settings.ballHalo
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_ball_halo_radius),
                                    value = s.radiusPercent,
                                    range = FloatBallDefaults.AlertBallHaloRadiusMin..
                                        FloatBallDefaults.AlertBallHaloRadiusMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(anchor = it.anchor.copy(
                                                ballHalo = it.anchor.ballHalo.copy(radiusPercent = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_ball_halo_intensity),
                                    value = s.haloScale,
                                    range = FloatBallDefaults.AlertHaloScaleMin..
                                        FloatBallDefaults.AlertHaloScaleMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(anchor = it.anchor.copy(
                                                ballHalo = it.anchor.ballHalo.copy(haloScale = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            AlertSpeedBrightnessCycles(s.speed, s.brightness, s.cycles,
                                onSpeed = { v -> updateAlert { it.copy(anchor = it.anchor.copy(ballHalo = it.anchor.ballHalo.copy(speed = v))) } },
                                onBrightness = { v -> updateAlert { it.copy(anchor = it.anchor.copy(ballHalo = it.anchor.ballHalo.copy(brightness = v))) } },
                                onCycles = { v -> updateAlert { it.copy(anchor = it.anchor.copy(ballHalo = it.anchor.ballHalo.copy(cycles = v))) } },
                            )
                        }
                    }
                    AlertResetStyleRow {
                        updateAlert {
                            it.copy(
                                anchor = when (it.anchor.style) {
                                    AnchorAlertStyle.Ring -> it.anchor.copy(
                                        ring = RingAlertSettings(),
                                    )
                                    AnchorAlertStyle.Ripple -> it.anchor.copy(
                                        fullRipple = FullRippleAlertSettings(),
                                    )
                                    AnchorAlertStyle.BallBurst -> it.anchor.copy(
                                        ballBurst = BallBurstAlertSettings(),
                                    )
                                    AnchorAlertStyle.BallHalo -> it.anchor.copy(
                                        ballHalo = BallHaloAlertSettings(),
                                    )
                                },
                            )
                        }
                    }
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_preview),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_preview_subtitle),
                            icon = Icons.Default.PlayArrow,
                            shape = shape,
                            onClick = { facades.system.previewFloatBallAlert(context, AlertCategory.Anchor) },
                        )
                    }
                }
                SegmentedRows(rows)
            }
        }
    }
}
