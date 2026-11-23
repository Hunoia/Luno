package hunoia.luno.floatball.settings.alert

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.BuiltinAlertPalettes
import hunoia.luno.config.model.allPalettes
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.screen.LunoScreenLazy

/**
 * 到达提示·调色板管理页：内置只读（缩略预览），自定义可点进编辑；「新建」追加一个
 * 默认 3 色自定义调色板并直接进编辑。删除在编辑页。
 */
@Composable
fun AlertPaletteManagerScreen(
    onBack: () -> Unit,
    onEditPalette: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAction())
    val alert = actionSettings.floatBall.notificationCapsule.alert
    val palettes = alert.allPalettes()

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_capsule_alert_palette_manage),
        onBack = onBack,
    ) {
        item(key = "builtin") {
            LunoGroup(
                title = stringResource(R.string.float_ball_capsule_alert_palette_builtin),
            ) {
                LunoSegmentedGroup {
                    BuiltinAlertPalettes.all.forEach { palette ->
                        item { shape ->
                            PaletteManagerRow(
                                palette = palette,
                                editable = false,
                                shape = shape,
                                onClick = null,
                            )
                        }
                    }
                }
            }
        }
        item(key = "custom") {
            LunoGroup(
                title = stringResource(R.string.float_ball_capsule_alert_palette_custom),
            ) {
                LunoSegmentedGroup {
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_palette_new),
                            icon = Icons.Default.Add,
                            shape = shape,
                            onClick = {
                                val newPalette = AlertPalette(
                                    id = "custom_${System.currentTimeMillis()}",
                                    name = "新调色板",
                                    colors = listOf(
                                        0xFFE53935.toInt(),
                                        0xFFFB8C00.toInt(),
                                        0xFF43A047.toInt(),
                                    ),
                                )
                                updateAlert(scope) {
                                    it.copy(customPalettes = it.customPalettes + newPalette)
                                }
                                onEditPalette(newPalette.id)
                            },
                        )
                    }
                    val custom = palettes.filter { it.id.startsWith("custom_") }
                    if (custom.isEmpty()) {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_capsule_alert_palette_empty),
                                shape = shape,
                            )
                        }
                    } else {
                        custom.forEach { palette ->
                            item { shape ->
                                PaletteManagerRow(
                                    palette = palette,
                                    editable = true,
                                    shape = shape,
                                    onClick = { onEditPalette(palette.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 管理页单行：缩略预览 + 名称 + 内置/自定义徽标 + 可编辑箭头。 */
@Composable
private fun PaletteManagerRow(
    palette: AlertPalette,
    editable: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: (() -> Unit)?,
) {
    LunoSettingsRow(
        title = palette.name,
        subtitle = if (palette.rainbow) {
            stringResource(R.string.float_ball_capsule_alert_palette_rainbow)
        } else {
            stringResource(R.string.float_ball_capsule_alert_palette_color_count, palette.colors.size)
        },
        shape = shape,
        leading = { PalettePreview(palette) },
        trailing = if (editable) {
            { androidx.compose.material3.Icon(Icons.Default.Edit, contentDescription = null) }
        } else {
            null
        },
        onClick = onClick,
    )
}
