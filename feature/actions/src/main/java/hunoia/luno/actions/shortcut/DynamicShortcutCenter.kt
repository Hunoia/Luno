package hunoia.luno.actions.shortcut

import android.content.Context
import android.content.pm.LauncherApps
import hunoia.luno.action.model.ShortcutIconFiles
import hunoia.luno.bridge.launcher.DynamicShortcuts
import hunoia.luno.bridge.launcher.toShortcutInfo
import hunoia.luno.bridge.system.HomeRoleController
import hunoia.luno.config.DynamicShortcutRefsStore
import hunoia.luno.config.HomeSwitchSnapshotStore
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.DynamicShortcutRef
import hunoia.luno.core.domain.HomeSwitchSnapshot
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.runtime.surface.SurfaceDispatch
import hunoia.luno.shizuku.ShizukuFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** `app.launchShortcut` 的动作形态，与选择页落盘的同一种 JSON。 */
fun DynamicShortcutRef.storedAction(): StoredAction = StoredAction.of(
    "app.launchShortcut",
    "data" to JsonSerializer.encodeToString(toShortcutInfo()),
)

/**
 * 动态快捷方式的业务编排。
 *
 * 系统只把动态快捷方式暴露给默认桌面，所以**采集**（读全部入口、pin 全部、导入全部）
 * 需要 Luno 持默认桌面权限；启动同样依赖该权限（`LauncherApps.startShortcut`）。
 * 启动走与动作执行完全相同的 `app.launchShortcut` 分发。
 */
object DynamicShortcutCenter {

    fun hasPermission(context: Context): Boolean = DynamicShortcuts.hasHostPermission(context)

    fun homeSettingsIntent() = DynamicShortcuts.homeSettingsIntent()

    /** 已导入入口（选择器 / 搜索合并用）。 */
    suspend fun imported(): List<DynamicShortcutRef> =
        DynamicShortcutRefsStore.imported()

    /**
     * 采集全部（需默认桌面权限）：读系统全部动态 / 已固定入口，pin 全部并标为已导入。
     * 已导入但系统已消失的记录不静默删除。
     */
    suspend fun collectAll(context: Context): Int {
        check(hasPermission(context)) { "Shortcut access requires the default home app" }
        val entries = withContext(Dispatchers.IO) {
            DynamicShortcuts.query(context)
        }
        // pin 替换调用方整组：必须先读已有 pin 再追加本次全部，避免把已固定入口丢掉。
        entries.groupBy { it.packageName to it.userSerial }.forEach { (key, group) ->
            val (packageName, userSerial) = key
            val existing = withContext(Dispatchers.IO) {
                DynamicShortcuts.pinnedIds(context, packageName, userSerial)
            }
            DynamicShortcuts.pin(
                context, packageName, userSerial,
                existing + group.map { it.shortcutId },
            )
        }
        val refs = withContext(Dispatchers.IO) {
            entries.map { entry ->
                val bitmap = DynamicShortcuts.iconBitmap(
                    context = context,
                    packageName = entry.packageName,
                    shortcutId = entry.shortcutId,
                    userSerial = entry.userSerial,
                )
                val saved = ShortcutIconFiles.saveBitmap(
                    context, bitmap, iconKey(entry.packageName, entry.shortcutId, entry.userSerial),
                )
                DynamicShortcutRef(
                    packageName = entry.packageName,
                    shortcutId = entry.shortcutId,
                    userSerial = entry.userSerial,
                    userId = entry.userId,
                    label = entry.label,
                    appLabel = entry.appLabel,
                    imported = true,
                    iconPath = saved,
                )
            }
        }
        DynamicShortcutRefsStore.update { it.collectAll(refs) }
        DynamicShortcuts.notifyChanged(context)
        return refs.size
    }

    /**
     * pin 拦截：受理应用内「添加到桌面」请求并入库（仅持默认桌面的窗口期）。
     *
     * accept 把 pin 记到 Luno 名下——这是本机 ShortcutService 按 launcher 隔离 pinned
     * 集合后，别人桌面已固定入口能进采集视野的唯一合规通道。返回入口标签（toast 用），
     * 受理 / 落库失败回 null。
     */
    suspend fun importPinRequest(context: Context, request: LauncherApps.PinItemRequest): String? {
        val info = DynamicShortcuts.acceptPinRequest(request) ?: return null
        val serial = DynamicShortcuts.serialNumberForUser(context, info.userHandle)
        val bitmap = DynamicShortcuts.iconBitmapFromInfo(context, info)
        val saved = ShortcutIconFiles.saveBitmap(context, bitmap, iconKey(info.`package`, info.id, serial))
        val ref = DynamicShortcutRef(
            packageName = info.`package`,
            shortcutId = info.id,
            userSerial = serial,
            userId = info.userHandle.hashCode(),
            label = (info.shortLabel ?: info.longLabel ?: info.id).toString(),
            appLabel = DynamicShortcuts.appLabelOf(context, info.`package`, info.userHandle),
            imported = true,
            iconPath = saved,
        )
        DynamicShortcutRefsStore.update { it.upsertImported(ref) }
        DynamicShortcuts.notifyChanged(context)
        return ref.label
    }

    /** Shizuku 就绪 = 自动切换可用；未就绪时入口置灰，走手动更改默认桌面兜底。 */
    fun autoSwitchReady(): Boolean = HomeRoleController.available()

    /** 自动切换会话进行中：有未恢复的快照，且自己仍是 HOME 持有者。 */
    suspend fun activeSession(context: Context): Boolean {
        val snapshot = HomeSwitchSnapshotStore.snapshot()
        if (snapshot.homePackage.isBlank()) return false
        return HomeRoleController.holders().contains(context.packageName)
    }

    /**
     * 自动切换为临时桌面：快照原桌面持有者与导航方式 → 落库 → 切换。
     * 任何一步失败清掉快照并返回 false（此时系统态未变或已回滚）。
     */
    suspend fun beginAutoSession(context: Context): Boolean {
        if (HomeRoleController.holders().contains(context.packageName)) return false
        val holders = HomeRoleController.holders()
        if (holders.isEmpty()) return false
        val snapshot = HomeSwitchSnapshot(
            homePackage = holders.first(),
            holders = holders,
            navigationMode = HomeRoleController.navigationMode(),
            createdAtMs = System.currentTimeMillis(),
        )
        HomeSwitchSnapshotStore.update { snapshot }
        val switched = HomeRoleController.setHomeTo(
            context.packageName,
            DynamicShortcuts.homeEntryComponent(context),
        )
        if (!switched) HomeSwitchSnapshotStore.clear()
        return switched
    }

    /**
     * 结束自动切换会话：先采集（仍持 HOME，失败不挡恢复）→ 恢复原桌面 → 还原导航方式。
     * 桌面恢复失败保留快照（横幅态可重试），返回恢复是否成功。
     */
    suspend fun endAutoSession(context: Context): Boolean {
        val snapshot = HomeSwitchSnapshotStore.snapshot()
        if (snapshot.homePackage.isBlank()) return false
        runCatching { collectAll(context) }
        val restored = HomeRoleController.restoreHome(context.packageName, snapshot.holders)
        snapshot.navigationMode?.let { HomeRoleController.setNavigationMode(it) }
        if (restored) HomeSwitchSnapshotStore.clear()
        return restored
    }

    /**
     * 轻点已导入项：走与动作执行完全相同的 `app.launchShortcut` 分发，
     * 复用解冻、启动统计与小窗路由。
     */
    fun launch(ref: DynamicShortcutRef, onSettled: (Boolean) -> Unit = {}) {
        SurfaceDispatch.dispatch(
            action = ref.storedAction(),
            origin = DispatchOrigin.PANEL,
            onSettled = onSettled,
        )
    }

    private fun iconKey(packageName: String, shortcutId: String, userSerial: Long): String =
        "dyn_${packageName}_${shortcutId}_${userSerial}"
}
