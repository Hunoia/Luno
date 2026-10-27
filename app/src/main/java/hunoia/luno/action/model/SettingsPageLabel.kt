package hunoia.luno.action.model

import hunoia.luno.R

/**
 * 设置页名称资源：快速搜索与选择页弹窗共用。
 *
 * 资源归属 `:app`，所以这个扩展也留在这里；执行口的 `SettingsPage` 只是枚举。
 */
val SettingsPage.labelRes: Int
    get() = when (this) {
        SettingsPage.WIFI -> R.string.settings_page_wifi
        SettingsPage.BLUETOOTH -> R.string.settings_page_bluetooth
        SettingsPage.DISPLAY -> R.string.settings_page_display
        SettingsPage.BATTERY -> R.string.settings_page_battery
        SettingsPage.APPS -> R.string.settings_page_apps
        SettingsPage.NETWORK -> R.string.settings_page_network
        SettingsPage.ACCESSIBILITY -> R.string.settings_page_accessibility
        SettingsPage.MAIN -> R.string.settings_page_main
    }
