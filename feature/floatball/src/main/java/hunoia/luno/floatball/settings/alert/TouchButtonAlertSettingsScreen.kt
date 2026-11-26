package hunoia.luno.floatball.settings.alert

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import hunoia.luno.config.defaults.GestureSettingsDefaults
import hunoia.luno.config.model.AlertLibrary
import hunoia.luno.config.model.AlertLimits
import hunoia.luno.config.model.AlertPreset
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.SwipeIndicator
import hunoia.luno.config.model.SwipeIndicatorStyle
import hunoia.luno.config.model.TouchButtonAlertSettings
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.domain.tune.TuneConfig
import hunoia.luno.floatball.R
import hunoia.luno.ui.component.LunoFab
import hunoia.luno.ui.component.page.LunoPage
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.SegmentedRows
import hunoia.luno.ui.component.row.segmentedRows
import hunoia.luno.ui.component.sheet.ColorPickerBottomSheet
import hunoia.luno.ui.component.sheet.toColorSpec
import hunoia.luno.ui.component.sheet.toInitialSelection
import hunoia.luno.ui.facade.LocalFacades
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.MarkColorSize
import hunoia.luno.ui.theme.toMarkColor
import kotlinx.coroutines.launch

/**
 * 触钮动画：两段互不冲突。
 *
 * - **预设动效**：按下即播、跟手；每个触钮单独绑定（镜像钮共用源钮）。
 * - **滑动指示条**：滑过阈值才出现，是一条被手指拉出来的屏幕边（贴边流体）；尺寸 / 描边 / 颜色可配。
 *
 * 预设与到达提示共用同一个库；预设动效的强度 / 速度是触钮这一类的独立一套。
 */
@Composable
fun TouchButtonAlertSettingsScreen(
    onBack: () -> Unit,
    focusButtonId: String? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val facades = LocalFacades.current
    var alert by remember {
        mutableStateOf(TuneConfig.latestAction().floatBall.buttonAlert)
    }
    var library by remember {
        mutableStateOf<AlertLibrary>(
            TuneConfig.latestAction().floatBall.notificationCapsule.alertLibrary,
        )
    }
    var buttons by remember { mutableStateOf<List<GestureButton>>(GestureConfig.latestButtons()) }
    var indicator by remember { mutableStateOf(TuneConfig.latestGesture().swipeIndicator) }
    var pickButtonId by remember { mutableStateOf<String?>(null) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var showStrokeColorPicker by rememberSaveable { mutableStateOf(false) }
    var previewPresetId by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        val action = TuneConfig.latestAction()
        alert = action.floatBall.buttonAlert
        library = action.floatBall.notificationCapsule.alertLibrary
        buttons = GestureConfig.latestButtons()
        indicator = TuneConfig.latestGesture().swipeIndicator
        onPauseOrDispose { }
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun updateAlert(transform: (TouchButtonAlertSettings) -> TouchButtonAlertSettings) {
        alert = transform(alert)
        scope.launch {
            TuneConfig.updateAction { current ->
                current.copy(
                    floatBall = current.floatBall.copy(
                        buttonAlert = transform(current.floatBall.buttonAlert),
                    ),
                )
            }
        }
    }

    fun updateIndicator(transform: (SwipeIndicator) -> SwipeIndicator) {
        indicator = transform(indicator)
        scope.launch {
            TuneConfig.updateGesture { current ->
                current.copy(swipeIndicator = transform(current.swipeIndicator))
            }
        }
    }

    fun presetName(presetId: String?): String {
        if (presetId == null) return resources.getString(R.string.button_alert_unbound)
        val preset: AlertPreset? = library.presetById(presetId)
        return preset?.displayNameString(context)
            ?: resources.getString(R.string.float_ball_alert_preset_missing)
    }

    val orderedButtons = remember(buttons, focusButtonId) {
        if (focusButtonId == null) buttons
        else buttons.sortedBy { if (it.id == focusButtonId) 0 else 1 }
    }
    val previewPreset: AlertPreset? = (
        previewPresetId?.let { library.presetById(it) }
            ?: alert.bindings.values.firstOrNull()?.let { library.presetById(it) }
        )

    LunoPage(title = stringResource(R.string.button_alert_title),
onBack = onBack,
floatingContent = {
            LunoFab(
                onClick = {
                    when {
                        !alert.enabled -> toast(resources.getString(R.string.button_alert_preview_disabled))
                        previewPreset == null || !previewPreset.isPlayable() ->
                            toast(resources.getString(R.string.button_alert_preview_none))
                        else -> facades.system.previewFloatBallAlertPreset(
                            context,
                            previewPreset,
                            intensityPercent = alert.intensityPercent,
                            speedPercent = alert.speedPercent,
                        )
                    }
                },
                icon = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.button_alert_preview),
            )
        },
sheets = {
            pickButtonId?.let { buttonId ->
                AlertPresetSelectSheet(
                    library = library,
                    currentId = alert.presetFor(buttonId),
                    onDismiss = { pickButtonId = null },
                    onSelect = { presetId ->
                        pickButtonId = null
                        previewPresetId = presetId
                        updateAlert { it.withBinding(buttonId, presetId) }
                    },
                    noneSubtitle = stringResource(R.string.button_alert_unbound_hint),
                )
            }
            if (showColorPicker) {
                val scheme = MaterialTheme.colorScheme
                ColorPickerBottomSheet(
                    onDismissRequest = { showColorPicker = false },
                    initialSelection = indicator.color.toInitialSelection(
                        scheme,
                        primaryFallback = true,
                    ),
                    onColorSelected = { selection ->
                        showColorPicker = false
                        updateIndicator { it.copy(color = selection.toColorSpec(scheme)) }
                    },
                )
            }
            if (showStrokeColorPicker) {
                val scheme = MaterialTheme.colorScheme
                ColorPickerBottomSheet(
                    onDismissRequest = { showStrokeColorPicker = false },
                    initialSelection = indicator.strokeColor.toInitialSelection(
                        scheme,
                        primaryFallback = true,
                    ),
                    onColorSelected = { selection ->
                        showStrokeColorPicker = false
                        updateIndicator { it.copy(strokeColor = selection.toColorSpec(scheme)) }
                    },
                )
            }
        }) {
    list(bottomContentPadding = FloatingContentBottom) {
        lazy {

        item(key = "effect") {
            val rows = segmentedRows {
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.button_alert_enabled),
                        subtitle = stringResource(R.string.button_alert_enabled_subtitle),
                        checked = alert.enabled,
                        shape = shape,
                        onCheckedChange = { checked ->
                            updateAlert { it.copy(enabled = checked) }
                        },
                    )
                }
                item { shape ->
                    AlertKnobSlider(
                        label = stringResource(R.string.button_alert_intensity),
                        value = alert.intensityPercent,
                        range = ButtonIntensityRange,
                        display = { "${it.toInt()}%" },
                        shape = shape,
                        onCommit = { value ->
                            updateAlert { it.copy(intensityPercent = value) }
                        },
                    )
                }
                item { shape ->
                    AlertKnobSlider(
                        label = stringResource(R.string.button_alert_speed),
                        value = alert.speedPercent,
                        range = ButtonSpeedRange,
                        display = { "${it.toInt()}%" },
                        shape = shape,
                        onCommit = { value -> updateAlert { it.copy(speedPercent = value) } },
                    )
                }
            }
            LunoGroup(
                title = stringResource(R.string.button_alert_effect_group),
                subtitle = stringResource(R.string.button_alert_effect_hint),
            ) {
                SegmentedRows(rows)
            }
        }
        item(key = "indicator") {
            val rows = segmentedRows {
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.button_alert_indicator_enabled),
                        subtitle = stringResource(R.string.button_alert_indicator_hint),
                        checked = indicator.enabled,
                        shape = shape,
                        onCheckedChange = { checked ->
                            updateIndicator { it.copy(enabled = checked) }
                        },
                    )
                }
                item { shape ->
                    LayerDropdownRow(
                        title = stringResource(R.string.button_alert_indicator_style),
                        options = SwipeIndicatorStyle.entries,
                        selected = indicator.style,
                        shape = shape,
                        optionLabel = { stringResource(it.labelRes()) },
                        onSelect = { style -> updateIndicator { it.copy(style = style) } },
                    )
                }
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.button_alert_indicator_size),
                        value = indicator.effectiveSizePercent,
                        range = GestureSettingsDefaults.SwipeIndicatorSizeMinPercent..
                            GestureSettingsDefaults.SwipeIndicatorSizeMaxPercent,
                        suffix = "%",
                        shape = shape,
                        onCommit = { value -> updateIndicator { it.copy(sizePercent = value) } },
                    )
                }
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.button_alert_indicator_stroke),
                        value = indicator.effectiveStrokeWidthDp,
                        range = GestureSettingsDefaults.SwipeIndicatorStrokeMinDp..
                            GestureSettingsDefaults.SwipeIndicatorStrokeMaxDp,
                        suffix = " dp",
                        shape = shape,
                        onCommit = { value -> updateIndicator { it.copy(strokeWidthDp = value) } },
                    )
                }
                if (indicator.effectiveStrokeWidthDp > 0) {
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.button_alert_indicator_stroke_color),
                            subtitle = stringResource(R.string.button_alert_indicator_stroke_color_hint),
                            shape = shape,
                            leading = {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .size(MarkColorSize)
                                        .background(
                                            color = indicator.strokeColor.toMarkColor(),
                                            shape = CircleShape,
                                        ),
                                )
                            },
                            onClick = { showStrokeColorPicker = true },
                        )
                    }
                }
                item { shape ->
                    LayerIntSlider(
                        label = stringResource(R.string.button_alert_indicator_max_drag),
                        value = indicator.effectiveMaxDragDp,
                        range = GestureSettingsDefaults.SwipeIndicatorMaxDragMinDp..
                            GestureSettingsDefaults.SwipeIndicatorMaxDragMaxDp,
                        suffix = " dp",
                        shape = shape,
                        onCommit = { value -> updateIndicator { it.copy(maxDragDp = value) } },
                    )
                }
                item { shape ->
                    LunoSwitchRow(
                        title = stringResource(R.string.button_alert_indicator_follow_button),
                        checked = indicator.followButtonColor,
                        shape = shape,
                        onCheckedChange = { checked ->
                            updateIndicator { it.copy(followButtonColor = checked) }
                        },
                    )
                }
                if (!indicator.followButtonColor) {
                    item { shape ->
                        LunoSettingsRow(
                            title = stringResource(R.string.button_alert_indicator_color),
                            subtitle = stringResource(R.string.button_alert_indicator_color_hint),
                            shape = shape,
                            leading = {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .size(MarkColorSize)
                                        .background(
                                            color = indicator.color.toMarkColor(),
                                            shape = CircleShape,
                                        ),
                                )
                            },
                            onClick = { showColorPicker = true },
                        )
                    }
                }
            }
            LunoGroup(
                title = stringResource(R.string.button_alert_indicator_group),
                subtitle = stringResource(R.string.button_alert_indicator_group_hint),
            ) {
                SegmentedRows(rows)
            }
        }
        item(key = "bindings") {
            LunoGroup(
                title = stringResource(R.string.button_alert_bindings_group),
                subtitle = stringResource(R.string.button_alert_bindings_hint),
                badgeText = "${buttons.count { alert.presetFor(it.id) != null }}",
            ) {
                if (orderedButtons.isEmpty()) {
                    SegmentedRows(
                        segmentedRows {
                            item { shape ->
                                LunoSettingsRow(
                                    title = stringResource(R.string.button_alert_no_buttons),
                                    subtitle = stringResource(R.string.button_alert_no_buttons_hint),
                                    shape = shape,
                                )
                            }
                        },
                    )
                } else {
                    SegmentedRows(
                        segmentedRows {
                            orderedButtons.forEach { button ->
                                item { shape ->
                                    LunoSettingsRow(
                                        title = button.displayTitle(),
                                        subtitle = presetName(alert.presetFor(button.id)),
                                        icon = Icons.Default.PlayArrow,
                                        shape = shape,
                                        onClick = { pickButtonId = button.id },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        }
    }
}
}

@Composable
private fun GestureButton.displayTitle(): String =
    name.ifBlank { stringResource(R.string.button_alert_default_button_name) }

private val ButtonIntensityRange =
    AlertLimits.IntensityMin..AlertLimits.IntensityMax
private val ButtonSpeedRange =
    AlertLimits.SpeedMin..AlertLimits.SpeedMax