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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.defaults.ActionSettingsDefaults
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.service.DispatchClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/**
 * 行副标题：展示当前生效值（未配置时即默认值）。
 * 与 [ActionRowSettingsDialog] 同一套键位与默认值，改其一须同步另一个。
 */
fun rowSettingsSubtitle(action: StoredAction): String = when (action.typeId) {
    "internal.volumeScrub" ->
        "灵敏度 ${action.paramInt("stepThresholdDp") ?: ActionSettingsDefaults.VolumeScrubStepThresholdDp}dp"
    "internal.hideGestureButton" ->
        "${action.paramStr("delayMs")?.toLongOrNull() ?: ActionSettingsDefaults.HideGestureButtonDelayMs}ms"
    "internal.floatBallVisibility" ->
        when (action.paramStr("visible") ?: "toggle") {
            "true" -> "显示"; "false" -> "隐藏"; else -> "切换"
        }
    "internal.buttonsVisibility" -> {
        val visible = if (action.paramBool("visible", false)) "显示" else "隐藏"
        val target = action.paramStr("target") ?: "ALL"
        val scope = when (target) {
            "ALL" -> "全部触钮"
            else -> "${action.paramIds().size} 个触钮"
        }
        "$visible · $scope"
    }
    "system.openSettings" ->
        when (action.paramStr("page") ?: "MAIN") {
            "WIFI" -> "Wi-Fi"; "BLUETOOTH" -> "蓝牙"; "DISPLAY" -> "显示"
            "BATTERY" -> "电池"; "APPS" -> "应用"; "NETWORK" -> "网络"
            "ACCESSIBILITY" -> "无障碍"; else -> "主设置"
        }
    "system.volume" -> {
        val stream = when (action.paramStr("stream") ?: "MUSIC") {
            "RING" -> "铃声"; "NOTIFICATION" -> "通知"; "ALARM" -> "闹钟"
            "VOICE" -> "通话"; "SYSTEM" -> "系统"; else -> "媒体"
        }
        val direction = if ((action.paramStr("direction") ?: "UP") == "UP") "增大" else "减小"
        "$stream · $direction"
    }
    "system.vibrate" -> action.paramStr("pattern") ?: "0,500"
    "accessibility.tap" -> tapSubtitle(action)
    "accessibility.longPress" -> {
        val duration = action.paramStr("durationMs")?.toLongOrNull() ?: 1000L
        "${tapSubtitle(action)} · ${duration}ms"
    }
    "accessibility.swipe" ->
        when (action.paramStr("direction") ?: "UP") {
            "DOWN" -> "向下"; "LEFT" -> "向左"; "RIGHT" -> "向右"; "TO_TOP" -> "到顶部"; else -> "向上"
        }
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
    // 各类型共用一组草稿状态；确认时只取当前类型关心的字段组包。
    var step by remember {
        mutableStateOf((action.paramStr("stepThresholdDp")?.toIntOrNull()
            ?: ActionSettingsDefaults.VolumeScrubStepThresholdDp).toFloat())
    }
    var delayMs by remember {
        mutableStateOf((action.paramStr("delayMs")?.toLongOrNull()
            ?: ActionSettingsDefaults.HideGestureButtonDelayMs).toFloat())
    }
    var ballVisible by remember { mutableStateOf(action.paramStr("visible") ?: "toggle") }
    var btnVisible by remember {
        mutableStateOf(action.paramStr("visible")?.toBooleanStrictOrNull() ?: false)
    }
    var target by remember { mutableStateOf(action.paramStr("target") ?: "ALL") }
    var selectedIds by remember { mutableStateOf(action.paramIds().toSet()) }
    var settingsPage by remember { mutableStateOf(action.paramStr("page") ?: "MAIN") }
    var stream by remember { mutableStateOf(action.paramStr("stream") ?: "MUSIC") }
    var volumeDirection by remember { mutableStateOf(action.paramStr("direction") ?: "UP") }
    var pattern by remember { mutableStateOf(action.paramStr("pattern") ?: "0,500") }
    var useTriggerPoint by remember {
        mutableStateOf(action.paramBool("useTriggerPoint", false))
    }
    var xDp by remember { mutableStateOf((action.paramInt("xDp") ?: 0).toString()) }
    var yDp by remember { mutableStateOf((action.paramInt("yDp") ?: 0).toString()) }
    var durationMs by remember {
        mutableStateOf((action.paramStr("durationMs")?.toLongOrNull() ?: 1000L).toFloat())
    }
    var swipeDirection by remember { mutableStateOf(action.paramStr("direction") ?: "UP") }
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
        title = { Text(text = paramDialogTitle(action.typeId)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (action.typeId) {
                    "internal.volumeScrub" -> {
                        LabeledSlider("灵敏度", step, 4f..64f, "${step.toInt()}dp") { step = it }
                    }
                    "internal.hideGestureButton" -> {
                        LabeledSlider("隐藏触钮时长", delayMs, 0f..3000f, "${delayMs.toInt()}ms") { delayMs = it }
                    }
                    "internal.floatBallVisibility" -> {
                        LabeledChips(
                            "显隐",
                            listOf("toggle" to "切换", "true" to "显示", "false" to "隐藏"),
                            ballVisible,
                        ) { ballVisible = it }
                    }
                    "internal.buttonsVisibility" -> {
                        LabeledChips(
                            "显隐",
                            listOf("true" to "显示", "false" to "隐藏"),
                            if (btnVisible) "true" else "false",
                        ) { btnVisible = it.toBooleanStrict() }
                        LabeledChips(
                            "作用范围",
                            listOf("ALL" to "全部触钮", "EXCEPT" to "除选中外", "ONLY" to "仅选中"),
                            target,
                        ) { target = it }
                        if (target != "ALL") {
                            Text(text = "触钮", style = MaterialTheme.typography.labelLarge)
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
                            "设置页",
                            listOf(
                                "WIFI" to "Wi-Fi", "BLUETOOTH" to "蓝牙", "DISPLAY" to "显示",
                                "BATTERY" to "电池", "APPS" to "应用", "NETWORK" to "网络",
                                "ACCESSIBILITY" to "无障碍", "MAIN" to "主设置",
                            ),
                            settingsPage,
                        ) { settingsPage = it }
                    }
                    "system.volume" -> {
                        LabeledChips(
                            "音频流",
                            listOf(
                                "MUSIC" to "媒体", "RING" to "铃声", "NOTIFICATION" to "通知",
                                "ALARM" to "闹钟", "VOICE" to "通话", "SYSTEM" to "系统",
                            ),
                            stream,
                        ) { stream = it }
                        LabeledChips("方向", listOf("UP" to "增大", "DOWN" to "减小"), volumeDirection) {
                            volumeDirection = it
                        }
                    }
                    "system.vibrate" -> {
                        LabeledTextField("振动模式（毫秒，逗号分隔）", pattern) { pattern = it }
                    }
                    "accessibility.tap", "accessibility.longPress" -> {
                        LabeledChips(
                            "落点",
                            listOf("true" to "触发位置", "false" to "固定坐标"),
                            if (useTriggerPoint) "true" else "false",
                        ) { useTriggerPoint = it.toBooleanStrict() }
                        if (!useTriggerPoint) {
                            // 屏幕取点：经 :service 侧 overlay，回填后仍可在坐标框手改。
                            val context = LocalContext.current
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
                                pickHint = if (ok) "" else "取点服务未就绪"
                            }) { Text("屏幕取点") }
                            if (pickHint.isNotEmpty()) {
                                Text(text = pickHint, style = MaterialTheme.typography.bodySmall)
                            }
                            LabeledTextField("横坐标 dp", xDp) { xDp = it }
                            LabeledTextField("纵坐标 dp", yDp) { yDp = it }
                        }
                        if (action.typeId == "accessibility.longPress") {
                            LabeledSlider("时长", durationMs, 100f..10000f, "${durationMs.toInt()}ms") {
                                durationMs = it
                            }
                        }
                    }
                    "accessibility.swipe" -> {
                        LabeledChips(
                            "方向",
                            listOf(
                                "UP" to "向上", "DOWN" to "向下", "LEFT" to "向左",
                                "RIGHT" to "向右", "TO_TOP" to "到顶部",
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
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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

private fun tapSubtitle(action: StoredAction): String {
    val x = action.paramInt("xDp")
    val y = action.paramInt("yDp")
    return if (action.paramBool("useTriggerPoint", false) || x == null || y == null) {
        "触发位置"
    } else {
        "$x, $y dp"
    }
}

private fun paramDialogTitle(typeId: String): String = when (typeId) {
    "internal.volumeScrub" -> "滑动调音量"
    "internal.hideGestureButton" -> "隐藏触钮"
    "internal.floatBallVisibility" -> "悬浮球显隐"
    "internal.buttonsVisibility" -> "触钮显隐"
    "system.openSettings" -> "打开设置页"
    "system.volume" -> "音量控制"
    "system.vibrate" -> "振动"
    "accessibility.tap" -> "点击"
    "accessibility.longPress" -> "长按"
    "accessibility.swipe" -> "滑动"
    else -> "设置"
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Text(text = "$label · $valueText", style = MaterialTheme.typography.labelLarge)
    Slider(value = value, onValueChange = onValueChange, valueRange = range)
}

@Composable
private fun LabeledChips(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(text = label, style = MaterialTheme.typography.labelLarge)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, text) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(text) },
            )
        }
    }
}

@Composable
private fun LabeledTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
