package hunoia.luno.ui.component.sheet

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import hunoia.luno.design.R
import hunoia.luno.ui.theme.ExtraLargeShape
import hunoia.luno.ui.theme.LargeGutter
import hunoia.luno.ui.theme.LargeShape
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SheetListMaxHeight

/**
 * 底部确认弹窗：仍是 Dialog（带遮罩、点外部/返回关闭），只是对话框本体
 * 锚在屏幕底部（澎湃OS 样式），四角大圆角、四周留边；按钮为澎湃大按钮。
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
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ConfirmDialogContent(
            title = title,
            text = text,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            destructive = destructive,
            onDismiss = onDismissRequest,
            onConfirm = {
                onDismissRequest()
                onConfirm()
            },
        )
    }
}

/**
 * 确认弹窗内容（底部锚定卡片 + 取消/确认）：[ConfirmDialog] 与 desktop 部件确认 Activity
 * 共用，避免 Activity 里再套一层 Dialog 产生双窗口/双遮罩。
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = PageGutter, vertical = PageGutter)
                .fillMaxWidth()
                .clip(ExtraLargeShape)
                .background(MaterialTheme.colorScheme.surface, ExtraLargeShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(LargeGutter),
            verticalArrangement = Arrangement.spacedBy(ListSpacing),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            text?.let { body ->
                // 多行正文（如“无法保存”的原因清单）超长时内部滚动
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
    }
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
    // Dialog 是独立窗口且半透明：adjustResize 在这种窗口上不可靠（键盘盖住内容）。
    // 改 ADJUST_NOTHING 让窗口不自动缩放/平移，改由 imePadding() 把内容顶到键盘上方。
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }
    // 按 initialValue 复位：同一调用点切换目标（换引擎/换重命名对象）时不能残留上次的输入。
    // 光标放末尾（编辑预填后直接续写，而不是从最前插入）。
    var textValue by rememberSaveable(initialValue, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(initialValue.length)))
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val curOnConfirm by rememberUpdatedState(onConfirm)

    fun submit() {
        if (!confirmEnabled) return
        keyboard?.hide()
        curOnConfirm(textValue.text)
    }

    // 自动聚焦：键盘随之弹出。
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // 边到边：内容延伸到状态栏/导航栏区域，IME inset 才会派发下来（imePadding 才有值）。
            decorFitsSystemWindows = false,
        ),
    ) {
        // 遮罩点按关闭；卡片本体不响应遮罩点击（镜像 ConfirmDialog）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 键盘上方：IME inset 撑开内容底边，卡片（BottomCenter）贴住键盘上沿。
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = PageGutter, vertical = PageGutter)
                    .fillMaxWidth()
                    .clip(ExtraLargeShape)
                    .background(MaterialTheme.colorScheme.surface, ExtraLargeShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(LargeGutter),
                verticalArrangement = Arrangement.spacedBy(ListSpacing),
            ) {
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
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = { submit() },
                        enabled = confirmEnabled,
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}

@Composable
fun CustomDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmLabel: String = stringResource(R.string.confirm),
    dismissLabel: String = stringResource(R.string.cancel),
    onConfirm: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { content() },
        confirmButton = {
            if (onConfirm != null) {
                TextButton(onClick = { onConfirm() }) { Text(confirmLabel) }
            }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(dismissLabel) } },
    )
}
