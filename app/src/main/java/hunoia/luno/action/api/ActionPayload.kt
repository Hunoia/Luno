package hunoia.luno.action.api

import hunoia.luno.config.model.Action
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.model.LauncherInfo
import hunoia.luno.core.JsonSerializer

val Action.appInfo: AppInfo? get() {
    if (value == ActionFacade.EXTRA_LAUNCH_APP) {
        return JsonSerializer.decodeFromString<AppInfo>(data)
    }
    return null
}

val Action.shortcutInfo: LauncherInfo.ShortcutInfo? get() {
    if (value == ActionFacade.EXTRA_LAUNCH_SHORTCUT) {
        return JsonSerializer.decodeFromString<LauncherInfo.ShortcutInfo>(data)
    }
    return null
}
