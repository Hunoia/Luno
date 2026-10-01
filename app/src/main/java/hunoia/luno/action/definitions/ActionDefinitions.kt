package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import hunoia.luno.action.api.ActionIds
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SettingsInputComposite
import hunoia.luno.action.definition.PlayPause
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.Widgets
import hunoia.luno.action.definition.ActionCategory
import hunoia.luno.action.model.*

object ActionDefinitions {

    val definitions: List<ActionDefinition> = buildList {
        // App
        add(ActionDefinition(
            Action.LaunchApp::class, "app.launch", "启动应用", ActionCategory.APP,
            Capability.None,
            listOf(
                ParameterDefinition.AppSelector("packageName", "应用"),
                ParameterDefinition.Bool("miniWindow", "小窗模式", "false"),
                ParameterDefinition.ActivitySelector("className", "Activity", required = false, packageKey = "packageName"),
                ParameterDefinition.MiniWindow("miniWindowSettings", "小窗"),
            ),
            icon = Icons.Default.Android,
        ))
        add(ActionDefinition(
            Action.OpenActivity::class, "app.openActivity", "打开 Activity", ActionCategory.APP,
            Capability.None,
            listOf(
                ParameterDefinition.AppSelector("packageName", "应用"),
                ParameterDefinition.ActivitySelector("activityClassName", "Activity"),
                ParameterDefinition.Bool("miniWindow", "小窗模式", "false"),
                ParameterDefinition.MiniWindow("miniWindowSettings", "小窗"),
            ),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
        ))
        add(ActionDefinition(
            Action.AppDetails::class, "app.details", "应用详情", ActionCategory.APP,
            Capability.None,
            listOf(ParameterDefinition.AppSelector("packageName", "应用")),
            icon = Icons.Default.Info,
        ))
        add(ActionDefinition(
            Action.Popup::class, "app.popup", "弹出当前应用", ActionCategory.APP,
            Capability.None,
            parameters = listOf(ParameterDefinition.MiniWindow("miniWindowSettings", "小窗")),
            icon = Icons.Default.Window,
        ))
        add(ActionDefinition(
            Action.LaunchShortcut::class, "app.launchShortcut", "启动快捷方式", ActionCategory.APP,
            Capability.None,
            listOf(ParameterDefinition.Text("data", "快捷方式数据", required = false)),
            icon = Icons.AutoMirrored.Default.Shortcut,
        ))

        // Intent
        add(ActionDefinition(
            Action.OpenUrl::class, "intent.openUrl", "打开链接", ActionCategory.INTENT,
            Capability.None,
            listOf(
                ParameterDefinition.Text("url", "URL"),
                ParameterDefinition.MiniWindow("miniWindowSettings", "小窗"),
            ),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
        ))
        add(ActionDefinition(
            Action.ShareText::class, "intent.shareText", "分享文本", ActionCategory.INTENT,
            Capability.None,
            listOf(
                ParameterDefinition.Text("text", "文本"),
                ParameterDefinition.Text("mimeType", "MIME 类型", required = false, defaultValue = "text/plain"),
            ),
            icon = Icons.Default.Share,
        ))
        add(ActionDefinition(
            Action.ShareFile::class, "intent.shareFile", "分享文件", ActionCategory.INTENT,
            Capability.None,
            listOf(
                ParameterDefinition.Path("filePath", "文件路径"),
                ParameterDefinition.Text("mimeType", "MIME 类型", required = false),
            ),
            icon = Icons.Default.Upload,
        ))
        add(ActionDefinition(
            Action.OpenFile::class, "intent.openFile", "打开文件", ActionCategory.INTENT,
            Capability.None,
            listOf(
                ParameterDefinition.Path("filePath", "文件路径"),
                ParameterDefinition.Text("mimeType", "MIME 类型", required = false),
            ),
            icon = Icons.Default.Description,
        ))
        add(ActionDefinition(
            Action.Assist::class, "intent.assist", "系统助手", ActionCategory.INTENT,
            Capability.None,
            icon = Icons.Default.Assistant,
        ))

        // System
        add(ActionDefinition(
            Action.OpenSettings::class, "system.openSettings", "打开设置页", ActionCategory.SYSTEM,
            Capability.None,
            listOf(ParameterDefinition.Enum("page", "设置页", listOf(
                EnumOption("WIFI", "Wi-Fi"),
                EnumOption("BLUETOOTH", "蓝牙"),
                EnumOption("DISPLAY", "显示"),
                EnumOption("BATTERY", "电池"),
                EnumOption("APPS", "应用"),
                EnumOption("NETWORK", "网络"),
                EnumOption("ACCESSIBILITY", "无障碍"),
                EnumOption("MAIN", "主设置"),
            ), defaultValue = "MAIN")),
            icon = Icons.Default.Settings,
        ))
        add(ActionDefinition(
            Action.Volume::class, "system.volume", "音量控制", ActionCategory.SYSTEM,
            Capability.None,
            listOf(
                ParameterDefinition.Enum("stream", "音频流", listOf(
                    EnumOption("MUSIC", "媒体"),
                    EnumOption("RING", "铃声"),
                    EnumOption("NOTIFICATION", "通知"),
                    EnumOption("ALARM", "闹钟"),
                    EnumOption("VOICE", "通话"),
                    EnumOption("SYSTEM", "系统"),
                ), defaultValue = "MUSIC"),
                ParameterDefinition.Enum("direction", "方向", listOf(
                    EnumOption("UP", "增大"),
                    EnumOption("DOWN", "减小"),
                ), defaultValue = "UP"),
            ),
            icon = Icons.AutoMirrored.Default.VolumeUp,
        ))
        add(ActionDefinition(
            Action.Vibrate::class, "system.vibrate", "振动", ActionCategory.SYSTEM,
            Capability.None,
            listOf(ParameterDefinition.Text("pattern", "振动模式", required = false, defaultValue = "0,500")),
            icon = Icons.Default.Vibration,
        ))
        add(ActionDefinition(
            Action.Clipboard::class, "system.clipboard", "剪贴板", ActionCategory.SYSTEM,
            Capability.None,
            listOf(
                ParameterDefinition.Enum("operation", "操作", listOf(
                    EnumOption("RANDOM_NAME", "随机名称"),
                    EnumOption("GENERATE_PASSWORD", "生成密码"),
                ), defaultValue = "RANDOM_NAME"),
                ParameterDefinition.Number("length", "长度", required = false, min = 4, max = 64),
            ),
            icon = Icons.Default.ContentCopy,
        ))
        add(ActionDefinition(
            Action.Media::class, "system.media", "媒体控制", ActionCategory.SYSTEM,
            Capability.None,
            listOf(ParameterDefinition.Enum("command", "命令", listOf(
                EnumOption("PLAY_PAUSE", "播放/暂停"),
                EnumOption("NEXT", "下一首"),
                EnumOption("PREVIOUS", "上一首"),
                EnumOption("MUTE", "静音"),
            ), defaultValue = "PLAY_PAUSE")),
            icon = Icons.Default.PlayPause,
        ))
        add(ActionDefinition(
            Action.Flashlight::class, "system.flashlight", "手电筒", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.FlashlightOn,
        ))

        // Accessibility
        add(ActionDefinition(Action.Back::class, "accessibility.back", "返回", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.AutoMirrored.Filled.ArrowBack))
        add(ActionDefinition(Action.Home::class, "accessibility.home", "主页", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Home))
        add(ActionDefinition(Action.Recents::class, "accessibility.recents", "最近任务", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.ViewCarousel))
        add(ActionDefinition(Action.Tap::class, "accessibility.tap", "点击", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.TouchApp))
        add(ActionDefinition(Action.LongPress::class, "accessibility.longPress", "长按", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Gesture))
        add(ActionDefinition(
            Action.Swipe::class, "accessibility.swipe", "滑动", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            listOf(ParameterDefinition.Enum("direction", "方向", listOf(
                EnumOption("UP", "向上"),
                EnumOption("DOWN", "向下"),
                EnumOption("LEFT", "向左"),
                EnumOption("RIGHT", "向右"),
                EnumOption("TO_TOP", "到顶部"),
            ), defaultValue = "UP")),
            icon = Icons.Default.VerticalAlignTop,
        ))
        add(ActionDefinition(
            Action.InputText::class, "accessibility.inputText", "输入文本", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            listOf(ParameterDefinition.TextLarge("text", "文本")),
            icon = Icons.Default.TouchApp,
        ))
        add(ActionDefinition(Action.Screenshot::class, "accessibility.screenshot", "截屏", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Screenshot))
        add(ActionDefinition(Action.SplitScreen::class, "accessibility.splitScreen", "分屏", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Splitscreen))
        add(ActionDefinition(Action.PowerButton::class, "accessibility.powerButton", "电源菜单", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.PowerSettingsNew))
        add(ActionDefinition(Action.NotificationPanel::class, "accessibility.notificationPanel", "通知面板", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Notifications))
        add(ActionDefinition(Action.QuickPanel::class, "accessibility.quickPanel", "快捷面板", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Dashboard))
        add(ActionDefinition(Action.LockScreen::class, "accessibility.lockScreen", "锁屏", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.ScreenLockPortrait))

        // Package
        add(ActionDefinition(
            Action.PackageEnable::class, "package.enable", "启用应用", ActionCategory.PACKAGE,
            Capability.Shizuku, listOf(ParameterDefinition.AppSelector("packageName", "应用")),
            icon = Icons.Default.PlayCircle,
        ))
        add(ActionDefinition(
            Action.PackageDisable::class, "package.disable", "禁用应用", ActionCategory.PACKAGE,
            Capability.Shizuku, listOf(ParameterDefinition.AppSelector("packageName", "应用")),
            icon = Icons.Default.PauseCircle,
        ))
        add(ActionDefinition(
            Action.PackageForceStop::class, "package.forceStop", "强制停止", ActionCategory.PACKAGE,
            Capability.Shizuku, listOf(ParameterDefinition.AppSelector("packageName", "应用")),
            icon = Icons.Default.StopCircle,
        ))
        add(ActionDefinition(
            Action.PackageClearData::class, "package.clearData", "清除数据", ActionCategory.PACKAGE,
            Capability.Shizuku, listOf(ParameterDefinition.AppSelector("packageName", "应用")),
            icon = Icons.Default.Delete,
        ))
        add(ActionDefinition(
            Action.PackageUninstall::class, "package.uninstall", "卸载应用", ActionCategory.PACKAGE,
            Capability.Shizuku, listOf(ParameterDefinition.AppSelector("packageName", "应用")),
            icon = Icons.Default.DeleteForever,
        ))

        // Settings
        add(ActionDefinition(
            Action.SettingsGet::class, "settings.get", "读取设置", ActionCategory.SETTINGS,
            Capability.Shizuku,
            listOf(
                ParameterDefinition.Enum("namespace", "命名空间", listOf(
                    EnumOption("SYSTEM", "System"),
                    EnumOption("GLOBAL", "Global"),
                    EnumOption("SECURE", "Secure"),
                ), defaultValue = "SYSTEM"),
                ParameterDefinition.Text("key", "Key"),
            ),
            icon = Icons.Default.SettingsInputComponent,
        ))
        add(ActionDefinition(
            Action.SettingsPut::class, "settings.put", "写入设置", ActionCategory.SETTINGS,
            Capability.Shizuku,
            listOf(
                ParameterDefinition.Enum("namespace", "命名空间", listOf(
                    EnumOption("SYSTEM", "System"),
                    EnumOption("GLOBAL", "Global"),
                    EnumOption("SECURE", "Secure"),
                ), defaultValue = "SYSTEM"),
                ParameterDefinition.Text("key", "Key"),
                ParameterDefinition.Text("value", "Value"),
            ),
            icon = Icons.Default.SettingsInputComposite,
        ))

        // SystemCmd
        add(ActionDefinition(Action.Reboot::class, "systemCmd.reboot", "重启", ActionCategory.SYSTEMCMD, Capability.Shizuku,
            icon = Icons.Default.Refresh))
        add(ActionDefinition(Action.Shutdown::class, "systemCmd.shutdown", "关机", ActionCategory.SYSTEMCMD, Capability.Shizuku,
            icon = Icons.Default.PowerOff))

        // Shell
        add(ActionDefinition(
            Action.CustomCommand::class, "shell.custom", "自定义命令", ActionCategory.SHELL,
            Capability.Shizuku,
            listOf(
                ParameterDefinition.TextLarge("command", "Shell 命令"),
                ParameterDefinition.Bool("showToast", "显示结果", "false"),
            ),
            icon = Icons.Default.Terminal,
        ))

        // Internal
        add(ActionDefinition(Action.None::class, "internal.none", "无", ActionCategory.INTERNAL, Capability.None,
            legacyId = ActionIds.NONE,
            icon = Icons.Default.Block))
        add(ActionDefinition(Action.SubGesture::class, "internal.subGesture", "子手势", ActionCategory.INTERNAL, Capability.None,
            legacyId = ActionIds.SUB_GESTURE,
            icon = Icons.Default.Gesture))
        add(ActionDefinition(Action.HideGestureButton::class, "internal.hideGestureButton", "隐藏触钮", ActionCategory.INTERNAL, Capability.None,
            parameters = listOf(ParameterDefinition.Number(
                key = "delayMs",
                label = "隐藏触钮时长",
                required = false,
                defaultValue = "1000",
                min = 0,
                max = 3000,
            )),
            legacyId = ActionIds.HIDE_GESTURE_BUTTON,
            icon = Icons.Default.VisibilityOff))
        add(ActionDefinition(Action.QuickAppLauncher::class, "internal.quickAppLauncher", "快速启动面板", ActionCategory.INTERNAL, Capability.None,
            legacyId = ActionIds.QUICK_APP_LAUNCHER,
            icon = Icons.Default.Apps))
        add(ActionDefinition(Action.VolumeScrub::class, "internal.volumeScrub", "滑动调音量", ActionCategory.INTERNAL, Capability.None,
            parameters = listOf(
                ParameterDefinition.Bool(
                    key = "horizontalEnabled",
                    label = "水平音量调节",
                    defaultValue = "true",
                ),
                ParameterDefinition.Number(
                    key = "stepThresholdDp",
                    label = "灵敏度",
                    required = false,
                    defaultValue = "14",
                    min = 4,
                    max = 64,
                ),
            ),
            legacyId = ActionIds.VOLUME_SCRUB,
            icon = Icons.Default.Widgets))
        add(ActionDefinition(Action.PreviousApp::class, "internal.previousApp", "上一个应用", ActionCategory.INTERNAL, Capability.None,
            parameters = listOf(ParameterDefinition.AppSelectorMulti(
                key = "excludePackageNames",
                label = "上个应用排除",
            )),
            legacyId = ActionIds.PREVIOUS_APP,
            icon = Icons.Default.SwapHoriz))
    }

    private val byTypeId: Map<String, ActionDefinition> = definitions.associateBy { it.typeId }

    fun byTypeId(typeId: String): ActionDefinition? = byTypeId[typeId]

    /** 不可在动作库中新建的类型：占位型，或必须引用具体实体的类型 */
    private val libraryExcluded: Set<String> = setOf(
        "internal.none",
        "internal.subGesture",
        "app.launchShortcut",
    )

    private val pickerExcluded: Set<String> = libraryExcluded + setOf(
        "app.launch",
        "accessibility.tap",
        "accessibility.longPress",
    )

    fun libraryDefinitions(): List<ActionDefinition> = definitions.filterNot { it.typeId in libraryExcluded }

    fun pickerDefinitions(): List<ActionDefinition> =
        definitions.filter { it.typeId !in pickerExcluded && it.parameters.none { p -> p.required } }

    private val categoryOrder: Map<ActionCategory, Int> =
        libraryDefinitions().map { it.category }.distinct().withIndex().associate { (i, c) -> c to i }

    fun categoryOrder(category: ActionCategory): Int = categoryOrder[category] ?: Int.MAX_VALUE
}
