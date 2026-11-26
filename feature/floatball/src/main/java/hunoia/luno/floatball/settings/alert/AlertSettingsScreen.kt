package hunoia.luno.floatball.settings.alert

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.page.LunoPage
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch

/**
 * 到达提示动效（消费方设置页）：开关 / 单选预设 / 全局强度与速度 / 行为。
 *
 * 预设库与调色板是**总设置与管理**，放在设置根目录；本页不重复它们的入口。
 * 预设选择走底部 sheet（36 内置 + 我的，需要搜索），不在本页展开长列表。
 * 试播为唯一主操作，固定右下 FAB：整页滚动时都能随时点。
 */
@Composable
fun AlertSettingsScreen(
    onBack: () -> Unit,
    onNavToAlertBlocklist: () -> Unit,
) {
    val editor = rememberAlertEditor()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val facades = LocalFacades.current
    var showPresetSheet by remember { mutableStateOf(false) }

    val alert = editor.alert
    val library = editor.library
    val selected = alert.selectedPreset(library)
    val playable = alert.enabled && (alert.randomize || selected != null)

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    LunoPage(title = stringResource(R.string.float_ball_alert_settings_title),
onBack = onBack,
floatingContent = {
            LunoFab(
                onClick = {
                    if (playable) {
                        facades.system.previewFloatBallAlert(context)
                    } else {
                        toast(
                            resources.getString(
                                if (!alert.enabled) {
                                    R.string.float_ball_alert_preview_disabled
                                } else {
                                    R.string.float_ball_alert_preview_none
                                },
                            ),
                        )
                    }
                },
                icon = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.float_ball_capsule_alert_preview_all),
                containerColor = if (playable) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
        },
sheets = {
            if (showPresetSheet) {
                AlertPresetSelectSheet(
                    library = library,
                    currentId = alert.presetId,
                    onDismiss = { showPresetSheet = false },
                    onSelect = { presetId ->
                        showPresetSheet = false
                        scope.launch { editor.commitAlert { it.copy(presetId = presetId) } }
                    },
                )
            }
        }) {
    list(bottomContentPadding = FloatingContentBottom) {
        lazy {

        item(key = "alert") {
            val rows = segmentedRows {
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_capsule_alert_enabled),
                        subtitle = stringResource(R.string.float_ball_capsule_alert_enabled_subtitle),
                        checked = alert.enabled,
                        shape = shape,
                        onCheckedChange = { checked ->
                            editor.update { it.copy(enabled = checked) }
                        },
                    )
                }
                item { shape ->
                    LunoSettingsRow(
                        title = stringResource(R.string.float_ball_alert_preset),
                        subtitle = when {
                            selected != null -> selected.displayName()
                            alert.presetId == null ->
                                stringResource(R.string.float_ball_alert_preset_none)
                            else -> stringResource(R.string.float_ball_alert_preset_missing)
                        },
                        shape = shape,
                        onClick = { showPresetSheet = true },
                    )
                }
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_alert_randomize),
                        subtitle = stringResource(R.string.float_ball_alert_randomize_hint),
                        checked = alert.randomize,
                        shape = shape,
                        onCheckedChange = { checked ->
                            editor.update { it.copy(randomize = checked) }
                        },
                    )
                }
                item { shape ->
                    AlertKnobSlider(
                        label = stringResource(R.string.float_ball_alert_intensity),
                        value = alert.intensityPercent,
                        range = AlertIntensityRange,
                        display = { "${it.toInt()}%" },
                        shape = shape,
                        onCommit = { value ->
                            editor.update { it.copy(intensityPercent = value) }
                        },
                    )
                }
                item { shape ->
                    AlertKnobSlider(
                        label = stringResource(R.string.float_ball_alert_speed),
                        value = alert.speedPercent,
                        range = AlertSpeedRange,
                        display = { "${it.toInt()}%" },
                        shape = shape,
                        onCommit = { value ->
                            editor.update { it.copy(speedPercent = value) }
                        },
                    )
                }
            }
            LunoGroup(title = stringResource(R.string.float_ball_alert_basic_group)) {
                SegmentedRows(rows)
            }
        }
        item(key = "behavior") {
            val rows = segmentedRows {
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_capsule_alert_play_every),
                        subtitle = stringResource(R.string.float_ball_capsule_alert_play_every_subtitle),
                        checked = alert.playOnEveryNotification,
                        shape = shape,
                        onCheckedChange = { checked ->
                            editor.update { it.copy(playOnEveryNotification = checked) }
                        },
                    )
                }
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.float_ball_alert_read_text),
                        subtitle = stringResource(R.string.float_ball_alert_read_text_subtitle),
                        checked = alert.readNotificationText,
                        shape = shape,
                        onCheckedChange = { checked ->
                            editor.update { it.copy(readNotificationText = checked) }
                        },
                    )
                }
                item { shape ->
                    LunoSettingsRow(
                        title = stringResource(R.string.float_ball_capsule_alert_blocklist),
                        icon = Icons.Default.Block,
                        subtitle = stringResource(
                            R.string.float_ball_capsule_alert_blocklist_count,
                            alert.appBlocklist.size,
                        ),
                        shape = shape,
                        onClick = onNavToAlertBlocklist,
                    )
                }
            }
            LunoGroup(
                title = stringResource(R.string.float_ball_alert_behavior_group),
                subtitle = stringResource(R.string.float_ball_alert_behavior_hint),
            ) {
                SegmentedRows(rows)
            }
        }

        }
    }
}
}

private val AlertIntensityRange =
    AlertLimits.IntensityMin..AlertLimits.IntensityMax
private val AlertSpeedRange =
    AlertLimits.SpeedMin..AlertLimits.SpeedMax