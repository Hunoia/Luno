package hunoia.luno.ui.settings.todo

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.config.model.TodoConfig
import hunoia.luno.config.model.TodoItem
import hunoia.luno.domain.gridpanel.TodoCenter
import hunoia.luno.ui.component.row.LunoGroup
import hunoia.luno.ui.component.screen.LunoScreen
import hunoia.luno.ui.component.sheet.ConfirmDialog
import hunoia.luno.ui.theme.ContentBottom
import hunoia.luno.ui.theme.FloatingContentBottom
import hunoia.luno.ui.theme.PageGutter
import kotlinx.coroutines.launch

/**
 * 代办编辑：新增（todoId 空）或编辑。字段 = 文字（唯一用户可见内容，无颜色/频率）。
 * 保存走底部 FAB，删除在顶栏（仅编辑态）。
 *
 * 字段状态初始为 null、渲染回退到已加载的 [TodoItem] 值：配置流首帧为空（`initialValue`），
 * 否则 `rememberSaveable` 会用空值初始化导致重进丢内容；用户一编辑即覆盖。
 * 增删改与完成切换走 [TodoCenter] 门面（配置写入，不走 Action / Dispatcher）。
 */
@Composable
fun TodoEditScreen(
    todoId: String?,
    onBack: () -> Unit,
) {
    val config by TodoCenter.todoConfig.collectAsStateWithLifecycle(initialValue = TodoConfig())
    val todo = config.todoById(todoId.orEmpty())
    val scope = rememberCoroutineScope()

    var text by rememberSaveable(todoId) { mutableStateOf<String?>(null) }
    val shownText = text ?: todo?.text.orEmpty()

    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun save() {
        val trimmed = shownText.trim()
        if (trimmed.isBlank()) return
        scope.launch {
            if (todo == null) {
                TodoCenter.addTodo(
                    TodoItem(
                        text = trimmed,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                TodoCenter.updateTodo(todo.copy(text = trimmed))
            }
            onBack()
        }
    }

    LunoScreen(
        title = stringResource(
            if (todo == null) R.string.todo_create_title else R.string.todo_edit_title,
        ),
        onBack = onBack,
        bottomContentPadding = FloatingContentBottom,
        actions = {
            if (todo != null) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.todo_delete),
                    )
                }
            }
        },
        floatingContent = { _ ->
            val fabModifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = PageGutter, bottom = ContentBottom)
                .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
                .size(64.dp)
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                FloatingActionButton(
                    onClick = ::save,
                    modifier = fabModifier,
                    shape = FloatingActionButtonDefaults.shape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = stringResource(R.string.todo_save),
                    )
                }
            }
        },
        dialogs = {
            if (showDeleteConfirm && todo != null) {
                ConfirmDialog(
                    title = stringResource(R.string.todo_delete),
                    text = stringResource(R.string.todo_delete_confirm),
                    destructive = true,
                    onDismissRequest = { showDeleteConfirm = false },
                    onConfirm = {
                        scope.launch {
                            TodoCenter.deleteTodo(todo.id)
                            onBack()
                        }
                    },
                )
            }
        },
    ) {
        LunoGroup(title = stringResource(R.string.todo_text)) {
            OutlinedTextField(
                value = shownText,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.todo_text_hint)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
