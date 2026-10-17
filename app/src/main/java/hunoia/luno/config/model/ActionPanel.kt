package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.model.StoredAction
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 动作面板：触发时弹出的弧形菜单，菜单项由用户从动作里挑。
 *
 * 单值化改造前，「弹面板」不是一个动作，而是**动作槽里存了 ≥2 个动作**的副作用
 * （`SideGestureContainer.runGestureActions` 判 `meaningfulActions.size > 1`）。
 * 现在它是显式动作类型 `internal.actionPanel`，本类只描述面板**内容**。
 *
 * 面板列表全局一份，挂在 [ActionSettings.actionPanels] 下 —— 这不是随手选的：
 * 只有挂在 `ActionSettings` 下才能顺着 `ConfigProvider.actionSettings → SettingsStore →
 * SettingsState → GestureOverlayView` 到达 `:service` 进程的运行时，运行时弹不出面板
 * 是静默失败，不会有任何报错。
 *
 * [items] 是内联动作而非动作库引用：`__longPress` 长按变体就藏在
 * `StoredAction.params` 里，面板项逐项带变体时零成本；而引用库条目会强迫用户
 * 先把每个动作都存进库才能组面板（见 docs/action-select-single.md §P2）。
 */
@Serializable
@Keep
data class ActionPanel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val items: List<StoredAction> = emptyList(),
) {
    companion object {
        fun create(name: String = ""): ActionPanel = ActionPanel(name = name)
    }
}

/** 全局动作面板列表。 */
@Serializable
@Keep
data class ActionPanelSettings(
    val panels: List<ActionPanel> = emptyList(),
) {
    fun panelById(id: String): ActionPanel? = panels.firstOrNull { it.id == id }

    /** 按 id 更新/新增：已有则替换，没有则追加到末尾。 */
    fun withPanel(panel: ActionPanel): ActionPanelSettings = copy(
        panels = if (panels.any { it.id == panel.id }) {
            panels.map { if (it.id == panel.id) panel else it }
        } else {
            panels + panel
        }
    )

    fun withoutPanel(id: String): ActionPanelSettings =
        copy(panels = panels.filterNot { it.id == id })

    /** 引用了这个面板的动作数量（删面板前提示用，见 docs §3.1）。 */
    fun countReferences(predicate: (String) -> Boolean): Int =
        panels.count { panel -> predicate(panel.id) }
}