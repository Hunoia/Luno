package hunoia.luno.quicklaunch.query

import android.content.Context
import hunoia.luno.quicklaunch.model.AppInfo

data class QuickAppLauncherAppList(
    val apps: List<AppInfo>,
    val frozenPkgs: Set<String>
)

object QuickAppLauncherBaseQuery {
    fun queryApps(context: Context): QuickAppLauncherAppList {
        val launcherApps = AppQuery.queryLauncherActivities(
            context = context,
            allowRepeatPackage = false
        )
        return QuickAppLauncherAppList(
            apps = launcherApps,
            frozenPkgs = emptySet()
        )
    }
}
