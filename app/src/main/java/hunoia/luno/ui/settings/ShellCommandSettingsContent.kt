package hunoia.luno.ui.settings

import hunoia.luno.ui.theme.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.ShellCommandData
import hunoia.luno.bridge.feedback.showToast
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.ShellTemplateData
import hunoia.luno.core.JsonSerializer
import hunoia.luno.shizuku.ShizukuFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CommandSuggestion(
    val label: String,
    val command: String,
)

private val commonCommands = listOf(
    CommandSuggestion("音量增加", "cmd media_session volume --show --adj raise"),
    CommandSuggestion("音量减小", "cmd media_session volume --show --adj lower"),
    CommandSuggestion("亮度增加", "input keyevent 221"),
    CommandSuggestion("亮度减小", "input keyevent 220"),
    CommandSuggestion("播放/暂停", "cmd media_session dispatch play-pause"),
    CommandSuggestion("下一首", "cmd media_session dispatch next"),
    CommandSuggestion("上一首", "cmd media_session dispatch previous"),
    CommandSuggestion("回到桌面", "input keyevent 3"),
    CommandSuggestion("返回键", "input keyevent 4"),
    CommandSuggestion("最近任务", "input keyevent 187"),
    CommandSuggestion("开启WiFi", "cmd wifi set-wifi-enabled enabled"),
    CommandSuggestion("关闭WiFi", "cmd wifi set-wifi-enabled disabled"),
    CommandSuggestion("开启蓝牙", "cmd bluetooth_manager enable"),
    CommandSuggestion("关闭蓝牙", "cmd bluetooth_manager disable"),
    CommandSuggestion("开启勿扰", "cmd notification set_dnd on"),
    CommandSuggestion("关闭勿扰", "cmd notification set_dnd off"),
    CommandSuggestion("电源菜单", "input keyevent --longpress 26"),
    CommandSuggestion("息屏", "input keyevent 223"),
    CommandSuggestion("唤醒屏幕", "input keyevent 224"),
    CommandSuggestion("设置亮度", "settings put system screen_brightness 128"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShellCommandSettingsContent(
    action: Action,
    onConfirm: (String) -> Unit,
    showConfirmButton: Boolean = true,
    onDataChange: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp),
    showTestButton: Boolean = true,
    showTemplateMode: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existingData = remember(action.data) {
        runCatching { JsonSerializer.decodeFromString<ShellCommandData>(action.data) }.getOrNull()
    }
    var command by remember(action.data) { mutableStateOf(existingData?.command.orEmpty()) }
    var showToast by remember(action.data) { mutableStateOf(existingData?.showToast ?: true) }
    var template by remember(action.data) { mutableStateOf(existingData?.template) }
    var testing by remember { mutableStateOf(false) }
    var testOutput by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    val isTemplateMode = template != null

    fun emitChange() {
        onDataChange?.invoke(JsonSerializer.encodeToString(ShellCommandData(command.trim(), showToast, template)))
    }

    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showTemplateMode) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isTemplateMode,
                    onClick = {
                        if (isTemplateMode) {
                            template = null
                            emitChange()
                        }
                    },
                    shape = RoundedCornerShape(8.dp, 0.dp, 0.dp, 8.dp),
                ) { Text(stringResource(R.string.shell_command_mode_raw)) }
                SegmentedButton(
                    selected = isTemplateMode,
                    onClick = {
                        if (!isTemplateMode) {
                            template = ShellTemplateData("")
                            emitChange()
                        }
                    },
                    shape = RoundedCornerShape(0.dp, 8.dp, 8.dp, 0.dp),
                ) { Text(stringResource(R.string.shell_command_mode_template)) }
            }
        }

        if (isTemplateMode) {
            ShellTemplateEditor(
                template = template ?: ShellTemplateData(""),
                generatedCommand = command,
                onTemplateChange = { newTemplate, newCommand ->
                    template = newTemplate
                    command = newCommand
                    emitChange()
                },
            )
        } else {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = command,
                onValueChange = {
                    command = it.take(2000)
                    emitChange()
                },
                label = { Text(stringResource(R.string.shell_command_label)) },
                placeholder = { Text(stringResource(R.string.shell_command_placeholder)) },
                minLines = 3,
                maxLines = 6,
            )
            Text(
                text = stringResource(R.string.shell_command_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = { showSuggestions = !showSuggestions }) {
                Text(if (showSuggestions) stringResource(R.string.collapse) else stringResource(R.string.expand) + " 常用命令")
            }

            if (showSuggestions) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    commonCommands.forEach { suggestion ->
                        FilterChip(
                            selected = command.trim() == suggestion.command,
                            onClick = {
                                command = suggestion.command
                                emitChange()
                            },
                            label = { Text(suggestion.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.shell_command_show_toast),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = showToast,
                onCheckedChange = {
                    showToast = it
                    emitChange()
                }
            )
        }
        if (showTestButton && testOutput.isNotBlank()) {
            TestOutputBox(testOutput)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showTestButton) {
                TextButton(
                    enabled = command.isNotBlank() && !testing,
                    onClick = {
                        val testCommand = command.trim()
                        testing = true
                        testOutput = context.getString(R.string.testing)
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ShizukuFacade.runShellCommand(context.applicationContext, testCommand)
                            }
                            testing = false
                            val output = result.output.ifBlank { context.getString(R.string.shell_command_no_output) }
                            testOutput = if (result.success) {
                                context.getString(
                                    R.string.shell_command_test_output,
                                    result.exitCode,
                                    result.elapsedMs,
                                    output
                                )
                            } else {
                                context.getString(
                                    R.string.shell_command_test_error_output,
                                    result.error ?: "unknown error",
                                    result.exitCode,
                                    result.elapsedMs,
                                    output
                                )
                            }
                            if (result.success) {
                                showToast(result.output.ifBlank { context.getString(R.string.shell_command_no_output) }.take(500))
                            } else {
                                showToast((result.error ?: result.output.ifBlank { "unknown error" }).take(500))
                            }
                        }
                    }
                ) {
                    Text(stringResource(if (testing) R.string.testing else R.string.test))
                }
            }
            if (showConfirmButton) {
                TextButton(
                    enabled = command.isNotBlank(),
                    onClick = {
                        onConfirm(JsonSerializer.encodeToString(ShellCommandData(command.trim(), showToast, template)))
                    }
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Text(text = stringResource(id = R.string.confirm))
                }
            }
        }
    }
}

@Composable
fun TestOutputBox(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
