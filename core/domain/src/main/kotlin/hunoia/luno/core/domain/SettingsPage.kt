package hunoia.luno.core.domain

/**
 * 系统设置页（`system.openSettings` 的 page 参数取值）。
 *
 * 住在契约层：动作定义、搜索条目、UI 选择器都要用它；显示名与资源 id 留在
 * `:feature:actions`（展示知识）。
 */
enum class SettingsPage {
    WIFI, BLUETOOTH, DISPLAY, BATTERY, APPS, NETWORK, ACCESSIBILITY, MAIN,
    ;

    companion object {
        /** 缺省页：动作未配置时的目标，选择页回退与解码默认值共用同一个事实源。 */
        val DEFAULT = MAIN
    }
}
