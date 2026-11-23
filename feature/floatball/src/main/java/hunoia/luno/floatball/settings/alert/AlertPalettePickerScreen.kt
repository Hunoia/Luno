package hunoia.luno.floatball.settings.alert

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.config.model.AlertCategory
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.allPalettes
import hunoia.luno.config.model.paletteIdOf
import hunoia.luno.config.model.withPaletteId
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.screen.LunoScreenLazy

/**
 * 到达提示·调色板选择页：某分类从「跟随动效颜色」+ 内置/自定义调色板里单选。
 *
 * 选完即落盘并返回；「管理调色板」进管理页（选择页留在返回栈，回来还能继续挑）。
 */
@Composable
fun AlertPalettePickerScreen(
    category: AlertCategory,
    onBack: () -> Unit,
    onNavToPaletteManager: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAction())
    val alert = actionSettings.floatBall.notificationCapsule.alert
    val palettes = alert.allPalettes()
    val currentId = alert.paletteIdOf(category)

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_capsule_alert_palette_picker),
        onBack = onBack,
    ) {
        item(key = "pick") {
            LunoGroup(
                title = stringResource(category.titleRes()),
                subtitle = stringResource(R.string.float_ball_capsule_alert_palette_picker),
            ) {
                LunoSegmentedGroup {
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_palette_none),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_palette_none_subtitle),
                            shape = shape,
                            trailing = if (currentId == null) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                updateAlert(scope) { it.withPaletteId(category, null) }
                                onBack()
                            },
                        )
                    }
                    palettes.forEach { palette ->
                        item { shape ->
                            PalettePickerRow(
                                palette = palette,
                                selected = palette.id == currentId,
                                shape = shape,
                                onClick = {
                                    updateAlert(scope) { it.withPaletteId(category, palette.id) }
                                    onBack()
                                },
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
