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

    /**
     * 写入暂存记录：新记录 `imported = false`；已有记录保留原来的确认状态。
     * 已有记录在重新固定失败时仍可启动，不因一次失败丢掉此前已确认的入口。
     */
    fun withStaged(selected: List<DynamicShortcutRef>): DynamicShortcutRefs {
        if (selected.isEmpty()) return this
        val incoming = selected.associateBy { it.identity }
        val kept = items.map { existing ->
            incoming[existing.identity]?.let { fresh ->
                fresh.copy(
                    // 老图标仍然可用；本次没拿到新图标时不要把它清掉。
                    iconPath = fresh.iconPath ?: existing.iconPath,
                    imported = existing.imported,
                )
            } ?: existing
        }
        val added = selected.filter { find(it.identity) == null }
        return copy(items = kept + added)
    }

    /** 系统 pin 已确认：按身份把记录标为可启动。 */
    fun withConfirmed(identities: Collection<DynamicShortcutIdentity>): DynamicShortcutRefs {
        if (identities.isEmpty()) return this
        val confirmed = identities.toSet()
        return copy(items = items.map { item ->
            if (item.identity in confirmed) item.copy(imported = true) else item
        })
    }

    /**
     * 一次系统查询的快照：只刷新本地已有记录（标签 / 图标 / 中断导入的确认），
     * 不把查询到的候选全部落盘——未勾选的入口不该占本地记录。
     * 已有记录只升级不降级：系统查询暂时失败 / 别的桌面重建索引时，不能把已确认入口清掉。
     */
    fun withSystemSnapshot(snapshot: List<DynamicShortcutRef>): DynamicShortcutRefs {
        if (snapshot.isEmpty()) return this
        val incoming = snapshot.associateBy { it.identity }
        return copy(items = items.map { existing ->
            incoming[existing.identity]?.let { system ->
                system.copy(
                    iconPath = system.iconPath ?: existing.iconPath,
                    imported = existing.imported || system.imported,
                )
            } ?: existing
        })
    }

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

/**
 * 导入协调端口：先暂存元信息，再让系统固定，回查确认后才发布为可启动。
 * 平台实现（`:core:bridge`）与存储实现（`:core:data`）都从调用方注入。
 */
interface ShortcutImportBackend {
    suspend fun pinnedIds(packageName: String, userSerial: Long): Set<String>
    suspend fun stage(shortcuts: List<DynamicShortcutIdentity>)
    suspend fun pin(packageName: String, userSerial: Long, ids: Set<String>)
    suspend fun confirm(shortcuts: List<DynamicShortcutIdentity>)
}

/**
 * 单个（包名 + 资料空间）分组的导入：保留调用方已有的 pin，只追加新选中的 id。
 *
 * `pinShortcuts` 会替换调用方此前固定的整组 id，所以必须带上已有的 pin；
 * 固定后再回查一次系统实际结果，只有系统真正认下的入口才标记为可启动。
 */
suspend fun importShortcutGroup(
    backend: ShortcutImportBackend,
    selected: List<DynamicShortcutIdentity>,
) {
    require(selected.isNotEmpty())
    val first = selected.first()
    require(selected.all { it.packageName == first.packageName && it.userSerial == first.userSerial })
    val existing = backend.pinnedIds(first.packageName, first.userSerial)
    backend.stage(selected)
    backend.pin(first.packageName, first.userSerial, existing + selected.map { it.shortcutId })
    val actual = backend.pinnedIds(first.packageName, first.userSerial)
    val confirmed = selected.filter { it.shortcutId in actual }
    backend.confirm(confirmed)
    check(confirmed.size == selected.size) { "Some shortcuts were not pinned" }
}
