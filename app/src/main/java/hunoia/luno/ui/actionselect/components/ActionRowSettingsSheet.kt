package hunoia.luno.ui.actionselect.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hunoia.luno.R
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.domain.gesture.GestureConfig
import hunoia.luno.ui.actionselect.components.rowsettings.ActionRowSettingsRegistry
import hunoia.luno.ui.actionselect.components.rowsettings.RowSettingsEditorScope
import hunoia.luno.ui.component.sheet.LunoSheet
import hunoia.luno.ui.component.sheet.OptimizedScrollState
import hunoia.luno.ui.component.sheet.SheetFooterActions
import hunoia.luno.ui.component.sheet.SheetHeader
import hunoia.luno.ui.theme.CardInnerSpacing
import hunoia.luno.ui.theme.PageGutter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/** 参数改在动作选择页行尾「设置」Sheet 里配的动作类型。 */
val RowSettingsActionTypeIds: Set<String> = ActionRowSettingsRegistry.typeIds

/** 行副标题：展示当前生效值；未注册的动作返回空串。 */
fun rowSettingsSubtitle(action: StoredAction, context: Context): String =
    ActionRowSettingsRegistry.byTypeId(action.typeId)
        ?.subtitle(action.params, context)
        .orEmpty()

/**
 * 行尾设置 Sheet：描述符驱动，不再按 typeId 写中心分支。
 *
 * - 底部 Sheet；整个内容只有一个 `verticalScroll`。
 * - 字段草稿是一份 `JsonObject`：改哪个键写哪个键，确认时由描述符规范化。
 * - 校验、标题、副标题、编辑器全部来自 [ActionRowSettingsRegistry]。
 */
@Composable
fun ActionRowSettingsSheet(
    action: StoredAction,
    onDismiss: () -> Unit,
    onConfirm: (StoredAction) -> Unit,
) {
    val descriptor = ActionRowSettingsRegistry.byTypeId(action.typeId) ?: return
    val context = LocalContext.current
    var draft by remember(action) { mutableStateOf(action.params) }
    val buttons by GestureConfig.buttons.collectAsStateWithLifecycle(initialValue = emptyList())
    val scrollState = rememberScrollState()

    val scope = RowSettingsEditorScope(
        typeId = action.typeId,
        draft = draft,
        buttons = buttons,
        context = context,
        setStr = { key, value -> draft = JsonObject(draft + (key to JsonPrimitive(value))) },
        setBool = { key, value -> draft = JsonObject(draft + (key to JsonPrimitive(value))) },
        setInt = { key, value -> draft = JsonObject(draft + (key to JsonPrimitive(value))) },
        setLong = { key, value -> draft = JsonObject(draft + (key to JsonPrimitive(value))) },
        setStringArray = { key, values ->
            draft = JsonObject(
                draft + (
                    key to buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }
                    ),
            )
        },
    )

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
                title = stringResource(descriptor.titleRes),
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
                descriptor.editor(scope)
            }
            SheetFooterActions(
                onDismissRequest = onDismiss,
                onConfirm = {
                    onConfirm(action.copy(params = descriptor.canonicalize(draft)))
                    onDismiss()
                },
                confirmLabel = stringResource(R.string.ok),
                confirmEnabled = descriptor.isValid(draft),
            )
        }
    }
}
