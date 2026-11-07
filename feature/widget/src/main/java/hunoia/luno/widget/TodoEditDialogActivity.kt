package hunoia.luno.widget

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.core.LunoLog
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.gridpanel.TodoExtras
import hunoia.luno.gridpanel.R as GridR
import hunoia.luno.ui.theme.ExtraLargeShape
import hunoia.luno.ui.theme.LargeGutter
import hunoia.luno.ui.theme.ListSpacing
import hunoia.luno.ui.theme.PageGutter
import hunoia.luno.ui.theme.SideGestureTheme
import kotlinx.coroutines.launch

/**
 * 待办编辑弹窗（桌面部件「+」/点行直达，不进应用页面；面板部件同一入口）。
 *
 * 经 [TodoExtras.EXTRA_TODO_ID] 区分：空 = 新增，非空 = 编辑（预填文本 + 删除）。
 * 样式 / 行为 1:1 镜像应用内 `TodoListScreen.TodoEditDialog`：底部锚定贴键盘、
 * 自动聚焦弹键盘、Done 提交、遮罩点按关闭；删除为直删（与应用内一致，无二次确认）。
 * 写回 [TodoCenter] 后直接刷新全部待办部件（自闭环，不依赖 WidgetSync 订阅者）。
 */
class TodoEditDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val todoId = intent.getStringExtra(TodoExtras.EXTRA_TODO_ID)
        // Dialog 主题浮窗：与应用内 Dialog 同样关 decorFitsSystemWindows + ADJUST_NOTHING，
        // 让 IME inset 派发下来（imePadding 生效），内容顶到键盘上方。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // 套 Luno 主题：与页面内 TodoEditDialog（SideGestureTheme 下）视觉一致。
            SideGestureTheme {
                TodoEditDialog(todoId = todoId) { finish() }
            }
        }
    }
}

@Composable
private fun TodoEditDialog(todoId: String?, onFinished: () -> Unit) {
    // ADJUST_NOTHING：浮窗不自动缩放，由 imePadding 顶起（镜像应用内弹窗 SideEffect）。
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }

    val config by TodoCenter.todoConfig.collectAsState(initial = TodoConfig())
    val existing = todoId?.let { config.todoById(it) }
    var textValue by rememberSaveable(todoId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    // 数据流到达后回填编辑文本：首帧 config 尚未加载（existing=null），若直接初始化会得到空串。
    // 光标放末尾（编辑预填后直接续写）。
    var filled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(existing?.id) {
        if (!filled && existing != null) {
            textValue = TextFieldValue(existing.text, selection = TextRange(existing.text.length))
            filled = true
        }
    }
    val scope = rememberCoroutineScope()
    // 弹窗内无 Activity receiver（compose 函数内），用 LocalContext 拿 applicationContext。
    val appContext = LocalContext.current.applicationContext
    val isEdit = existing != null
    val trimmed = textValue.text.trim()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val curOnFinished by rememberUpdatedState(onFinished)

    fun submit() {
        if (trimmed.isEmpty()) return
        keyboard?.hide()
        val text = trimmed
        scope.launch {
            if (existing == null) {
                TodoCenter.addTodo(TodoItem(text = text, createdAt = System.currentTimeMillis()))
            } else {
                TodoCenter.updateTodo(existing.copy(text = text))
            }
            runCatching { WidgetSync.updateTodoWidgets(appContext) }
                .onFailure { LunoLog.w(TAG, "update after save failed: ${it.message}") }
            curOnFinished()
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // 遮罩点按关闭；卡片本体不响应遮罩点击（镜像应用内弹窗）。底部锚定 + imePadding。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinished,
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
                text = stringResource(if (isEdit) GridR.string.todo_edit_title else GridR.string.todo_create_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                singleLine = true,
                placeholder = { Text(stringResource(GridR.string.todo_text_hint)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ListSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isEdit) {
                    // 直删（与应用内一致，无二次确认）。
                    TextButton(onClick = {
                        scope.launch {
                            TodoCenter.deleteTodo(existing.id)
                            runCatching { WidgetSync.updateTodoWidgets(appContext) }
                                .onFailure { LunoLog.w(TAG, "update after delete failed: ${it.message}") }
                            curOnFinished()
                        }
                    }) {
                        Text(
                            text = stringResource(GridR.string.todo_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onFinished) {
                    Text(stringResource(R.string.widget_todo_cancel))
                }
                TextButton(onClick = { submit() }, enabled = trimmed.isNotEmpty()) {
                    Text(stringResource(GridR.string.todo_save))
                }
            }
        }
    }
}

private const val TAG = "TodoEditDialog"
