package hunoia.luno.action.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import hunoia.luno.core.domain.PARAM_X_DP
import hunoia.luno.core.domain.PARAM_Y_DP
import hunoia.luno.core.domain.USE_TRIGGER_POINT_KEY
import hunoia.luno.core.domain.GRID_PANEL_PARAM_PANEL_ID
import hunoia.luno.core.domain.ACTION_PANEL_TYPE_ID

/**
 * 内置动作的 `typeId → Action` 映射登记。
 *
 * 这是 Action 扩展点收敛后的写法：中心 `ActionMapper` 不再持有一个大 `when`，
 * 每条映射只是注册表里的一个条目。新增动作时在对应功能模块登记映射并注册定义，
 * 不需要修改分发口或映射中心。
 */
internal object BuiltinActionDecoders {

    private var installed = false

    fun ensure() {
        if (installed) return
        installed = true
        install()
    }

    private fun install() {
        ActionMappingRegistry.register("app.launch") { p -> Action.LaunchApp(
            packageName = p.str("packageName"),
            miniWindow = p.bool("miniWindow"),
            className = p.str("className"),
            intentAction = p.str("intentAction"),
            data = p.str("data"),
            extras = p.mapOfStr("extras"),
        )
        }
        ActionMappingRegistry.register("app.details") { p -> Action.AppDetails(p.str("packageName"))
        }
        ActionMappingRegistry.register("app.popup") { p -> Action.Popup
        }
        ActionMappingRegistry.register("app.launchShortcut") { p -> Action.LaunchShortcut(p.str("data"))
        }
        ActionMappingRegistry.register("intent.openUrl") { p -> Action.OpenUrl(
            url = p.str("url"),
            queryParameters = p.queryParams(),
            miniWindow = p.bool("miniWindow"),
        )
        }
        ActionMappingRegistry.register("intent.shareText") { p -> Action.ShareText(p.str("text"), p.str("mimeType", "text/plain"))
        }
        ActionMappingRegistry.register("intent.shareFile") { p -> Action.ShareFile
        }
        ActionMappingRegistry.register("intent.assist") { p -> Action.Assist
        }
        ActionMappingRegistry.register("system.openSettings") { p -> Action.OpenSettings(p.enumValueOr("page", SettingsPage.MAIN))
        }
        ActionMappingRegistry.register("system.volume") { p -> Action.Volume(
            p.enumValueOr("stream", AudioStream.MUSIC),
            p.enumValueOr("direction", VolumeDirection.UP),
        )
        }
        ActionMappingRegistry.register("system.vibrate") { p -> Action.Vibrate(p.str("pattern", "0,500"))
        }
        ActionMappingRegistry.register("system.clipboardRandomName") { p -> Action.ClipboardRandomName
        }
        ActionMappingRegistry.register("system.mediaPlayPause") { p -> Action.MediaPlayPause
        }
        ActionMappingRegistry.register("system.mediaNext") { p -> Action.MediaNext
        }
        ActionMappingRegistry.register("system.mediaPrevious") { p -> Action.MediaPrevious
        }
        ActionMappingRegistry.register("system.mediaMute") { p -> Action.MediaMute
        }
        ActionMappingRegistry.register("system.flashlight") { p -> Action.Flashlight
        }
        ActionMappingRegistry.register("http.request") { p -> Action.HttpRequest(
            url = p.str("url"),
            method = p.str("method", "GET"),
            headers = p.str("headers"),
            body = p.str("body"),
        )
        }
        ActionMappingRegistry.register("system.toast") { p -> Action.Toast(p.str("text"))
        }
        ActionMappingRegistry.register("system.notify") { p -> Action.Notify(
            title = p.str("title", "Luno"),
            text = p.str("text"),
        )
        }
        ActionMappingRegistry.register("system.clipboardSet") { p -> Action.SetClipboard(p.str("text"))
        }
        ActionMappingRegistry.register("accessibility.back") { p -> Action.Back
        }
        ActionMappingRegistry.register("accessibility.home") { p -> Action.Home
        }
        ActionMappingRegistry.register("accessibility.recents") { p -> Action.Recents
        }
        ActionMappingRegistry.register("accessibility.tap") { p -> Action.Tap(
            p.intOrNull(PARAM_X_DP),
            p.intOrNull(PARAM_Y_DP),
            p.bool(USE_TRIGGER_POINT_KEY),
        )
        }
        ActionMappingRegistry.register("accessibility.longPress") { p -> Action.LongPress(
            p.intOrNull(PARAM_X_DP),
            p.intOrNull(PARAM_Y_DP),
            p.long("durationMs", 1000L),
            p.bool(USE_TRIGGER_POINT_KEY),
        )
        }
        ActionMappingRegistry.register("accessibility.doubleTap") { p -> Action.DoubleTap(
            p.intOrNull(PARAM_X_DP),
            p.intOrNull(PARAM_Y_DP),
            p.bool(USE_TRIGGER_POINT_KEY),
        )
        }
        ActionMappingRegistry.register("accessibility.scroll") { p -> Action.ScrollFocused(
            p.str("direction") != "BACKWARD",
        )
        }
        ActionMappingRegistry.register("accessibility.swipe") { p -> Action.Swipe(p.enumValue("direction"))
        }
        ActionMappingRegistry.register("accessibility.screenshot") { p -> Action.Screenshot
        }
        ActionMappingRegistry.register("accessibility.screenPick") { p -> Action.ScreenPick
        }
        ActionMappingRegistry.register("accessibility.screenLinks") { p -> Action.ScreenLinks
        }
        ActionMappingRegistry.register("accessibility.splitScreen") { p -> Action.SplitScreen
        }
        ActionMappingRegistry.register("accessibility.powerButton") { p -> Action.PowerButton
        }
        ActionMappingRegistry.register("accessibility.notificationPanel") { p -> Action.NotificationPanel
        }
        ActionMappingRegistry.register("accessibility.quickPanel") { p -> Action.QuickPanel
        }
        ActionMappingRegistry.register("accessibility.lockScreen") { p -> Action.LockScreen
        }
        ActionMappingRegistry.register("shell.custom") { p -> Action.CustomCommand(
            p.str("command"),
            p.bool("showToast"),
            p.bool("copyOutput"),
        )
        }
        ActionMappingRegistry.register(Action.NONE_TYPE_ID) { p -> Action.None
        }
        ActionMappingRegistry.register("internal.subGesture") { p -> Action.SubGesture
        }
        ActionMappingRegistry.register("internal.buttonsVisibility") { p -> Action.ButtonsVisibility(
            p.bool("visible", false),
            p.enumValueOr("target", ButtonTarget.ALL),
            p.arrayOfStr("buttonIds"),
            p.long("hideForMs", 0),
        )
        }
        ActionMappingRegistry.register("internal.quickAppLauncher") { p -> Action.QuickAppLauncher
        }
        ActionMappingRegistry.register("internal.floatBallVisibility") { p -> Action.FloatBallVisibility(
            when (p.str("visible", "toggle")) {
                "true" -> true
                "false" -> false
                else -> null
            },
            p.long("hideForMs", 0),
        )
        }
        ActionMappingRegistry.register("internal.gridPanel") { p -> Action.GridPanel(p.str(GRID_PANEL_PARAM_PANEL_ID))
        }
        ActionMappingRegistry.register("internal.quickSearch") { p -> Action.QuickSearch
        }
        ActionMappingRegistry.register("internal.volumeScrub") { p -> Action.VolumeScrub(
            // 未写阈值 = null，默认值由实现侧（配置层）决定，映射层不持默认。
            stepThresholdDp = (p["stepThresholdDp"] as? JsonPrimitive)
                ?.contentOrNull?.toIntOrNull(),
        )
        }
        ActionMappingRegistry.register("internal.pointer") { p -> Action.Pointer
        }
        ActionMappingRegistry.register("internal.previousApp") { p -> Action.PreviousApp
        }
        ActionMappingRegistry.register("internal.runRule") { p -> Action.RunRule(
            ruleId = p.str("ruleId"),
            params = p.ruleParams(),
        ) }
        // 动作面板由 SideGestureContainer.runGestureActions 就地展开成面板项，正常路径
        // 不会流到执行层。能走到这里说明面板被删或内容为空 —— 按「无动作」收口，
        // 而不是抛 UnknownActionTypeException 让手势链路报错。
        ActionMappingRegistry.register(ACTION_PANEL_TYPE_ID) { p -> Action.None }
    }
}
