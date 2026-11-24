package hunoia.luno.config.repository

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GlobalGestureSettings
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.libraryEntryId

/**
 * 动作库条目引用清理：删掉库条目后，把配置里指向它的引用一并摘掉。
 *
 * 与 [PanelCleaner] / [SubGestureCleaner] 同口径（删了什么就清什么）：悬空引用在运行时
 * 只会被当成「无动作」，配置里看不出来——面板项/触发槽静默失效。长按变体是独立引用，
 * [cleanOne] 会连变体一起清。
 *
 * 纯变换（[clean]）与落盘（[cleanLibraryReferences]）分开：变换能直接单测，
 * IO 层只负责读快照 + 写回四个域。
 */
object LibraryCleaner {

    suspend fun cleanLibraryReferences(entryId: String) {
        val shouldRemove: (StoredAction) -> Boolean = { it.libraryEntryId() == entryId }
        ConfigProvider.updateActionSettings { settings ->
            val cleaned = clean(settings, shouldRemove)
            // 动作动效的绑定按条目 id 存：删条目必须一并摘掉，否则页面留着一条永远不播的行。
            cleaned.copy(
                floatBall = cleaned.floatBall.copy(
                    actionAlert = cleaned.floatBall.actionAlert.withoutEntry(entryId),
                ),
            )
        }
        ConfigProvider.updateGestureButtons { buttons -> clean(buttons, shouldRemove) }
        ConfigProvider.updateSubGestureSettings { settings -> clean(settings, shouldRemove) }
        ConfigProvider.updateGlobalGesture { settings -> clean(settings, shouldRemove) }
    }

    fun clean(settings: ActionSettings, shouldRemove: (StoredAction) -> Boolean): ActionSettings =
        settings.copy(
            actionPanels = settings.actionPanels.copy(
                panels = settings.actionPanels.panels.map { panel ->
                    panel.copy(items = panel.items.mapNotNull { it.cleanOne(shouldRemove) })
                },
            ),
            actionGridPanels = settings.actionGridPanels.copy(
                panels = settings.actionGridPanels.panels.map { panel ->
                    panel.copy(
                        portrait = panel.portrait.copy(
                            items = panel.portrait.items.cleanPanelItems(shouldRemove),
                        ),
                        landscape = panel.landscape.copy(
                            items = panel.landscape.items.cleanPanelItems(shouldRemove),
                        ),
                    )
                },
            ),
            floatBall = settings.floatBall.copy(
                tapAction = settings.floatBall.tapAction.cleanOne(shouldRemove),
            ),
        )

    fun clean(buttons: List<GestureButton>, shouldRemove: (StoredAction) -> Boolean): List<GestureButton> =
        buttons.map { it.cleanActions(shouldRemove) }

    fun clean(settings: SubGestureSettings, shouldRemove: (StoredAction) -> Boolean): SubGestureSettings =
        settings.copy(subGestures = settings.subGestures.map { it.cleanActions(shouldRemove) })

    fun clean(settings: GlobalGestureSettings, shouldRemove: (StoredAction) -> Boolean): GlobalGestureSettings =
        settings.cleanActions(shouldRemove)
}
