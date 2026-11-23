package hunoia.luno.floatball.settings.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.config.model.AlertCategory
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.EdgeAlertStyle
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoDropdownRow
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.toMarkColor

/**
 * 到达提示·边缘类设置页：类目开关、类内单选样式（呼吸光 / 流光）与各自旋钮。
 *
 * 行形状全部由 [LunoSegmentedGroup] 自动算（不再手数行数）；旋钮松手才落盘。
 * 「试播该分类」只播这一类，失败原因由服务侧 Toast 反馈。
 */
@Composable
fun AlertEdgeSettingsScreen(onBack: () -> Unit) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val alert = actionSettings.floatBall.notificationCapsule.alert
    val settings = alert.edge
    var showSecondColorPicker by remember { mutableStateOf(false) }

    LunoScreenLazy(
        title = stringResource(AlertCategory.Edge.titleRes()),
        onBack = onBack,
        sheets = {
            if (showSecondColorPicker) {
                val scheme = MaterialTheme.colorScheme
                ColorPickerBottomSheet(
                    onDismissRequest = { showSecondColorPicker = false },
                    onCopyColor = { hex ->
                        ClipboardCenter.writeTextImmediate(hex, "Luno color")
                    },
                    initialSelection = alert.edge.edgeFlow.secondColor.toInitialSelection(
                        scheme = scheme,
                        primaryFallback = true,
                    ),
                    onColorSelected = { selection ->
                        updateAlert(scope) {
                            it.copy(
                                edge = it.edge.copy(
                                    edgeFlow = it.edge.edgeFlow.copy(
                                        secondColor = selection.toColorSpec(scheme),
                                    ),
                                ),
                            )
                        }
                        showSecondColorPicker = false
                    },
                )
            }
        },
    ) {
        item(key = "edge") {
            LunoGroup(
                title = stringResource(AlertCategory.Edge.titleRes()),
                subtitle = stringResource(AlertCategory.Edge.subtitleRes()),
            ) {
                LunoSegmentedGroup {
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_capsule_alert_category_enabled),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_category_enabled_subtitle),
                            checked = settings.enabled,
                            onCheckedChange = { checked ->
                                updateAlert(scope) { it.copy(edge = it.edge.copy(enabled = checked)) }
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        val styles = EdgeAlertStyle.entries
                        LunoDropdownRow(
                            title = stringResource(R.string.float_ball_capsule_alert_style),
                            options = styleLabels(styles),
                            selectedIndex = styles.indexOf(settings.style).coerceAtLeast(0),
                            onSelect = { index ->
                                updateAlert(scope) {
                                    it.copy(edge = it.edge.copy(style = styles[index]))
                                }
                            },
                            shape = shape,
                        )
                    }
                    when (settings.style) {
                        EdgeAlertStyle.EdgeGlow -> {
                            val s = settings.edgeGlow
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_edge_depth),
                                    value = s.bandWidthDp,
                                    range = FloatBallDefaults.AlertEdgeGlowBandWidthDpMin..
                                        FloatBallDefaults.AlertEdgeGlowBandWidthDpMax,
                                    display = { "${it.toInt()} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(edge = it.edge.copy(
                                                edgeGlow = it.edge.edgeGlow.copy(bandWidthDp = v),
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
                                            it.copy(edge = it.edge.copy(
                                                edgeGlow = it.edge.edgeGlow.copy(speed = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_corner_radius),
                                    value = s.cornerRadiusDp,
                                    range = FloatBallDefaults.AlertEdgeGlowCornerDpMin..
                                        FloatBallDefaults.AlertEdgeGlowCornerDpMax,
                                    display = { "${it.toInt()} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(edge = it.edge.copy(
                                                edgeGlow = it.edge.edgeGlow.copy(cornerRadiusDp = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_hardness),
                                    value = s.hardness,
                                    range = FloatBallDefaults.AlertEdgeGlowHardnessMin..
                                        FloatBallDefaults.AlertEdgeGlowHardnessMax,
                                    display = { "${it.toInt()}" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(edge = it.edge.copy(
                                                edgeGlow = it.edge.edgeGlow.copy(hardness = v),
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
                                            it.copy(edge = it.edge.copy(
                                                edgeGlow = it.edge.edgeGlow.copy(brightness = v),
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
                                            it.copy(edge = it.edge.copy(
                                                edgeGlow = it.edge.edgeGlow.copy(cycles = v),
                                            ))
                                        }
                                    },
                                )
                            }
                        }

                        EdgeAlertStyle.EdgeFlow -> {
                            val s = settings.edgeFlow
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_edge_depth),
                                    value = s.bandWidthDp,
                                    range = FloatBallDefaults.AlertFlowBandWidthDpMin..
                                        FloatBallDefaults.AlertFlowBandWidthDpMax,
                                    display = { "${it.toInt()} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(edge = it.edge.copy(
                                                edgeFlow = it.edge.edgeFlow.copy(bandWidthDp = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            // 刃感三件套：主带越窄、深度越硬、拖尾越淡，越像一道利刃。
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_core_band),
                                    value = s.coreBandPermille,
                                    range = FloatBallDefaults.AlertFlowCoreBandPermilleMin..
                                        FloatBallDefaults.AlertFlowCoreBandPermilleMax,
                                    display = { "${it.toInt() / 10f}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(edge = it.edge.copy(
                                                edgeFlow = it.edge.edgeFlow.copy(coreBandPermille = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_trail_width),
                                    value = s.trailScale,
                                    range = FloatBallDefaults.AlertFlowTrailScaleMin..
                                        FloatBallDefaults.AlertFlowTrailScaleMax,
                                    display = { if (it <= 0f) stringResource(R.string.float_ball_off) else "×${it / 100f}" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(edge = it.edge.copy(
                                                edgeFlow = it.edge.edgeFlow.copy(trailScale = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_trail_alpha),
                                    value = s.trailAlpha,
                                    range = FloatBallDefaults.AlertFlowTrailAlphaMin..
                                        FloatBallDefaults.AlertFlowTrailAlphaMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(edge = it.edge.copy(
                                                edgeFlow = it.edge.edgeFlow.copy(trailAlpha = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_hardness),
                                    value = s.hardness,
                                    range = FloatBallDefaults.AlertFlowHardnessMin..
                                        FloatBallDefaults.AlertFlowHardnessMax,
                                    display = { "${it.toInt()}" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(edge = it.edge.copy(
                                                edgeFlow = it.edge.edgeFlow.copy(hardness = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_corner_radius),
                                    value = s.cornerRadiusDp,
                                    range = FloatBallDefaults.AlertEdgeGlowCornerDpMin..
                                        FloatBallDefaults.AlertEdgeGlowCornerDpMax,
                                    display = { "${it.toInt()} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(edge = it.edge.copy(
                                                edgeFlow = it.edge.edgeFlow.copy(cornerRadiusDp = v),
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
                                            it.copy(edge = it.edge.copy(
                                                edgeFlow = it.edge.edgeFlow.copy(speed = v),
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
                                            it.copy(edge = it.edge.copy(
                                                edgeFlow = it.edge.edgeFlow.copy(brightness = v),
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
                                            it.copy(edge = it.edge.copy(
                                                edgeFlow = it.edge.edgeFlow.copy(cycles = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                LunoSwitchRow(
                                    title = stringResource(R.string.float_ball_capsule_alert_dual_band),
                                    subtitle = stringResource(R.string.float_ball_capsule_alert_dual_band_subtitle),
                                    checked = s.dualBand,
                                    onCheckedChange = { checked ->
                                        updateAlert(scope) {
                                            it.copy(edge = it.edge.copy(
                                                edgeFlow = it.edge.edgeFlow.copy(dualBand = checked),
                                            ))
                                        }
                                    },
                                    shape = shape,
                                )
                            }
                            if (s.dualBand) {
                                item { shape ->
                                    LunoSwitchRow(
                                        title = stringResource(R.string.float_ball_capsule_alert_dual_color),
                                        subtitle = stringResource(R.string.float_ball_capsule_alert_dual_color_subtitle),
                                        checked = s.dualColor,
                                        onCheckedChange = { checked ->
                                            updateAlert(scope) {
                                                it.copy(edge = it.edge.copy(
                                                    edgeFlow = it.edge.edgeFlow.copy(dualColor = checked),
                                                ))
                                            }
                                        },
                                        shape = shape,
                                    )
                                }
                            }
                            if (s.dualBand && s.dualColor) {
                                item { shape ->
                                    val scheme = MaterialTheme.colorScheme
                                    LunoSettingsRow(
                                        title = stringResource(R.string.float_ball_capsule_alert_second_color),
                                        icon = Icons.Default.Palette,
                                        subtitle = stringResource(
                                            if (s.secondColor.isUnset) {
                                                R.string.float_ball_capsule_second_color_default
                                            } else {
                                                R.string.float_ball_capsule_color_custom
                                            },
                                        ),
                                        shape = shape,
                                        leading = {
                                            Box(
                                                modifier = Modifier
                                                    .size(MarkColorSize)
                                                    .background(
                                                        color = if (s.secondColor.isUnset) {
                                                            scheme.secondary
                                                        } else {
                                                            s.secondColor.toMarkColor()
                                                        },
                                                        shape = CircleShape,
                                                    ),
                                            )
                                        },
                                        onClick = { showSecondColorPicker = true },
                                    )
                                }
                            }
                        }
                    }
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_preview),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_preview_subtitle),
                            icon = Icons.Default.PlayArrow,
                            shape = shape,
                            onClick = { facades.system.previewFloatBallAlert(context, AlertCategory.Edge) },
                        )
                    }
                }
            }
        }
    }
}
