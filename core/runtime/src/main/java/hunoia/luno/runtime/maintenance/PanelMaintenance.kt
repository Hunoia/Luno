package hunoia.luno.runtime.maintenance

/**
 * 面板资源的收尾钩子。
 *
 * 删除手势按钮 / 子手势后，面板里引用的快捷方式图标可能变成孤儿。清理逻辑属于
 * `:feature:gridpanel`，但调用点在 `:feature:gesture`——直接依赖会成环
 * （gridpanel → gesture 已有）。所以把动作挂在两者都依赖的 `:core:runtime`，由 `:app` 注入。
 */
object PanelMaintenance {

    /** 配置变更落盘后回收孤儿资源；未注入时静默跳过。 */
    var cleanupOrphanResources: (suspend () -> Unit)? = null
}
