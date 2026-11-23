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
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.ParticleAlertStyle
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
 * 到达提示·粒子类设置页：类目开关、类内单选运动（① 只有顶部雨）与旋钮。
 *
 * 粒子类的旋钮按**类目共享**（数量 / 大小 / 摆幅 / 重力 / 行程 / 速度 / 亮度 / 波次），
 * 新增运动只追加自己的差异项——这是 ui-guidelines §2.4 里「每效果各存一组」的例外。
 */
@Composable
fun AlertParticleSettingsScreen(onBack: () -> Unit) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val settings = actionSettings.floatBall.notificationCapsule.alert.particle

    LunoScreenLazy(
        title = stringResource(AlertCategory.Particle.titleRes()),
        onBack = onBack,
    ) {
        item(key = "particle") {
            LunoGroup(
                title = stringResource(AlertCategory.Particle.titleRes()),
                subtitle = stringResource(AlertCategory.Particle.subtitleRes()),
            ) {
                LunoSegmentedGroup {
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_capsule_alert_category_enabled),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_category_enabled_subtitle),
                            checked = settings.enabled,
                            onCheckedChange = { checked ->
                                updateAlert(scope) { it.copy(particle = it.particle.copy(enabled = checked)) }
                            },
                            shape = shape,
                        )
                    }
                    item { shape ->
                        val styles = ParticleAlertStyle.entries
                        LunoDropdownRow(
                            title = stringResource(R.string.float_ball_capsule_alert_style),
                            options = styleLabels(styles),
                            selectedIndex = styles.indexOf(settings.style).coerceAtLeast(0),
                            onSelect = { index ->
                                updateAlert(scope) {
                                    it.copy(particle = it.particle.copy(style = styles[index]))
                                }
                            },
                            shape = shape,
                        )
                    }
                    when (settings.style) {
                        ParticleAlertStyle.TopRain -> {
                            val s = settings.particles
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_count),
                                    value = s.count,
                                    range = FloatBallDefaults.AlertParticlesCountMin..
                                        FloatBallDefaults.AlertParticlesCountMax,
                                    display = { it.toInt().toString() },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(count = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_size),
                                    value = s.sizeScale,
                                    range = FloatBallDefaults.AlertParticlesSizeScaleMin..
                                        FloatBallDefaults.AlertParticlesSizeScaleMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(sizeScale = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_sway),
                                    value = s.swayScale,
                                    range = FloatBallDefaults.AlertParticlesSwayScaleMin..
                                        FloatBallDefaults.AlertParticlesSwayScaleMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(swayScale = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_gravity),
                                    value = s.gravity,
                                    range = FloatBallDefaults.AlertParticlesGravityMin..
                                        FloatBallDefaults.AlertParticlesGravityMax,
                                    display = { "${it.toInt()}" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(gravity = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_travel),
                                    value = s.travelDp,
                                    range = FloatBallDefaults.AlertParticlesTravelDpMin..
                                        FloatBallDefaults.AlertParticlesTravelDpMax,
                                    display = { "${it.toInt()} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(travelDp = v),
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
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(speed = v),
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
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(brightness = v),
                                            ))
                                        }
                                    },
                                )
                            }
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_cycles),
                                    value = s.waves,
                                    range = FloatBallDefaults.AlertCyclesMin..
                                        FloatBallDefaults.AlertCyclesMax,
                                    display = { "${it.toInt()} ×" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert(scope) {
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(waves = v),
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
                            onClick = { facades.system.previewFloatBallAlert(context, AlertCategory.Particle) },
                        )
                    }
                }
            }
        }
    }
}
