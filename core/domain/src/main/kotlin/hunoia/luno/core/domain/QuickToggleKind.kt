package hunoia.luno.core.domain

/**
 * 「快捷开关」动作（`system.toggle`）的目标开关。
 *
 * 住在契约层：动作定义、搜索变体、执行器共用；显示名与图标留在 `:feature:actions`
 * （展示知识）。枚举顺序即选择页与搜索变体的展示顺序。
 */
enum class QuickToggleKind {
    WIFI,
    BLUETOOTH,
    DND,
    MOBILE_DATA,
    AIRPLANE,
    LOCATION,
    HOTSPOT,
    AUTO_ROTATE,
    DARK_MODE,
    ;

    companion object {
        /** 缺省开关：动作未配置时的目标，选择页回退与解码默认值共用同一个事实源。 */
        val DEFAULT = DND
    }
}
