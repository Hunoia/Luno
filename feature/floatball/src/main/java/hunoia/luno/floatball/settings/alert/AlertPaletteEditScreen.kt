package hunoia.luno.floatball.settings.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.clipboard.ClipboardCenter
import hunoia.luno.config.model.AlertPalette
import hunoia.luno.config.model.ColorSource
import hunoia.luno.config.model.ColorSpec
import hunoia.luno.config.model.removePalette
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSegmentedGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.theme.LargeShape

/**
 * 到达提示·调色板编辑页（自定义）：名称、彩虹开关、逐色编辑（点色块取色）、
 * 增删颜色（1–5 色）与删除调色板。删除会清空引用它的分类的 `paletteId`。
 */
@Composable
fun AlertPaletteEditScreen(
    paletteId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = TuneConfig.latestAction())
    val alert = actionSettings.floatBall.notificationCapsule.alert
    val palette = alert.customPalettes.firstOrNull { it.id == paletteId }
    if (palette == null) {
        // 已被删（如删除后返回）：直接回管理页。
        onBack()
        return
    }
    var editingColorIndex by remember { mutableStateOf(-1) }

    fun updatePalette(transform: (hunoia.luno.config.model.AlertPalette) -> hunoia.luno.config.model.AlertPalette) {
        updateAlert(scope) {
            it.copy(
                customPalettes = it.customPalettes.map { p ->
                    if (p.id == paletteId) transform(p) else p
                },
            )
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_capsule_alert_palette_manage),
        onBack = onBack,
        sheets = {
            if (editingColorIndex >= 0) {
                val scheme = MaterialTheme.colorScheme
                val currentArgb = palette.colors.getOrNull(editingColorIndex) ?: 0xFFFFFFFF.toInt()
                ColorPickerBottomSheet(
                    onDismissRequest = { editingColorIndex = -1 },
                    onCopyColor = { hex -> ClipboardCenter.writeTextImmediate(hex, "Luno color") },
                    initialSelection = ColorSpec(
                        source = ColorSource.Custom,
                        customColor = currentArgb,
                    ).toInitialSelection(scheme = scheme, primaryFallback = false),
                    onColorSelected = { selection ->
                        val argb = selection.toColorSpec(scheme).customColor
                        updatePalette { p ->
                            p.copy(colors = p.colors.toMutableList().also { it[editingColorIndex] = argb })
                        }
                        editingColorIndex = -1
                    },
                )
            }
        },
    ) {
        item(key = "name") {
            OutlinedTextField(
                value = palette.name,
                onValueChange = { name -> updatePalette { it.copy(name = name) } },
                label = { Text(stringResource(R.string.float_ball_capsule_alert_palette_name)) },
                singleLine = true,
                shape = LargeShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }
        item(key = "flags") {
            LunoGroup {
                LunoSegmentedGroup {
                    item { shape ->
                        LunoSwitchRow(
                            title = stringResource(R.string.float_ball_capsule_alert_palette_rainbow),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_palette_rainbow_subtitle),
                            checked = palette.rainbow,
                            onCheckedChange = { checked -> updatePalette { it.copy(rainbow = checked) } },
                            shape = shape,
                        )
                    }
                }
            }
        }
        item(key = "colors") {
            LunoGroup(
                title = stringResource(R.string.float_ball_capsule_alert_palette_colors),
                subtitle = stringResource(R.string.float_ball_capsule_alert_palette_color_limit),
            ) {
                LunoSegmentedGroup {
                    palette.colors.take(5).forEachIndexed { index, argb ->
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(
                                    R.string.float_ball_capsule_alert_palette_color_count,
                                    index + 1,
                                ),
                                subtitle = "#%08X".format(argb),
                                shape = shape,
                                leading = {
                                    Row(
                                        modifier = Modifier
                                            .size(PaletteDotSize)
                                            .border(1.dp, Color(argb).copy(alpha = 0.3f), CircleShape)
                                            .background(Color(argb), CircleShape),
                                    ) {}
                                },
                                onClick = { editingColorIndex = index },
                            )
                        }
                    }
                    if (palette.colors.size < 5) {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.float_ball_capsule_alert_palette_add_color),
                                icon = Icons.Default.Add,
                                shape = shape,
                                onClick = {
                                    updatePalette {
                                        it.copy(colors = it.colors + 0xFFE53935.toInt())
                                    }
                                },
                            )
                        }
                    }
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.float_ball_capsule_alert_palette_delete),
                            subtitle = stringResource(R.string.float_ball_capsule_alert_palette_delete_subtitle),
                            icon = Icons.Default.Delete,
                            shape = shape,
                            onClick = {
                                updateAlert(scope) { it.removePalette(paletteId) }
                                onBack()
                            },
                        )
                    }
                }
            }
        }
    }
}
