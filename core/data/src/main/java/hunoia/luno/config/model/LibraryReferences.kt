package hunoia.luno.config.model

import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.libraryEntryId
import hunoia.luno.core.domain.longPressVariant

/**
 * 动作库条目引用（`internal.libraryRef`）的统计。
 *
 * 面板项、网格面板项、触钮槽、子手势槽、全局手势触发、悬浮球单击共用同一套
 * [StoredAction]，所以删库条目前必须能数清「谁指着我」：否则悬空引用只在运行时
 * 表现为「点了没反应」，配置里看不出来。
 * 长按变体（`__longPress`）也是独立一处引用，必须一起数。
 *
 * 纯函数，只读快照；真正改配置的清理在 `LibraryCleaner`。
 */
object LibraryReferences {

    /** 引用处数（按条目 id 分组）：每个动作槽算一处，长按变体单独算一处。 */
    fun count(
        action: ActionSettings,
        buttons: List<GestureButton>,
        subGestures: List<SubGesture>,
        globalGesture: GlobalGestureSettings,
    ): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        fun tally(action: StoredAction?) {
            action?.libraryEntryId()?.let { id ->
                counts[id] = (counts[id] ?: 0) + 1
            }
            action?.longPressVariant()?.libraryEntryId()?.let { id ->
                counts[id] = (counts[id] ?: 0) + 1
            }
        }
        action.actionPanels.panels.forEach { panel -> panel.items.forEach(::tally) }
        action.actionGridPanels.panels.forEach { panel ->
            panel.portrait.items.forEach { item -> item.actionItems().forEach(::tally) }
            panel.landscape.items.forEach { item -> item.actionItems().forEach(::tally) }
        }
        buttons.flatMap { it.actionList() }.forEach(::tally)
        subGestures.flatMap { it.actionList() }.forEach(::tally)
        globalGesture.zones.flatMap { zone -> zone.triggers.flatMap { it.storedActions } }.forEach(::tally)
        tally(action.floatBall.tapAction)
        return counts
    }
}
