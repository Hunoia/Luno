package hunoia.luno.actions.shortcut

import android.content.Context
import hunoia.luno.action.model.ActionDeepLink
import hunoia.luno.action.model.ShortcutIconFiles
import hunoia.luno.bridge.launcher.DynamicShortcuts
import hunoia.luno.bridge.launcher.toShortcutInfo
import hunoia.luno.config.DynamicShortcutRefsStore
import hunoia.luno.core.domain.DispatchOrigin
import hunoia.luno.core.domain.DynamicShortcutRef
import hunoia.luno.core.domain.JsonSerializer
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.runtime.surface.SurfaceDispatch
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

    fun actionLink(ref: DynamicShortcutRef): String = ActionDeepLink.ofAction(ref.storedAction())

    private fun iconKey(packageName: String, shortcutId: String, userSerial: Long): String =
        "dyn_${packageName}_${shortcutId}_${userSerial}"
}
