package hunoia.luno.ui.actionselect.components.rowsettings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.actions.R as ActionsR
import hunoia.luno.domain.AppGraph
import hunoia.luno.ui.component.input.LunoTextSlider
import hunoia.luno.ui.component.row.LunoSelectableRow
import hunoia.luno.ui.component.row.selectedSegmentedShape
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SegmentedGap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** 内置可配置动作的行设置描述符。 */
internal object BuiltinRowSettings {

    fun registerAll(register: (ActionRowSettingsDescriptor) -> Unit) {
        register(volumeScrub())
        register(floatBallVisibility())
        register(buttonsVisibility())
        register(openSettings())
        register(volume())
        register(vibrate())
        register(tapLike("accessibility.tap", R.string.row_dialog_tap, longPress = false))
        register(tapLike("accessibility.longPress", R.string.row_dialog_long_press, longPress = true))
        register(tapLike("accessibility.doubleTap", R.string.row_dialog_double_tap, longPress = false))
        register(scroll())
        register(shareText())
        register(swipe())
    }

    private fun volumeScrub() = ActionRowSettingsDescriptor(
        typeId = "internal.volumeScrub",
        titleRes = R.string.row_dialog_volume_scrub,
        subtitle = { params, context ->
            "${context.getString(R.string.row_settings_sensitivity)} · " +
                "${params.int("stepThresholdDp", RowSettingsDefaults.VolumeScrubStepDp)}dp"
        },
        canonicalize = { draft ->
            buildJsonObject {
                put(
                    "stepThresholdDp",
                    JsonPrimitive(draft.int("stepThresholdDp", RowSettingsDefaults.VolumeScrubStepDp)),
                )
            }
        },
        editor = { scope ->
            val step = scope.draft.int("stepThresholdDp", RowSettingsDefaults.VolumeScrubStepDp)
            LunoTextSlider(
                value = step.toFloat(),
                onValueChange = { scope.setInt("stepThresholdDp", it.toInt()) },
                text = stringResource(R.string.row_settings_sensitivity),
                valueDisplay = { "${it.toInt()}dp" },
                valueRange = 4f..64f,
            )
        },
    )

    private fun floatBallVisibility() = ActionRowSettingsDescriptor(
        typeId = "internal.floatBallVisibility",
        titleRes = R.string.row_dialog_float_ball,
        subtitle = { params, context ->
            if (params.long("hideForMs", 0) > 0) {
                context.getString(R.string.row_settings_hide_temporarily) + " · " +
                    params.long("hideForMs", 0) + "ms"
            } else when (params.str("visible", RowSettingsDefaults.FloatBallVisible)) {
                "true" -> context.getString(R.string.row_settings_show)
                "false" -> context.getString(R.string.row_settings_hide)
                else -> context.getString(R.string.row_settings_toggle)
            }
        },
        canonicalize = { draft ->
            val hideForMs = draft.long("hideForMs", 0)
            buildJsonObject {
                put("visible", JsonPrimitive(draft.str("visible", RowSettingsDefaults.FloatBallVisible)))
                if (hideForMs > 0) put("hideForMs", JsonPrimitive(hideForMs))
            }
        },
        editor = { scope ->
            val hideForMs = scope.draft.long("hideForMs", 0)
            LabeledChips(
                R.string.row_settings_visibility,
                listOf(
                    "toggle" to R.string.row_settings_toggle,
                    "true" to R.string.row_settings_show,
                    "false" to R.string.row_settings_hide,
                    "HIDE_FOR" to R.string.row_settings_hide_temporarily,
                ),
                if (hideForMs > 0) "HIDE_FOR"
                else scope.draft.str("visible", RowSettingsDefaults.FloatBallVisible),
            ) {
                if (it == "HIDE_FOR") {
                    scope.setStr("visible", "toggle")
                    if (hideForMs <= 0) {
                        scope.setLong("hideForMs", RowSettingsDefaults.HideGestureButtonDelayMs)
                    }
                } else {
                    scope.setLong("hideForMs", 0)
                    scope.setStr("visible", it)
                }
            }
            if (hideForMs > 0) {
                LunoTextSlider(
                    value = hideForMs.toFloat(),
                    onValueChange = { scope.setLong("hideForMs", it.toLong()) },
                    text = stringResource(R.string.row_settings_hide_duration),
                    valueDisplay = { "${it.toLong()}ms" },
                    valueRange = 500f..30000f,
                )
            }
        },
    )

    private fun buttonsVisibility() = ActionRowSettingsDescriptor(
        typeId = "internal.buttonsVisibility",
        titleRes = R.string.row_dialog_buttons_visibility,
        subtitle = { params, context ->
            val hideForMs = params.long("hideForMs", 0)
            val target = params.str("target", RowSettingsDefaults.ButtonsTarget)
            val scope = when (target) {
                "ALL" -> context.getString(R.string.row_settings_scope_all)
                else -> "${params.ids("buttonIds").size} ${context.getString(R.string.row_settings_buttons)}"
            }
            if (hideForMs > 0) {
                context.getString(R.string.row_settings_hide_temporarily) + " · " +
                    hideForMs + "ms · " + scope
            } else {
                val visible = context.getString(
                    if (params.bool("visible", RowSettingsDefaults.ButtonsVisible)) {
                        R.string.row_settings_show
                    } else {
                        R.string.row_settings_hide
                    },
                )
                "$visible · $scope"
            }
        },
        canonicalize = { draft ->
            val hideForMs = draft.long("hideForMs", 0)
            val target = draft.str("target", RowSettingsDefaults.ButtonsTarget)
            buildJsonObject {
                put("visible", JsonPrimitive(draft.bool("visible", RowSettingsDefaults.ButtonsVisible)))
                put("target", JsonPrimitive(target))
                if (target != "ALL") {
                    put("buttonIds", buildJsonArray { draft.ids("buttonIds").forEach { add(JsonPrimitive(it)) } })
                }
                if (hideForMs > 0) put("hideForMs", JsonPrimitive(hideForMs))
            }
        },
        editor = { scope ->
            ButtonsVisibilityEditor(scope)
        },
    )

    private fun openSettings() = ActionRowSettingsDescriptor(
        typeId = "system.openSettings",
        titleRes = R.string.row_dialog_open_settings,
        subtitle = { params, context ->
            context.getString(settingsPageLabelRes(params.str("page", RowSettingsDefaults.SettingsPage)))
        },
        canonicalize = { draft ->
            buildJsonObject {
                put("page", JsonPrimitive(draft.str("page", RowSettingsDefaults.SettingsPage)))
            }
        },
        editor = { scope ->
            LabeledChips(
                R.string.row_settings_settings_page,
                listOf(
                    "WIFI" to ActionsR.string.settings_page_wifi,
                    "BLUETOOTH" to ActionsR.string.settings_page_bluetooth,
                    "DISPLAY" to ActionsR.string.settings_page_display,
                    "BATTERY" to ActionsR.string.settings_page_battery,
                    "APPS" to ActionsR.string.settings_page_apps,
                    "NETWORK" to ActionsR.string.settings_page_network,
                    "ACCESSIBILITY" to ActionsR.string.settings_page_accessibility,
                    "MAIN" to ActionsR.string.settings_page_main,
                ),
                scope.draft.str("page", RowSettingsDefaults.SettingsPage),
            ) { scope.setStr("page", it) }
        },
    )

    private fun volume() = ActionRowSettingsDescriptor(
        typeId = "system.volume",
        titleRes = R.string.row_dialog_volume,
        subtitle = { params, context ->
            val stream = context.getString(
                audioStreamLabelRes(params.str("stream", RowSettingsDefaults.AudioStream)),
            )
            val direction = context.getString(
                if (params.str("direction", RowSettingsDefaults.VolumeDirection) == "UP") {
                    R.string.row_settings_direction_up
                } else {
                    R.string.row_settings_direction_down
                },
            )
            "$stream · $direction"
        },
        canonicalize = { draft ->
            buildJsonObject {
                put("stream", JsonPrimitive(draft.str("stream", RowSettingsDefaults.AudioStream)))
                put("direction", JsonPrimitive(draft.str("direction", RowSettingsDefaults.VolumeDirection)))
            }
        },
        editor = { scope ->
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
                scope.draft.str("stream", RowSettingsDefaults.AudioStream),
            ) { scope.setStr("stream", it) }
            LabeledChips(
                R.string.row_settings_direction,
                listOf(
                    "UP" to R.string.row_settings_direction_up,
                    "DOWN" to R.string.row_settings_direction_down,
                ),
                scope.draft.str("direction", RowSettingsDefaults.VolumeDirection),
            ) { scope.setStr("direction", it) }
        },
    )

    private fun vibrate() = ActionRowSettingsDescriptor(
        typeId = "system.vibrate",
        titleRes = R.string.row_dialog_vibrate,
        subtitle = { params, _ -> params.str("pattern", RowSettingsDefaults.VibratePattern) },
        canonicalize = { draft ->
            buildJsonObject {
                put("pattern", JsonPrimitive(draft.str("pattern", RowSettingsDefaults.VibratePattern)))
            }
        },
        isValid = { draft ->
            VibratePatternRegex.matches(draft.str("pattern", RowSettingsDefaults.VibratePattern).trim())
        },
        editor = { scope ->
            val text = scope.draft.str("pattern", RowSettingsDefaults.VibratePattern)
            val invalid = !VibratePatternRegex.matches(text.trim())
            LabeledTextField(
                R.string.row_settings_pattern_hint,
                text,
                { scope.setStr("pattern", it) },
                isError = invalid,
                keyboardType = KeyboardType.Text,
            )
            if (invalid) FieldError(stringResource(R.string.row_settings_pattern_invalid))
        },
    )

    private fun tapLike(typeId: String, titleRes: Int, longPress: Boolean) = ActionRowSettingsDescriptor(
        typeId = typeId,
        titleRes = titleRes,
        subtitle = { params, context -> tapSubtitle(params, context) },
        canonicalize = { draft ->
            if (!longPress) {
                tapPointParams(draft)
            } else {
                buildJsonObject {
                    tapPointParams(draft).forEach { (k, v) -> put(k, v) }
                    put("durationMs", JsonPrimitive(draft.long("durationMs", RowSettingsDefaults.LongPressDurationMs)))
                }
            }
        },
        isValid = { draft ->
            draft.bool("useTriggerPoint", RowSettingsDefaults.UseTriggerPoint) ||
                (draft.str("xDp", "").toIntOrNull() != null && draft.str("yDp", "").toIntOrNull() != null)
        },
        editor = { scope -> TapLikeEditor(scope, longPress) },
    )

    private fun scroll() = ActionRowSettingsDescriptor(
        typeId = "accessibility.scroll",
        titleRes = R.string.row_dialog_scroll,
        subtitle = { params, context ->
            context.getString(
                if (params.str("direction", "FORWARD") == "BACKWARD") R.string.row_settings_scroll_up
                else R.string.row_settings_scroll_down
            )
        },
        canonicalize = { draft ->
            buildJsonObject { put("direction", JsonPrimitive(draft.str("direction", "FORWARD"))) }
        },
        editor = { scope ->
            LabeledChips(
                R.string.row_settings_direction,
                listOf(
                    "FORWARD" to R.string.row_settings_scroll_down,
                    "BACKWARD" to R.string.row_settings_scroll_up,
                ),
                scope.draft.str("direction", "FORWARD"),
            ) { scope.setStr("direction", it) }
        },
    )

    private fun shareText() = ActionRowSettingsDescriptor(
        typeId = "intent.shareText",
        titleRes = R.string.row_dialog_share_text,
        subtitle = { params, _ -> params.str("text", "") },
        canonicalize = { draft ->
            buildJsonObject {
                put("text", JsonPrimitive(draft.str("text", "")))
                put("mimeType", JsonPrimitive("text/plain"))
            }
        },
        isValid = { draft -> draft.str("text", "").isNotBlank() },
        editor = { scope ->
            val text = scope.draft.str("text", "")
            LabeledTextField(
                R.string.row_settings_text_content,
                text,
                { scope.setStr("text", it) },
                keyboardType = KeyboardType.Text,
            )
            if (text.isBlank()) FieldError(stringResource(R.string.row_settings_text_required))
        },
    )

    private fun swipe() = ActionRowSettingsDescriptor(
        typeId = "accessibility.swipe",
        titleRes = R.string.row_dialog_swipe,
        subtitle = { params, context ->
            context.getString(
                when (params.str("direction", RowSettingsDefaults.SwipeDirection)) {
                    "DOWN" -> R.string.row_settings_swipe_down
                    "LEFT" -> R.string.row_settings_swipe_left
                    "RIGHT" -> R.string.row_settings_swipe_right
                    "TO_TOP" -> R.string.row_settings_swipe_to_top
                    else -> R.string.row_settings_swipe_up
                },
            )
        },
        canonicalize = { draft ->
            buildJsonObject {
                put("direction", JsonPrimitive(draft.str("direction", RowSettingsDefaults.SwipeDirection)))
            }
        },
        editor = { scope ->
            LabeledChips(
                R.string.row_settings_direction,
                listOf(
                    "UP" to R.string.row_settings_swipe_up,
                    "DOWN" to R.string.row_settings_swipe_down,
                    "LEFT" to R.string.row_settings_swipe_left,
                    "RIGHT" to R.string.row_settings_swipe_right,
                    "TO_TOP" to R.string.row_settings_swipe_to_top,
                ),
                scope.draft.str("direction", RowSettingsDefaults.SwipeDirection),
            ) { scope.setStr("direction", it) }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonsVisibilityEditor(scope: RowSettingsEditorScope) {
    val hideForMs = scope.draft.long("hideForMs", 0)
    val visible = scope.draft.bool("visible", RowSettingsDefaults.ButtonsVisible)
    LabeledChips(
        R.string.row_settings_visibility,
        listOf(
            "true" to R.string.row_settings_show,
            "false" to R.string.row_settings_hide,
            "HIDE_FOR" to R.string.row_settings_hide_temporarily,
        ),
        when {
            hideForMs > 0 -> "HIDE_FOR"
            visible -> "true"
            else -> "false"
        },
    ) {
        if (it == "HIDE_FOR") {
            if (hideForMs <= 0) scope.setLong("hideForMs", RowSettingsDefaults.HideGestureButtonDelayMs)
        } else {
            scope.setLong("hideForMs", 0)
            scope.setBool("visible", it.toBooleanStrict())
        }
    }
    if (hideForMs > 0) {
        LunoTextSlider(
            value = hideForMs.toFloat(),
            onValueChange = { scope.setLong("hideForMs", it.toLong()) },
            text = stringResource(R.string.row_settings_hide_duration),
            valueDisplay = { "${it.toLong()}ms" },
            valueRange = 500f..30000f,
        )
    }
    val target = scope.draft.str("target", RowSettingsDefaults.ButtonsTarget)
    LabeledChips(
        R.string.row_settings_scope,
        listOf(
            "ALL" to R.string.row_settings_scope_all,
            "EXCEPT" to R.string.row_settings_scope_except,
            "ONLY" to R.string.row_settings_scope_only,
        ),
        target,
    ) { scope.setStr("target", it) }
    if (target != "ALL") {
        val selectedIds = scope.draft.ids("buttonIds").toSet()
        Text(
            text = stringResource(R.string.row_settings_buttons) + " · ${selectedIds.size}",
            style = MaterialTheme.typography.labelLarge,
        )
        val selectionFlags = scope.buttons.map { it.id in selectedIds }
        Column(verticalArrangement = Arrangement.spacedBy(SegmentedGap)) {
            scope.buttons.forEachIndexed { index, button ->
                LunoSelectableRow(
                    modifier = Modifier.fillMaxWidth(),
                    title = button.name.ifBlank {
                        stringResource(hunoia.luno.core.data.R.string.gesture_button)
                    },
                    selected = button.id in selectedIds,
                    onSelectedChange = { checked ->
                        val next = if (checked) selectedIds + button.id else selectedIds - button.id
                        scope.setStringArray("buttonIds", next.toList())
                    },
                    shape = selectedSegmentedShape(index, scope.buttons.size, selectionFlags),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TapLikeEditor(scope: RowSettingsEditorScope, longPress: Boolean) {
    val useTriggerPoint = scope.draft.bool("useTriggerPoint", RowSettingsDefaults.UseTriggerPoint)
    val xText = scope.draft.str("xDp", RowSettingsDefaults.TapXDp.toString())
    val yText = scope.draft.str("yDp", RowSettingsDefaults.TapYDp.toString())
    LabeledChips(
        R.string.row_settings_fall_point,
        listOf(
            "true" to R.string.row_settings_trigger_point,
            "false" to R.string.row_settings_fixed_point,
        ),
        if (useTriggerPoint) "true" else "false",
    ) { scope.setBool("useTriggerPoint", it.toBooleanStrict()) }
    if (!useTriggerPoint) {
        var pickHint by remember { mutableStateOf("") }
        TextButton(onClick = {
            val ok = AppGraph.runtime.showPointPicker(
                initialXdp = xText.toIntOrNull(),
                initialYdp = yText.toIntOrNull(),
                onCommit = { x, y ->
                    scope.setInt("xDp", x)
                    scope.setInt("yDp", y)
                },
                onDismiss = {},
                onUnavailable = {
                    pickHint = scope.context.getString(R.string.row_settings_point_picker_failed)
                },
            )
            pickHint = if (ok) "" else scope.context.getString(R.string.row_settings_point_picker_failed)
        }) { Text(stringResource(R.string.point_pick)) }
        if (pickHint.isNotEmpty()) Text(text = pickHint, style = MaterialTheme.typography.bodySmall)
        LabeledTextField(
            R.string.row_settings_coord_x,
            xText,
            { scope.setStr("xDp", it) },
            isError = xText.toIntOrNull() == null,
            keyboardType = KeyboardType.Number,
        )
        LabeledTextField(
            R.string.row_settings_coord_y,
            yText,
            { scope.setStr("yDp", it) },
            isError = yText.toIntOrNull() == null,
            keyboardType = KeyboardType.Number,
        )
        if (xText.toIntOrNull() == null || yText.toIntOrNull() == null) {
            FieldError(stringResource(R.string.row_settings_coord_invalid))
        }
    }
    if (longPress) {
        val duration = scope.draft.long("durationMs", RowSettingsDefaults.LongPressDurationMs)
        LunoTextSlider(
            value = duration.toFloat(),
            onValueChange = { scope.setLong("durationMs", it.toLong()) },
            text = stringResource(R.string.row_settings_duration),
            valueDisplay = { "${it.toLong()}ms" },
            valueRange = 100f..10000f,
        )
    }
}

private fun settingsPageLabelRes(page: String): Int = when (page) {
    "WIFI" -> ActionsR.string.settings_page_wifi
    "BLUETOOTH" -> ActionsR.string.settings_page_bluetooth
    "DISPLAY" -> ActionsR.string.settings_page_display
    "BATTERY" -> ActionsR.string.settings_page_battery
    "APPS" -> ActionsR.string.settings_page_apps
    "NETWORK" -> ActionsR.string.settings_page_network
    "ACCESSIBILITY" -> ActionsR.string.settings_page_accessibility
    else -> ActionsR.string.settings_page_main
}

private fun audioStreamLabelRes(stream: String): Int = when (stream) {
    "RING" -> R.string.audio_stream_ring
    "NOTIFICATION" -> R.string.audio_stream_notification
    "ALARM" -> R.string.audio_stream_alarm
    "VOICE" -> R.string.audio_stream_voice
    "SYSTEM" -> R.string.audio_stream_system
    else -> R.string.audio_stream_music
}

private fun tapSubtitle(params: JsonObject, context: Context): String {
    val x = params.str("xDp", "").toIntOrNull()
    val y = params.str("yDp", "").toIntOrNull()
    return if (params.bool("useTriggerPoint", RowSettingsDefaults.UseTriggerPoint) || x == null || y == null) {
        context.getString(R.string.row_settings_trigger_point)
    } else {
        context.getString(R.string.row_settings_coord_dp, x, y)
    }
}

@Composable
private fun FieldError(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabeledChips(
    labelRes: Int,
    options: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, textRes) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(textRes)) },
                shape = LargeShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = null,
            )
        }
    }
}

@Composable
private fun LabeledTextField(
    labelRes: Int,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(labelRes)) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
