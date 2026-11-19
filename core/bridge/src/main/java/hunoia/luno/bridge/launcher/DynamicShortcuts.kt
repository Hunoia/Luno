package hunoia.luno.bridge.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import hunoia.luno.core.LunoLog as Log

/**
 * 系统动态快捷方式（`LauncherApps`）的唯一平台出口。
 *
 * `pinShortcuts` / `getShortcuts` 只对持有 `ROLE_HOME` 的默认桌面开放；读取与固定都放在
 * 设置页的显式操作里，不做后台轮询。切回原桌面后，此前已 pin 的入口仍可经
 * `startShortcut` 启动（系统没有公开的 unpin API）。
 *
 * minSdk = 33：`profiles` / `getApplicationInfo(pkg, 0, user)` / `RoleManager` 全可用，
 * 不需要版本分支。
 */
object DynamicShortcuts {

    private const val TAG = "DynamicShortcuts"

    /** `:service` 的搜索索引与主进程导入页共享的刷新信号（进程内广播，不导出）。 */
    const val ACTION_CHANGED = "hunoia.luno.action.DYNAMIC_SHORTCUTS_CHANGED"

    /** 系统当前可见的动态 / 已固定快捷方式。 */
    data class Entry(
        val packageName: String,
        val shortcutId: String,
        val userSerial: Long,
        /** 资料空间的 Android 用户 id（`UserHandle.hashCode()`；0 = 主用户）。 */
        val userId: Int,
        val label: String,
        val appLabel: String,
        /** 系统侧是否已 pin；本地 `imported` 只认回查确认后的结果。 */
        val pinned: Boolean,
    )

    fun hasHostPermission(context: Context): Boolean = runCatching {
        launcher(context).hasShortcutHostPermission()
    }.getOrDefault(false)

    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    /**
     * 读取所有可访问资料空间的动态 / 已固定入口。锁定中的资料空间跳过（读取会抛），
     * 主用户仍可正常导入；不解锁就整页失败没有意义。
     */
    fun query(context: Context): List<Entry> {
        val launcher = launcher(context)
        val users = context.getSystemService(UserManager::class.java)
        val result = arrayListOf<Entry>()
        val profiles = runCatching { launcher.profiles }.getOrDefault(listOf(Process.myUserHandle()))
        for (user in profiles) {
            if (!runCatching { users.isUserUnlocked(user) }.getOrDefault(false)) {
                Log.i(TAG, "skip locked profile uid=${user.hashCode()}")
                continue
            }
            val serial = runCatching { users.getSerialNumberForUser(user) }.getOrDefault(-1L)
            if (serial < 0) continue
            val shortcuts = runCatching { launcher.getShortcuts(shortcutQuery(), user) }
                .getOrElse {
                    Log.w(TAG, "getShortcuts failed uid=${user.hashCode()}", it)
                    emptyList()
                }
                .orEmpty()
            for (info in shortcuts) {
                if (!info.isEnabled) continue
                val label = (info.shortLabel ?: info.longLabel ?: info.id).toString()
                val appLabel = runCatching {
                    launcher.getApplicationInfo(info.`package`, 0, user)
                }.getOrNull()?.let { appInfo ->
                    runCatching { context.packageManager.getApplicationLabel(appInfo).toString() }
                        .getOrDefault(info.`package`)
                } ?: info.`package`
                result += Entry(
                    packageName = info.`package`,
                    shortcutId = info.id,
                    userSerial = serial,
                    // 与 AppCatalog.queryCloneApps 的 profile.hashCode() 同口径：
                    // 选择页按这个值把动态入口挂到对应资料空间的应用行。
                    userId = user.hashCode(),
                    label = label,
                    appLabel = appLabel,
                    pinned = info.isPinned,
                )
            }
        }
        return result.sortedWith(
            compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }, { it.userSerial }),
        )
    }

    fun pinnedIds(context: Context, packageName: String, userSerial: Long): Set<String> {
        val user = userForSerial(context, userSerial) ?: return emptySet()
        val launcher = launcher(context)
        return runCatching {
            launcher.getShortcuts(
                shortcutQuery().apply { setPackage(packageName) },
                user,
            ).orEmpty().filter { it.isPinned }.mapTo(linkedSetOf()) { it.id }
        }.getOrElse {
            Log.w(TAG, "pinnedIds failed pkg=$packageName user=$userSerial", it)
            emptySet()
        }
    }

    /** 系统会替换调用方的整组 pin：调用方必须把已有 id 一起传进来。 */
    fun pin(context: Context, packageName: String, userSerial: Long, ids: Set<String>) {
        if (ids.isEmpty()) return
        val user = requireNotNull(userForSerial(context, userSerial)) {
            "User profile is unavailable"
        }
        launcher(context).pinShortcuts(packageName, ids.toList(), user)
    }

    /**
     * 快捷方式自己的图标（不是应用图标）。读取后立刻落成 PNG，选择器/搜索只读本地文件，
     * 不需要在每次组合时再回系统查询。
     */
    fun iconBitmap(
        context: Context,
        packageName: String,
        shortcutId: String,
        userSerial: Long,
        sizePx: Int = 192,
    ): Bitmap? = runCatching {
        val user = if (userSerial < 0) Process.myUserHandle() else userForSerial(context, userSerial)
        user ?: return null
        val info = launcher(context).getShortcuts(
            shortcutQuery().apply {
                setPackage(packageName)
                setShortcutIds(listOf(shortcutId))
            },
            user,
        ).orEmpty().firstOrNull { it.id == shortcutId } ?: return null
        val drawable = launcher(context).getShortcutIconDrawable(
            info,
            context.resources.displayMetrics.densityDpi,
        ) ?: return null
        val side = sizePx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, side, side)
        drawable.draw(Canvas(bitmap))
        bitmap
    }.getOrElse {
        Log.w(TAG, "icon failed pkg=$packageName id=$shortcutId user=$userSerial", it)
        null
    }

    /** 用户序列号 → `UserHandle`；资料空间被移除 / 序列号无效时返回 null。 */
    fun userForSerial(context: Context, userSerial: Long): UserHandle? {
        if (userSerial < 0) return Process.myUserHandle()
        return runCatching {
            context.getSystemService(UserManager::class.java).getUserForSerialNumber(userSerial)
        }.getOrNull()
    }

    /**
     * 资料空间用户 id（`UserHandle.hashCode()`）反查：旧导入记录缺 `userId` 时，
     * 用 `userSerial` 经 UserManager 换算，不依赖默认桌面权限。
     */
    fun userIdForSerial(context: Context, userSerial: Long): Int =
        userForSerial(context, userSerial)?.hashCode() ?: -1

    /** 导入完成后让 `:service` 的搜索索引下次打开时重扫。 */
    fun notifyChanged(context: Context) {
        runCatching {
            context.sendBroadcast(Intent(ACTION_CHANGED).setPackage(context.packageName))
        }
    }

    private fun launcher(context: Context): LauncherApps =
        context.getSystemService(LauncherApps::class.java)

    private fun shortcutQuery(): LauncherApps.ShortcutQuery = LauncherApps.ShortcutQuery().apply {
        setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
    }
}
