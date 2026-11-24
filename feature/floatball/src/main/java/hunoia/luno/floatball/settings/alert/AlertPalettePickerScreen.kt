package hunoia.luno.floatball.settings.alert

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.AlertSlot
import hunoia.luno.config.model.allPalettes
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreenLazy
import kotlinx.coroutines.launch

/**
 * 到达提示·调色板选择页（按槽位）：从「跟随预设颜色」+ 内置 / 素材库调色板里单选。
 *
 * 素材库与 `alert` 子树平级（`notificationCapsule.alertLibrary`），v3 重置不会清掉。
 * 选完即落盘并返回；「管理调色板」进管理页。
 */
@Composable
fun AlertPalettePickerScreen(
    slot: AlertSlot,
    onBack: () -> Unit,
    onNavToPaletteManager: () -> Unit,
) {
    val editor = rememberAlertEditor()
    val overrides = editor.alert.slotSettings(slot).overrides
    val palettes = editor.library.allPalettes()
    val currentId = overrides.paletteId
    val scope = rememberCoroutineScope()

    var leaving by remember { mutableStateOf(false) }
    fun commitAndBack(paletteId: String?) {
        if (leaving) return
        leaving = true
        scope.launch {
            persistAlertNow {
                it.withSlot(slot) { s ->
                    s.withOverrides { o -> o.copy(paletteId = paletteId, colors = emptyList()) }
                }
            }
            TuneConfig.syncActionSnapshot()
            onBack()
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_capsule_alert_palette_picker),
        onBack = {
            if (!leaving) {
                leaving = true
                onBack()
            }
        },
    ) {
        item(key = "pick") {
            LunoGroup(
                title = stringResource(slot.titleRes()),
                subtitle = stringResource(R.string.float_ball_capsule_alert_palette_picker),
            ) {
                val rows = segmentedRows {
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_palette_none),
                            subtitle = stringResource(R.string.float_ball_alert_palette_none_subtitle),
                            shape = shape,
                            trailing = if (currentId == null) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = { commitAndBack(null) },
                        )
                    }
                    palettes.forEach { palette ->
                        item { shape ->
                            PalettePickerRow(
                                palette = palette,
                                selected = palette.id == currentId,
                                shape = shape,
                                onClick = { commitAndBack(palette.id) },
                            )
                        }
                    }
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_palette_manage),
                            icon = Icons.Default.Add,
                            shape = shape,
                            onClick = onNavToPaletteManager,
                        )
                    }
                }
                SegmentedRows(rows)
            }
        }
    }
}

/** 单行调色板：缩略预览 + 名称 + 色数徽标 + 选中勾。 */
@Composable
private fun PalettePickerRow(
    palette: AlertPalette,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
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
        trailing = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else {
            null
        },
        onClick = onClick,
    )
}