package hunoia.luno.core.domain

/**
 * 链接/Intent 的 Query 参数（键值 + 启用开关）。
 *
 * 住在契约层：动作模型（`Action.OpenUrl`）、启动能力库的 `AppLauncher` 都要用它，
 * 但不该让能力库反向依赖执行模型。
 */
data class QueryParam(
    val name: String,
    val value: String,
    val enabled: Boolean = true,
)
