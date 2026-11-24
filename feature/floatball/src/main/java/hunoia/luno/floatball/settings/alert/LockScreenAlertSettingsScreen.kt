package hunoia.luno.floatball.settings.alert

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.MomentAlertSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch

/**
 * 锁屏动画：执行「锁屏」动作前先播一份预设，压到全黑（最长 2.0s）再真正锁屏。
 *
 * 预设与到达提示 / 动作动效共用同一个库；强度 / 速度是锁屏这一类的独立一套。
 * 未开 / 未选预设 / 播不出时立即锁屏，不阻塞。
 */
@Composable
fun LockScreenAlertSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val facades = LocalFacades.current
    var alert by remember { mutableStateOf(TuneConfig.latestAction().floatBall.lockAlert) }
    var library by remember { mutableStateOf(TuneConfig.latestAction().floatBall.notificationCapsule.alertLibrary) }
    var showSheet by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        val ball = TuneConfig.latestAction().floatBall
        val lock = ball.lockAlert
        // 兼容旧配置：以前只能把锁屏动效绑在「动作动效」里（锁屏后播放，看不见）。
        // 打开本页时把它迁移到锁屏动画并清掉旧绑定，避免两处配置打架。
        val fallbackId = ball.actionAlert.takeIf { it.enabled }
            ?.presetFor("accessibility.lockScreen", null)
        if (lock.presetId.isNullOrBlank() && fallbackId != null) {
            alert = lock.copy(
                enabled = true,
                presetId = fallbackId,
                intensityPercent = ball.actionAlert.intensityPercent,
                speedPercent = ball.actionAlert.speedPercent,
            )
            scope.launch {
                TuneConfig.updateAction { current ->
                    val cur = current.floatBall
                    current.copy(
                        floatBall = cur.copy(
                            lockAlert = cur.lockAlert.copy(
                                enabled = true,
                                presetId = fallbackId,
                                intensityPercent = cur.actionAlert.intensityPercent,
                                speedPercent = cur.actionAlert.speedPercent,
                            ),
                            actionAlert = cur.actionAlert.withBinding(
                                typeId = "accessibility.lockScreen",
                                entryId = null,
                                presetId = null,
                            ),
                        ),
                    )
                }
                TuneConfig.syncActionSnapshot()
            }
        } else {
            alert = lock
        }
        library = ball.notificationCapsule.alertLibrary
        onPauseOrDispose { }
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun update(transform: (MomentAlertSettings) -> MomentAlertSettings) {
        alert = transform(alert)
        scope.launch {
            TuneConfig.updateAction { current ->
                current.copy(
                    floatBall = current.floatBall.copy(
                        lockAlert = transform(current.floatBall.lockAlert),
                    ),
                )
            }
        }
    }

    val selected: AlertPreset? = library.presetById(alert.presetId)

    LunoScreenLazy(
        title = stringResource(R.string.lock_alert_title),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        floatingContent = {
            LunoFab(
                onClick = {
                    when {
                        !alert.enabled -> toast(context.getString(R.string.lock_alert_preview_disabled))
                        selected == null || !selected.isPlayable() ->
                            toast(context.getString(R.string.lock_alert_preview_none))
                        else -> facades.system.previewFloatBallAlertPreset(
                            context,
                            selected,
                            intensityPercent = alert.intensityPercent,
                            speedPercent = alert.speedPercent,
                        )
                    }
                },
                icon = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.lock_alert_preview),
            )
        },
        sheets = {
            if (showSheet) {
                AlertPresetSelectSheet(
                    library = library,
                    currentId = alert.presetId,
                    onDismiss = { showSheet = false },
                    onSelect = { presetId ->
                        showSheet = false
                        update { it.copy(presetId = presetId) }
                    },
                    noneSubtitle = stringResource(R.string.lock_alert_unbound_hint),
                )
            }
        },
    ) {
        item(key = "basic") {
            val rows = segmentedRows {
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.lock_alert_enabled),
                        subtitle = stringResource(R.string.lock_alert_enabled_subtitle),
                        checked = alert.enabled,
                        shape = shape,
                        onCheckedChange = { checked -> update { it.copy(enabled = checked) } },
                    )
                }
                item { shape ->
                    AlertKnobSlider(
                        label = stringResource(R.string.lock_alert_intensity),
                        value = alert.intensityPercent,
                        range = LockIntensityRange,
                        display = { "${it.toInt()}%" },
                        shape = shape,
                        onCommit = { value -> update { it.copy(intensityPercent = value) } },
                    )
                }
                item { shape ->
                    AlertKnobSlider(
                        label = stringResource(R.string.lock_alert_speed),
                        value = alert.speedPercent,
                        range = LockSpeedRange,
                        display = { "${it.toInt()}%" },
                        shape = shape,
                        onCommit = { value -> update { it.copy(speedPercent = value) } },
                    )
                }
            }
            LunoGroup(
                title = stringResource(R.string.lock_alert_basic_group),
                subtitle = stringResource(R.string.lock_alert_basic_hint),
            ) {
                SegmentedRows(rows)
            }
        }
        item(key = "preset") {
            LunoGroup(
                title = stringResource(R.string.lock_alert_preset_group),
                subtitle = stringResource(R.string.lock_alert_preset_hint),
            ) {
                SegmentedRows(
                    segmentedRows {
                        item { shape ->
                            LunoSettingsRow(
                                title = stringResource(R.string.lock_alert_preset),
                                subtitle = when {
                                    selected != null -> selected.displayName()
                                    alert.presetId == null ->
                                        stringResource(R.string.lock_alert_preset_none)
                                    else -> stringResource(R.string.float_ball_alert_preset_missing)
                                },
                                icon = Icons.Default.PlayArrow,
                                shape = shape,
                                onClick = { showSheet = true },
                            )
                        }
                    },
                )
            }
        }
    }
}

private val LockIntensityRange =
    AlertLimits.IntensityMin..AlertLimits.IntensityMax
private val LockSpeedRange =
    AlertLimits.SpeedMin..AlertLimits.SpeedMax