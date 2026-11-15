package hunoia.luno.action.definitions

import androidx.annotation.StringRes
import hunoia.luno.actions.R

/**
 * 选择页里可展开的动作组。
 *
 * 组只做展示收拢：组头是一行可展开行（与动作行同规格、不可选中），
 * 点按展开/收起，子项仍是具体 [hunoia.luno.core.domain.StoredAction]。
 * [order] 是同一分类内的排序，组按升序排在单动作之前。
 */
enum class ActionGroupSpec(
    val key: String,
    @get:StringRes val labelRes: Int,
    val order: Int,
) {
    QuickToggle("quick_toggle", R.string.action_group_quick_toggle, 0),
    OpenSettings("open_settings", R.string.action_group_open_settings, 1),

    MediaControl("media_control", R.string.action_group_media_control, 0),
    VolumeUp("volume_up", R.string.action_group_volume_up, 1),
    VolumeDown("volume_down", R.string.action_group_volume_down, 2),

    Scroll("scroll", R.string.action_group_scroll, 0),
    ;

    companion object {
        fun byKey(key: String): ActionGroupSpec? = entries.firstOrNull { it.key == key }
    }
}
