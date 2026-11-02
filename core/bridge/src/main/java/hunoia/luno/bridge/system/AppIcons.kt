package hunoia.luno.bridge.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle

/**
 * 应用图标查询：主用户走 PackageManager，分身走 LauncherApps。
 *
 * 分身的包名/Activity 与主应用完全相同，只有用户不同；LauncherApps 会返回
 * MIUI 自带的「分身角标」图标（getBadgedIcon），这也是系统选择器里看到的样式。
 */
object AppIcons {

    fun drawable(
        context: Context,
        packageName: String,
        className: String = "",
        userId: Int = 0,
    ): Drawable? = runCatching {
        if (userId == 0 || userId == Process.myUserHandle().hashCode()) {
            val pm = context.packageManager
            if (className.isNotEmpty()) {
                pm.getActivityIcon(ComponentName.createRelative(packageName, className))
            } else {
                pm.getApplicationIcon(packageName)
            }
        } else {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val user = userHandle(userId) ?: return null
            val activities = launcherApps.getActivityList(packageName, user)
            val activity = activities.firstOrNull {
                className.isEmpty() || it.componentName.className == className
            } ?: activities.firstOrNull() ?: return null
            activity.getBadgedIcon(0)
        }
    }.getOrNull()

    /** 用户 id → UserHandle；UserHandle 没有公开的 of(int)，uid 换算可用。 */
    fun userHandle(userId: Int): UserHandle? = runCatching {
        UserHandle.getUserHandleForUid(userId * 100000)
    }.getOrNull()
}
