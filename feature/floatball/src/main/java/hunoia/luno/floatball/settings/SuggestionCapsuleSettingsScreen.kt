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
 * 建议胶囊设置页：只管**胶囊自己**（显示 / 跟手 / 停留时长 / 队列 / 图标）。
 *
 * 识别规则、链接分发、情景识别都在识别器页（`RecognitionSettingsScreen`）：
 * 胶囊只负责把识别结果显示出来，不再拥有一份自己的规则表。
 */
@Composable
fun SuggestionCapsuleSettingsScreen(
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
    val clipball = floatBall.clipboardBall

    var localAutoHide by remember(clipball.autoHideMs) {
        mutableFloatStateOf(clipball.autoHideMs.toFloat())
    }
    var localIdleDim by remember(clipball.idleDimDelayMs) {
        mutableFloatStateOf(clipball.idleDimDelayMs.toFloat())
    }
    var localTtl by remember(floatBall.inbox.clipboardTtlMs) {
        mutableFloatStateOf(floatBall.inbox.clipboardTtlMs.toFloat())
    }
    var localCapacity by remember(floatBall.inbox.suggestionCapacity, floatBall.inbox.capacity) {
        mutableFloatStateOf((floatBall.inbox.suggestionCapacity ?: floatBall.inbox.capacity).toFloat())
    }

    var overlayGranted by remember { mutableStateOf(context.canDrawOverlays()) }
    // 建议球自己的图标：选图 → 裁剪 → 落盘，不跟随主球。
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
        title = stringResource(R.string.float_ball_suggestion_group),
        onBack = onBack,
        state = listState,
        bottomContentPadding = FloatingContentBottom,
        sheets = {
            BallCustomIconSheet(
                context = context,
                picker = iconPicker,
                current = clipball.customIcon,
                onChange = { icon ->
                    update { it.copy(clipboardBall = it.clipboardBall.copy(customIcon = icon)) }
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
            LunoGroup(title = stringResource(R.string.float_ball_suggestion_group)) {
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_suggestion_enabled),
                    subtitle = stringResource(R.string.float_ball_suggestion_enabled_subtitle),
                    checked = clipball.enabled,
                    onCheckedChange = { checked ->
                        update { it.copy(clipboardBall = it.clipboardBall.copy(enabled = checked)) }
                        facades.system.setFloatBallEnabled(context, checked)
                    },
                    shape = segmentedShape(0, 6),
                )
                LunoSwitchRow(
                    title = stringResource(R.string.float_ball_suggestion_follow_hand),
                    subtitle = stringResource(R.string.float_ball_suggestion_follow_hand_subtitle),
                    checked = clipball.followHand,
                    onCheckedChange = { checked ->
                        update {
                            it.copy(clipboardBall = it.clipboardBall.copy(followHand = checked))
                        }
                    },
                    shape = segmentedShape(1, 6),
                )
                LunoTextSlider(
                    value = localIdleDim,
                    onValueChange = { value -> localIdleDim = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                clipboardBall = it.clipboardBall.copy(
                                    idleDimDelayMs = localIdleDim.toLong(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_idle_dim_delay),
                    valueDisplay = { if (it <= 0f) stringResource(R.string.float_ball_off) else "${it.toInt()} ms" },
                    valueRange = FloatBallDefaults.IdleDimDelayMsMin.toFloat()..
                        FloatBallDefaults.IdleDimDelayMsMax.toFloat(),
                    shape = segmentedShape(2, 6),
                )
                LunoTextSlider(
                    value = localAutoHide,
                    onValueChange = { value -> localAutoHide = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                clipboardBall = it.clipboardBall.copy(
                                    autoHideMs = localAutoHide.toLong(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_capsule_auto_hide),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.ClipboardBallAutoHideMsMin.toFloat()..
                        FloatBallDefaults.ClipboardBallAutoHideMsMax.toFloat(),
                    shape = segmentedShape(3, 6),
                )
                LunoTextSlider(
                    value = localTtl,
                    onValueChange = { value -> localTtl = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(inbox = it.inbox.copy(clipboardTtlMs = localTtl.toLong()))
                        }
                    },
                    text = stringResource(R.string.float_ball_suggestion_ttl),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.InboxSuggestionTtlMsMin.toFloat()..
                        FloatBallDefaults.InboxSuggestionTtlMsMax.toFloat(),
                    shape = segmentedShape(4, 6),
                )
                LunoTextSlider(
                    value = localCapacity,
                    onValueChange = { value -> localCapacity = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                inbox = it.inbox.copy(
                                    suggestionCapacity = localCapacity.toInt(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_suggestion_capacity),
                    valueDisplay = { "${it.toInt()}" },
                    valueRange = FloatBallDefaults.InboxCapacityMin.toFloat()..
                        FloatBallDefaults.InboxCapacityMax.toFloat(),
                    shape = segmentedShape(5, 6),
                )
            }
        }

        item(key = "icon") {
            val hasIcon = clipball.customIcon.path != null
            LunoGroup(title = stringResource(R.string.float_ball_appearance)) {
                BallCustomIconRows(
                    context = context,
                    icon = clipball.customIcon,
                    startIndex = 0,
                    rowCount = if (hasIcon) 2 else 1,
                    onPick = iconPicker::pick,
                    onReset = {
                        resetBallIcon(scope, clipball.customIcon) { icon ->
                            update { it.copy(clipboardBall = it.clipboardBall.copy(customIcon = icon)) }
                        }
                    },
                )
            }
        }

        // 识别器（规则表 + 情景识别开关/名单）统一在识别器页：情景识别是识别器的第五个输入源，
        // 挂在建议胶囊下会让「按来源配置」散在两处。
        item(key = "recognition") {
            LunoGroup {
                LunoSettingsRow(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.float_ball_suggestion_recognition),
                    subtitle = stringResource(R.string.float_ball_suggestion_recognition_hint),
                    shape = segmentedShape(0, 1),
                    onClick = onNavToRecognition,
                )
            }
        }
    }
}
