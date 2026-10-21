package hunoia.luno.action.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SettingsInputComposite
import hunoia.luno.action.model.ACTION_PANEL_PARAM_PANEL_ID
import hunoia.luno.action.model.ACTION_PANEL_TYPE_ID
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.Widgets
import hunoia.luno.action.definitions.ActionCategory
import hunoia.luno.action.model.*

object ActionDefinitions {

    val definitions: List<ActionDefinition> = buildList {
        // App
        add(ActionDefinition(
            "app.launch", "启动应用", ActionCategory.APP,
            Capability.None,
            listOf(
                ParameterDefinition.AppSelector("packageName", "应用"),
                ParameterDefinition.Bool("miniWindow", "小窗模式", "false"),
                ParameterDefinition.ActivitySelector("className", "Activity", required = false, packageKey = "packageName"),
            ),
            icon = Icons.Default.Android,
        ))
        add(ActionDefinition(
            "app.details", "应用详情", ActionCategory.APP,
            Capability.None,
            listOf(ParameterDefinition.AppSelector("packageName", "应用")),
            icon = Icons.Default.Info,
        ))
        // 无参定义：动作库自动不显示；小窗尺寸/位置一律用全局小窗设置。
        add(ActionDefinition(
            "app.popup", "小窗当前应用", ActionCategory.APP,
            Capability.None,
            icon = Icons.Default.Window,
        ))
        add(ActionDefinition(
            "app.launchShortcut", "启动快捷方式", ActionCategory.APP,
            Capability.None,
            listOf(ParameterDefinition.Text("data", "快捷方式数据", required = false)),
            icon = Icons.AutoMirrored.Default.Shortcut,
        ))

        // Intent
        add(ActionDefinition(
            "intent.openUrl", "打开链接", ActionCategory.INTENT,
            Capability.None,
            listOf(
                ParameterDefinition.Text("url", "URL"),
            ),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
        ))
        add(ActionDefinition(
            "intent.shareText", "分享文本", ActionCategory.INTENT,
            Capability.None,
            listOf(
                ParameterDefinition.Text("text", "文本"),
                ParameterDefinition.Text("mimeType", "MIME 类型", required = false, defaultValue = "text/plain", allowTemplates = false),
            ),
            icon = Icons.Default.Share,
        ))
        add(ActionDefinition(
            "intent.shareFile", "分享文件", ActionCategory.INTENT,
            Capability.None,
            icon = Icons.Default.Upload,
        ))
        add(ActionDefinition(
            "intent.assist", "系统助手", ActionCategory.INTENT,
            Capability.None,
            icon = Icons.Default.Assistant,
        ))

        // System
        // 参数改在动作选择页行尾设置弹窗里配（openSettings 页面由快速搜索长按共用写入）。
        add(ActionDefinition(
            "system.openSettings", "打开设置页", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.Settings,
        ))
        add(ActionDefinition(
            "system.volume", "音量控制", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.AutoMirrored.Default.VolumeUp,
        ))
        add(ActionDefinition(
            "system.vibrate", "振动", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.Vibration,
        ))
        // 剪贴板/媒体控制拆成无参数动作；密码长度用动作设置里的密码生成配置。
        add(ActionDefinition(
            "system.clipboardRandomName", "剪贴板随机名称", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.ContentCopy,
        ))
        add(ActionDefinition(
            "system.clipboardPassword", "生成密码", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.Password,
        ))
        add(ActionDefinition(
            "system.mediaPlayPause", "播放/暂停", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.PlayPause,
        ))
        add(ActionDefinition(
            "system.mediaNext", "下一首", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.SkipNext,
        ))
        add(ActionDefinition(
            "system.mediaPrevious", "上一首", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.SkipPrevious,
        ))
        add(ActionDefinition(
            "system.mediaMute", "静音", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.VolumeOff,
        ))
        add(ActionDefinition(
            "system.flashlight", "手电筒", ActionCategory.SYSTEM,
            Capability.None,
            icon = Icons.Default.FlashlightOn,
        ))

        // Accessibility
        add(ActionDefinition("accessibility.back", "返回", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.AutoMirrored.Filled.ArrowBack))
        add(ActionDefinition("accessibility.home", "主页", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Home))
        add(ActionDefinition("accessibility.recents", "最近任务", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.ViewCarousel))
        add(ActionDefinition("accessibility.tap", "点击", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.TouchApp))
        add(ActionDefinition("accessibility.longPress", "长按", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Gesture))
        add(ActionDefinition(
            "accessibility.swipe", "滑动", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.VerticalAlignTop,
        ))
        add(ActionDefinition(
            "accessibility.inputText", "输入文本", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            listOf(ParameterDefinition.TextLarge("text", "文本")),
            icon = Icons.Default.TouchApp,
        ))
        add(ActionDefinition("accessibility.screenshot", "截屏", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Screenshot))
        add(ActionDefinition("accessibility.splitScreen", "分屏", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Splitscreen))
        add(ActionDefinition("accessibility.powerButton", "电源菜单", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.PowerSettingsNew))
        add(ActionDefinition("accessibility.notificationPanel", "通知面板", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Notifications))
        add(ActionDefinition("accessibility.quickPanel", "快捷面板", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.Dashboard))
        add(ActionDefinition("accessibility.lockScreen", "锁屏", ActionCategory.ACCESSIBILITY, Capability.Accessibility,
            icon = Icons.Default.ScreenLockPortrait))

        // Settings
        add(ActionDefinition(
            "settings.get", "读取设置", ActionCategory.SETTINGS,
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
            "settings.put", "写入设置", ActionCategory.SETTINGS,
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
        add(ActionDefinition("systemCmd.reboot", "重启", ActionCategory.SYSTEMCMD, Capability.Shizuku,
            icon = Icons.Default.Refresh))
        add(ActionDefinition("systemCmd.shutdown", "关机", ActionCategory.SYSTEMCMD, Capability.Shizuku,
            icon = Icons.Default.PowerOff))

        // Shell
        add(ActionDefinition(
            "shell.custom", "自定义命令", ActionCategory.SHELL,
            Capability.Shizuku,
            listOf(
                ParameterDefinition.TextLarge("command", "Shell 命令"),
                ParameterDefinition.Bool("showToast", "显示结果", "false"),
            ),
            icon = Icons.Default.Terminal,
        ))

        // Internal
        add(ActionDefinition("internal.none", "无", ActionCategory.INTERNAL, Capability.None,
            icon = Icons.Default.Block))
        add(ActionDefinition("internal.subGesture", "子手势", ActionCategory.INTERNAL, Capability.None,
            icon = Icons.Default.Gesture))
        /**
         * 动作面板：触发时弹弧形菜单让用户挑一个。`panelId` 必填——既不出现在
         * `pickerDefinitions`（面板行按全局列表动态合成），也存不出空引用。
         */
        add(ActionDefinition(ACTION_PANEL_TYPE_ID, "动作面板", ActionCategory.INTERNAL, Capability.None,
            parameters = listOf(ParameterDefinition.Text(
                key = ACTION_PANEL_PARAM_PANEL_ID,
                label = "面板",
                required = true,
            )),
            icon = Icons.Default.RadioButtonChecked))
        /**
         * 网格面板：触发时在触点附近弹网格快捷中心。与 [ACTION_PANEL_TYPE_ID] 一样
         * 必须落到具体面板，但不带 panelId 参数：选择页按全局面板列表动态合成一行
         * 一个具名面板（见 `assembleDataTransform`），引用由合成行带进去；无参同时
         * 进不了动作库新建页（`ActionCatalog.libraryDefinitions` 只收有参动作），
         * 不必占 `libraryExcluded`。
         */
        add(ActionDefinition(GRID_PANEL_TYPE_ID, "网格面板", ActionCategory.INTERNAL, Capability.None,
            icon = Icons.Default.GridView))
        add(ActionDefinition("internal.hideGestureButton", "隐藏触钮", ActionCategory.INTERNAL, Capability.None,
            icon = Icons.Default.VisibilityOff))
        // 参数改在动作选择页行尾设置按钮里配（弹窗），不设置落运行时默认值。
        add(ActionDefinition("internal.buttonsVisibility", "触钮显隐", ActionCategory.INTERNAL, Capability.None,
            icon = Icons.Default.Visibility))
        add(ActionDefinition("internal.quickAppLauncher", "快速启动面板", ActionCategory.INTERNAL, Capability.None,
            icon = Icons.Default.Apps))
        add(ActionDefinition("internal.floatBallVisibility", "悬浮球显隐", ActionCategory.INTERNAL, Capability.None,
            icon = Icons.Default.Adjust))
        add(ActionDefinition("internal.quickSearch", "快速搜索", ActionCategory.INTERNAL, Capability.None,
            icon = Icons.Default.Search))
        // 水平调节删掉，恒开；灵敏度在动作选择页行尾设置按钮里配。
        add(ActionDefinition("internal.volumeScrub", "滑动调音量", ActionCategory.INTERNAL, Capability.None,
            icon = Icons.Default.Widgets))
        add(ActionDefinition("internal.pointer", "指针", ActionCategory.INTERNAL, Capability.None,
            icon = Icons.Default.TouchApp))
        // 无参定义：动作库只收有参动作，自动不显示；选择页仍可选。
        // 排除名单改为全局「最近应用黑名单」（动作设置），历史源头过滤。
        add(ActionDefinition("internal.previousApp", "上一个应用", ActionCategory.INTERNAL, Capability.None,
            icon = Icons.Default.SwapHoriz))
        // 规则即动作：只能引用已存规则（库新建/定义表直选都不开放），ruleId 必填。
        add(ActionDefinition("internal.runRule", "运行规则", ActionCategory.INTERNAL, Capability.None,
            parameters = listOf(ParameterDefinition.Text(
                key = "ruleId",
                label = "规则",
            )),
            icon = Icons.Default.PlayCircle))
    }

    private val byTypeId: Map<String, ActionDefinition> = definitions.associateBy { it.typeId }

    fun byTypeId(typeId: String): ActionDefinition? = byTypeId[typeId]

    /** 不可在动作库中新建的类型：占位型，或必须引用具体实体的类型 */
    private val libraryExcluded: Set<String> = setOf(
        "internal.none",
        "internal.subGesture",
        "internal.runRule",
        ACTION_PANEL_TYPE_ID,
        "app.launchShortcut",
        // 应用详情只服务快速搜索长按入口，不在库/选择页开放新建
        "app.details",
        // 网格面板通用空行：panelId 只能由选择页合成的具名行带入，
        // 库/选择页/自动化全禁（无参本就进不了库，这里为 picker/automation 生效）。
        GRID_PANEL_TYPE_ID,
    )

    /** 不可作为自动化规则动作的类型：依赖运行时触点，或在自动化上下文中空跑 */
    val automationExcluded: Set<String> = libraryExcluded + setOf(
        "accessibility.tap",
        "accessibility.longPress",
        "internal.hideGestureButton",
    )

    fun libraryDefinitions(): List<ActionDefinition> = definitions.filterNot { it.typeId in libraryExcluded }

    fun automationExcludedTypeIds(): Set<String> = automationExcluded

    /**
     * 触钮/子手势动作选择页要排除的类型。必须由调用方显式取用，不能依赖
     * [pickerDefinitions] 的默认参数：收到空集合时 `typeId !in emptySet()` 恒真，
     * 整张表都会变成可选，包括「启动快捷方式」和 `internal.subGesture`。
     */
    fun pickerExcludedTypeIds(): Set<String> = libraryExcluded

    fun pickerDefinitions(excluded: Set<String> = libraryExcluded): List<ActionDefinition> =
        definitions.filter { it.typeId !in excluded && it.parameters.none { p -> p.required } }

    private val categoryOrder: Map<ActionCategory, Int> =
        libraryDefinitions().map { it.category }.distinct().withIndex().associate { (i, c) -> c to i }

    fun categoryOrder(category: ActionCategory): Int = categoryOrder[category] ?: Int.MAX_VALUE
}
