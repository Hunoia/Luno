package hunoia.luno.floatball.settings.alert

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.BuiltinAlertPalettes
import hunoia.luno.config.model.allPalettes
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreenLazy
import java.util.UUID
import kotlinx.coroutines.launch

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
    // 本地即显状态：从编辑页 / 管理页返回时跟着 resume 刷新（写盘不保证回推活跃订阅者）。
    val editor = rememberAlertEditor()
    val alert = editor.alert
    val palettes = alert.allPalettes()
    // 连点「新建」会建出两个调色板并连进两次编辑页：用 creating 收口。
    var creating by remember { mutableStateOf(false) }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_capsule_alert_palette_manage),
        onBack = onBack,
    ) {
        item(key = "builtin") {
            LunoGroup(
                title = stringResource(R.string.float_ball_capsule_alert_palette_builtin),
            ) {
                val rows = segmentedRows {
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
                SegmentedRows(rows)
            }
        }
        item(key = "custom") {
            LunoGroup(
                title = stringResource(R.string.float_ball_capsule_alert_palette_custom),
            ) {
                val rows = segmentedRows {
                    item { shape ->
                        val defaultName = stringResource(R.string.float_ball_capsule_alert_palette_default_name)
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_palette_new),
                            icon = Icons.Default.Add,
                            shape = shape,
                            onClick = {
                                if (!creating) {
                                    creating = true
                                    val newPalette = AlertPalette(
                                        id = "custom_${UUID.randomUUID()}",
                                        name = defaultName,
                                        colors = listOf(
                                            0xFFE53935.toInt(),
                                            0xFFFB8C00.toInt(),
                                            0xFF43A047.toInt(),
                                        ),
                                    )
                                    // 先等写盘完成并同步兜底缓存，再进编辑页：否则编辑页首帧读不到
                                    // 这个 id（写盘回推异步）会被判定为「已删除」直接弹回。
                                    scope.launch {
                                        persistAlertNow { it.copy(customPalettes = it.customPalettes + newPalette) }
                                        TuneConfig.syncActionSnapshot()
                                        onEditPalette(newPalette.id)
                                    }
                                }
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
                SegmentedRows(rows)
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
