package hunoia.luno.search

import hunoia.luno.core.domain.ActionSearchSource

/**
 * 搜索侧能力注入点（ADR-0001 D2）。
 *
 * `:feature:search` 只依赖 `ActionSearchSource` 契约；实现由 `:feature:actions` 提供，
 * `:app` 装配时注入一次。新增来源（脚本动作、跨设备动作）时追加实现即可，搜索不需要改。
 */
object SearchCapabilities {
    var actionSearch: ActionSearchSource = object : ActionSearchSource {
        override fun index(): List<hunoia.luno.core.domain.ActionSearchEntry> = emptyList()
    }
}
