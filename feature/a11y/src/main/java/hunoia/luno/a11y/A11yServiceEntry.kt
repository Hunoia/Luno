package hunoia.luno.a11y

/**
 * 一个已安装无障碍服务在管理页的投影。
 *
 * [id] 是 `ComponentName.flattenToString()`，与系统
 * `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 里的 token 同形，
 * 因此可直接回传给 [A11yManagerFacade.setEnabled] 做启停。
 */
data class A11yServiceEntry(
    val id: String,
    val label: String,
    val description: String,
    val packageName: String,
    /** 是否当前启用（全局总闸关闭视为全未启用，见 `AccessibilitySettings.enabledComponents`）。 */
    val enabled: Boolean,
    /** 是否本应用的服务：列表警示文案与「关掉自己会被保活写回」的提示都由它决定。 */
    val isOwnService: Boolean,
)
