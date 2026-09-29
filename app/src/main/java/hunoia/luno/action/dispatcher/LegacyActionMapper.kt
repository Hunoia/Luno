package hunoia.luno.action.dispatcher

import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.action.model.StoredAction
import hunoia.luno.config.model.Action as LegacyAction
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.OpenUrlQueryParameter
import hunoia.luno.config.model.ShellCommandData
import hunoia.luno.core.JsonSerializer
import hunoia.luno.quicklaunch.model.AppInfo
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object LegacyActionMapper {

    fun map(action: LegacyAction): StoredAction? = when (action.value) {
        ActionFacade.NONE -> StoredAction.of("internal.none")
        ActionFacade.BACK -> StoredAction.of("accessibility.back")
        ActionFacade.HOME -> StoredAction.of("accessibility.home")
        ActionFacade.RECENT -> StoredAction.of("accessibility.recents")
        ActionFacade.VOLUME_UP -> StoredAction.of("system.volume", "stream" to "MUSIC", "direction" to "UP")
        ActionFacade.VOLUME_DOWN -> StoredAction.of("system.volume", "stream" to "MUSIC", "direction" to "DOWN")
        ActionFacade.MUTE -> StoredAction.of("system.media", "command" to "MUTE")
        ActionFacade.PLAY_PAUSE_SONG -> StoredAction.of("system.media", "command" to "PLAY_PAUSE")
        ActionFacade.LAST_SONG -> StoredAction.of("system.media", "command" to "PREVIOUS")
        ActionFacade.NEXT_SONG -> StoredAction.of("system.media", "command" to "NEXT")
        ActionFacade.PREVIOUS_APP -> StoredAction.of("internal.previousApp")
        ActionFacade.OPEN_NOTIFICATION_PANEL -> StoredAction.of("accessibility.notificationPanel")
        ActionFacade.OPEN_QUICK_PANEL -> StoredAction.of("accessibility.quickPanel")
        ActionFacade.LOCK_SCREEN -> StoredAction.of("accessibility.lockScreen")
        ActionFacade.FLASHLIGHT -> StoredAction.of("system.flashlight")
        ActionFacade.SPLIT_SCREEN -> StoredAction.of("accessibility.splitScreen")
        ActionFacade.POPUP_SCREEN -> StoredAction.of("app.popup")
        ActionFacade.ASSIST_APP -> StoredAction.of("intent.assist")
        ActionFacade.SCREENSHOT -> StoredAction.of("accessibility.screenshot")
        ActionFacade.POWER_BUTTON -> StoredAction.of("accessibility.powerButton")
        ActionFacade.HIDE_GESTURE_BUTTON -> StoredAction.of("internal.hideGestureButton")
        ActionFacade.KEEP_SCREEN_ON -> StoredAction.of("internal.keepScreenOn")
        ActionFacade.BACK_TO_TOP -> StoredAction.of("accessibility.swipe", "direction" to "TO_TOP")
        ActionFacade.OPEN_APP_ACTIVITY -> openActivity(action.data)
        ActionFacade.OPEN_URL -> decodeData(action.data)?.let { openUrl(it) }
        ActionFacade.QUICK_APP_LAUNCHER -> StoredAction.of("internal.quickAppLauncher")
        ActionFacade.RANDOM_NAME -> StoredAction.of("system.clipboard", "operation" to "RANDOM_NAME")
        ActionFacade.GENERATE_PASSWORD_COPY -> StoredAction.of("system.clipboard", "operation" to "GENERATE_PASSWORD")
        ActionFacade.CLICK_CURRENT_POSITION -> StoredAction.of("accessibility.tap")
        ActionFacade.VOLUME_SCRUB -> StoredAction.of("internal.volumeScrub")
        ActionFacade.EXECUTE_SHELL_COMMAND -> shellCommand(action.data)
        ActionFacade.SUB_GESTURE -> StoredAction.of("internal.subGesture")
        ActionFacade.EXTRA_LAUNCH_APP -> launchApp(action.data)
        ActionFacade.EXTRA_LAUNCH_SHORTCUT -> StoredAction.of("app.launchShortcut", "data" to action.data)
        else -> null
    }

    private fun openActivity(data: String): StoredAction? {
        val d = decodeData(data) ?: return null
        return when {
            d.activityClassName.isNotBlank() && d.packageName.isNotBlank() -> StoredAction.of(
                "app.openActivity",
                "packageName" to d.packageName,
                "activityClassName" to d.activityClassName,
                "miniWindow" to d.miniWindow.toString(),
            )
            d.packageName.isNotBlank() -> StoredAction.of(
                "app.launch",
                "packageName" to d.packageName,
                "miniWindow" to d.miniWindow.toString(),
            )
            d.url.isNotBlank() -> openUrl(d)
            else -> null
        }
    }

    fun mapUrlData(data: OpenAppOrUrlData): StoredAction? {
        if (data.url.isBlank()) return null
        return StoredAction(
            "intent.openUrl",
            buildJsonObject {
                put("url", data.url)
                if (data.queryParameters.isNotEmpty()) {
                    put("queryParameters", buildJsonArray {
                        data.queryParameters.forEach { qp ->
                            add(buildJsonObject {
                                put("name", qp.name)
                                put("value", qp.value)
                                put("enabled", qp.enabled.toString())
                            })
                        }
                    })
                }
            },
        )
    }

    private fun openUrl(d: OpenAppOrUrlData): StoredAction? = mapUrlData(d)

    private fun launchApp(data: String): StoredAction? {
        val appInfo = runCatching { JsonSerializer.decodeFromString<AppInfo>(data) }.getOrNull() ?: return null
        if (appInfo.packageName.isBlank()) return null
        return StoredAction.of(
            "app.launch",
            "packageName" to appInfo.packageName,
            "miniWindow" to appInfo.miniWindow.toString(),
            "className" to appInfo.className,
        )
    }

    fun mapShellCommand(command: String, showToast: Boolean = false): StoredAction? {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("#")) {
            val typeId = trimmed.substring(1)
            return if (ActionDefinitions.byTypeId(typeId) != null) StoredAction.of(typeId) else null
        }
        return StoredAction.of(
            "shell.custom",
            "command" to trimmed,
            "showToast" to showToast.toString(),
        )
    }

    private fun shellCommand(data: String): StoredAction? {
        val d = runCatching { JsonSerializer.decodeFromString<ShellCommandData>(data) }.getOrNull() ?: return null
        return mapShellCommand(d.command, d.showToast)
    }

    private fun decodeData(data: String): OpenAppOrUrlData? {
        if (data.isBlank()) return null
        return runCatching { JsonSerializer.decodeFromString<OpenAppOrUrlData>(data) }.getOrNull()
    }
}
