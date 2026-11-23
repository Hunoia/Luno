package hunoia.luno.floatball.settings

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.MaterialTheme
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
import hunoia.luno.config.model.CapsuleGestureBehavior
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
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.FloatingContentBottom
import kotlinx.coroutines.launch

/**
 * 结果胶囊设置页：只管**胶囊自己**（跟手 / 静置 / 停留时长 / 队列 / 行高 / 图标）。
 *
 * 规则表、来源读取开关、直执行提示开关、情景识别都在分发规则页（`RecognitionSettingsScreen`），
 * 本页从那里进入：结果胶囊是分发规则的**结果显示面**，没有自己的开关，也不拥有一份规则表。
 */
@Composable
fun ResultCapsuleSettingsScreen(
    onBack: () -> Unit,
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
    var localTtl by remember(floatBall.inbox.resultTtlMs) {
        mutableFloatStateOf(floatBall.inbox.resultTtlMs.toFloat())
    }
    var localCapacity by remember(floatBall.inbox.resultCapacity, floatBall.inbox.capacity) {
        mutableFloatStateOf((floatBall.inbox.resultCapacity ?: floatBall.inbox.capacity).toFloat())
    }
    var localVisibleRows by remember(capsule.visibleRows) {
        mutableFloatStateOf(capsule.visibleRows.toFloat())
    }
    var localRowHeight by remember(capsule.rowHeightDp) {
        mutableFloatStateOf(capsule.rowHeightDp.toFloat())
    }

    var overlayGranted by remember { mutableStateOf(context.canDrawOverlays()) }
    // 结果球自己的图标：选图 → 裁剪 → 落盘，不跟随主球。
    val iconPicker = rememberBallIconPickerState()
    var showBallColorPicker by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
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
            if (showBallColorPicker) {
                val scheme = MaterialTheme.colorScheme
                ColorPickerBottomSheet(
                    onDismissRequest = { showBallColorPicker = false },
                    onCopyColor = { hex ->
                        hunoia.luno.clipboard.ClipboardCenter.writeTextImmediate(hex, "Luno color")
                    },
                    initialSelection = capsule.ballColor.toInitialSelection(
                        scheme = scheme,
                        primaryFallback = true,
                    ),
                    onColorSelected = { selection ->
                        update {
                            it.copy(
                                resultCapsule = it.resultCapsule.copy(
                                    ballColor = selection.toColorSpec(scheme),
                                ),
                            )
                        }
                        showBallColorPicker = false
                    },
                )
            }
            if (showBackgroundColorPicker) {
                val scheme = MaterialTheme.colorScheme
                ColorPickerBottomSheet(
                    onDismissRequest = { showBackgroundColorPicker = false },
                    onCopyColor = { hex ->
                        hunoia.luno.clipboard.ClipboardCenter.writeTextImmediate(hex, "Luno color")
                    },
                    initialSelection = capsule.backgroundColor.toInitialSelection(
                        scheme = scheme,
                        primaryFallback = true,
                    ),
                    onColorSelected = { selection ->
                        update {
                            it.copy(
                                resultCapsule = it.resultCapsule.copy(
                                    backgroundColor = selection.toColorSpec(scheme),
                                ),
                            )
                        }
                        showBackgroundColorPicker = false
                    },
                )
            }
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
                    shape = segmentedShape(0, 7),
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
                    shape = segmentedShape(1, 7),
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
                    shape = segmentedShape(2, 7),
                )
                LunoTextSlider(
                    value = localVisibleRows,
                    onValueChange = { value -> localVisibleRows = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                resultCapsule = it.resultCapsule.copy(
                                    visibleRows = localVisibleRows.toInt(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_capsule_visible_rows),
                    valueDisplay = { "${it.toInt()}" },
                    valueRange = FloatBallDefaults.CapsuleVisibleRowsMin.toFloat()..
                        FloatBallDefaults.CapsuleVisibleRowsMax.toFloat(),
                    shape = segmentedShape(3, 7),
                )
                LunoTextSlider(
                    value = localRowHeight,
                    onValueChange = { value -> localRowHeight = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(
                                resultCapsule = it.resultCapsule.copy(
                                    rowHeightDp = localRowHeight.toInt(),
                                ),
                            )
                        }
                    },
                    text = stringResource(R.string.float_ball_capsule_row_height),
                    valueDisplay = { "${it.toInt()} dp" },
                    valueRange = FloatBallDefaults.CapsuleRowHeightDpMin.toFloat()..
                        FloatBallDefaults.CapsuleRowHeightDpMax.toFloat(),
                    shape = segmentedShape(4, 7),
                )
                LunoTextSlider(
                    value = localTtl,
                    onValueChange = { value -> localTtl = value },
                    onValueChangeFinished = {
                        update {
                            it.copy(inbox = it.inbox.copy(resultTtlMs = localTtl.toLong()))
                        }
                    },
                    text = stringResource(R.string.float_ball_result_ttl),
                    valueDisplay = { "${(it / 1000).toInt()} s" },
                    valueRange = FloatBallDefaults.InboxResultTtlMsMin.toFloat()..
                        FloatBallDefaults.InboxResultTtlMsMax.toFloat(),
                    shape = segmentedShape(5, 7),
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
                    shape = segmentedShape(6, 7),
                )
            }
        }

        // 手势：球长按与行级手势仅在内置行为间互选；行长按默认保留操作菜单（现行为）。
        item(key = "gestures") {
            val rows = 4
            LunoGroup(title = stringResource(R.string.float_ball_capsule_gestures)) {
                GestureBehaviorRow(
                    title = stringResource(R.string.float_ball_ball_long_press),
                    current = capsule.ballLongPressBehavior,
                    options = listOf(
                        CapsuleGestureBehavior.NONE,
                        CapsuleGestureBehavior.CLEAR_QUEUE,
                    ),
                    onSelect = { value ->
                        update {
                            it.copy(resultCapsule = it.resultCapsule.copy(ballLongPress = value))
                        }
                    },
                    shape = segmentedShape(0, rows),
                )
                GestureBehaviorRow(
                    title = stringResource(R.string.float_ball_row_long_press),
                    current = capsule.rowLongPressBehavior,
                    options = listOf(
                        CapsuleGestureBehavior.MENU,
                        CapsuleGestureBehavior.IGNORE_ROW,
                        CapsuleGestureBehavior.NONE,
                    ),
                    onSelect = { value ->
                        update {
                            it.copy(resultCapsule = it.resultCapsule.copy(rowLongPress = value))
                        }
                    },
                    shape = segmentedShape(1, rows),
                )
                GestureBehaviorRow(
                    title = stringResource(R.string.float_ball_row_swipe_away),
                    current = capsule.rowSwipeAwayBehavior,
                    options = listOf(
                        CapsuleGestureBehavior.IGNORE_ROW,
                        CapsuleGestureBehavior.COLLAPSE,
                        CapsuleGestureBehavior.NONE,
                    ),
                    onSelect = { value ->
                        update {
                            it.copy(resultCapsule = it.resultCapsule.copy(rowSwipeAway = value))
                        }
                    },
                    shape = segmentedShape(2, rows),
                )
                GestureBehaviorRow(
                    title = stringResource(R.string.float_ball_row_swipe_toward),
                    current = capsule.rowSwipeTowardBehavior,
                    options = listOf(
                        CapsuleGestureBehavior.COLLAPSE,
                        CapsuleGestureBehavior.IGNORE_ROW,
                        CapsuleGestureBehavior.NONE,
                    ),
                    onSelect = { value ->
                        update {
                            it.copy(resultCapsule = it.resultCapsule.copy(rowSwipeToward = value))
                        }
                    },
                    shape = segmentedShape(3, rows),
                )
            }
        }

        item(key = "icon") {
            val hasIcon = capsule.customIcon.path != null
            // 外观组行数 = 图标行（1–2）+ 颜色行（球色 / 背景色各 1 行），段形随之。
            val iconRows = if (hasIcon) 2 else 1
            val colorRows = capsuleColorRowCount()
            LunoGroup(title = stringResource(R.string.float_ball_appearance)) {
                BallCustomIconRows(
                    context = context,
                    icon = capsule.customIcon,
                    startIndex = 0,
                    rowCount = iconRows + colorRows,
                    onPick = iconPicker::pick,
                    onReset = {
                        resetBallIcon(scope, capsule.customIcon) { icon ->
                            update { it.copy(resultCapsule = it.resultCapsule.copy(customIcon = icon)) }
                        }
                    },
                )
                CapsuleColorRows(
                    ballSpec = capsule.ballColor,
                    backgroundSpec = capsule.backgroundColor,
                    defaultColor = MaterialTheme.colorScheme.primary,
                    startIndex = iconRows,
                    rowCount = iconRows + colorRows,
                    onBallClick = { showBallColorPicker = true },
                    onBackgroundClick = { showBackgroundColorPicker = true },
                )
            }
        }
    }
}
