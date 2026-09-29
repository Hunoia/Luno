package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.model.StoredAction
import hunoia.luno.core.JsonSerializer
import kotlinx.serialization.Serializable

/** Action.value 取此值时，data 为 [NewActionData]：内联的新模型动作 */
const val VALUE_NEW_ACTION = "new_action"

/** Action.value 取此值时，data 为 [ActionLibraryRefData]：动作库条目引用 */
const val VALUE_LIBRARY_REF = "library_ref"

@Serializable
@Keep
data class ActionLibraryRefData(
    val entryId: String
)

@Serializable
@Keep
data class NewActionData(
    val storedAction: StoredAction
)

/** 内联动作：新模型 typeId + 参数，随 Action 一起持久化 */
internal fun Action.newActionData(): StoredAction? {
    if (value != VALUE_NEW_ACTION) return null
    return runCatching { JsonSerializer.decodeFromString<NewActionData>(data).storedAction }
        .getOrNull()
        ?.takeIf { it.typeId.isNotBlank() }
}

/**
 * 动作库条目引用。
 * value 为 [VALUE_LIBRARY_REF] 时是新格式；旧数据把引用塞进了
 * EXECUTE_SHELL_COMMAND / OPEN_URL / OPEN_APP_ACTIVITY 的 data 字段，两种都认。
 */
internal fun Action.actionLibraryRefId(): String? {
    val isLegacyMarker = value == ActionFacade.EXECUTE_SHELL_COMMAND ||
        value == ActionFacade.OPEN_URL ||
        value == ActionFacade.OPEN_APP_ACTIVITY
    if (value != VALUE_LIBRARY_REF && !isLegacyMarker) return null
    return runCatching { JsonSerializer.decodeFromString<ActionLibraryRefData>(data).entryId }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}
