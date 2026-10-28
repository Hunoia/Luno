package hunoia.luno.action.model

import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.core.domain.StoredAction
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * `app.launchShortcut` 内嵌的快捷方式数据（解 data JSON）。
 *
 * 归 `:feature:actions`：动作参数形态是动作模块的知识；模型本身在 `:core:bridge`
 * （带 Bitmap，不能进纯 JVM domain），所以这里不产生 actions → search 的横向依赖。
 */
fun StoredAction.shortcutInfo(): LauncherInfo.ShortcutInfo? {
    if (typeId != "app.launchShortcut") return null
    val data = (params["data"] as? JsonPrimitive)?.contentOrNull ?: return null
    return runCatching {
        JsonSerializer.decodeFromString<LauncherInfo.ShortcutInfo>(data)
    }.getOrNull()
}
