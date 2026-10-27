package hunoia.luno.action.model

import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.quicklaunch.model.qualifiedNameWithIntents
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * `app.launchShortcut` 内嵌的快捷方式标识（解 data JSON，用于去重与比对）。
 *
 * 归 `:feature:search`：`LauncherInfo.ShortcutInfo` 是查询侧模型，动作层不持有它。
 */
fun StoredAction.shortcutQualifiedName(): String? {
    if (typeId != "app.launchShortcut") return null
    val data = (params["data"] as? JsonPrimitive)?.contentOrNull ?: return null
    return runCatching {
        JsonSerializer.decodeFromString<LauncherInfo.ShortcutInfo>(data)
    }.getOrNull()?.qualifiedNameWithIntents
}
