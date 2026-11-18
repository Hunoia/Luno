package hunoia.luno.floatball.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.bridge.system.gotoOverlaySettings
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.FloatBallDefaults
import hunoia.luno.config.model.FloatBallSettings
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.component.screen.LunoScreenLazy
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch

/**
 * 结果胶囊设置页：只管**胶囊自己**（跟手 / 静置 / 停留时长 / 队列 / 图标）。
 *
 * 规则表、来源读取开关、直执行提示开关都在分发规则页（`RecognitionSettingsScreen`）：
 * 结果胶囊是分发规则的**结果显示面**，没有自己的开关，也不拥有一份自己的规则表。
 */
@Composable
fun ResultCapsuleSettingsScreen(
    onBack: () -> Unit,
    onNavToRecognition: () -> Unit = {},
) {
    val facades = LocalFacades.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val actionSettings by TuneConfig.action
        .collectAsStateWithLifecycle(initialValue = ActionSettings())
    val floatBall = actionSettings.floatBall
    val capsule = floatBall.resultCapsule

    var localAutoHide by remember(capsule.autoHideMs) {
        mutableFloatStateOf(capsule.autoHideMs.toFloat())
    }
    var localIdleDim by remember(capsule.idleDimDelayMs) {
        mutableFloatStateOf(capsule.idleDimDelayMs.toFloat())
    }
    var localTtl by remember(floatBall.inbox.clipboardTtlMs) {
        mutableFloatStateOf(floatBall.inbox.clipboardTtlMs.toFloat())
    }
    var localCapacity by remember(floatBall.inbox.resultCapacity, floatBall.inbox.capacity) {
        mutableFloatStateOf((floatBall.inbox.resultCapacity ?: floatBall.inbox.capacity).toFloat())
    }

    var overlayGranted by remember { mutableStateOf(context.canDrawOverlays()) }
    // 结果球自己的图标：选图 → 裁剪 → 落盘，不跟随主球。
    val iconPicker = rememberBallIconPickerState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = context.canDrawOverlays()
                if (granted != overlayGranted) {
                    overlayGranted = granted
                    facades.system.refreshFloatBall(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun update(transform: (FloatBallSettings) -> FloatBallSettings) {
        scope.launch {
            TuneConfig.updateAction { current ->
                current.copy(floatBall = transform(current.floatBall))
            }
        }
    }

    LunoScreenLazy(
        title = stringResource(R.string.float_ball_result_group),
        onBack = onBack,
        state = listState,
        bottomContentPadding = FloatingContentBottom,
        sheets = {
            BallCustomIconSheet(
                context = context,
                picker = iconPicker,
                current = capsule.customIcon,
                onChange = { icon ->
                    update { it.copy(resultCapsule = it.resultCapsule.copy(customIcon = icon)) }
                },
            )
        },
    ) {
        if (!overlayGranted) {
            item(key = "permission") {
                LunoGroup {
                    LunoSettingsRow(
                        title = stringResource(R.string.float_ball_permission_required),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        shape = segmentedShape(0, 1),
                        onClick = {
                            context.gotoOverlaySettings()
                            overlayGranted = context.canDrawOverlays()
                        },
                    )
                }
            }
        }

        item(key = "behavior") {
            LunoGroup(title = stringResource(R.string.float_ball_result_group)) {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_result_follow_hand),
                    subtitle = stringResource(R.string.float_ball_result_follow_hand_subtitle),
                    checked = capsule.followHand,
                    onCheckedChange = { checked ->
                        update {
                            it.copy(resultCapsule = it.resultCapsule.copy(followHand = checked))
                        }
                    },
                    shape = segmentedShape(0, 5),
                )
                LunoTextSlider(
                    value = localIdleDim,
                    onValueChange = { value -> localIdleDim = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                resultCapsule = it.resultCapsule.copy(
                                    idleDimDelayMs = localIdleDim.toLong(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_idle_dim_delay),
                    valueDisplay = { if (it <= 0f) stringResource(R.string.float_ball_off) else "${it.toInt()} ms" },
                    valueRange = FloatBallDefaults.IdleDimDelayMsMin.toFloat()..
                        FloatBallDefaults.IdleDimDelayMsMax.toFloat(),
                    shape = segmentedShape(1, 5),
                )
                LunoTextSlider(
                    value = localAutoHide,
                    onValueChange = { value -> localAutoHide = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                resultCapsule = it.resultCapsule.copy(
                                    autoHideMs = localAutoHide.toLong(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_capsule_auto_hide),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.ClipboardBallAutoHideMsMin.toFloat()..
                        FloatBallDefaults.ClipboardBallAutoHideMsMax.toFloat(),
                    shape = segmentedShape(2, 5),
                )
                LunoTextSlider(
                    value = localTtl,
                    onValueChange = { value -> localTtl = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(inbox = it.inbox.copy(clipboardTtlMs = localTtl.toLong()))
                        }
                    },
                    text = stringResource(R.string.float_ball_result_ttl),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.InboxResultTtlMsMin.toFloat()..
                        FloatBallDefaults.InboxResultTtlMsMax.toFloat(),
                    shape = segmentedShape(3, 5),
                )
                LunoTextSlider(
                    value = localCapacity,
                    onValueChange = { value -> localCapacity = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                inbox = it.inbox.copy(
                                    resultCapacity = localCapacity.toInt(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_result_capacity),
                    valueDisplay = { "${it.toInt()}" },
                    valueRange = FloatBallDefaults.InboxCapacityMin.toFloat()..
                        FloatBallDefaults.InboxCapacityMax.toFloat(),
                    shape = segmentedShape(4, 5),
                )
            }
        }

        item(key = "icon") {
            val hasIcon = capsule.customIcon.path != null
            LunoGroup(title = stringResource(R.string.float_ball_appearance)) {
                BallCustomIconRows(
                    context = context,
                    icon = capsule.customIcon,
                    startIndex = 0,
                    rowCount = if (hasIcon) 2 else 1,
                    onPick = iconPicker::pick,
                    onReset = {
                        resetBallIcon(scope, capsule.customIcon) { icon ->
                            update { it.copy(resultCapsule = it.resultCapsule.copy(customIcon = icon)) }
                        }
                    },
                )
            }
        }

        // 规则表、来源读取开关、情景识别、直执行提示统一在分发规则页：它们都是「规则」的事，
        // 挂在这页会让「按来源配置」散在两处。
        item(key = "recognition") {
            LunoGroup {
                LunoSettingsRow(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.float_ball_result_recognition),
                    subtitle = stringResource(R.string.float_ball_result_recognition_hint),
                    shape = segmentedShape(0, 1),
                    onClick = onNavToRecognition,
                )
            }
        }
    }
}
