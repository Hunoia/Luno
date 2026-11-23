package hunoia.luno.floatball.settings.alert

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertCategory
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.NotificationAlertSettings
import hunoia.luno.config.model.ParticleAlertStyle
import hunoia.luno.config.model.ParticleShape
import hunoia.luno.config.model.ParticleShapeSettings
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 到达提示·粒子类设置页：类目开关、类内单选运动（雨 / 喷泉 / 流星 / 彩带 / 雪花 /
 * 花瓣 / 气泡 / 星火 / 萤火）与旋钮。
 *
 * 粒子类的旋钮按**类目共享**（数量 / 大小 / 行程 / 速度 / 亮度 / 波次），
 * 只有运动差异项按所选运动显隐：摆幅仅顶部雨、重力仅顶部雨 / 星火、
 * 散开仅喷泉 / 星火、拖尾仅流星；
 * 形状对全部运动生效（每运动都接 `drawMote`）。这是 ui-guidelines §2.4
 * 里「每效果各存一组」的例外。
 */
@Composable
fun AlertParticleSettingsScreen(onBack: () -> Unit, onNavToPalette: () -> Unit = {}) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 本地即显状态：用户操作（切运动 / 旋钮 / 开关）立即更新 UI，再异步落盘；
    // resume 时从真实配置再同步一次，兜底调色板页返回 / 外部写入（见 rememberAlertEditor）。
    val editor = rememberAlertEditor()
    val alert = editor.alert
    // settings 提到页面顶层：LunoScreen 的 content 是普通 Column（非懒重组），
    // 重组时整个 content lambda 重跑，when(settings.style) 才能跟着换分支。
    val settings = alert.particle

    // 本地即显 + 异步落盘：transform 先应用到本地（UI 立即反映），再写回配置。
    fun updateAlert(transform: (NotificationAlertSettings) -> NotificationAlertSettings) =
        editor.update(transform)

    LunoScreen(
        title = stringResource(AlertCategory.Particle.titleRes()),
        onBack = onBack,
    ) {
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
                            updateAlert { it.copy(particle = it.particle.copy(enabled = checked)) }
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
                            updateAlert {
                                it.copy(particle = it.particle.copy(style = styles[index]))
                            }
                        },
                        shape = shape,
                    )
                }
                item { shape ->
                    AlertPaletteSettingsRow(
                        palette = alert.paletteFor(AlertCategory.Particle),
                        fallbackColor = alert.color.toMarkColor(),
                        shape = shape,
                        onClick = onNavToPalette,
                    )
                }
                item { shape ->
                    val shapes = ParticleShape.entries
                    LunoDropdownRow(
                        title = stringResource(R.string.float_ball_capsule_alert_particle_shape),
                        options = shapes.map {
                            stringResource(when (it) {
                                ParticleShape.Dot -> R.string.float_ball_capsule_alert_particle_shape_dot
                                ParticleShape.CustomImage -> R.string.float_ball_capsule_alert_particle_shape_image
                                ParticleShape.AppIcon -> R.string.float_ball_capsule_alert_particle_shape_app_icon
                            })
                        },
                        selectedIndex = shapes.indexOf(settings.shape.shape).coerceAtLeast(0),
                        onSelect = { index ->
                            updateAlert {
                                it.copy(particle = it.particle.copy(
                                    shape = it.particle.shape.copy(shape = shapes[index]),
                                ))
                            }
                        },
                        shape = shape,
                    )
                }
                if (settings.shape.shape == ParticleShape.CustomImage) {
                    item { shape ->
                        val pickImage = rememberLauncherForActivityResult(
                            ActivityResultContracts.GetContent(),
                        ) { uri ->
                            if (uri != null) {
                                // GetContent 授权只在当前任务存活，立即复制到 filesDir 落盘。
                                scope.launch {
                                    val copied = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val dir = File(
                                                context.filesDir,
                                                ParticleShapeSettings.ImageDirName,
                                            )
                                            if (!dir.exists() && !dir.mkdirs()) return@runCatching null
                                            val name = "shape_${System.currentTimeMillis()}.png"
                                            val dst = File(dir, name)
                                            val ok = context.contentResolver
                                                .openInputStream(uri)
                                                ?.use { input ->
                                                    dst.outputStream().use { output -> input.copyTo(output) }
                                                } != null
                                            if (ok) {
                                                name
                                            } else {
                                                runCatching { dst.delete() }
                                                null
                                            }
                                        }.getOrNull()
                                    }
                                    if (copied != null) {
                                        // 只存文件名（换机备份仍可判定失效）；顺手删掉上一张，不留孤儿文件。
                                        val previous = editor.alert.particle.shape.customImage
                                        updateAlert {
                                            it.copy(particle = it.particle.copy(
                                                shape = it.particle.shape.copy(customImage = copied),
                                            ))
                                        }
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                if (previous.isNotBlank() && previous != copied &&
                                                    !previous.contains('/')
                                                ) {
                                                    File(
                                                        File(context.filesDir, ParticleShapeSettings.ImageDirName),
                                                        previous,
                                                    ).delete()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_particle_shape_image_pick),
                            icon = Icons.Default.Refresh,
                            shape = shape,
                            onClick = { pickImage.launch("image/*") },
                        )
                    }
                }
            }
        }
        key(settings.style) {
            LunoGroup {
                LunoSegmentedGroup {
                    // 粒子类旋钮按类目共享（数量 / 大小 / 摆幅 / 重力 / 行程 / 速度 / 亮度 / 波次）：
                    // 所有运动共用同一组旋钮，差异只在渲染层（ui-guidelines §2.4 例外）。
                    run {
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
                                    updateAlert {
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
                                    updateAlert {
                                        it.copy(particle = it.particle.copy(
                                            particles = it.particle.particles.copy(sizeScale = v),
                                        ))
                                    }
                                },
                            )
                        }
                        // 运动差异项按所选运动显隐：摆幅仅顶部雨、重力仅顶部雨/星火/喷泉、
                        // 散开仅喷泉/星火、拖尾仅流星；对当前运动无效的旋钮不显示，避免「设了没反应」。
                        if (settings.style == ParticleAlertStyle.TopRain) {
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_sway),
                                    value = s.swayScale,
                                    range = FloatBallDefaults.AlertParticlesSwayScaleMin..
                                        FloatBallDefaults.AlertParticlesSwayScaleMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(swayScale = v),
                                            ))
                                        }
                                    },
                                )
                            }
                        }
                        if (settings.style == ParticleAlertStyle.TopRain ||
                            settings.style == ParticleAlertStyle.Spark
                        ) {
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_gravity),
                                    value = s.gravity,
                                    range = FloatBallDefaults.AlertParticlesGravityMin..
                                        FloatBallDefaults.AlertParticlesGravityMax,
                                    display = { "${it.toInt()}" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(gravity = v),
                                            ))
                                        }
                                    },
                                )
                            }
                        }
                        // 散开幅度只对底部喷泉 / 星火生效（横向 / 径向散开基准）；拖尾长度只对流星。
                        if (settings.style == ParticleAlertStyle.Spark ||
                            settings.style == ParticleAlertStyle.BottomFountain
                        ) {
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_spread),
                                    value = s.spreadScale,
                                    range = FloatBallDefaults.AlertParticlesSpreadScaleMin..
                                        FloatBallDefaults.AlertParticlesSpreadScaleMax,
                                    display = { "${it.toInt()}%" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(spreadScale = v),
                                            ))
                                        }
                                    },
                                )
                            }
                        }
                        if (settings.style == ParticleAlertStyle.Meteor) {
                            item { shape ->
                                AlertKnobSlider(
                                    label = stringResource(R.string.float_ball_capsule_alert_particle_tail),
                                    value = s.tailLengthDp,
                                    range = FloatBallDefaults.AlertParticlesTailLengthDpMin..
                                        FloatBallDefaults.AlertParticlesTailLengthDpMax,
                                    display = { "${it.toInt()} dp" },
                                    shape = shape,
                                    onCommit = { v ->
                                        updateAlert {
                                            it.copy(particle = it.particle.copy(
                                                particles = it.particle.particles.copy(tailLengthDp = v),
                                            ))
                                        }
                                    },
                                )
                            }
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
                                    updateAlert {
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
                                    updateAlert {
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
                                    updateAlert {
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
                                    updateAlert {
                                        it.copy(particle = it.particle.copy(
                                            particles = it.particle.particles.copy(waves = v),
                                        ))
                                    }
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
                            onClick = { facades.system.previewFloatBallAlert(context, AlertCategory.Particle) },
                        )
                    }
                }
            }
        }
    }
}
