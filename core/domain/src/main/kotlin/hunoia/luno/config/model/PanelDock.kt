package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

object PanelDockDefaults {
    /**
     * 底栏成员上限。底栏是固定宽 tab、不滚动的胶囊（见 ui-guidelines §4.2），
     * 再多就会溢出屏幕；需要更多入口时应该分面板，而不是把胶囊拉成传送带。
     */
    const val MaxMembers = 6

    /** 至少两块面板才值得切换：一块时底栏只是白占高度。 */
    const val MinVisibleMembers = 2
}

/**
 * 面板底栏配置：在面板内切换不同的网格面板 / 单功能面板。
 *
 * 底栏是 `grid-panel` Surface 的 chrome，不是新 Surface、不是新面板计数：
 * 切换只换内容，面板窗口、锚点、前台应用快照都不重来。
 *
 * 成员是**面板引用 id**（网格面板 UUID 或 `single:<widgetId>`），与手势绑定用的是同一套 id，
 * 因此悬空引用可以像其它引用一样被 `PanelCleaner` 一并摘掉。
 * 顺序即 tab 顺序；切换本身不落盘（下一次打开仍回到触发条目绑定的那块）。
 */
@Serializable
@Keep
data class PanelDockSettings(
    /** 总开关。关掉 = 底栏不渲染、也不扣面板可用高度。 */
    val enabled: Boolean = false,
    val members: List<String> = emptyList(),
) {
    /** 追加到末尾；已在列表里或已满上限时原样返回（不重复、不重排）。 */
    fun withMember(id: String): PanelDockSettings =
        if (id.isBlank() || members.contains(id) || members.size >= PanelDockDefaults.MaxMembers) {
            this
        } else {
            copy(members = members + id)
        }

    fun withoutMember(id: String): PanelDockSettings =
        if (members.contains(id)) copy(members = members - id) else this

    /** 上移 / 下移一位（[-1] / [+1]），越界不动。 */
    fun moved(id: String, offset: Int): PanelDockSettings {
        val from = members.indexOf(id)
        if (from < 0) return this
        val to = (from + offset).coerceIn(0, members.lastIndex)
        if (to == from) return this
        return copy(members = members.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** 还能再加：受 [PanelDockDefaults.MaxMembers] 限制。 */
    val canAddMore: Boolean get() = members.size < PanelDockDefaults.MaxMembers
}
