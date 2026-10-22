package hunoia.luno.ui.actionselect.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.defaults.ActionSettingsDefaults
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.service.DispatchClient
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.sheet.OptimizedScrollState
import hunoia.luno.ui.component.sheet.SheetFooterActions
import hunoia.luno.ui.component.sheet.SheetHeader
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** 参数改在动作选择页行尾「设置」Sheet 里配的动作类型。 */
val RowSettingsActionTypeIds: Set<String> = setOf(
    "internal.volumeScrub",
    "internal.floatBallVisibility",
    "internal.buttonsVisibility",
    "system.openSettings",
    "system.volume",
    "system.vibrate",
    "accessibility.tap",
    "accessibility.longPress",
    "accessibility.doubleTap",
    "accessibility.scroll",
    "accessibility.swipe",
    "intent.shareText",
)

/**
 * 每类动作的参数默认值：副标题、Sheet 草稿、恢复默认共用这一份。
 * 空 params 即等价于这里的值（与运行时读取侧的 fallback 保持一致）。
 */
private object RowSettingsDefaults {
    val VolumeScrubStepDp = ActionSettingsDefaults.VolumeScrubStepThresholdDp
    val HideGestureButtonDelayMs = ActionSettingsDefaults.HideGestureButtonDelayMs
    const val FloatBallVisible = "toggle"
    const val ButtonsVisible = false
    const val ButtonsTarget = "ALL"
    const val SettingsPage = "MAIN"
    const val AudioStream = "MUSIC"
    const val VolumeDirection = "UP"
    const val VibratePattern = "0,500"
    const val UseTriggerPoint = false
    val LongPressDurationMs = 1000L
    const val SwipeDirection = "UP"
    const val TapXDp = 0
    const val TapYDp = 0
}

private val VibratePatternRegex = Regex("""^\d+(\s*,\s*\d+)*$""")

private fun JsonObject.str(key: String, default: String): String =
    (this[key] as? JsonPrimitive)?.contentOrNull ?: default

private fun JsonObject.int(key: String, default: Int): Int =
    str(key, "").toIntOrNull() ?: default

private fun JsonObject.long(key: String, default: Long): Long =
    str(key, "").toLongOrNull() ?: default

private fun JsonObject.bool(key: String, default: Boolean): Boolean =
    str(key, "").toBooleanStrictOrNull() ?: default

private fun JsonObject.ids(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

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
 * 与 [ActionRowSettingsSheet] 共用 [RowSettingsDefaults] 与 canonical 参数构建。
 */
fun rowSettingsSubtitle(action: StoredAction, context: Context): String =
    rowSettingsSubtitle(action.typeId, action.params, context)

private fun rowSettingsSubtitle(typeId: String, params: JsonObject, context: Context): String = when (typeId) {
    "internal.volumeScrub" ->
        "${context.getString(R.string.row_settings_sensitivity)} · " +
            "${params.int("stepThresholdDp", RowSettingsDefaults.VolumeScrubStepDp)}dp"
    "internal.floatBallVisibility" ->
        if ((params.long("hideForMs", 0)) > 0) {
            context.getString(R.string.row_settings_hide_temporarily) + " · " +
                params.long("hideForMs", 0) + "ms"
        } else when (params.str("visible", RowSettingsDefaults.FloatBallVisible)) {
            "true" -> context.getString(R.string.row_settings_show)
            "false" -> context.getString(R.string.row_settings_hide)
            else -> context.getString(R.string.row_settings_toggle)
        }
    "internal.buttonsVisibility" -> {
        if ((params.long("hideForMs", 0)) > 0) {
            context.getString(R.string.row_settings_hide_temporarily) + " · " +
                params.long("hideForMs", 0) + "ms"
        } else {
        val visible = context.getString(
            if (params.bool("visible", RowSettingsDefaults.ButtonsVisible)) {
                R.string.row_settings_show
            } else {
                R.string.row_settings_hide
            },
        )
        val target = params.str("target", RowSettingsDefaults.ButtonsTarget)
        val scope = when (target) {
            "ALL" -> context.getString(R.string.row_settings_scope_all)
            else -> "${params.ids("buttonIds").size} ${context.getString(R.string.row_settings_buttons)}"
        }
        "$visible · $scope"
        }
    }
    "system.openSettings" ->
        context.getString(settingsPageLabelRes(params.str("page", RowSettingsDefaults.SettingsPage)))
    "system.volume" -> {
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
    }
    "system.vibrate" -> params.str("pattern", RowSettingsDefaults.VibratePattern)
    "accessibility.tap" -> tapSubtitle(params, context)
    "accessibility.doubleTap" -> tapSubtitle(params, context)
    "accessibility.scroll" -> context.getString(
        if (params.str("direction", "FORWARD") == "BACKWARD") R.string.row_settings_scroll_up
        else R.string.row_settings_scroll_down
    )
    "accessibility.longPress" ->
        "${tapSubtitle(params, context)} · " +
            "${params.long("durationMs", RowSettingsDefaults.LongPressDurationMs)}ms"
    "intent.shareText" -> params.str("text", "")
    "accessibility.swipe" -> context.getString(
        when (params.str("direction", RowSettingsDefaults.SwipeDirection)) {
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
 * 行尾设置 Sheet：按 typeId 渲染配置项。
 *
 * - 底部 Sheet 而不是 AlertDialog：大字体 / 多选项时空间够用，也和其他设置页同语言。
 * - 整个内容只有一个 `verticalScroll`：触钮多选不再嵌套滚动。
 * - 字段草稿是一份 `JsonObject`：改哪个键写哪个键，确认时经 [canonicalParams] 规范化；
 *   标题下方实时显示副标题预览，「恢复默认」清空草稿即回默认值。
 * - 非法坐标 / 振动 pattern 会禁用保存，不再静默写 0。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionRowSettingsSheet(
    action: StoredAction,
    onDismiss: () -> Unit,
    onConfirm: (StoredAction) -> Unit,
) {
    val context = LocalContext.current
    val typeId = action.typeId
    var draft by remember(action) { mutableStateOf(action.params) }
    val buttons by GestureConfig.buttons.collectAsStateWithLifecycle(initialValue = emptyList())
    val scrollState = rememberScrollState()

    fun setStr(key: String, value: String) {
        draft = JsonObject(draft + (key to JsonPrimitive(value)))
    }

    fun setBool(key: String, value: Boolean) {
        draft = JsonObject(draft + (key to JsonPrimitive(value)))
    }

    fun setInt(key: String, value: Int) {
        draft = JsonObject(draft + (key to JsonPrimitive(value)))
    }

    fun setLong(key: String, value: Long) {
        draft = JsonObject(draft + (key to JsonPrimitive(value)))
    }

    val isTapLike = typeId == "accessibility.tap" || typeId == "accessibility.longPress" ||
        typeId == "accessibility.doubleTap"
    val useTriggerPoint = draft.bool("useTriggerPoint", RowSettingsDefaults.UseTriggerPoint)
    val xText = draft.str("xDp", RowSettingsDefaults.TapXDp.toString())
    val yText = draft.str("yDp", RowSettingsDefaults.TapYDp.toString())
    val patternText = draft.str("pattern", RowSettingsDefaults.VibratePattern)

    val coordsInvalid = isTapLike && !useTriggerPoint &&
        (xText.toIntOrNull() == null || yText.toIntOrNull() == null)
    val patternInvalid = typeId == "system.vibrate" && !VibratePatternRegex.matches(patternText.trim())
    val textInvalid = typeId == "intent.shareText" && draft.str("text", "").isBlank()

    val canonical = canonicalParams(typeId, draft)

    LunoSheet(
        onDismissRequest = onDismiss,
        scrollState = OptimizedScrollState.Scroll(scrollState),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = PageGutter),
            verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
        ) {
            SheetHeader(
                title = stringResource(paramSheetTitleRes(typeId)),
                trailing = {
                    TextButton(onClick = { draft = JsonObject(emptyMap()) }) {
                        Text(stringResource(R.string.row_settings_reset))
                    }
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PageGutter),
                verticalArrangement = Arrangement.spacedBy(CardInnerSpacing),
            ) {
                when (typeId) {
                    "internal.volumeScrub" -> {
                        val step = draft.int("stepThresholdDp", RowSettingsDefaults.VolumeScrubStepDp)
                        LabeledSlider(
                            R.string.row_settings_sensitivity,
                            step.toFloat(),
                            4f..64f,
                            "${step}dp",
                        ) { setInt("stepThresholdDp", it.toInt()) }
                    }
                    "internal.floatBallVisibility" -> {
                        val hideForMs = draft.long("hideForMs", 0)
                        if (hideForMs > 0) {
                            LabeledSlider(
                                R.string.row_settings_hide_duration,
                                hideForMs.toFloat(),
                                500f..30000f,
                                "${hideForMs}ms",
                            ) { setLong("hideForMs", it.toLong()) }
                            TextButton(onClick = {
                                setLong("hideForMs", 0)
                                setStr("visible", "toggle")
                            }) {
                                Text(stringResource(R.string.row_settings_back_to_normal))
                            }
                        } else {
                            LabeledChips(
                                R.string.row_settings_visibility,
                                listOf(
                                    "toggle" to R.string.row_settings_toggle,
                                    "true" to R.string.row_settings_show,
                                    "false" to R.string.row_settings_hide,
                                    "HIDE_FOR" to R.string.row_settings_hide_temporarily,
                                ),
                                draft.str("visible", RowSettingsDefaults.FloatBallVisible),
                            ) {
                                // 选临时隐藏时预填时长，否则 hideForMs 恒 0、滑杆分支永不出现。
                                if (it == "HIDE_FOR") {
                                    setStr("visible", "toggle")
                                    setLong("hideForMs", 5000L)
                                } else {
                                    setStr("visible", it)
                                }
                            }
                        }
                    }
                    "internal.buttonsVisibility" -> {
                        val hideForMs = draft.long("hideForMs", 0)
                        if (hideForMs > 0) {
                            LabeledSlider(
                                R.string.row_settings_hide_duration,
                                hideForMs.toFloat(),
                                500f..30000f,
                                "${hideForMs}ms",
                            ) { setLong("hideForMs", it.toLong()) }
                            TextButton(onClick = { setLong("hideForMs", 0) }) {
                                Text(stringResource(R.string.row_settings_back_to_normal))
                            }
                        } else {
                        val visible = draft.bool("visible", RowSettingsDefaults.ButtonsVisible)
                        LabeledChips(
                            R.string.row_settings_visibility,
                            listOf(
                                "true" to R.string.row_settings_show,
                                "false" to R.string.row_settings_hide,
                                "HIDE_FOR" to R.string.row_settings_hide_temporarily,
                            ),
                            if (visible) "true" else "false",
                        ) {
                            if (it == "HIDE_FOR") {
                                setLong("hideForMs", RowSettingsDefaults.HideGestureButtonDelayMs)
                            } else {
                                setBool("visible", it.toBooleanStrict())
                            }
                        }
                        val target = draft.str("target", RowSettingsDefaults.ButtonsTarget)
                        LabeledChips(
                            R.string.row_settings_scope,
                            listOf(
                                "ALL" to R.string.row_settings_scope_all,
                                "EXCEPT" to R.string.row_settings_scope_except,
                                "ONLY" to R.string.row_settings_scope_only,
                            ),
                            target,
                        ) { setStr("target", it) }
                        if (target != "ALL") {
                            val selectedIds = draft.ids("buttonIds").toSet()
                            Text(
                                text = stringResource(R.string.row_settings_buttons) +
                                    " · ${selectedIds.size}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            // 不再套独立滚动框：整张 Sheet 只有一个滚动容器，避免嵌套滚动抢手势
                            buttons.forEach { button ->
                                val checked = button.id in selectedIds
                                fun toggle() {
                                    val next = if (checked) selectedIds - button.id else selectedIds + button.id
                                    draft = JsonObject(
                                        draft + (
                                            "buttonIds" to buildJsonArray {
                                                buttons.forEach { b -> if (b.id in next) add(JsonPrimitive(b.id)) }
                                            }
                                            ),
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { toggle() },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 整行可点，Checkbox 只做展示，避免点 checkbox 时双重切换
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Text(
                                        text = button.name.ifBlank { button.id },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
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
                            draft.str("page", RowSettingsDefaults.SettingsPage),
                        ) { setStr("page", it) }
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
                            draft.str("stream", RowSettingsDefaults.AudioStream),
                        ) { setStr("stream", it) }
                        LabeledChips(
                            R.string.row_settings_direction,
                            listOf(
                                "UP" to R.string.row_settings_direction_up,
                                "DOWN" to R.string.row_settings_direction_down,
                            ),
                            draft.str("direction", RowSettingsDefaults.VolumeDirection),
                        ) { setStr("direction", it) }
                    }
                    "system.vibrate" -> {
                        LabeledTextField(
                            R.string.row_settings_pattern_hint,
                            patternText,
                            { setStr("pattern", it) },
                            isError = patternInvalid,
                            keyboardType = KeyboardType.Text,
                        )
                        if (patternInvalid) {
                            FieldError(stringResource(R.string.row_settings_pattern_invalid))
                        }
                    }
                    "accessibility.tap", "accessibility.longPress", "accessibility.doubleTap" -> {
                        LabeledChips(
                            R.string.row_settings_fall_point,
                            listOf(
                                "true" to R.string.row_settings_trigger_point,
                                "false" to R.string.row_settings_fixed_point,
                            ),
                            if (useTriggerPoint) "true" else "false",
                        ) { setBool("useTriggerPoint", it.toBooleanStrict()) }
                        if (!useTriggerPoint) {
                            // 屏幕取点：经 :service 侧 overlay，回填后仍可在坐标框手改。
                            var pickHint by remember { mutableStateOf("") }
                            TextButton(onClick = {
                                val ok = DispatchClient.showPointPicker(
                                    initialXdp = xText.toIntOrNull(),
                                    initialYdp = yText.toIntOrNull(),
                                    onCommit = { x, y ->
                                        setInt("xDp", x)
                                        setInt("yDp", y)
                                    },
                                    onDismiss = {},
                                    onUnavailable = {
                                        pickHint = context.getString(R.string.row_settings_point_picker_failed)
                                    },
                                )
                                pickHint = if (ok) "" else context.getString(R.string.row_settings_point_picker_failed)
                            }) { Text(stringResource(R.string.point_pick)) }
                            if (pickHint.isNotEmpty()) {
                                Text(text = pickHint, style = MaterialTheme.typography.bodySmall)
                            }
                            LabeledTextField(
                                R.string.row_settings_coord_x,
                                xText,
                                { setStr("xDp", it) },
                                isError = xText.toIntOrNull() == null,
                                keyboardType = KeyboardType.Number,
                            )
                            LabeledTextField(
                                R.string.row_settings_coord_y,
                                yText,
                                { setStr("yDp", it) },
                                isError = yText.toIntOrNull() == null,
                                keyboardType = KeyboardType.Number,
                            )
                            if (coordsInvalid) {
                                FieldError(stringResource(R.string.row_settings_coord_invalid))
                            }
                        }
                        if (typeId == "accessibility.longPress") {
                            val duration = draft.long("durationMs", RowSettingsDefaults.LongPressDurationMs)
                            LabeledSlider(
                                R.string.row_settings_duration,
                                duration.toFloat(),
                                100f..10000f,
                                "${duration}ms",
                            ) { setLong("durationMs", it.toLong()) }
                        }
                    }
                    "accessibility.scroll" -> {
                        LabeledChips(
                            R.string.row_settings_direction,
                            listOf(
                                "FORWARD" to R.string.row_settings_scroll_down,
                                "BACKWARD" to R.string.row_settings_scroll_up,
                            ),
                            draft.str("direction", "FORWARD"),
                        ) { setStr("direction", it) }
                    }
                    "intent.shareText" -> {
                        LabeledTextField(
                            R.string.row_settings_text_content,
                            draft.str("text", ""),
                            { setStr("text", it) },
                            keyboardType = KeyboardType.Text,
                        )
                        if (textInvalid) {
                            FieldError(stringResource(R.string.row_settings_text_required))
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
                            draft.str("direction", RowSettingsDefaults.SwipeDirection),
                        ) { setStr("direction", it) }
                    }
                }
            }
            SheetFooterActions(
                onDismissRequest = onDismiss,
                onConfirm = {
                    onConfirm(action.copy(params = canonical))
                    onDismiss()
                },
                confirmLabel = stringResource(R.string.ok),
                confirmEnabled = !coordsInvalid && !patternInvalid && !textInvalid,
            )
        }
    }
}

/** 只写当前类型关心的键，并把文本/数字/布尔规范成运行时读取的形态。 */
private fun canonicalParams(typeId: String, draft: JsonObject): JsonObject = when (typeId) {
    "internal.volumeScrub" -> buildJsonObject {
        put(
            "stepThresholdDp",
            JsonPrimitive(draft.int("stepThresholdDp", RowSettingsDefaults.VolumeScrubStepDp)),
        )
    }
    "internal.floatBallVisibility" -> {
        val hideForMs = draft.long("hideForMs", 0)
        buildJsonObject {
            put("visible", JsonPrimitive(draft.str("visible", RowSettingsDefaults.FloatBallVisible)))
            if (hideForMs > 0) put("hideForMs", JsonPrimitive(hideForMs))
        }
    }
    "internal.buttonsVisibility" -> {
        val hideForMs = draft.long("hideForMs", 0)
        buildJsonObject {
            put("visible", JsonPrimitive(draft.bool("visible", RowSettingsDefaults.ButtonsVisible)))
            if (hideForMs > 0) {
                put("hideForMs", JsonPrimitive(hideForMs))
            } else {
                val target = draft.str("target", RowSettingsDefaults.ButtonsTarget)
                put("target", JsonPrimitive(target))
                if (target != "ALL") {
                    put("buttonIds", buildJsonArray { draft.ids("buttonIds").forEach { add(JsonPrimitive(it)) } })
                }
            }
        }
    }
    "system.openSettings" -> buildJsonObject {
        put("page", JsonPrimitive(draft.str("page", RowSettingsDefaults.SettingsPage)))
    }
    "system.volume" -> buildJsonObject {
        put("stream", JsonPrimitive(draft.str("stream", RowSettingsDefaults.AudioStream)))
        put("direction", JsonPrimitive(draft.str("direction", RowSettingsDefaults.VolumeDirection)))
    }
    "system.vibrate" -> buildJsonObject {
        put("pattern", JsonPrimitive(draft.str("pattern", RowSettingsDefaults.VibratePattern)))
    }
    "accessibility.tap" -> tapPointParams(draft)
    "accessibility.doubleTap" -> tapPointParams(draft)
    "intent.shareText" -> buildJsonObject {
        put("text", JsonPrimitive(draft.str("text", "")))
        put("mimeType", JsonPrimitive("text/plain"))
    }
    "accessibility.scroll" -> buildJsonObject {
        put("direction", JsonPrimitive(draft.str("direction", "FORWARD")))
    }
    "accessibility.longPress" -> buildJsonObject {
        tapPointParams(draft).forEach { (k, v) -> put(k, v) }
        put("durationMs", JsonPrimitive(draft.long("durationMs", RowSettingsDefaults.LongPressDurationMs)))
    }
    "accessibility.swipe" -> buildJsonObject {
        put("direction", JsonPrimitive(draft.str("direction", RowSettingsDefaults.SwipeDirection)))
    }
    else -> JsonObject(emptyMap())
}

private fun tapPointParams(draft: JsonObject): JsonObject =
    if (draft.bool("useTriggerPoint", RowSettingsDefaults.UseTriggerPoint)) {
        buildJsonObject { put("useTriggerPoint", JsonPrimitive(true)) }
    } else {
        buildJsonObject {
            put("useTriggerPoint", JsonPrimitive(false))
            put("xDp", JsonPrimitive(draft.str("xDp", "").toIntOrNull() ?: RowSettingsDefaults.TapXDp))
            put("yDp", JsonPrimitive(draft.str("yDp", "").toIntOrNull() ?: RowSettingsDefaults.TapYDp))
        }
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

private fun paramSheetTitleRes(typeId: String): Int = when (typeId) {
    "internal.volumeScrub" -> R.string.row_dialog_volume_scrub
    "internal.floatBallVisibility" -> R.string.row_dialog_float_ball
    "internal.buttonsVisibility" -> R.string.row_dialog_buttons_visibility
    "system.openSettings" -> R.string.row_dialog_open_settings
    "system.volume" -> R.string.row_dialog_volume
    "system.vibrate" -> R.string.row_dialog_vibrate
    "accessibility.tap" -> R.string.row_dialog_tap
    "accessibility.longPress" -> R.string.row_dialog_long_press
    "accessibility.doubleTap" -> R.string.row_dialog_double_tap
    "accessibility.scroll" -> R.string.row_dialog_scroll
    "intent.shareText" -> R.string.row_dialog_share_text
    "accessibility.swipe" -> R.string.row_dialog_swipe
    else -> R.string.row_settings_settings_page
}

@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
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

/** 选项多时用 FlowRow 换行，不再一行塞 8 个 chip 被弹窗裁掉。 */
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
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}
