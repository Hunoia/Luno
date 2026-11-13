package hunoia.luno.config.repository

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.ActionSettings
import hunoia.luno.config.model.GestureButton
import hunoia.luno.config.model.GridPanelItem
import hunoia.luno.config.model.PanelReferences
import hunoia.luno.config.model.SubGestureSettings
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.actionPanelId
import hunoia.luno.core.domain.gridPanelId

/**
 * 面板引用清理：删掉动作面板 / 网格面板后，把配置里指向它的引用一并摘掉。
 *
 * 与 [SubGestureCleaner] 同口径（删了什么就清什么）：悬空引用在运行时只会被当成
 * 「无动作」，配置里看不出来——面板项的短按/长按静默失效，触钮槽绑着却永远不响应。
 * 长按变体是独立引用，[cleanOne] 会连变体一起清。
 *
 * 纯变换（[clean]）与落盘（[cleanPanelReferences]）分开：变换能直接单测，
 * IO 层只负责读快照 + 写回三个域。
 */
object PanelCleaner {

    /** 引用该面板的处数（面板项含变体 + 触钮/子手势槽 + 悬浮球）：删前提示用。 */
    suspend fun countPanelReferences(panelId: String): Int = PanelReferences.count(
        panelId = panelId,
        action = ConfigProvider.getActionSettings(),
        buttons = ConfigProvider.getGestureButtons(),
        subGestures = ConfigProvider.getSubGestureSettings().subGestures,
    )

    suspend fun cleanPanelReferences(deletedId: String) {
        ConfigProvider.updateActionSettings { settings -> clean(settings, deletedId) }
        ConfigProvider.updateGestureButtons { buttons -> clean(buttons, deletedId) }
        ConfigProvider.updateSubGestureSettings { settings -> clean(settings, deletedId) }
    }

    fun clean(settings: ActionSettings, deletedId: String): ActionSettings {
        val shouldRemove = panelMatcher(deletedId)
        return settings.copy(
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
            // 底栏成员也是面板引用：不摘掉就是一个点了没反应的 tab。
            panelDock = settings.panelDock.withoutMember(deletedId),
            floatBall = settings.floatBall.copy(
                tapAction = settings.floatBall.tapAction.cleanOne(shouldRemove),
            ),
        )
    }

    fun clean(buttons: List<GestureButton>, deletedId: String): List<GestureButton> {
        val shouldRemove = panelMatcher(deletedId)
        return buttons.map { it.cleanActions(shouldRemove) }
    }

    fun clean(settings: SubGestureSettings, deletedId: String): SubGestureSettings {
        val shouldRemove = panelMatcher(deletedId)
        return settings.copy(
            subGestures = settings.subGestures.map { it.cleanActions(shouldRemove) },
        )
    }

    private fun panelMatcher(deletedId: String): (StoredAction) -> Boolean = { action ->
        action.actionPanelId() == deletedId || action.gridPanelId() == deletedId
    }

    /**
     * 网格项承载动作列表：清掉失效动作，清空就整项删除。
     * 网格项没有「空槽」形态（必须占格），留下空壳只会白占格子。
     */
    private fun List<GridPanelItem>.cleanPanelItems(
        shouldRemove: (StoredAction) -> Boolean,
    ): List<GridPanelItem> = mapNotNull { item ->
        val kind = item.kind
        if (kind !is GridPanelItem.Kind.Action) return@mapNotNull item
        val kept = kind.items.mapNotNull { it.cleanOne(shouldRemove) }
        if (kept.isEmpty()) null else item.copy(kind = GridPanelItem.Kind.Action(kept))
    }
}
