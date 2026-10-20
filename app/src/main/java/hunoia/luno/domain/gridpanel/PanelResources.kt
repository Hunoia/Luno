package hunoia.luno.domain.gridpanel

import hunoia.luno.action.model.ShortcutIconFiles
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.AutomationRule
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.RuleStep
import hunoia.luno.config.model.SubGesture
import hunoia.luno.core.AppContext
import hunoia.luno.gridpanel.widget.PanelAppWidgetHost

/**
 * 面板资源生命周期：删除面板 / 单项、reset、恢复备份后，回收不再被引用的
 * AppWidget id 与快捷方式图标文件。
 *
 * 两类资源都不在 DataStore 里，配置删除不会自动带走它们：
 * - AppWidget id 需要在系统宿主上 `deleteAppWidgetId`，否则永久占用槽位；
 * - 快捷方式位图落在 `filesDir/shortcut_icons`，不删就只增不减。
 *
 * 清理采取「以当前配置为准」的孤儿扫描：先收集全量引用，再删其余。比在每个
 * 删除点手写差量可靠 —— 不会因为将来新增删除入口又漏一处。
 */
object PanelResources {

    /** 全量扫描当前配置并回收孤儿资源。配置变更落盘后调用。 */
    suspend fun cleanup() {
        val actionSettings = ConfigProvider.getActionSettings()
        val buttons = ConfigProvider.getGestureButtons()
        val subGestures = ConfigProvider.getSubGestureSettings().subGestures
        val library = ConfigProvider.getNewActionLibrarySettings().entries
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
        PanelAppWidgetHost.get().releaseOrphans(referencedWidgetIds)
    }

    private fun ActionSettings.storedActions(): List<StoredAction> = buildList {
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
