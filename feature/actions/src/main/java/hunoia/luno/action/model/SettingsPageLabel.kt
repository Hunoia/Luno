package hunoia.luno.action.model

import hunoia.luno.core.domain.SettingsPage

/**
 * 设置页的展示名：动作变体（搜索结果 / 选择页 / 按钮标题）共用一份。
 *
 * 与 `ActionDefinition.name` 同口径用中文常量：搜索索引在无 Context 的契约口组装
 * （`ActionSearchSource.index`），不能只给资源 id。执行口的 `SettingsPage` 只是枚举。
 */
val SettingsPage.label: String
    get() = when (this) {
        SettingsPage.WIFI -> "Wi-Fi"
        SettingsPage.BLUETOOTH -> "蓝牙"
        SettingsPage.DISPLAY -> "显示"
        SettingsPage.BATTERY -> "电池"
        SettingsPage.APPS -> "应用"
        SettingsPage.NETWORK -> "网络"
        SettingsPage.ACCESSIBILITY -> "无障碍"
        SettingsPage.MAIN -> "主设置"
    }
