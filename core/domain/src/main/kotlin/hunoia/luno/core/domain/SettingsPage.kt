package hunoia.luno.core.domain

/**
 * 系统设置页（`system.openSettings` 的 page 参数取值）。
 *
 * 住在契约层：动作定义、搜索条目、UI 选择器都要用它；显示名与资源 id 留在
 * `:feature:actions`（展示知识）。
 */
enum class SettingsPage {
    WIFI, BLUETOOTH, DISPLAY, BATTERY, APPS, NETWORK, ACCESSIBILITY, MAIN
}
