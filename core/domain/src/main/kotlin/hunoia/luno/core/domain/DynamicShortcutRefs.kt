package hunoia.luno.core.domain

import kotlinx.serialization.Serializable

/**
 * 已读取到本机的动态快捷方式引用。
 *
 * 引用按「包名 + shortcut id + 用户序列号」唯一：工作资料与主用户里的同名入口不合并。
 * 这是系统给「默认桌面」的授权引用，属于设备本地数据，刻意不进入配置备份。
 */
@Serializable
data class DynamicShortcutRef(
    val packageName: String,
    val shortcutId: String,
    val userSerial: Long,
    /**
     * 资料空间的 Android 用户 id（`UserHandle.hashCode()`，0 = 主用户）。
     * 旧记录没有此字段时为 -1，下次系统快照会补上。
     */
    val userId: Int = -1,
    val label: String,
    val appLabel: String,
    /** false = 已暂存、系统 pin 尚未确认；true = 可启动。 */
    val imported: Boolean = false,
    /** 本地图标文件。系统入口更新后图标不跟随，重读时按最新定义覆盖。 */
    val iconPath: String? = null,
) {
    val identity: DynamicShortcutIdentity
        get() = DynamicShortcutIdentity(packageName, shortcutId, userSerial)
}

/** 引用身份：跨 UI 选择与导入确认共用，避免三处各拼一份字符串。 */
data class DynamicShortcutIdentity(
    val packageName: String,
    val shortcutId: String,
    val userSerial: Long,
)

@Serializable
data class DynamicShortcutRefs(
    val items: List<DynamicShortcutRef> = emptyList(),
) {
    fun find(identity: DynamicShortcutIdentity): DynamicShortcutRef? =
        items.firstOrNull { it.identity == identity }

    /** 仅保留可启动记录（导入列表与选择器使用）。 */
    fun imported(): List<DynamicShortcutRef> = items.filter { it.imported }

    /**
     * 全量采集：导入全部（`imported = true`）。
     *
     * 采集把系统快照里的每个入口都标为已导入、可启动。已导入但系统已消失的记录保留
     * （用户可重新采集时移除），不静默丢数据。一次采集后无需再持默认桌面权限。
     */
    fun collectAll(snapshot: List<DynamicShortcutRef>): DynamicShortcutRefs {
        if (snapshot.isEmpty()) return this
        val incoming = snapshot.associateBy { it.identity }
        val refreshed = snapshot.map { it.copy(imported = true) }
        val orphanImported = items.filter { it.imported && incoming[it.identity] == null }
        return copy(items = refreshed + orphanImported)
    }
}
