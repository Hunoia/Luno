package hunoia.luno.core.domain

import kotlinx.serialization.Serializable

/** 导入来源：pin = pin 拦截（应用内「添加到桌面」受理）；collect = 全量采集。 */
const val SHORTCUT_SOURCE_PIN: String = "pin"
const val SHORTCUT_SOURCE_COLLECT: String = "collect"

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
    /** 导入来源（[SHORTCUT_SOURCE_PIN] / [SHORTCUT_SOURCE_COLLECT]）；旧记录回 collect。 */
    val source: String = SHORTCUT_SOURCE_COLLECT,
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
     * 已存在的记录保留原来源（pin 拦截进来的不因重新采集降级为 collect）。
     */
    fun collectAll(snapshot: List<DynamicShortcutRef>): DynamicShortcutRefs {
        if (snapshot.isEmpty()) return this
        val incoming = snapshot.associateBy { it.identity }
        val existing = items.associateBy { it.identity }
        val refreshed = snapshot.map {
            it.copy(imported = true, source = existing[it.identity]?.source ?: SHORTCUT_SOURCE_COLLECT)
        }
        val orphanImported = items.filter { it.imported && incoming[it.identity] == null }
        return copy(items = refreshed + orphanImported)
    }

    /**
     * 单条导入：pin 拦截（应用内「添加到桌面」）把一条入口记为已导入。
     *
     * 同身份已有记录被整体替换（标签/图标随系统最新定义走），其余条目原样保留——
     * 与 [collectAll] 不同，这里不做孤儿清理：单条导入不持有全量快照，不能判定谁已消失。
     */
    fun upsertImported(ref: DynamicShortcutRef): DynamicShortcutRefs {
        val incoming = ref.copy(imported = true)
        val index = items.indexOfFirst { it.identity == incoming.identity }
        val refreshed = if (index >= 0) {
            items.toMutableList().also { it[index] = incoming }
        } else {
            items + incoming
        }
        return copy(items = refreshed)
    }

    /** 移除一条记录（已导入管理里的删除）：只删引用，不动系统 pin。 */
    fun remove(identity: DynamicShortcutIdentity): DynamicShortcutRefs =
        copy(items = items.filterNot { it.identity == identity })
}

/**
 * 临时桌面的环境快照：自动切换前记下原桌面持有者与导航方式，恢复时按此还原。
 *
 * 纯设备瞬态，不进备份；进程被杀后靠它找回「当前处于临时桌面」的事实。
 */
@Serializable
data class HomeSwitchSnapshot(
    val homePackage: String,
    /** 切换前的 HOME 角色持有者（按系统返回顺序），恢复时逐个补齐。 */
    val holders: List<String> = emptyList(),
    /** 切换前的导航方式（`settings get secure navigation_mode`）；读不到为 null，不恢复。 */
    val navigationMode: String? = null,
    val createdAtMs: Long = 0L,
)
