package hunoia.luno.action.template

import android.content.Context
import hunoia.luno.action.model.AudioStream
import hunoia.luno.config.model.RingerMode
import hunoia.luno.runtime.condition.ConditionContext
import hunoia.luno.runtime.condition.formatTimeOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** 编辑器可选的运行时变量，token 为 `${...}` 内的名字。 */
data class TemplateVariable(
    val token: String,
    val label: String,
    val sample: String,
    val triggerOnly: Boolean = false,
)

/**
 * 模板取值：条件匹配用的 ConditionContext 同时也是模板变量来源，
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
        TemplateVariable(CLIPBOARD, "当前剪贴板文本", "剪贴内容"),
    )

    fun volumeToken(stream: AudioStream): String = VOLUME_PREFIX + stream.name.lowercase()

    /** 只计算实际用到的 token；未知 token 跳过，由 ParamTemplates 原样保留。 */
    suspend fun resolve(
        used: Set<String>,
        ctx: ConditionContext,
        appContext: Context,
    ): Map<String, String> {
        if (used.isEmpty()) return emptyMap()
        val values = LinkedHashMap<String, String>(used.size)
        for (token in used) {
            val value = when (token) {
                PKG -> ctx.packageName
                PKG_LABEL -> appLabel(appContext, ctx.packageName) ?: ctx.packageName
                TIME -> formatTimeOfDay(ctx.minuteOfDay)
                DATE -> today()
                HOUR -> "%02d".format(ctx.minuteOfDay / 60)
                MINUTE -> "%02d".format(ctx.minuteOfDay % 60)
                TIMESTAMP -> System.currentTimeMillis().toString()
                BATTERY -> ctx.batteryLevel.takeIf { it >= 0 }?.toString().orEmpty()
                CHARGING -> ctx.isCharging.toString()
                NETWORK -> ctx.networkType.name
                SILENT -> (ctx.ringerMode == RingerMode.SILENT).toString()
                HEADPHONES -> ctx.headphonesConnected.toString()
                BLUETOOTH -> (ctx.bluetoothAudioConnected ?: ctx.bluetoothAdapterOn).toString()
                AIRPLANE -> ctx.airplaneMode.toString()
                LANDSCAPE -> ctx.isLandscape.toString()
                LOCKSCREEN -> ctx.isLockScreen.toString()
                LAUNCHER -> ctx.isLauncher.toString()
                KEYBOARD -> ctx.isKeyboardInput.toString()
                TRIGGER_APP -> ctx.appChangedTo.orEmpty()
                TRIGGER_GESTURE_ID -> ctx.gesturePerformedId.orEmpty()
                TRIGGER_GESTURE_NAME -> (ctx.gesturePerformedName ?: ctx.gesturePerformedId).orEmpty()
                TRIGGER_RULE_NAME -> ctx.triggerRuleName.orEmpty()
                TRIGGER_SCREEN -> ctx.screenEvent?.name.orEmpty()
                TRIGGER_CHARGING -> ctx.chargingChangedTo?.toString().orEmpty()
                TRIGGER_VOLUME -> ctx.volumeChanged?.let { "${it.stream.name}_${it.direction.name}" }.orEmpty()
                TRIGGER_NOTIFICATION_PACKAGE -> ctx.notificationPosted?.packageName.orEmpty()
                TRIGGER_NOTIFICATION_TITLE -> ctx.notificationPosted?.title.orEmpty()
                TRIGGER_NOTIFICATION_TEXT -> ctx.notificationPosted?.text.orEmpty()
                TRIGGER_NOTIFICATION_APP -> ctx.notificationPosted?.packageName
                    ?.let { appLabel(appContext, it) }
                    .orEmpty()
                CLIPBOARD -> readClipboard(appContext)
                else -> {
                    if (!token.startsWith(VOLUME_PREFIX)) continue
                    audioStream(token)?.let { ctx.volumePercent[it]?.toString().orEmpty() } ?: continue
                }
            }
            values[token] = value
        }
        return values
    }

    private fun audioStream(token: String): AudioStream? {
        val name = token.removePrefix(VOLUME_PREFIX).uppercase()
        return AudioStream.entries.firstOrNull { it.name == name }
    }

    /**
     * 剪贴板文本：后台无焦点时系统返回 null（Android 10+ 限制），此时给空串。
     * resolve 本来就是 suspend，可在 IO 线程读，失败一律吞掉给空串。
     */
    private fun readClipboard(appContext: Context): String {
        return runCatching {
            val cm = appContext.getSystemService(android.content.ClipboardManager::class.java)
                ?: return ""
            if (!cm.hasPrimaryClip()) return ""
            cm.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString().orEmpty()
        }.getOrDefault("")
    }

    private fun today(): String {
        val calendar = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    private suspend fun appLabel(appContext: Context, packageName: String): String? {
        if (packageName.isBlank()) return null
        lastLabel?.let { (cachedPackage, cachedLabel) ->
            if (cachedPackage == packageName) return cachedLabel
        }
        val label = withContext(Dispatchers.IO) {
            runCatching {
                val pm = appContext.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()?.takeIf { it.isNotBlank() }
        } ?: return null
        lastLabel = packageName to label
        return label
    }

    /** 手势每次触发都可能用到，靠一条记忆避免反复走 PackageManager。 */
    @Volatile
    private var lastLabel: Pair<String, String>? = null
}
