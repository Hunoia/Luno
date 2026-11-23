package hunoia.luno.floatball.settings.alert

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AlertCategory
import hunoia.luno.config.model.AnchorAlertStyle
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.facade.LocalFacades

/**
 * 到达提示·球心·全屏类设置页：类目开关、类内单选样式（球光环 / 全屏涟漪）与旋钮。
 *
 * 球光环锚定通知胶囊的收起圆；全屏涟漪可选球心或屏幕中心。两者都画在整屏舞台窗上，
 * 试播时胶囊没显示会先弹一条演示胶囊（球心锚点需要真实胶囊矩形）。
 */
@Composable
fun AlertAnchorSettingsScreen(onBack: () -> Unit) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val settings = actionSettings.floatBall.notificationCapsule.alert.anchor

    LunoScreenLazy(
        title = stringResource(AlertCategory.Anchor.titleRes()),
        onBack = onBack,
    ) {
        item(key = "anchor") {
            LunoGroup(
                title = stringResource(AlertCategory.Anchor.titleRes()),
                subtitle = stringResource(AlertCategory.Anchor.subtitleRes()),
            ) {
                LunoSegmentedGroup {
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_capsule_alert_category_enabled),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_category_enabled_subtitle),
                            checked = settings.enabled,
                            onCheckedChange = { checked ->
                                updateAlert(scope) { it.copy(anchor = it.anchor.copy(enabled = checked)) }
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
                                updateAlert(scope) {
                                    it.copy(anchor = it.anchor.copy(style = styles[index]))
                                }
                            },
                            shape = shape,
                        )
                    }
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
                                            it.copy(anchor = it.anchor.copy(
                                                fullRipple = it.anchor.fullRipple.copy(strokeWidthDp = v),
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
                                            it.copy(anchor = it.anchor.copy(
                                                fullRipple = it.anchor.fullRipple.copy(cycles = v),
                                            ))
                                        }
                                    },
                                )
                            }
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
            }
        }
    }
}
