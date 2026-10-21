package hunoia.luno.ui.actionselect.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.defaults.ActionSettingsDefaults
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.service.DispatchClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** 参数改在动作选择页行尾「设置」弹窗里配的动作类型。 */
val RowSettingsActionTypeIds: Set<String> = setOf(
    "internal.volumeScrub",
    "internal.hideGestureButton",
    "internal.floatBallVisibility",
    "internal.buttonsVisibility",
    "system.openSettings",
    "system.volume",
    "system.vibrate",
    "accessibility.tap",
    "accessibility.longPress",
    "accessibility.swipe",
)

private fun StoredAction.paramStr(key: String): String? =
    (params[key] as? JsonPrimitive)?.contentOrNull

private fun StoredAction.paramIds(): List<String> =
    (params["buttonIds"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

private fun StoredAction.paramBool(key: String, default: Boolean): Boolean =
    paramStr(key)?.toBooleanStrictOrNull() ?: default

private fun StoredAction.paramInt(key: String): Int? = paramStr(key)?.toIntOrNull()

private fun settingsPageLabelRes(page: String): Int = when (page) {
    "WIFI" -> R.string.settings_page_wifi
    "BLUETOOTH" -> R.string.settings_page_bluetooth
    "DISPLAY" -> R.string.settings_page_display
    "BATTERY" -> R.string.settings_page_battery
    "APPS" -> R.string.settings_page_apps
    "NETWORK" -> R.string.settings_page_network
    "ACCESSIBILITY" -> R.string.settings_page_accessibility
    else -> R.string.settings_page_main
}

private fun audioStreamLabelRes(stream: String): Int = when (stream) {
    "RING" -> R.string.audio_stream_ring
    "NOTIFICATION" -> R.string.audio_stream_notification
    "ALARM" -> R.string.audio_stream_alarm
    "VOICE" -> R.string.audio_stream_voice
    "SYSTEM" -> R.string.audio_stream_system
    else -> R.string.audio_stream_music
}

/**
 * 行副标题：展示当前生效值（未配置时即默认值）。
 * 与 [ActionRowSettingsDialog] 同一套键位与默认值，改其一须同步另一个。
 */
fun rowSettingsSubtitle(action: StoredAction, context: android.content.Context): String = when (action.typeId) {
    "internal.volumeScrub" ->
        "${context.getString(R.string.row_settings_sensitivity)} · " +
            "${action.paramInt("stepThresholdDp") ?: ActionSettingsDefaults.VolumeScrubStepThresholdDp}dp"
    "internal.hideGestureButton" ->
        "${action.paramStr("delayMs")?.toLongOrNull() ?: ActionSettingsDefaults.HideGestureButtonDelayMs}ms"
    "internal.floatBallVisibility" ->
        when (action.paramStr("visible") ?: "toggle") {
            "true" -> context.getString(R.string.row_settings_show)
            "false" -> context.getString(R.string.row_settings_hide)
            else -> context.getString(R.string.row_settings_toggle)
        }
    "internal.buttonsVisibility" -> {
        val visible = context.getString(
            if (action.paramBool("visible", false)) R.string.row_settings_show else R.string.row_settings_hide,
        )
        val target = action.paramStr("target") ?: "ALL"
        val scope = when (target) {
            "ALL" -> context.getString(R.string.row_settings_scope_all)
            else -> "${action.paramIds().size} ${context.getString(R.string.row_settings_buttons)}"
        }
        "$visible · $scope"
    }
    "system.openSettings" -> context.getString(settingsPageLabelRes(action.paramStr("page") ?: "MAIN"))
    "system.volume" -> {
        val stream = context.getString(audioStreamLabelRes(action.paramStr("stream") ?: "MUSIC"))
        val direction = context.getString(
            if ((action.paramStr("direction") ?: "UP") == "UP") {
                R.string.row_settings_direction_up
            } else {
                R.string.row_settings_direction_down
            },
        )
        "$stream · $direction"
    }
    "system.vibrate" -> action.paramStr("pattern") ?: "0,500"
    "accessibility.tap" -> tapSubtitle(action, context)
    "accessibility.longPress" -> {
        val duration = action.paramStr("durationMs")?.toLongOrNull() ?: 1000L
        "${tapSubtitle(action, context)} · ${duration}ms"
    }
    "accessibility.swipe" -> context.getString(
        when (action.paramStr("direction") ?: "UP") {
            "DOWN" -> R.string.row_settings_swipe_down
            "LEFT" -> R.string.row_settings_swipe_left
            "RIGHT" -> R.string.row_settings_swipe_right
            "TO_TOP" -> R.string.row_settings_swipe_to_top
            else -> R.string.row_settings_swipe_up
        },
    )
    else -> ""
}

/**
 * 行尾设置弹窗：按 typeId 渲染对应配置项。不设置的字段不写 params，
 * 落运行时默认值（灵敏度 14dp / 触钮隐藏 1000ms / 悬浮球 切换 / 触钮显隐 隐藏全部）。
 */
@Composable
fun ActionRowSettingsDialog(
    action: StoredAction,
    onDismiss: () -> Unit,
    onConfirm: (StoredAction) -> Unit,
) {
    val context = LocalContext.current
    // 各类型共用一组草稿状态；确认时只取当前类型关心的字段组包。
    // 记住 action 作 key：切换编辑目标时草稿随之重置，不残留上一行的值。
    var step by remember(action) {
        mutableStateOf((action.paramStr("stepThresholdDp")?.toIntOrNull()
            ?: ActionSettingsDefaults.VolumeScrubStepThresholdDp).toFloat())
    }
    var delayMs by remember(action) {
        mutableStateOf((action.paramStr("delayMs")?.toLongOrNull()
            ?: ActionSettingsDefaults.HideGestureButtonDelayMs).toFloat())
    }
    var ballVisible by remember(action) { mutableStateOf(action.paramStr("visible") ?: "toggle") }
    var btnVisible by remember(action) {
        mutableStateOf(action.paramStr("visible")?.toBooleanStrictOrNull() ?: false)
    }
    var target by remember(action) { mutableStateOf(action.paramStr("target") ?: "ALL") }
    var selectedIds by remember(action) { mutableStateOf(action.paramIds().toSet()) }
    var settingsPage by remember(action) { mutableStateOf(action.paramStr("page") ?: "MAIN") }
    var stream by remember(action) { mutableStateOf(action.paramStr("stream") ?: "MUSIC") }
    var volumeDirection by remember(action) { mutableStateOf(action.paramStr("direction") ?: "UP") }
    var pattern by remember(action) { mutableStateOf(action.paramStr("pattern") ?: "0,500") }
    var useTriggerPoint by remember(action) {
        mutableStateOf(action.paramBool("useTriggerPoint", false))
    }
    var xDp by remember(action) { mutableStateOf((action.paramInt("xDp") ?: 0).toString()) }
    var yDp by remember(action) { mutableStateOf((action.paramInt("yDp") ?: 0).toString()) }
    var durationMs by remember(action) {
        mutableStateOf((action.paramStr("durationMs")?.toLongOrNull() ?: 1000L).toFloat())
    }
    var swipeDirection by remember(action) { mutableStateOf(action.paramStr("direction") ?: "UP") }
    val buttons by GestureConfig.buttons.collectAsStateWithLifecycle(initialValue = emptyList())

    fun buildParams(action: StoredAction): JsonObject = when (action.typeId) {
        "internal.volumeScrub" -> buildJsonObject {
            put("stepThresholdDp", JsonPrimitive(step.toInt()))
        }
        "internal.hideGestureButton" -> buildJsonObject {
            put("delayMs", JsonPrimitive(delayMs.toLong()))
        }
        "internal.floatBallVisibility" -> buildJsonObject {
            put("visible", JsonPrimitive(ballVisible))
        }
        "internal.buttonsVisibility" -> buildJsonObject {
            put("visible", JsonPrimitive(btnVisible))
            put("target", JsonPrimitive(target))
            if (target != "ALL") {
                put("buttonIds", buildJsonArray { selectedIds.forEach { add(JsonPrimitive(it)) } })
            }
        }
        "system.openSettings" -> buildJsonObject {
            put("page", JsonPrimitive(settingsPage))
        }
        "system.volume" -> buildJsonObject {
            put("stream", JsonPrimitive(stream))
            put("direction", JsonPrimitive(volumeDirection))
        }
        "system.vibrate" -> buildJsonObject {
            put("pattern", JsonPrimitive(pattern))
        }
        "accessibility.tap" -> tapPointParams(useTriggerPoint, xDp, yDp)
        "accessibility.longPress" -> buildJsonObject {
            tapPointParams(useTriggerPoint, xDp, yDp).forEach { (k, v) -> put(k, v) }
            put("durationMs", JsonPrimitive(durationMs.toLong()))
        }
        "accessibility.swipe" -> buildJsonObject {
            put("direction", JsonPrimitive(swipeDirection))
        }
        else -> JsonObject(emptyMap())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(paramDialogTitleRes(action.typeId))) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (action.typeId) {
                    "internal.volumeScrub" -> {
                        LabeledSlider(
                            R.string.row_settings_sensitivity, step, 4f..64f, "${step.toInt()}dp",
                        ) { step = it }
                    }
                    "internal.hideGestureButton" -> {
                        LabeledSlider(
                            R.string.row_settings_hide_duration, delayMs, 0f..3000f, "${delayMs.toInt()}ms",
                        ) { delayMs = it }
                    }
                    "internal.floatBallVisibility" -> {
                        LabeledChips(
                            R.string.row_settings_visibility,
                            listOf(
                                "toggle" to R.string.row_settings_toggle,
                                "true" to R.string.row_settings_show,
                                "false" to R.string.row_settings_hide,
                            ),
                            ballVisible,
                        ) { ballVisible = it }
                    }
                    "internal.buttonsVisibility" -> {
                        LabeledChips(
                            R.string.row_settings_visibility,
                            listOf(
                                "true" to R.string.row_settings_show,
                                "false" to R.string.row_settings_hide,
                            ),
                            if (btnVisible) "true" else "false",
                        ) { btnVisible = it.toBooleanStrict() }
                        LabeledChips(
                            R.string.row_settings_scope,
                            listOf(
                                "ALL" to R.string.row_settings_scope_all,
                                "EXCEPT" to R.string.row_settings_scope_except,
                                "ONLY" to R.string.row_settings_scope_only,
                            ),
                            target,
                        ) { target = it }
                        if (target != "ALL") {
                            Text(text = stringResource(R.string.row_settings_buttons), style = MaterialTheme.typography.labelLarge)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                buttons.forEach { button ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = button.id in selectedIds,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked) {
                                                    selectedIds + button.id
                                                } else {
                                                    selectedIds - button.id
                                                }
                                            },
                                        )
                                        Text(text = button.name.ifBlank { button.id })
                                    }
                                }
                            }
                        }
                    }
                    "system.openSettings" -> {
                        LabeledChips(
                            R.string.row_settings_settings_page,
                            listOf(
                                "WIFI" to R.string.settings_page_wifi,
                                "BLUETOOTH" to R.string.settings_page_bluetooth,
                                "DISPLAY" to R.string.settings_page_display,
                                "BATTERY" to R.string.settings_page_battery,
                                "APPS" to R.string.settings_page_apps,
                                "NETWORK" to R.string.settings_page_network,
                                "ACCESSIBILITY" to R.string.settings_page_accessibility,
                                "MAIN" to R.string.settings_page_main,
                            ),
                            settingsPage,
                        ) { settingsPage = it }
                    }
                    "system.volume" -> {
                        LabeledChips(
                            R.string.row_settings_stream,
                            listOf(
                                "MUSIC" to R.string.audio_stream_music,
                                "RING" to R.string.audio_stream_ring,
                                "NOTIFICATION" to R.string.audio_stream_notification,
                                "ALARM" to R.string.audio_stream_alarm,
                                "VOICE" to R.string.audio_stream_voice,
                                "SYSTEM" to R.string.audio_stream_system,
                            ),
                            stream,
                        ) { stream = it }
                        LabeledChips(
                            R.string.row_settings_direction,
                            listOf(
                                "UP" to R.string.row_settings_direction_up,
                                "DOWN" to R.string.row_settings_direction_down,
                            ),
                            volumeDirection,
                        ) { volumeDirection = it }
                    }
                    "system.vibrate" -> {
                        LabeledTextField(R.string.row_settings_pattern_hint, pattern) { pattern = it }
                    }
                    "accessibility.tap", "accessibility.longPress" -> {
                        LabeledChips(
                            R.string.row_settings_fall_point,
                            listOf(
                                "true" to R.string.row_settings_trigger_point,
                                "false" to R.string.row_settings_fixed_point,
                            ),
                            if (useTriggerPoint) "true" else "false",
                        ) { useTriggerPoint = it.toBooleanStrict() }
                        if (!useTriggerPoint) {
                            // 屏幕取点：经 :service 侧 overlay，回填后仍可在坐标框手改。
                            var pickHint by remember { mutableStateOf("") }
                            TextButton(onClick = {
                                val ok = DispatchClient.showPointPicker(
                                    initialXdp = xDp.toIntOrNull(),
                                    initialYdp = yDp.toIntOrNull(),
                                    onCommit = { x, y ->
                                        xDp = x.toString()
                                        yDp = y.toString()
                                    },
                                    onDismiss = {},
                                )
                                pickHint = if (ok) "" else context.getString(R.string.row_settings_point_picker_failed)
                            }) { Text(stringResource(R.string.point_pick)) }
                            if (pickHint.isNotEmpty()) {
                                Text(text = pickHint, style = MaterialTheme.typography.bodySmall)
                            }
                            LabeledTextField(R.string.row_settings_coord_x, xDp) { xDp = it }
                            LabeledTextField(R.string.row_settings_coord_y, yDp) { yDp = it }
                        }
                        if (action.typeId == "accessibility.longPress") {
                            LabeledSlider(
                                R.string.row_settings_duration, durationMs, 100f..10000f, "${durationMs.toInt()}ms",
                            ) {
                                durationMs = it
                            }
                        }
                    }
                    "accessibility.swipe" -> {
                        LabeledChips(
                            R.string.row_settings_direction,
                            listOf(
                                "UP" to R.string.row_settings_swipe_up,
                                "DOWN" to R.string.row_settings_swipe_down,
                                "LEFT" to R.string.row_settings_swipe_left,
                                "RIGHT" to R.string.row_settings_swipe_right,
                                "TO_TOP" to R.string.row_settings_swipe_to_top,
                            ),
                            swipeDirection,
                        ) { swipeDirection = it }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(action.copy(params = buildParams(action)))
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun tapPointParams(useTriggerPoint: Boolean, xDp: String, yDp: String): JsonObject =
    if (useTriggerPoint) {
        buildJsonObject { put("useTriggerPoint", JsonPrimitive(true)) }
    } else {
        buildJsonObject {
            put("useTriggerPoint", JsonPrimitive(false))
            put("xDp", JsonPrimitive(xDp.toIntOrNull() ?: 0))
            put("yDp", JsonPrimitive(yDp.toIntOrNull() ?: 0))
        }
    }

private fun tapSubtitle(action: StoredAction, context: android.content.Context): String {
    val x = action.paramInt("xDp")
    val y = action.paramInt("yDp")
    return if (action.paramBool("useTriggerPoint", false) || x == null || y == null) {
        context.getString(R.string.row_settings_trigger_point)
    } else {
        context.getString(R.string.row_settings_coord_dp, x, y)
    }
}

private fun paramDialogTitleRes(typeId: String): Int = when (typeId) {
    "internal.volumeScrub" -> R.string.row_dialog_volume_scrub
    "internal.hideGestureButton" -> R.string.row_dialog_hide_gesture_button
    "internal.floatBallVisibility" -> R.string.row_dialog_float_ball
    "internal.buttonsVisibility" -> R.string.row_dialog_buttons_visibility
    "system.openSettings" -> R.string.row_dialog_open_settings
    "system.volume" -> R.string.row_dialog_volume
    "system.vibrate" -> R.string.row_dialog_vibrate
    "accessibility.tap" -> R.string.row_dialog_tap
    "accessibility.longPress" -> R.string.row_dialog_long_press
    "accessibility.swipe" -> R.string.row_dialog_swipe
    else -> R.string.row_settings_settings_page
}

@Composable
private fun LabeledSlider(
    labelRes: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Text(
        text = "${stringResource(labelRes)} · $valueText",
        style = MaterialTheme.typography.labelLarge,
    )
    Slider(value = value, onValueChange = onValueChange, valueRange = range)
}

@Composable
private fun LabeledChips(
    labelRes: Int,
    options: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, textRes) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(textRes)) },
            )
        }
    }
}

@Composable
private fun LabeledTextField(labelRes: Int, value: String, onValueChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
