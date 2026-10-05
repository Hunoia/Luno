package hunoia.luno.quicklaunch.query

import android.content.Context
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.content.pm.PackageManager
import hunoia.luno.bridge.system.queryIntentActivitiesCompat
import hunoia.luno.quicklaunch.model.AppInfo

data class QuickAppLauncherAppList(
    val apps: List<AppInfo>,
    val disabledPkgs: Set<String>
)

object LauncherIconQuery {

    fun resolveShortcutIconResourceId(context: Context, iconResource: ShortcutIconResource): Int {
        return runCatching {
            val resources = context.packageManager.getResourcesForApplication(iconResource.packageName)
            @Suppress("DiscouragedApi")
            resources.getIdentifier(iconResource.resourceName, null, null)
        }.getOrDefault(0)
    }
}

object LauncherEnvironment {
    fun isLauncherPackage(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val launcherIntent = Intent().apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
        }
        return context.packageManager
            .queryIntentActivitiesCompat(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { resolveInfo ->
                val resolvedPackage = resolveInfo.activityInfo.packageName ?: return@filter false
                context.packageManager.getLaunchIntentForPackage(resolvedPackage) == null
            }
            .any { resolveInfo -> resolveInfo.activityInfo?.packageName == packageName }
    }
}
