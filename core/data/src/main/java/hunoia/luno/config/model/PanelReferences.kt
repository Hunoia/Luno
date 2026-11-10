package hunoia.luno.config.model

import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.actionPanelId
import hunoia.luno.core.domain.gridPanelId
import hunoia.luno.core.domain.longPressVariant

/**
 * 面板引用（`internal.actionPanel` / `internal.gridPanel`）的统计。
 *
 * 面板项、触钮槽、子手势槽、悬浮球单击共用同一套 [StoredAction]，所以删面板前必须
 * 能数清「谁指着我」：否则悬空引用只在运行时表现为「点了没反应」，配置里看不出来。
 * 长按变体（`__longPress`）也是独立一处引用，必须一起数。
 *
 * 纯函数，只读快照；真正改配置的清理在 `PanelCleaner`（要连变体一起摘）。
 */
object PanelReferences {

    /** 引用处数：每个动作槽算一处，长按变体单独算一处。 */
    fun count(
        panelId: String,
        action: ActionSettings,
        buttons: List<GestureButton>,
        subGestures: List<SubGesture>,
    ): Int {
        var total = 0
        fun tally(action: StoredAction?) {
            if (action == null) return
            if (action.isPanelRef(panelId)) total++
            if (action.longPressVariant()?.isPanelRef(panelId) == true) total++
        }
        action.actionPanels.panels.forEach { panel -> panel.items.forEach(::tally) }
        action.actionGridPanels.panels.forEach { panel ->
            panel.portrait.items.forEach { item -> item.actionItems().forEach(::tally) }
            panel.landscape.items.forEach { item -> item.actionItems().forEach(::tally) }
        }
        buttons.flatMap { it.actionList() }.forEach(::tally)
        subGestures.flatMap { it.actionList() }.forEach(::tally)
        tally(action.floatBall.tapAction)
        return total
    }
}

/** 触钮的全部动作槽：四个方向类 + 点按/双击/长按。 */
fun GestureButton.actionList(): List<StoredAction> =
    slideActions.actions.values +
        slideHoldActions.actions.values +
        longSlideActions.actions.values +
        longSlideHoldActions.actions.values +
        listOfNotNull(tapActions, doubleTapActions, longPressActions)

/** 子手势的四个方向类动作槽。 */
fun SubGesture.actionList(): List<StoredAction> =
    slideActions.actions.values +
        slideHoldActions.actions.values +
        longSlideActions.actions.values +
        longSlideHoldActions.actions.values

private fun StoredAction.isPanelRef(panelId: String): Boolean =
    actionPanelId() == panelId || gridPanelId() == panelId

/** 网格项承载的动作（小部件/内置部件没有动作）。 */
private fun GridPanelItem.actionItems(): List<StoredAction> =
    (kind as? GridPanelItem.Kind.Action)?.items.orEmpty()
