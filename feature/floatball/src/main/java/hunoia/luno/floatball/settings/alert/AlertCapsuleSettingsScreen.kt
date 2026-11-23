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
import hunoia.luno.config.model.CapsuleAlertStyle
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
 * 到达提示·胶囊本体类设置页：类目开关、类内单选样式（描边脉冲 / 边框流光）与旋钮。
 *
 * 这一类的效果画在胶囊窗口自己的 Compose 层（不新增窗口、不动几何）；
 * 试播时胶囊没显示会先弹一条演示胶囊。
 */
@Composable
fun AlertCapsuleSettingsScreen(onBack: () -> Unit) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val settings = actionSettings.floatBall.notificationCapsule.alert.capsule

    LunoScreenLazy(
        title = stringResource(AlertCategory.Capsule.titleRes()),
        onBack = onBack,
    ) {
        item(key = "capsule") {
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
                                updateAlert(scope) { it.copy(capsule = it.capsule.copy(enabled = checked)) }
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
                                updateAlert(scope) {
                                    it.copy(capsule = it.capsule.copy(style = styles[index]))
                                }
                            },
                            shape = shape,
                        )
                    }
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
                                            it.copy(capsule = it.capsule.copy(
                                                shimmer = it.capsule.shimmer.copy(trailScale = v),
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
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
                                        updateAlert(scope) {
                                            it.copy(capsule = it.capsule.copy(
                                                shimmer = it.capsule.shimmer.copy(cycles = v),
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
                            onClick = { facades.system.previewFloatBallAlert(context, AlertCategory.Capsule) },
                        )
                    }
                }
            }
        }
    }
}
