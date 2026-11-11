package hunoia.luno.picker.components.rowsettings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.picker.R
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** 内置可配置动作的行设置描述符。 */
internal object BuiltinRowSettings {

    private const val QUICK_TOGGLE_TYPE_ID = "system.toggle"
    private const val OPEN_SETTINGS_TYPE_ID = "system.openSettings"

    private val audioStreams = listOf("MUSIC", "RING", "NOTIFICATION", "ALARM", "VOICE", "SYSTEM")
    private val floatBallVisibilityKeys = listOf("toggle", "true", "false", "HIDE_FOR")
    private val buttonsVisibilityKeys = listOf("true", "false", "HIDE_FOR")
    private val buttonScopeKeys = listOf("ALL", "EXCEPT", "ONLY")

    fun registerAll(register: (ActionRowSettingsDescriptor) -> Unit) {
        register(volumeScrub())
        register(recentsPicker())
        register(floatBallVisibility())
        register(buttonsVisibility())
        register(
            variantsAction(
                OPEN_SETTINGS_TYPE_ID,
                R.string.row_dialog_open_settings,
                R.string.row_settings_settings_page,
            ),
        )
        register(volume())
        register(
            variantsAction(
                QUICK_TOGGLE_TYPE_ID,
                R.string.row_dialog_quick_toggle,
                R.string.row_settings_toggle_kind,
            ),
        )
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
            SettingSlider(
                value = scope.draft.int("stepThresholdDp", RowSettingsDefaults.VolumeScrubStepDp).toFloat(),
                onValueChange = { scope.setInt("stepThresholdDp", it.toInt()) },
                title = stringResource(R.string.row_settings_sensitivity),
                valueDisplay = { "${it.toInt()}dp" },
                valueRange = 4f..64f,
            )
        },
    )

    private fun recentsPicker() = ActionRowSettingsDescriptor(
        typeId = "internal.recentsPicker",
        titleRes = R.string.row_dialog_recents_picker,
        subtitle = { params, context ->
            context.getString(
                if (params.str("layout", RowSettingsDefaults.RecentsPickerLayout) == "HORIZONTAL") {
                    R.string.row_settings_recents_layout_horizontal
                } else {
                    R.string.row_settings_recents_layout_vertical
                },
            )
        },
        canonicalize = { draft ->
            buildJsonObject {
                put(
                    "layout",
                    JsonPrimitive(draft.str("layout", RowSettingsDefaults.RecentsPickerLayout)),
                )
            }
        },
        editor = { scope ->
            val layout = scope.draft.str("layout", RowSettingsDefaults.RecentsPickerLayout)
            SettingChoiceRow(
                title = stringResource(R.string.row_settings_recents_layout),
                options = listOf(
                    stringResource(R.string.row_settings_recents_layout_vertical),
                    stringResource(R.string.row_settings_recents_layout_horizontal),
                ),
                selectedIndex = if (layout == "HORIZONTAL") 1 else 0,
            ) { scope.setStr("layout", if (it == 1) "HORIZONTAL" else "VERTICAL") }
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
            val selectedKey = if (hideForMs > 0) {
                "HIDE_FOR"
            } else {
                scope.draft.str("visible", RowSettingsDefaults.FloatBallVisible)
            }
            SettingChoiceList(
                title = stringResource(R.string.row_settings_visibility),
                options = listOf(
                    stringResource(R.string.row_settings_toggle),
                    stringResource(R.string.row_settings_show),
                    stringResource(R.string.row_settings_hide),
                    stringResource(R.string.row_settings_hide_temporarily),
                ),
                selectedIndex = floatBallVisibilityKeys.indexOf(selectedKey).coerceAtLeast(0),
            ) { index ->
                val key = floatBallVisibilityKeys[index]
                if (key == "HIDE_FOR") {
                    scope.setStr("visible", "toggle")
                    if (hideForMs <= 0) {
                        scope.setLong("hideForMs", RowSettingsDefaults.HideGestureButtonDelayMs)
                    }
                } else {
                    scope.setLong("hideForMs", 0)
                    scope.setStr("visible", key)
                }
            }
            AnimatedVisibility(visible = hideForMs > 0) {
                SettingSlider(
                    value = hideForMs.toFloat(),
                    onValueChange = { scope.setLong("hideForMs", it.toLong()) },
                    title = stringResource(R.string.row_settings_hide_duration),
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

    /**
     * 合并式动作的通用编辑器：选项来自 `ActionDefinition.variants`，不在选择页里另抄一张表。
     * 名称/图标由 `ActionDisplay` 按变体派生，副标题不再重复展现当前变体。
     */
    private fun variantsAction(
        typeId: String,
        titleRes: Int,
        choiceTitleRes: Int,
    ): ActionRowSettingsDescriptor {
        val definition = { ActionDefinitions.byTypeId(typeId) }
        return ActionRowSettingsDescriptor(
            typeId = typeId,
            titleRes = titleRes,
            subtitle = { _, _ -> "" },
            canonicalize = { draft ->
                // 未选/脏值回退到缺省变体（定义表首位），不依赖调用方补值。
                val variant = definition()?.resolvedVariant(draft)
                buildJsonObject {
                    variant?.params?.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
                }
            },
            editor = { scope ->
                val def = definition()
                val variants = def?.variants.orEmpty()
                val selected = variants.indexOfFirst { it == def?.resolvedVariant(scope.draft) }
                    .coerceAtLeast(0)
                SettingChoiceList(
                    title = stringResource(choiceTitleRes),
                    options = variants.map { it.label },
                    selectedIndex = selected,
                ) { index -> scope.setParams(variants[index].params) }
            },
        )
    }

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
            val stream = scope.draft.str("stream", RowSettingsDefaults.AudioStream)
            SettingChoiceList(
                title = stringResource(R.string.row_settings_stream),
                options = audioStreams.map { stringResource(audioStreamLabelRes(it)) },
                selectedIndex = audioStreams.indexOf(stream).coerceAtLeast(0),
            ) { scope.setStr("stream", audioStreams[it]) }
            val direction = scope.draft.str("direction", RowSettingsDefaults.VolumeDirection)
            SettingChoiceRow(
                title = stringResource(R.string.row_settings_direction),
                options = listOf(
                    stringResource(R.string.row_settings_direction_up),
                    stringResource(R.string.row_settings_direction_down),
                ),
                selectedIndex = if (direction == "DOWN") 1 else 0,
            ) { scope.setStr("direction", if (it == 1) "DOWN" else "UP") }
        },
    )

    @Composable
    private fun ButtonsVisibilityEditor(scope: RowSettingsEditorScope) {
        val hideForMs = scope.draft.long("hideForMs", 0)
        val visible = scope.draft.bool("visible", RowSettingsDefaults.ButtonsVisible)
        val visibilityKey = when {
            hideForMs > 0 -> "HIDE_FOR"
            visible -> "true"
            else -> "false"
        }
        SettingChoiceRow(
            title = stringResource(R.string.row_settings_visibility),
            options = listOf(
                stringResource(R.string.row_settings_show),
                stringResource(R.string.row_settings_hide),
                stringResource(R.string.row_settings_hide_temporarily),
            ),
            selectedIndex = buttonsVisibilityKeys.indexOf(visibilityKey).coerceAtLeast(0),
        ) { index ->
            val key = buttonsVisibilityKeys[index]
            if (key == "HIDE_FOR") {
                if (hideForMs <= 0) scope.setLong("hideForMs", RowSettingsDefaults.HideGestureButtonDelayMs)
            } else {
                scope.setLong("hideForMs", 0)
                scope.setBool("visible", key.toBooleanStrict())
            }
        }
        AnimatedVisibility(visible = hideForMs > 0) {
            SettingSlider(
                value = hideForMs.toFloat(),
                onValueChange = { scope.setLong("hideForMs", it.toLong()) },
                title = stringResource(R.string.row_settings_hide_duration),
                valueDisplay = { "${it.toLong()}ms" },
                valueRange = 500f..30000f,
            )
        }
        val target = scope.draft.str("target", RowSettingsDefaults.ButtonsTarget)
        SettingChoiceRow(
            title = stringResource(R.string.row_settings_scope),
            options = listOf(
                stringResource(R.string.row_settings_scope_all),
                stringResource(R.string.row_settings_scope_except),
                stringResource(R.string.row_settings_scope_only),
            ),
            selectedIndex = buttonScopeKeys.indexOf(target).coerceAtLeast(0),
        ) { scope.setStr("target", buttonScopeKeys[it]) }
        val selectedIds = scope.draft.ids("buttonIds").toSet()
        AnimatedVisibility(visible = target != "ALL") {
            SettingMultiChoiceList(
                title = "${stringResource(R.string.row_settings_buttons)} · ${selectedIds.size}",
                options = scope.buttons.map { button ->
                    button.id to button.name.ifBlank {
                        stringResource(hunoia.luno.core.data.R.string.gesture_button)
                    }
                },
                selectedIds = selectedIds,
                onToggle = { id, checked ->
                    val next = if (checked) selectedIds + id else selectedIds - id
                    scope.setStringArray("buttonIds", next.toList())
                },
            )
        }
    }
}

private fun audioStreamLabelRes(stream: String): Int = when (stream) {
    "RING" -> R.string.audio_stream_ring
    "NOTIFICATION" -> R.string.audio_stream_notification
    "ALARM" -> R.string.audio_stream_alarm
    "VOICE" -> R.string.audio_stream_voice
    "SYSTEM" -> R.string.audio_stream_system
    else -> R.string.audio_stream_music
}
