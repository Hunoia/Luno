package hunoia.luno.actions.library
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.action.definitions.ParameterDefinition
import hunoia.luno.actions.R
import hunoia.luno.core.AppContext
import hunoia.luno.ui.component.PressIconButton
import hunoia.luno.ui.component.PressTextButton
import hunoia.luno.ui.component.input.rememberControlledTextFieldState
import hunoia.luno.ui.component.row.LunoCardInput
import hunoia.luno.ui.component.row.LunoGroupItem
import hunoia.luno.ui.component.row.LunoInputRow
import hunoia.luno.ui.component.row.LunoSettingsRow
import hunoia.luno.ui.component.row.LunoSwitchRow
import hunoia.luno.ui.component.row.segmentedShape
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.SegmentedGap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
@Composable
fun ParameterEditor(
    definition: ParameterDefinition,
    currentParams: Map<String, JsonElement>,
    onParamChange: (String, String) -> Unit,
    onParamElementChange: (String, JsonElement) -> Unit = { _, _ -> },
    onPickApp: (String) -> Unit = {},
    onPickActivity: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    shape: Shape = LargeShape,
) {
    val required = definition.required
    val errorColor = MaterialTheme.colorScheme.error
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val unselected = stringResource(R.string.action_param_unselected)
    when (definition) {
        is ParameterDefinition.Text -> {
            val value = currentParams[definition.key]?.scalarText() ?: (definition.defaultValue ?: "")
            if (definition.twoRow) {
                // 长内容两行形式：参数名一行、值一行（卡内无边框输入，同链接规则
                // 编辑页的 exclude / template 用法），不嵌套 OutlinedTextField 盒。
                LunoCardInput(
                    value = value,
                    onValueChange = { onParamChange(definition.key, it) },
                    label = definition.label,
                    placeholder = definition.placeholder.orEmpty(),
                    isError = required && value.isBlank(),
                    singleLine = true,
                    shape = shape,
                    modifier = modifier.fillMaxWidth(),
                )
            } else {
                // 行内形式：标签左 + 值右对齐、无外框（同显隐设置页 / 链接规则编辑页），
                // 避免在 LunoGroup 里「卡内套输入框」（ui-guidelines §4.1）。
                LunoInputRow(
                    label = definition.label,
                    value = value,
                    onValueChange = { onParamChange(definition.key, it) },
                    placeholder = definition.placeholder.orEmpty(),
                    isError = required && value.isBlank(),
                    singleLine = true,
                    shape = shape,
                    modifier = modifier.fillMaxWidth(),
                )
            }
        }
        is ParameterDefinition.Bool -> {
            LunoSwitchRow(
                title = definition.label,
                checked = currentParams[definition.key]?.scalarText()?.toBoolean()
                    ?: definition.defaultValue.toBoolean(),
                onCheckedChange = { onParamChange(definition.key, it.toString()) },
                shape = shape,
                modifier = modifier.fillMaxWidth(),
            )
        }
        is ParameterDefinition.KeyValueMap -> {
            KeyValueMapEditor(
                value = currentParams[definition.key],
                onChange = { obj -> onParamElementChange(definition.key, obj) },
                modifier = modifier.fillMaxWidth(),
            )
        }
        is ParameterDefinition.AppSelector -> {
            val selected = currentParams[definition.key]?.scalarText() ?: ""
            var appLabel by remember(selected) { mutableStateOf<String?>(null) }
            LaunchedEffect(selected) {
                appLabel = if (selected.isBlank()) null else resolveAppLabel(selected)
            }
            val userIdKey = definition.userIdKey
            val userId = userIdKey?.let { currentParams[it]?.scalarText()?.toIntOrNull() } ?: 0
            val cloneSuffix = if (userIdKey != null && userId != 0) {
                " · " + stringResource(R.string.app_clone_label)
            } else {
                ""
            }
            val display = when {
                selected.isBlank() -> unselected
                appLabel != null && appLabel != selected -> "$appLabel ($selected)$cloneSuffix"
                else -> selected + cloneSuffix
            }
            val missing = required && selected.isBlank()
            LunoSettingsRow(
                title = definition.label,
                subtitle = display,
                onClick = { onPickApp(definition.key) },
                shape = shape,
                modifier = modifier.fillMaxWidth(),
                subtitleColor = if (missing) errorColor else hintColor,
            )
        }
        is ParameterDefinition.ActivitySelector -> {
            val selected = currentParams[definition.key]?.scalarText() ?: ""
            val packageName = currentParams[definition.packageKey]?.scalarText() ?: ""
            val enabled = packageName.isNotBlank()
            val display = selected.ifBlank { unselected }
            val missing = required && selected.isBlank()
            LunoSettingsRow(
                title = definition.label,
                subtitle = display,
                onClick = { if (enabled) onPickActivity(packageName, definition.key) },
                shape = shape,
                modifier = modifier.fillMaxWidth(),
                enabled = enabled,
                subtitleColor = if (missing && enabled) errorColor else hintColor,
            )
        }
        is ParameterDefinition.TextLarge -> {
            val value = currentParams[definition.key]?.scalarText() ?: (definition.defaultValue ?: "")
            // 多行长内容（如 Shell 命令）：无边框卡内输入，左对齐、可换行（同链接规则
            // 编辑页的 exclude / template 用法），不嵌套 OutlinedTextField 盒。
            LunoCardInput(
                value = value,
                onValueChange = { onParamChange(definition.key, it) },
                label = definition.label,
                isError = required && value.isBlank(),
                singleLine = false,
                minLines = 3,
                shape = shape,
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}

private data class KeyValueRow(val id: Long, val key: String, val value: String)

/**
 * 键值对编辑器（Intent extras / URL query）。
 * 行状态保存在本地，允许空键与重复键；序列化时只写入非空键，空键行只存在于 UI 草稿。
 *
 * 每行一个 [LunoGroupItem]（分段风格下独立小卡、2dp 缝；大卡风格下由组卡托底 + 行间
 * 分割线），不再「一张卡放全部行」。key/value 两格用 [BasicTextField]，无 OutlinedTextField
 * 盒（ui-guidelines §4.1）。组标题由调用方用参数自身的 label。
 */
@Composable
private fun KeyValueMapEditor(
    value: JsonElement?,
    onChange: (JsonObject) -> Unit,
    modifier: Modifier = Modifier,
) {
    val externalRows = remember(value) {
        (value as? JsonObject)
            ?.map { (key, element) -> key to ((element as? JsonPrimitive)?.contentOrNull.orEmpty()) }
            .orEmpty()
    }
    val idGenerator = remember { longArrayOf(0L) }
    fun newRow(key: String, text: String) = KeyValueRow(idGenerator[0]++, key, text)

    var rows by remember { mutableStateOf(externalRows.map { newRow(it.first, it.second) }) }
    var lastEmitted by remember { mutableStateOf(value as? JsonObject) }

    // 外部值变化（切换动作/重置）时重建行；忽略自己 onChange 的回声，避免空键行被抹掉。
    LaunchedEffect(value) {
        if (value != lastEmitted) {
            rows = externalRows.map { newRow(it.first, it.second) }
        }
    }

    fun emit(newRows: List<KeyValueRow>) {
        rows = newRows
        val obj = JsonObject(
            newRows.filter { it.key.isNotBlank() }
                .associate { it.key to JsonPrimitive(it.value) },
        )
        lastEmitted = obj
        onChange(obj)
    }

    val colorScheme = MaterialTheme.colorScheme
    // 行内左右内边距：与 LunoInputRow / LunoCardInput 的 RowInnerPadding（16dp）一致。
    val rowInnerPadding = 16.dp
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SegmentedGap),
    ) {
        rows.forEachIndexed { index, row ->
            key(row.id) {
                LunoGroupItem(
                    shape = segmentedShape(index, rows.size),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rowInnerPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KvField(
                            value = row.key,
                            onValueChange = { newKey ->
                                emit(rows.toMutableList().also { it[index] = it[index].copy(key = newKey) })
                            },
                            placeholder = stringResource(R.string.action_param_kv_key),
                            modifier = Modifier.weight(1f),
                        )
                        KvField(
                            value = row.value,
                            onValueChange = { newValue ->
                                emit(rows.toMutableList().also { it[index] = it[index].copy(value = newValue) })
                            },
                            placeholder = stringResource(R.string.action_param_kv_value),
                            modifier = Modifier.weight(1f),
                        )
                        PressIconButton(
                            onClick = { emit(rows.toMutableList().also { it.removeAt(index) }) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(hunoia.luno.design.R.string.delete),
                                tint = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        // 添加项：文本/图标左缘对齐卡内字段文本（16dp），靠 contentPadding 而非外层 padding。
        PressTextButton(
            onClick = { emit(rows + newRow("", "")) },
            contentPadding = PaddingValues(horizontal = rowInnerPadding),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.action_param_kv_add),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** 键值对编辑器里的无边框单格输入：占位与值叠在同一容器（同 [LunoCardInput] 口径）。 */
@Composable
private fun KvField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val fieldState = rememberControlledTextFieldState(value, onValueChange)
    BasicTextField(
        state = fieldState,
        modifier = modifier,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = colorScheme.onSurface),
        cursorBrush = SolidColor(colorScheme.primary),
        lineLimits = TextFieldLineLimits.SingleLine,
        decorator = TextFieldDecorator { innerField ->
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerField()
            }
        },
    )
}

private suspend fun resolveAppLabel(packageName: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val pm = AppContext.get().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}