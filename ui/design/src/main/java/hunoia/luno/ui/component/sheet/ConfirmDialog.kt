package hunoia.luno.ui.component.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import hunoia.luno.design.R
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.SheetListMaxHeight
import hunoia.luno.ui.component.PressTextButton

/**
 * 底部确认弹窗：Dialog（遮罩 + 点外部/返回关闭），本体锚在屏幕底部（澎湃OS 样式），
 * 四角大圆角、四周留边；按钮为澎湃大按钮。
 *
 * 窗口与进出场动画由 [AnchoredDialog] 提供，这里只填内容。
 */
@Composable
fun ConfirmDialog(
    title: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    text: String? = null,
    confirmLabel: String = stringResource(R.string.confirm),
    dismissLabel: String = stringResource(R.string.cancel),
    destructive: Boolean = false,
) {
    // 确认动作等退场播完再执行：调用点习惯在 onConfirm 里同步清"显示"状态，先执行动作会在
    // 退场起播前把弹层卸出组合，动画被截断（只有取消 / 点外部是滑出的）。这里的
    // pendingConfirm 就是那条"先播完、再执行"的桥；退场完成回调由 AnchoredDialog 保证。
    var pendingConfirm by remember { mutableStateOf(false) }
    AnchoredDialog(
        onDismiss = {
            if (pendingConfirm) {
                pendingConfirm = false
                onConfirm()
            }
            onDismissRequest()
        },
    ) { dismiss ->
        ConfirmCardContent(
            title = title,
            text = text,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            destructive = destructive,
            onDismiss = dismiss,
            onConfirm = {
                pendingConfirm = true
                dismiss()
            },
        )
    }
}

/**
 * 确认弹窗内容（底部锚定卡片 + 取消/确认）：独立 Activity 形态直接铺一屏（部件确认弹窗），
 * 不再套一层 Compose `Dialog`，避免双窗口/双遮罩。
 *
 * 与 [ConfirmDialog] 同一套语义：确认动作在退场播完后执行（同步写回 / 异步启动都行），
 * 退场播完才回调 [onDismiss]。
 */
@Composable
fun ConfirmDialogContent(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    text: String? = null,
    confirmLabel: String = stringResource(R.string.confirm),
    dismissLabel: String = stringResource(R.string.cancel),
    destructive: Boolean = false,
) {
    var pendingConfirm by remember { mutableStateOf(false) }
    AnchoredDialogContent(
        onDismiss = {
            if (pendingConfirm) {
                pendingConfirm = false
                onConfirm()
            }
            onDismiss()
        },
    ) { dismiss ->
        ConfirmCardContent(
            title = title,
            text = text,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            destructive = destructive,
            onDismiss = dismiss,
            onConfirm = {
                pendingConfirm = true
                dismiss()
            },
        )
    }
}

/** 确认卡片内容：标题 + 可选多行正文（超长内部滚动）+ 取消/确认。 */
@Composable
private fun ColumnScope.ConfirmCardContent(
    title: String,
    text: String?,
    confirmLabel: String,
    dismissLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    text?.let { body ->
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .heightIn(max = SheetListMaxHeight)
                .verticalScroll(rememberScrollState()),
        )
    }
    SheetActionButtons(
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        destructive = destructive,
    )
}

/** 单字段输入：IME Done 等同确认；[confirmEnabled] 为 false 时确认禁用。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
    confirmEnabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
) {
    // 按 initialValue 复位：同一调用点切换目标（换引擎/换重命名对象）时不能残留上次的输入。
    // 光标放末尾（编辑预填后直接续写，而不是从最前插入）。
    var textValue by rememberSaveable(initialValue, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(initialValue.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val curOnConfirm by rememberUpdatedState(onConfirm)
    var pendingText by remember { mutableStateOf<String?>(null) }

    AnchoredDialog(
        // 与 ConfirmDialog 同口径：确认动作等退场播完再执行（调用点通常在 onConfirm 里把
        // "显示"状态置空，先执行会在退场起播前把弹层卸掉，动画被截断）。文本在提交时就快照。
        onDismiss = {
            pendingText?.let { text ->
                pendingText = null
                curOnConfirm(text)
            }
            onDismissRequest()
        },
        imeAutoShow = true,
    ) { dismiss ->
        fun submit() {
            if (!confirmEnabled) return
            // 不在确认时收键盘：AnchoredDialog 退场会 hide，键盘自己滑完；提前 hide 会让
            // 键盘下滑与卡片退场各跑一段。
            pendingText = textValue.text
            dismiss()
        }

        // 自动聚焦：窗口获得焦点时由 SOFT_INPUT_STATE_ALWAYS_VISIBLE 拉起键盘，与卡片进场同时。
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = textValue,
            onValueChange = { textValue = it },
            label = label.takeIf { it.isNotEmpty() }?.let { l -> { Text(l) } },
            singleLine = true,
            isError = isError,
            shape = LargeShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        // supportingText 常驻占位：无错时空字符串，避免错误出现/消失时弹窗高度跳动
        if (supportingText.isNotEmpty()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ListSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            PressTextButton(onClick = dismiss) {
                Text(stringResource(R.string.cancel))
            }
            PressTextButton(
                onClick = { submit() },
                enabled = confirmEnabled,
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}
