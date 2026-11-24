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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.config.model.AlertCategory
import hunoia.luno.config.model.EdgeAlertStyle
import hunoia.luno.config.model.EdgeBladeRiseAlertSettings
import hunoia.luno.config.model.EdgeFireworksAlertSettings
import hunoia.luno.config.model.EdgeFlowAlertSettings
import hunoia.luno.config.model.EdgeGlowAlertSettings
import hunoia.luno.config.model.EdgeRippleAlertSettings
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
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.toMarkColor

/**
 * 到达提示·边缘类设置页：类目开关、类内单选样式（呼吸光 / 流光 / 波纹 / 烟花 /
 * 双刃上升）与各自旋钮。
 *
 * 行形状全部由 [LunoSegmentedGroup] 自动算（不再手数行数）；旋钮松手才落盘。
 * 「试播该分类」只播这一类，失败原因由服务侧 Toast 反馈。
 */
@Composable
fun AlertEdgeSettingsScreen(onBack: () -> Unit, onNavToPalette: () -> Unit = {}) {
    val facades = LocalFacades.current
    val context = LocalContext.current
    // 本地即显状态：用户操作（切样式 / 旋钮 / 开关）立即更新 UI，再异步落盘；
    // resume 时从真实配置再同步一次，兜底调色板页返回 / 外部写入（见 rememberAlertEditor）。
    val editor = rememberAlertEditor()
    val alert = editor.alert
    // settings 提到页面顶层：LunoScreen 的 content 是普通 Column（非懒重组），
    // 重组时整个 content lambda 重跑，when(settings.style) 才能跟着换分支。
    val settings = alert.edge
    var showSecondColorPicker by remember { mutableStateOf(false) }

    // 本地即显 + 异步落盘：transform 先应用到本地（UI 立即反映），再写回配置。
    fun updateAlert(transform: (NotificationAlertSettings) -> NotificationAlertSettings) =
        editor.update(transform)

    LunoScreen(
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
                        updateAlert {
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
                            updateAlert { it.copy(edge = it.edge.copy(enabled = checked)) }
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
                            updateAlert {
                                it.copy(edge = it.edge.copy(style = styles[index]))
                            }
                        },
                        shape = shape,
                    )
                }
                item { shape ->
                    AlertPaletteSettingsRow(
                        palette = alert.paletteFor(AlertCategory.Edge),
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                        updateAlert {
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
                                            updateAlert {
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
                    EdgeAlertStyle.EdgeRipple -> {
                        val s = settings.edgeRipple
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_edge_ripple_crests),
                                value = s.crests,
                                range = FloatBallDefaults.AlertEdgeRippleCrestsMin..
                                    FloatBallDefaults.AlertEdgeRippleCrestsMax,
                                display = { "${it.toInt()}" },
                                shape = shape,
                                onCommit = { v ->
                                    updateAlert { it.copy(edge = it.edge.copy(edgeRipple = it.edge.edgeRipple.copy(crests = v))) }
                                },
                            )
                        }
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_particle_size),
                                value = s.sizeDp,
                                range = FloatBallDefaults.AlertEdgeRippleSizeDpMin..
                                    FloatBallDefaults.AlertEdgeRippleSizeDpMax,
                                display = { "${it.toInt()} dp" },
                                shape = shape,
                                onCommit = { v ->
                                    updateAlert { it.copy(edge = it.edge.copy(edgeRipple = it.edge.edgeRipple.copy(sizeDp = v))) }
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
                                    updateAlert { it.copy(edge = it.edge.copy(edgeRipple = it.edge.edgeRipple.copy(cornerRadiusDp = v))) }
                                },
                            )
                        }
                        AlertSpeedBrightnessCycles(s.speed, s.brightness, s.cycles,
                            onSpeed = { v -> updateAlert { it.copy(edge = it.edge.copy(edgeRipple = it.edge.edgeRipple.copy(speed = v))) } },
                            onBrightness = { v -> updateAlert { it.copy(edge = it.edge.copy(edgeRipple = it.edge.edgeRipple.copy(brightness = v))) } },
                            onCycles = { v -> updateAlert { it.copy(edge = it.edge.copy(edgeRipple = it.edge.edgeRipple.copy(cycles = v))) } },
                        )
                    }
EdgeAlertStyle.EdgeFireworks -> {
                        val s = settings.edgeFireworks
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_edge_fireworks_bursts),
                                value = s.bursts,
                                range = FloatBallDefaults.AlertEdgeFireworksBurstsMin..
                                    FloatBallDefaults.AlertEdgeFireworksBurstsMax,
                                display = { "${it.toInt()}" },
                                shape = shape,
                                onCommit = { v ->
                                    updateAlert { it.copy(edge = it.edge.copy(edgeFireworks = it.edge.edgeFireworks.copy(bursts = v))) }
                                },
                            )
                        }
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_particle_size),
                                value = s.sizeDp,
                                range = FloatBallDefaults.AlertEdgeFireworksSizeDpMin..
                                    FloatBallDefaults.AlertEdgeFireworksSizeDpMax,
                                display = { "${it.toInt()} dp" },
                                shape = shape,
                                onCommit = { v ->
                                    updateAlert { it.copy(edge = it.edge.copy(edgeFireworks = it.edge.edgeFireworks.copy(sizeDp = v))) }
                                },
                            )
                        }
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_edge_fireworks_spread),
                                value = s.spreadDp,
                                range = FloatBallDefaults.AlertEdgeFireworksSpreadDpMin..
                                    FloatBallDefaults.AlertEdgeFireworksSpreadDpMax,
                                display = { "${it.toInt()} dp" },
                                shape = shape,
                                onCommit = { v ->
                                    updateAlert { it.copy(edge = it.edge.copy(edgeFireworks = it.edge.edgeFireworks.copy(spreadDp = v))) }
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
                                    updateAlert { it.copy(edge = it.edge.copy(edgeFireworks = it.edge.edgeFireworks.copy(cornerRadiusDp = v))) }
                                },
                            )
                        }
                        AlertSpeedBrightnessCycles(s.speed, s.brightness, s.cycles,
                            onSpeed = { v -> updateAlert { it.copy(edge = it.edge.copy(edgeFireworks = it.edge.edgeFireworks.copy(speed = v))) } },
                            onBrightness = { v -> updateAlert { it.copy(edge = it.edge.copy(edgeFireworks = it.edge.edgeFireworks.copy(brightness = v))) } },
                            onCycles = { v -> updateAlert { it.copy(edge = it.edge.copy(edgeFireworks = it.edge.edgeFireworks.copy(cycles = v))) } },
                        )
                    }
EdgeAlertStyle.EdgeBladeRise -> {
                        val s = settings.edgeBladeRise
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_edge_blade_width),
                                value = s.bladeWidthDp,
                                range = FloatBallDefaults.AlertEdgeBladeWidthDpMin..
                                    FloatBallDefaults.AlertEdgeBladeWidthDpMax,
                                display = { "${it.toInt()} dp" },
                                shape = shape,
                                onCommit = { v ->
                                    updateAlert { it.copy(edge = it.edge.copy(edgeBladeRise = it.edge.edgeBladeRise.copy(bladeWidthDp = v))) }
                                },
                            )
                        }
                        item { shape ->
                            AlertKnobSlider(
                                label = stringResource(R.string.float_ball_capsule_alert_edge_blade_height),
                                value = s.bladeHeightDp,
                                range = FloatBallDefaults.AlertEdgeBladeHeightDpMin..
                                    FloatBallDefaults.AlertEdgeBladeHeightDpMax,
                                display = { "${it.toInt()} dp" },
                                shape = shape,
                                onCommit = { v ->
                                    updateAlert { it.copy(edge = it.edge.copy(edgeBladeRise = it.edge.edgeBladeRise.copy(bladeHeightDp = v))) }
                                },
                            )
                        }
                        AlertSpeedBrightnessCycles(s.speed, s.brightness, s.cycles,
                            onSpeed = { v -> updateAlert { it.copy(edge = it.edge.copy(edgeBladeRise = it.edge.edgeBladeRise.copy(speed = v))) } },
                            onBrightness = { v -> updateAlert { it.copy(edge = it.edge.copy(edgeBladeRise = it.edge.edgeBladeRise.copy(brightness = v))) } },
                            onCycles = { v -> updateAlert { it.copy(edge = it.edge.copy(edgeBladeRise = it.edge.edgeBladeRise.copy(cycles = v))) } },
                        )
                    }
                    }
                    AlertResetStyleRow {
                        updateAlert {
                            it.copy(
                                edge = when (it.edge.style) {
                                    EdgeAlertStyle.EdgeGlow -> it.edge.copy(
                                        edgeGlow = EdgeGlowAlertSettings(),
                                    )
                                    EdgeAlertStyle.EdgeFlow -> it.edge.copy(
                                        edgeFlow = EdgeFlowAlertSettings(),
                                    )
                                    EdgeAlertStyle.EdgeRipple -> it.edge.copy(
                                        edgeRipple = EdgeRippleAlertSettings(),
                                    )
                                    EdgeAlertStyle.EdgeFireworks -> it.edge.copy(
                                        edgeFireworks = EdgeFireworksAlertSettings(),
                                    )
                                    EdgeAlertStyle.EdgeBladeRise -> it.edge.copy(
                                        edgeBladeRise = EdgeBladeRiseAlertSettings(),
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
                            onClick = { facades.system.previewFloatBallAlert(context, AlertCategory.Edge) },
                        )
                    }
                }
            }
        }
    }
}
