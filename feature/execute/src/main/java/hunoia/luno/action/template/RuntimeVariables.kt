package hunoia.luno.action.template

import android.content.Context
import hunoia.luno.core.domain.AudioStream
import hunoia.luno.core.domain.RuntimeSnapshot

/** 编辑器可选的运行时变量，token 为 `${...}` 内的名字。 */
data class TemplateVariable(
    val token: String,
    val label: String,
    val sample: String,
    val triggerOnly: Boolean = false,
)

/**
 * 模板取值：条件匹配用的 RuntimeSnapshot 同时也是模板变量来源，
 * 自动化规则触发时因此能读到 appChangedTo / screenEvent 等边沿值。
 */
object RuntimeVariables {

    const val PKG = "pkg"
    const val PKG_LABEL = "pkg.label"
    const val TIME = "time"
    const val DATE = "date"
    const val HOUR = "hour"
    const val MINUTE = "minute"
    const val TIMESTAMP = "timestamp"
    const val BATTERY = "battery"
    const val CHARGING = "charging"
    const val NETWORK = "network"
    const val SILENT = "silent"
    const val HEADPHONES = "headphones"
    const val BLUETOOTH = "bluetooth"
    const val AIRPLANE = "airplane"
    const val LANDSCAPE = "landscape"
    const val LOCKSCREEN = "lockscreen"
    const val LAUNCHER = "launcher"
    const val KEYBOARD = "keyboard"
    const val TRIGGER_APP = "trigger.app"
    const val TRIGGER_GESTURE_ID = "trigger.gesture.id"
    const val TRIGGER_GESTURE_NAME = "trigger.gesture.name"
    const val TRIGGER_RULE_NAME = "trigger.rule.name"
    const val TRIGGER_SCREEN = "trigger.screen"
    const val TRIGGER_CHARGING = "trigger.charging"
    const val TRIGGER_VOLUME = "trigger.volume"
    const val TRIGGER_NOTIFICATION_PACKAGE = "trigger.notification.package"
    const val TRIGGER_NOTIFICATION_TITLE = "trigger.notification.title"
    const val TRIGGER_NOTIFICATION_TEXT = "trigger.notification.text"
    const val TRIGGER_NOTIFICATION_APP = "trigger.notification.app"
    const val TRIGGER_NOTIFICATION_CHANNEL = "trigger.notification.channel"
    const val TRIGGER_NOTIFICATION_GROUP = "trigger.notification.group"
    const val TRIGGER_NOTIFICATION_CATEGORY = "trigger.notification.category"
    const val TRIGGER_PACKAGE_NAME = "trigger.package"
    const val TRIGGER_PACKAGE_ACTION = "trigger.package.action"
    const val TRIGGER_SHARE_TEXT = "trigger.share.text"
    const val TRIGGER_SHARE_URL = "trigger.share.url"
    const val TRIGGER_CLIPBOARD_TEXT = "trigger.clipboard.text"
    const val WIFI_SSID = "wifi.ssid"
    const val BLUETOOTH_DEVICE_NAME = "bluetooth.device.name"
    const val BLUETOOTH_DEVICE_ADDRESS = "bluetooth.device.address"
    const val CLIPBOARD = "clipboard"

    private const val VOLUME_PREFIX = "volume."

    val all: List<TemplateVariable> = listOf(
        TemplateVariable(PKG, "当前应用包名", "com.android.settings"),
        TemplateVariable(PKG_LABEL, "当前应用名", "设置"),
        TemplateVariable(TIME, "当前时间", "09:41"),
        TemplateVariable(DATE, "当前日期", "2026-10-02"),
        TemplateVariable(HOUR, "当前小时", "09"),
        TemplateVariable(MINUTE, "当前分钟", "41"),
        TemplateVariable(TIMESTAMP, "时间戳（毫秒）", "1767229260000"),
        TemplateVariable(BATTERY, "电量百分比", "76"),
        TemplateVariable(CHARGING, "是否充电", "false"),
        TemplateVariable(NETWORK, "网络类型", "WIFI"),
        TemplateVariable(volumeToken(AudioStream.MUSIC), "音乐音量", "60"),
        TemplateVariable(volumeToken(AudioStream.RING), "铃声音量", "45"),
        TemplateVariable(volumeToken(AudioStream.NOTIFICATION), "通知音量", "45"),
        TemplateVariable(volumeToken(AudioStream.ALARM), "闹钟音量", "70"),
        TemplateVariable(volumeToken(AudioStream.VOICE), "通话音量", "50"),
        TemplateVariable(volumeToken(AudioStream.SYSTEM), "系统音量", "50"),
        TemplateVariable(SILENT, "静音模式", "false"),
        TemplateVariable(HEADPHONES, "耳机是否连接", "false"),
        TemplateVariable(BLUETOOTH, "蓝牙音频是否连接", "false"),
        TemplateVariable(AIRPLANE, "飞行模式", "false"),
        TemplateVariable(LANDSCAPE, "是否横屏", "false"),
        TemplateVariable(LOCKSCREEN, "是否在锁屏", "false"),
        TemplateVariable(LAUNCHER, "是否在桌面", "false"),
        TemplateVariable(KEYBOARD, "键盘是否弹出", "false"),
        TemplateVariable(TRIGGER_APP, "触发时切换到的应用", "com.tencent.mm", triggerOnly = true),
        TemplateVariable(TRIGGER_GESTURE_ID, "触发手势的按钮 id", "1b2c", triggerOnly = true),
        TemplateVariable(TRIGGER_GESTURE_NAME, "触发手势的按钮名", "右缘", triggerOnly = true),
        TemplateVariable(TRIGGER_RULE_NAME, "触发规则名", "到家", triggerOnly = true),
        TemplateVariable(TRIGGER_SCREEN, "触发时的亮灭屏事件", "SCREEN_ON", triggerOnly = true),
        TemplateVariable(TRIGGER_CHARGING, "触发时的充电状态", "true", triggerOnly = true),
        TemplateVariable(TRIGGER_VOLUME, "触发时的音量变化", "music_UP", triggerOnly = true),
        TemplateVariable(TRIGGER_NOTIFICATION_PACKAGE, "触发通知的包名", "com.tencent.mm", triggerOnly = true),
        TemplateVariable(TRIGGER_NOTIFICATION_TITLE, "触发通知的标题", "张三", triggerOnly = true),
        TemplateVariable(TRIGGER_NOTIFICATION_TEXT, "触发通知的正文", "在吗", triggerOnly = true),
        TemplateVariable(TRIGGER_NOTIFICATION_APP, "触发通知的应用名", "微信", triggerOnly = true),
        TemplateVariable(TRIGGER_NOTIFICATION_CHANNEL, "触发通知的 channel", "messages", triggerOnly = true),
        TemplateVariable(TRIGGER_NOTIFICATION_GROUP, "触发通知的 group", "chat", triggerOnly = true),
        TemplateVariable(TRIGGER_NOTIFICATION_CATEGORY, "触发通知的 category", "msg", triggerOnly = true),
        TemplateVariable(TRIGGER_PACKAGE_NAME, "触发包变化的包名", "com.example.app", triggerOnly = true),
        TemplateVariable(TRIGGER_PACKAGE_ACTION, "触发包变化的动作", "added", triggerOnly = true),
        TemplateVariable(TRIGGER_SHARE_TEXT, "触发分享的完整文本", "看看这个 https://…", triggerOnly = true),
        TemplateVariable(TRIGGER_SHARE_URL, "触发分享中的首个链接", "https://…", triggerOnly = true),
        TemplateVariable(TRIGGER_CLIPBOARD_TEXT, "触发剪贴板的新文本", "剪贴内容", triggerOnly = true),
        TemplateVariable(WIFI_SSID, "当前 Wi‑Fi SSID", "Home"),
        TemplateVariable(BLUETOOTH_DEVICE_NAME, "当前蓝牙设备名", "耳机"),
        TemplateVariable(BLUETOOTH_DEVICE_ADDRESS, "当前蓝牙设备地址", "AA:BB:CC:DD:EE:FF"),
        TemplateVariable(CLIPBOARD, "当前剪贴板文本", "剪贴内容"),
    )

    fun volumeToken(stream: AudioStream): String = VOLUME_PREFIX + stream.name.lowercase()

    /** 只计算实际用到的 token；未知 token 跳过，由 ParamTemplates 原样保留。 */
    suspend fun resolve(
        used: Set<String>,
        ctx: RuntimeSnapshot,
        appContext: Context,
    ): Map<String, String> {
        if (used.isEmpty()) return emptyMap()
        val values = LinkedHashMap<String, String>(used.size)
        val context = VariableContext(ctx, appContext)
        for (token in used) {
            val value = VariableFunctionRegistry.resolve(token, context) ?: continue
            values[token] = value
        }
        return values
    }

}
