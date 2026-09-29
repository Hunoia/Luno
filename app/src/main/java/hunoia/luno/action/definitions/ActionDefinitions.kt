package hunoia.luno.action.definitions

import hunoia.luno.action.model.*

object ActionDefinitions {

    val definitions: List<ActionDefinition> = buildList {
        // App
        add(ActionDefinition(
            Action.LaunchApp::class, "app.launch", "启动应用", "App",
            Capability.None,
            listOf(
                ParameterDefinition.AppSelector("packageName", "应用"),
                ParameterDefinition.Bool("miniWindow", "小窗模式", "false"),
                ParameterDefinition.Text("className", "Activity 类名", required = false),
            ),
        ))
        add(ActionDefinition(
            Action.OpenActivity::class, "app.openActivity", "打开 Activity", "App",
            Capability.None,
            listOf(
                ParameterDefinition.AppSelector("packageName", "应用"),
                ParameterDefinition.ActivitySelector("activityClassName", "Activity"),
                ParameterDefinition.Bool("miniWindow", "小窗模式", "false"),
            ),
        ))
        add(ActionDefinition(
            Action.AppDetails::class, "app.details", "应用详情", "App",
            Capability.None,
            listOf(ParameterDefinition.AppSelector("packageName", "应用")),
        ))
        add(ActionDefinition(
            Action.Popup::class, "app.popup", "弹出当前应用", "App",
            Capability.None,
        ))
        add(ActionDefinition(
            Action.LaunchShortcut::class, "app.launchShortcut", "启动快捷方式", "App",
            Capability.None,
            listOf(ParameterDefinition.Text("data", "快捷方式数据", required = false)),
            isInternal = true,
        ))

        // Intent
        add(ActionDefinition(
            Action.OpenUrl::class, "intent.openUrl", "打开链接", "Intent",
            Capability.None,
            listOf(ParameterDefinition.Text("url", "URL")),
        ))
        add(ActionDefinition(
            Action.ShareText::class, "intent.shareText", "分享文本", "Intent",
            Capability.None,
            listOf(
                ParameterDefinition.Text("text", "文本"),
                ParameterDefinition.Text("mimeType", "MIME 类型", required = false, defaultValue = "text/plain"),
            ),
        ))
        add(ActionDefinition(
            Action.ShareFile::class, "intent.shareFile", "分享文件", "Intent",
            Capability.None,
            listOf(
                ParameterDefinition.Path("filePath", "文件路径"),
                ParameterDefinition.Text("mimeType", "MIME 类型", required = false),
            ),
        ))
        add(ActionDefinition(
            Action.OpenFile::class, "intent.openFile", "打开文件", "Intent",
            Capability.None,
            listOf(
                ParameterDefinition.Path("filePath", "文件路径"),
                ParameterDefinition.Text("mimeType", "MIME 类型", required = false),
            ),
        ))
        add(ActionDefinition(
            Action.Assist::class, "intent.assist", "系统助手", "Intent",
            Capability.None,
        ))

        // System
        add(ActionDefinition(
            Action.OpenSettings::class, "system.openSettings", "打开设置页", "System",
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
        ))
        add(ActionDefinition(
            Action.Volume::class, "system.volume", "音量控制", "System",
            Capability.None,
            listOf(
                ParameterDefinition.Enum("stream", "音频流", listOf(
                    EnumOption("MUSIC", "媒体"),
                    EnumOption("RING", "铃声"),
                    EnumOption("NOTIFICATION", "通知"),
                    EnumOption("ALARM", "闹钟"),
                ), defaultValue = "MUSIC"),
                ParameterDefinition.Enum("direction", "方向", listOf(
                    EnumOption("UP", "增大"),
                    EnumOption("DOWN", "减小"),
                ), defaultValue = "UP"),
            ),
        ))
        add(ActionDefinition(
            Action.Vibrate::class, "system.vibrate", "振动", "System",
            Capability.None,
            listOf(ParameterDefinition.Text("pattern", "振动模式", required = false, defaultValue = "0,500")),
        ))
        add(ActionDefinition(
            Action.Clipboard::class, "system.clipboard", "剪贴板", "System",
            Capability.None,
            listOf(
                ParameterDefinition.Enum("operation", "操作", listOf(
                    EnumOption("RANDOM_NAME", "随机名称"),
                    EnumOption("GENERATE_PASSWORD", "生成密码"),
                ), defaultValue = "RANDOM_NAME"),
                ParameterDefinition.Number("length", "长度", required = false, min = 4, max = 64),
            ),
        ))
        add(ActionDefinition(
            Action.Media::class, "system.media", "媒体控制", "System",
            Capability.None,
            listOf(ParameterDefinition.Enum("command", "命令", listOf(
                EnumOption("PLAY_PAUSE", "播放/暂停"),
                EnumOption("NEXT", "下一首"),
                EnumOption("PREVIOUS", "上一首"),
                EnumOption("MUTE", "静音"),
            ), defaultValue = "PLAY_PAUSE")),
        ))
        add(ActionDefinition(
            Action.Flashlight::class, "system.flashlight", "手电筒", "System",
            Capability.None,
        ))

        // Accessibility
        add(ActionDefinition(Action.Back::class, "accessibility.back", "返回", "Accessibility", Capability.Accessibility))
        add(ActionDefinition(Action.Home::class, "accessibility.home", "主页", "Accessibility", Capability.Accessibility))
        add(ActionDefinition(Action.Recents::class, "accessibility.recents", "最近任务", "Accessibility", Capability.Accessibility))
        add(ActionDefinition(Action.Tap::class, "accessibility.tap", "点击", "Accessibility", Capability.Accessibility))
        add(ActionDefinition(Action.LongPress::class, "accessibility.longPress", "长按", "Accessibility", Capability.Accessibility))
        add(ActionDefinition(
            Action.Swipe::class, "accessibility.swipe", "滑动", "Accessibility", Capability.Accessibility,
            listOf(ParameterDefinition.Enum("direction", "方向", listOf(
                EnumOption("UP", "向上"),
                EnumOption("DOWN", "向下"),
                EnumOption("LEFT", "向左"),
                EnumOption("RIGHT", "向右"),
                EnumOption("TO_TOP", "到顶部"),
            ), defaultValue = "UP")),
        ))
        add(ActionDefinition(
            Action.InputText::class, "accessibility.inputText", "输入文本", "Accessibility", Capability.Accessibility,
            listOf(ParameterDefinition.TextLarge("text", "文本")),
        ))
        add(ActionDefinition(Action.Screenshot::class, "accessibility.screenshot", "截屏", "Accessibility", Capability.Accessibility))
        add(ActionDefinition(Action.SplitScreen::class, "accessibility.splitScreen", "分屏", "Accessibility", Capability.Accessibility))
        add(ActionDefinition(Action.PowerButton::class, "accessibility.powerButton", "电源菜单", "Accessibility", Capability.Accessibility))
        add(ActionDefinition(Action.NotificationPanel::class, "accessibility.notificationPanel", "通知面板", "Accessibility", Capability.Accessibility))
        add(ActionDefinition(Action.QuickPanel::class, "accessibility.quickPanel", "快捷面板", "Accessibility", Capability.Accessibility))
        add(ActionDefinition(Action.LockScreen::class, "accessibility.lockScreen", "锁屏", "Accessibility", Capability.Accessibility))

        // Package
        add(ActionDefinition(
            Action.PackageEnable::class, "package.enable", "启用应用", "Package",
            Capability.Shizuku, listOf(ParameterDefinition.AppSelector("packageName", "应用")),
        ))
        add(ActionDefinition(
            Action.PackageDisable::class, "package.disable", "禁用应用", "Package",
            Capability.Shizuku, listOf(ParameterDefinition.AppSelector("packageName", "应用")),
        ))
        add(ActionDefinition(
            Action.PackageForceStop::class, "package.forceStop", "强制停止", "Package",
            Capability.Shizuku, listOf(ParameterDefinition.AppSelector("packageName", "应用")),
        ))
        add(ActionDefinition(
            Action.PackageClearData::class, "package.clearData", "清除数据", "Package",
            Capability.Shizuku, listOf(ParameterDefinition.AppSelector("packageName", "应用")),
        ))
        add(ActionDefinition(
            Action.PackageUninstall::class, "package.uninstall", "卸载应用", "Package",
            Capability.Shizuku, listOf(ParameterDefinition.AppSelector("packageName", "应用")),
        ))

        // Settings
        add(ActionDefinition(
            Action.SettingsGet::class, "settings.get", "读取设置", "Settings",
            Capability.Shizuku,
            listOf(
                ParameterDefinition.Enum("namespace", "命名空间", listOf(
                    EnumOption("SYSTEM", "System"),
                    EnumOption("GLOBAL", "Global"),
                    EnumOption("SECURE", "Secure"),
                ), defaultValue = "SYSTEM"),
                ParameterDefinition.Text("key", "Key"),
            ),
        ))
        add(ActionDefinition(
            Action.SettingsPut::class, "settings.put", "写入设置", "Settings",
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
        ))

        // SystemCmd
        add(ActionDefinition(Action.Reboot::class, "systemCmd.reboot", "重启", "SystemCmd", Capability.Shizuku))
        add(ActionDefinition(Action.Shutdown::class, "systemCmd.shutdown", "关机", "SystemCmd", Capability.Shizuku))

        // Shell
        add(ActionDefinition(
            Action.CustomCommand::class, "shell.custom", "自定义命令", "Shell",
            Capability.Shizuku,
            listOf(
                ParameterDefinition.TextLarge("command", "Shell 命令"),
                ParameterDefinition.Bool("showToast", "显示结果", "false"),
            ),
        ))

        // Internal
        add(ActionDefinition(Action.None::class, "internal.none", "无", "Internal", Capability.None, isInternal = true))
        add(ActionDefinition(Action.SubGesture::class, "internal.subGesture", "子手势", "Internal", Capability.None, isInternal = true))
        add(ActionDefinition(Action.HideGestureButton::class, "internal.hideGestureButton", "隐藏触钮", "Internal", Capability.None, isInternal = true))
        add(ActionDefinition(Action.QuickAppLauncher::class, "internal.quickAppLauncher", "快速启动面板", "Internal", Capability.None, isInternal = true))
        add(ActionDefinition(Action.VolumeScrub::class, "internal.volumeScrub", "滑动调音量", "Internal", Capability.None, isInternal = true))
        add(ActionDefinition(Action.KeepScreenOn::class, "internal.keepScreenOn", "屏幕常亮", "Internal", Capability.None, isInternal = true))
        add(ActionDefinition(Action.PreviousApp::class, "internal.previousApp", "上一个应用", "Internal", Capability.None, isInternal = true))
    }

    private val byTypeId: Map<String, ActionDefinition> = definitions.associateBy { it.typeId }

    fun byTypeId(typeId: String): ActionDefinition? = byTypeId[typeId]

    fun userDefinitions(): List<ActionDefinition> = definitions.filterNot { it.isInternal }
    fun internalDefinitions(): List<ActionDefinition> = definitions.filter { it.isInternal }
    fun byCategory(): Map<String, List<ActionDefinition>> = userDefinitions().groupBy { it.category }
}
