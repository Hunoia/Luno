package hunoia.luno.core.domain

/**
 * 搜索召回要用的「动作条目」契约。
 *
 * 归属判据（architecture_future §4.2 规则 3「共享能力必须可覆盖」）：
 * 「动作能不能被搜到、长什么样」的变化原因是动作定义演进；「怎么排序、怎么召回」
 * 的变化原因是搜索策略演进。两条变化原因分开，于是搜索只依赖这个契约，
 * 实现留在 `:feature:actions`（它才持有 ActionDefinitions / 图标 / 文案资源）。
 *
 * `:app` 组装时把实现注入 `:feature:search`；搜索不 import 动作模块。
 */
interface ActionSearchSource {
    /** 当前可用于搜索的动作条目（已按动作模块的口径过滤：默认排除 picker 排除集）。 */
    fun index(): List<ActionSearchEntry>
}

/**
 * 一条可搜索、可执行的动作。
 *
 * [icon] 是「交给 UI 层解释的不透明图标模型」：可能是 Compose `ImageVector`、文字/表情、
 * 或是应用图标模型（Coil 请求）。契约层不引入这些类型，由 UI 侧按 `is` 分支渲染。
 */
data class ActionSearchEntry(
    /** 稳定 key（按 typeId 或条目 id 派生），用于置顶/去重。 */
    val key: String,
    val group: String,
    val label: String,
    val typeId: String,
    val icon: Any?,
    /** 命中后交给执行口的动作（动作库条目也是 StoredAction）。 */
    val action: StoredAction,
)
