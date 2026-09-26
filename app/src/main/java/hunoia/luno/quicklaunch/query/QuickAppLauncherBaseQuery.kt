package hunoia.luno.quicklaunch.query

import hunoia.luno.quicklaunch.model.AppInfo

data class QuickAppLauncherAppList(
    val apps: List<AppInfo>,
    val disabledPkgs: Set<String>
)
