package hunoia.luno.core.domain

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `app.launch` 的唯一构造入口。
 *
 * Surface、搜索、链接分发都只表达「启动哪个应用」，参数键与类型定义集中在这里，
 * 避免各处手拼 `StoredAction.of("app.launch", ...)` 时出现拼写漂移。
 * 这里只构造持久化动作，不执行、不查询包信息。
 */
object AppLaunchAction {

    const val TYPE_ID: String = "app.launch"

    fun stored(
        packageName: String,
        className: String = "",
        miniWindow: Boolean = false,
        intentAction: String = "",
        data: String = "",
        extras: Map<String, String> = emptyMap(),
    ): StoredAction = StoredAction(
        typeId = TYPE_ID,
        params = buildJsonObject {
            put("packageName", packageName)
            if (className.isNotBlank()) put("className", className)
            if (miniWindow) put("miniWindow", true)
            if (intentAction.isNotBlank()) put("intentAction", intentAction)
            if (data.isNotBlank()) put("data", data)
            if (extras.isNotEmpty()) {
                put(
                    "extras",
                    buildJsonObject {
                        extras.forEach { (key, value) -> put(key, value) }
                    },
                )
            }
        },
    )
}
