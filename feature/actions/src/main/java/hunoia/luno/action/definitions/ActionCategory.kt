package hunoia.luno.action.definitions

/**
 * 动作选择页/动作库的分组。
 *
 * 名称只表达用户看到的语义，不绑定定义表的排列；顺序由
 * [ActionDefinitions.categoryOrder] 显式决定。
 */
enum class ActionCategory {
    /** 「无动作」：选择页置顶且不显示组标题。 */
    NONE,
    APP,
    LINK_SHARE,
    SYSTEM,
    MEDIA,
    CLIPBOARD,
    NAVIGATION,
    OPERATION,
    UI,
    POWER,
    VISIBILITY,
    ENTRY,
    GESTURE_TOOL,
    ADVANCED,
    ACTION_PANEL,
    GRID_PANEL,
    SUB_GESTURE,
}
