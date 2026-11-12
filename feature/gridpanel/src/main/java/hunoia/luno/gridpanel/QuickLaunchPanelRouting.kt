package hunoia.luno.gridpanel

import hunoia.luno.bridge.system.canDrawOverlays
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.core.AppContext
import hunoia.luno.gridpanel.internal.InternalWidgetIds

/**
 * 「快速启动」动作的入口收口：优先打开已配置的快速启动单功能面板，找不到再回落独立快速启动面板。
 *
 * 只认第一个 `internal.quickLaunch` 单功能面板；不新建面板、不改旧配置。
 * 没有悬浮窗权限时返回 false 让调用方走独立快速启动面板（那边会引导授权），
 * 避免动作从「有引导」退化成「静默失败」。
 */
object QuickLaunchPanelRouting {

    /** 已配置的快速启动单功能面板；没有则 null。只认第一个，顺序按面板列表。 */
    fun findConfiguredPanel(actionSettings: ActionSettings): hunoia.luno.config.model.GridPanel? =
        actionSettings.actionGridPanels.panels.firstOrNull {
            it.isSingle && it.singleWidgetId == InternalWidgetIds.QUICK_LAUNCH
        }

    fun showIfConfigured(actionSettings: ActionSettings, anchorX: Int, anchorY: Int): Boolean {
        val panel = findConfiguredPanel(actionSettings) ?: return false
        if (!AppContext.get().canDrawOverlays()) return false
        GridPanelOverlay.show(panel.id, anchorX, anchorY)
        return true
    }
}
