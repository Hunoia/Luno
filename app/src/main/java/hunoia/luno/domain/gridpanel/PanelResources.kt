package hunoia.luno.domain.gridpanel

import hunoia.luno.action.model.ShortcutIconFiles
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.SubGesture
import hunoia.luno.core.AppContext
import hunoia.luno.service.DispatchClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 面板资源生命周期：删除面板/单项、reset、恢复备份后，回收不再被引用的
 * AppWidget id 与快捷方式图标文件。两类资源都不在 DataStore 里，配置删除不会带走：
 * AppWidget id 不 `deleteAppWidgetId` 会永久占槽位，图标文件不删只增不减。
 * 清理以当前配置为准做孤儿扫描（先收全量引用再删其余），比各删除点写差量可靠。
 */
object PanelResources {

    /** 全量扫描当前配置并回收孤儿资源。配置变更落盘后调用。 */
    suspend fun cleanup() {
        // 清理是“尽力而为”的收尾动作：DataStore 读取/宿主调用失败只记日志，
        // 不能让删除面板后的清理异常反杀 UI 协程。文件扫描和 AppWidgetHost
        // 调用都可能阻塞，统一放 IO。
        withContext(Dispatchers.IO) {
            runCatching {
                val actionSettings = ConfigProvider.getActionSettings()
                val buttons = ConfigProvider.getGestureButtons()
                val subGestures = ConfigProvider.getSubGestureSettings().subGestures
                val library = ConfigProvider.getActionLibrarySettings().entries
                val rules = ConfigProvider.getAutomationRules()

                val referencedIcons = buildSet {
                    buttons.forEach { addAll(it.storedActions()) }
                    subGestures.forEach { addAll(it.storedActions()) }
                    library.forEach { add(it.storedAction) }
                    rules.forEach { addAll(it.storedActions()) }
                    addAll(actionSettings.storedActions())
                }.flatMapTo(mutableSetOf()) { ShortcutIconFiles.iconPathsOf(it) }
                ShortcutIconFiles.deleteUnreferenced(AppContext.get(), referencedIcons)

                val referencedWidgetIds = actionSettings.referencedAppWidgetIds()
                // AppWidgetHost 只允许 :service 创建/回收，避免主进程再挂一个同 HOST_ID 的监听。
                if (android.app.Application.getProcessName().orEmpty().endsWith(":service")) {
                    hunoia.luno.gridpanel.widget.PanelAppWidgetHost.get()
                        .releaseOrphans(referencedWidgetIds)
                } else {
                    DispatchClient.releaseOrphanAppWidgets(referencedWidgetIds)
                }
            }.onFailure { error ->
                android.util.Log.w("LunoResources", "panel resource cleanup failed", error)
            }
        }
    }

    private fun ActionSettings.storedActions(): List<StoredAction> = buildList {
        floatBall.tapAction?.let(::add)
        actionPanels.panels.forEach { addAll(it.items) }
        actionGridPanels.panels.forEach { panel ->
            panel.items.forEach { item ->
                (item.kind as? GridPanelItem.Kind.Action)?.items?.let(::addAll)
            }
        }
    }

    private fun ActionSettings.referencedAppWidgetIds(): Set<Int> = buildSet {
        actionGridPanels.panels.forEach { panel ->
            panel.items.forEach { item ->
                (item.kind as? GridPanelItem.Kind.AppWidget)?.let { add(it.appWidgetId) }
            }
        }
    }

    private fun DirectionActions.storedActions(): Collection<StoredAction> = actions.values

    private fun GestureButton.storedActions(): List<StoredAction> = buildList {
        listOfNotNull(tapActions, doubleTapActions, longPressActions).forEach(::add)
        addAll(slideActions.storedActions())
        addAll(slideHoldActions.storedActions())
        addAll(longSlideActions.storedActions())
        addAll(longSlideHoldActions.storedActions())
    }

    private fun SubGesture.storedActions(): List<StoredAction> = buildList {
        addAll(slideActions.storedActions())
        addAll(slideHoldActions.storedActions())
        addAll(longSlideActions.storedActions())
        addAll(longSlideHoldActions.storedActions())
    }

    private fun AutomationRule.storedActions(): List<StoredAction> =
        effect.steps.flatMap { it.storedActions() }

    private fun RuleStep.storedActions(): List<StoredAction> = buildList {
        action?.let(::add)
        addAll(thenSteps.flatMap { it.storedActions() })
        addAll(elseSteps.flatMap { it.storedActions() })
    }
}
