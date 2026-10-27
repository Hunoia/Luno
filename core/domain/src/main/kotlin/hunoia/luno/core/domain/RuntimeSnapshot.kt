package hunoia.luno.core.domain

/**
 * 一次求值/执行所看到的不可变运行态快照。
 *
 * 生产者：`:core:runtime` 的 SnapshotBuilder（tick / 事件刷新时构造一次）。
 * 消费者：execute（模板变量）、conditions（求值）、automation（求值）、gesture（判定）。
 *
 * 禁止消费者自己再读系统状态拼快照；需要新字段时加在这里并由 builder 统一提供。
 * 本类不得引用 Android 类型：`NotificationPulse` 的 `PendingIntent` 由 runtime 侧
 * 按通知 `key` 另存（见 NotificationContentIntents）。
 */
data class RuntimeSnapshot(
    val nowMs: Long,
    val packageName: String,
    val isLockScreen: Boolean,
    val isLauncher: Boolean,
    val isLandscape: Boolean,
    val isKeyboardInput: Boolean,
    val isCharging: Boolean,
    val batteryLevel: Int,
    val minuteOfDay: Int,
    val dayOfWeek: Int,

    val networkType: NetworkType,
    /** 当前 Wi‑Fi SSID；读不到/未连接时为 null。 */
    val wifiSsid: String? = null,
    val headphonesConnected: Boolean,
    val bluetoothAdapterOn: Boolean?,
    val bluetoothAudioConnected: Boolean?,
    val bluetoothDeviceName: String? = null,
    val bluetoothDeviceAddress: String? = null,
    val airplaneMode: Boolean,
    val ringerMode: RingerMode,
    val volumePercent: Map<AudioStream, Int>,
    val isMusicPlaying: Boolean,
    val isTorchOn: Boolean,
    val isDndOn: Boolean,

    val appChangedTo: String?,
    val screenEvent: ScreenEventType?,
    val chargingChangedTo: Boolean?,
    val volumeChanged: VolumeChange?,

    // v2 新增脉冲（null=无脉冲）
    val notificationPosted: NotificationPulse? = null,
    val notificationRemoved: NotificationPulse? = null,
    /** 当前通知栏里的通知快照（NOTIFICATION_EXISTS 状态条件用）。 */
    val activeNotifications: List<NotificationPulse> = emptyList(),
    val packageChanged: PackageChangePulse? = null,
    val shareReceived: SharePulse? = null,
    val clipboardChanged: ClipboardPulse? = null,
    /** INTERVAL 到期的 ruleId:分钟 键；ticker 写入，求值后随脉冲消费。 */
    val intervalDueKeys: Set<String> = emptySet(),
    val bluetoothChanged: ConnectionDirection? = null,
    val headphonesChanged: ConnectionDirection? = null,
    val scheduledTimeHit: Boolean = false,
    /** 手势脉冲：本轮命中的按钮 id/名称，无则 null。 */
    val gesturePerformedId: String? = null,
    val gesturePerformedName: String? = null,
    /** 本轮执行的自动化规则名（引擎触发时写入，模板变量 trigger.rule.name 用）。 */
    val triggerRuleName: String? = null,
    /** v3 脉冲：键盘弹出/收起、音乐启停、手电/勿扰开关变化。 */
    val keyboardEvent: KeyboardEventType? = null,
    val musicChanged: PlaybackDirection? = null,
    val torchChangedTo: Boolean? = null,
    val dndChangedTo: Boolean? = null,
    /** 全局/局部变量快照（key 带 `var.` 前缀）；默认空。 */
    val variables: Map<String, String> = emptyMap(),
)

data class VolumeChange(
    val stream: AudioStream,
    val direction: VolumeDirection,
)

/**
 * 通知脉冲：包名+标题+正文（匹配后原文即丢，历史默认不同原文）。
 * `PendingIntent` 不进快照；通知胶囊点击时按 [key] 从 runtime 侧取。
 */
data class NotificationPulse(
    val packageName: String,
    val title: String,
    val text: String,
    /** 原始 android.app.Notification.flags。 */
    val flags: Int = 0,
    val ongoing: Boolean = false,
    val clearable: Boolean = true,
    /** 默认 NotificationManager.IMPORTANCE_DEFAULT = 3（契约层不依赖 Android）。 */
    val importance: Int = DEFAULT_IMPORTANCE,
    /** 用于通知栏仍在显示的二段确认（延迟后查 activeNotifications）。 */
    val key: String? = null,
    val groupKey: String? = null,
    val channelId: String? = null,
    val category: String? = null,
) {
    companion object {
        const val DEFAULT_IMPORTANCE = 3
    }
}

/** 应用安装/卸载/更新脉冲。 */
data class PackageChangePulse(
    val action: String,
    val packageName: String,
)

/** 分享/链接接收脉冲；url 为提取出的首个 http(s) 链接（没有则为 null）。 */
data class SharePulse(
    val text: String,
    val url: String? = null,
    /** VIEW / SEND / 其它。 */
    val source: String = "",
)

/** 剪贴板球/面板读到的新文本。 */
data class ClipboardPulse(val text: String)
