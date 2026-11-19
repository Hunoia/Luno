package hunoia.luno.picker

import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.core.domain.AppInfo
import hunoia.luno.quickaccess.model.identity
import hunoia.luno.quickaccess.query.AppDrawerSections
import hunoia.luno.quickaccess.query.SearchText

/**
 * 选择页的一行应用：应用本体 + 挂在它下面的快捷方式来源。
 *
 * 快捷方式原本是两组独立列表（能创建快捷方式的应用 / 已有快捷方式的应用），
 * 同一个应用会在页面上出现三次。这里按**包名**归并：应用行点开就是它自己的快捷方式，
 * 用户找「微信 → 发消息」只需要记住应用名。
 *
 * 静态入口不带资料空间，随包挂到所有同包行（主/分身共享同一份 shortcuts.xml）；
 * 动态入口只挂到对应资料空间的行，避免「分身行显示主应用的动态快捷方式」。
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
            launchLaunchers = launchByPackage[app.packageName].orEmpty()
                .map { launcher ->
                    launcher.copy(
                        shortcuts = launcher.shortcuts.filter { it.belongsTo(app) },
                    )
                }
                .filter { it.shortcuts.isNotEmpty() },
        )
    }
}

/** 快捷方式是否属于该应用行：静态入口（无资料空间）随包挂所有行，动态只挂对应资料空间。 */
private fun LauncherInfo.ShortcutInfo.belongsTo(app: AppInfo): Boolean =
    userId < 0 || userId == app.userId

/** 搜索命中：应用名 / 包名 / 快捷方式名任一命中（拼音、首字母、模糊音由 [SearchText] 统一）。 */
internal fun AppShortcutEntry.matchesSearch(search: SearchText): Boolean {
    if (search.matches(app.label, app.packageName)) return true
    fun LauncherInfo.matches(): Boolean =
        search.matches(label, packageName) || shortcuts.any { search.matches(it.label) }
    return createLaunchers.any { it.matches() } || launchLaunchers.any { it.matches() }
}

/**
 * 搜索态只保留命中的子项：应用名命中时保留全部快捷方式；只有快捷方式名命中时
 * 只列命中的那一行，避免搜「发消息」却把整个应用的所有入口都摊开。
 */
internal fun AppShortcutEntry.filterChildren(search: SearchText): AppShortcutEntry {
    if (search.matches(app.label, app.packageName)) return this
    fun LauncherInfo.fields(): List<String> =
        listOf(label, packageName) + shortcuts.map { it.label }
    val creates = createLaunchers.filter { search.matchesAnyChunk(it.fields()) }
    val launches = launchLaunchers.mapNotNull { launcher ->
        val launcherFields = listOf(launcher.label, launcher.packageName)
        val launcherMatches = search.matchesAnyChunk(launcherFields)
        val shortcuts = if (launcherMatches) {
            launcher.shortcuts
        } else {
            launcher.shortcuts.filter { search.matchesAnyChunk(listOf(it.label)) }
        }
        launcher.copy(shortcuts = shortcuts).takeIf { launcherMatches || shortcuts.isNotEmpty() }
    }
    // 多词查询可能一段命中父级、另一段命中子项：实在剪不出相关子项时保留整行，
    // 不要出现“应用在列表里、点开却一个快捷方式都没有”的空壳。
    if (creates.isEmpty() && launches.isEmpty()) return this
    return copy(createLaunchers = creates, launchLaunchers = launches)
}
