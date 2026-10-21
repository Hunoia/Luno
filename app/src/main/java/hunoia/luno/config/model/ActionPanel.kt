package hunoia.luno.config.model

import androidx.annotation.Keep
import hunoia.luno.action.model.StoredAction
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 动作面板：触发时弹出的弧形菜单。必须挂在 [ActionSettings.actionPanels] 下，
 * 否则静默失败。[items] 用内联动作而非动作库引用：`__longPress` 变体随项零成本。
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

    /** 引用该面板的动作数量（删面板前提示用）。 */
    fun countReferences(predicate: (String) -> Boolean): Int =
        panels.count { panel -> predicate(panel.id) }
}