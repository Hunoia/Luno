package hunoia.luno.picker

import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.quickaccess.model.identity
import hunoia.luno.quickaccess.query.AppDrawerSections

/**
 * 选择页的一行应用：应用本体 + 挂在它下面的快捷方式来源。
 *
 * 快捷方式原本是两组独立列表（能创建快捷方式的应用 / 已有快捷方式的应用），
 * 同一个应用会在页面上出现三次。这里按**包名**归并：应用行点开就是它自己的快捷方式，
 * 用户找「微信 → 发消息」只需要记住应用名。
 *
 * 按包名而不是 [identity] 关联：快捷方式来源只带 `packageName/className`，不带 userId，
 * 同一包的分身会各自带一份相同快捷方式——这比「分身下列不出快捷方式」符合直觉。
 */
internal data class AppShortcutEntry(
    val app: AppInfo,
    /** 支持 ACTION_CREATE_SHORTCUT 的入口（每行一个创建器）。 */
    val createLaunchers: List<LauncherInfo>,
    /** 已有快捷方式的应用入口，`shortcuts` 是具体条目。 */
    val launchLaunchers: List<LauncherInfo>,
) {
    val rowKey: String get() = app.identity

    /** 已有快捷方式（多个入口可能指向同一包的不同 activity，展开时逐条列出）。 */
    val shortcuts: List<LauncherInfo.ShortcutInfo> get() = launchLaunchers.flatMap { it.shortcuts }

    val hasChildren: Boolean get() = createLaunchers.isNotEmpty() || shortcuts.isNotEmpty()
}

/**
 * 应用清单与两组快捷方式来源合成一个序列。**排序与分节不在这里**：
 * 交给 [AppDrawerSections.alphabeticalSectionsOf]（拼音首字母 A–#、节内全拼序），
 * 这里只负责把同一包的快捷方式挂到应用上。
 */
internal fun buildAppShortcutEntries(
    apps: List<AppInfo>,
    createShortcuts: List<LauncherInfo>,
    launchShortcuts: List<LauncherInfo>,
): List<AppShortcutEntry> {
    if (apps.isEmpty()) return emptyList()
    val createByPackage = createShortcuts.groupBy { it.packageName }
    val launchByPackage = launchShortcuts.groupBy { it.packageName }
    return apps.map { app ->
        AppShortcutEntry(
            app = app,
            createLaunchers = createByPackage[app.packageName].orEmpty(),
            launchLaunchers = launchByPackage[app.packageName].orEmpty(),
        )
    }
}

/** 搜索命中：应用名 / 包名 / 快捷方式名任一命中。 */
internal fun AppShortcutEntry.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    fun LauncherInfo.matches(): Boolean =
        label.contains(query, ignoreCase = true) ||
            packageName.contains(query, ignoreCase = true) ||
            shortcuts.any { it.label.contains(query, ignoreCase = true) }
    return app.label.contains(query, ignoreCase = true) ||
        app.packageName.contains(query, ignoreCase = true) ||
        createLaunchers.any { it.matches() } ||
        launchLaunchers.any { it.matches() }
}

/**
 * 搜索态只保留命中的子项：应用名命中时保留全部快捷方式；只有快捷方式名命中时
 * 只列命中的那一行，避免搜「发消息」却把整个应用的所有入口都摊开。
 */
internal fun AppShortcutEntry.filterChildren(query: String): AppShortcutEntry {
    if (query.isBlank()) return this
    val appMatches = app.label.contains(query, ignoreCase = true) ||
        app.packageName.contains(query, ignoreCase = true)
    if (appMatches) return this
    fun LauncherInfo.matches(): Boolean =
        label.contains(query, ignoreCase = true) ||
            packageName.contains(query, ignoreCase = true) ||
            shortcuts.any { it.label.contains(query, ignoreCase = true) }
    val creates = createLaunchers.filter { it.matches() }
    val launches = launchLaunchers.mapNotNull { launcher ->
        val launcherMatches = launcher.label.contains(query, ignoreCase = true) ||
            launcher.packageName.contains(query, ignoreCase = true)
        val shortcuts = if (launcherMatches) {
            launcher.shortcuts
        } else {
            launcher.shortcuts.filter { it.label.contains(query, ignoreCase = true) }
        }
        launcher.copy(shortcuts = shortcuts).takeIf { launcherMatches || shortcuts.isNotEmpty() }
    }
    return copy(createLaunchers = creates, launchLaunchers = launches)
}
