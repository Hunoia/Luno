package hunoia.luno.runtime.condition

import hunoia.luno.config.model.ConditionItem
import hunoia.luno.config.model.ConditionType
import hunoia.luno.config.model.NotificationMatchMode
import hunoia.luno.config.model.RingerMode
import hunoia.luno.config.model.ScreenType
import hunoia.luno.config.model.VariableCompareOp
import hunoia.luno.config.model.NetworkType
import hunoia.luno.core.domain.ConnectionDirection
import hunoia.luno.core.domain.NotificationPulse

/**
 * 单个条件类型的扩展点：求值、订阅键、脉冲分类与少数「供 ticker 预筛」的分类信息
 * 都在实现里声明。新增条件 = 新建实现 + 注册，不改求值器与事件总线。
 */
interface Condition {
    val type: String

    /** 一次性脉冲（命中即消费），参与边沿与订阅刷新判定。 */
    val isPulse: Boolean get() = false

    /** 决定规则在哪个事件域被评估；空集 = 不订阅专属事件（通用刷新兜底）。 */
    fun subscriptionKeys(item: ConditionItem): Set<String> = emptySet()

    fun evaluate(item: ConditionItem, ctx: ConditionContext, ruleId: String?): Boolean

    /** 时间窗条件（供 ticker 收集）。 */
    fun timeWindow(item: ConditionItem): TimeWindow? = null

    /** 定时触发条件（供 ticker 收集）。 */
    fun isScheduled(item: ConditionItem): Boolean = false

    /** 间隔触发条件（供 ticker 收集）。 */
    fun intervalMinutes(item: ConditionItem): Int? = null

    /** 是否依赖音量/铃声状态（决定音量回调注册）。 */
    val affectsVolume: Boolean get() = false

    /** 是否依赖音乐播放状态（决定播放回调注册）。 */
    val affectsMusic: Boolean get() = false

    /** 是否依赖手电筒状态（决定 torch 回调注册）。 */
    val affectsTorch: Boolean get() = false
}

/**
 * 条件扩展点注册表。只做 type 唯一校验与索引，求值逻辑全在 [Condition] 实现里。
 * 事件键常量来自 `AutomationEventKey`（`:feature:automation` 与本模块共享的纯字符串契约）。
 */
object ConditionRegistry {

    private val byType = LinkedHashMap<String, Condition>()

    init {
        registerForegroundConditions()
        registerDeviceConditions()
        registerPulseConditions()
    }

    fun register(condition: Condition) {
        val previous = byType.put(condition.type, condition)
        if (previous != null) error("duplicate condition type: ${condition.type}")
    }

    fun conditionOf(type: String): Condition? = byType[type]

    val registeredTypes: Set<String> get() = byType.keys

    /**
     * 单个叶子求值。GROUP 递归、MANUAL 恒假且忽略取反（取反恒真会变成永远触发），
     * 其余统一套用 [ConditionItem.negated]。
     */
    fun matches(item: ConditionItem, ctx: ConditionContext, ruleId: String?): Boolean {
        if (item.type == ConditionType.GROUP) return item.group?.matches(ctx, ruleId) == true
        if (item.type == ConditionType.MANUAL) return false
        val raw = conditionOf(item.type)?.evaluate(item, ctx, ruleId) ?: false
        return if (item.negated) !raw else raw
    }

    fun subscriptionKeys(type: String): Set<String> =
        conditionOf(type)?.subscriptionKeys(EMPTY_ITEM) ?: emptySet()

    fun subscriptionKeys(item: ConditionItem): Set<String> =
        conditionOf(item.type)?.subscriptionKeys(item) ?: emptySet()

    fun isPulse(type: String): Boolean = conditionOf(type)?.isPulse ?: false

    fun timeWindow(item: ConditionItem): TimeWindow? = conditionOf(item.type)?.timeWindow(item)

    fun isScheduled(item: ConditionItem): Boolean = conditionOf(item.type)?.isScheduled(item) == true

    fun intervalMinutes(item: ConditionItem): Int? = conditionOf(item.type)?.intervalMinutes(item)

    fun affectsVolume(type: String): Boolean = conditionOf(type)?.affectsVolume == true

    fun affectsMusic(type: String): Boolean = conditionOf(type)?.affectsMusic == true

    fun affectsTorch(type: String): Boolean = conditionOf(type)?.affectsTorch == true

    private val EMPTY_ITEM = ConditionItem(type = ConditionType.MANUAL)

    private fun registerForegroundConditions() {
        register(
            lambda(
                type = ConditionType.FOREGROUND_APP,
                keys = setOf(AutomationKeys.APP),
            ) { item, ctx, _ -> ctx.packageName in item.packageNames },
        )
        register(
            lambda(
                type = ConditionType.SCREEN_STATE,
                keys = setOf(AutomationKeys.APP, AutomationKeys.SCREEN, AutomationKeys.KEYBOARD),
            ) { item, ctx, _ -> item.screenStateMatches(ctx) },
        )
        register(
            lambda(
                type = ConditionType.APP_CHANGED_TO,
                keys = setOf(AutomationKeys.APP),
                pulse = true,
            ) { item, ctx, _ -> ctx.appChangedTo != null && ctx.appChangedTo in item.packageNames },
        )
        register(
            lambda(
                type = ConditionType.APP_PACKAGE_CHANGED,
                keys = setOf(AutomationKeys.PACKAGE),
                pulse = true,
            ) { item, ctx, _ -> item.packageChangedMatches(ctx) },
        )
        register(
            lambda(
                type = ConditionType.KEYBOARD_EVENT,
                keys = setOf(AutomationKeys.KEYBOARD),
                pulse = true,
            ) { item, ctx, _ ->
                ctx.keyboardEvent != null && ctx.keyboardEvent == item.keyboardEvent
            },
        )
        register(
            lambda(
                type = ConditionType.GESTURE_PERFORMED,
                keys = setOf(AutomationKeys.GESTURE),
                pulse = true,
            ) { _, ctx, _ -> ctx.gesturePerformedId != null },
        )
        register(
            lambda(type = ConditionType.MANUAL, pulse = true) { _, _, _ -> false },
        )
    }

    private fun registerDeviceConditions() {
        register(
            lambda(
                type = ConditionType.BATTERY_RANGE,
                keys = setOf(AutomationKeys.CHARGING),
            ) { item, ctx, _ ->
                rangeContains(ctx.batteryLevel, item.batteryLevelMin, item.batteryLevelMax)
            },
        )
        register(
            lambda(
                type = ConditionType.CHARGING,
                keys = setOf(AutomationKeys.CHARGING),
            ) { item, ctx, _ -> (item.charging ?: false) == ctx.isCharging },
        )
        register(
            lambda(
                type = ConditionType.CHARGING_CHANGED,
                keys = setOf(AutomationKeys.CHARGING),
                pulse = true,
            ) { item, ctx, _ ->
                ctx.chargingChangedTo != null && ctx.chargingChangedTo == item.charging
            },
        )
        register(
            lambda(
                type = ConditionType.TIME_RANGE,
                keys = setOf(AutomationKeys.TIME),
                timeWindow = { item ->
                    TimeWindow(item.startMinute ?: 0, item.endMinute ?: 0, item.timeWeekdays)
                },
            ) { item, ctx, _ ->
                inTimeWindow(ctx.minuteOfDay, item.startMinute, item.endMinute) &&
                    (item.timeWeekdays.isEmpty() || ctx.dayOfWeek in item.timeWeekdays)
            },
        )
        register(
            lambda(
                type = ConditionType.SCHEDULED_TIME,
                keys = setOf(AutomationKeys.TIME),
                pulse = true,
                scheduled = true,
            ) { item, ctx, _ -> ctx.scheduledTimeHit && ctx.minuteOfDay in item.scheduledMinutes },
        )
        register(
            lambda(
                type = ConditionType.INTERVAL,
                keys = setOf(AutomationKeys.INTERVAL),
                pulse = true,
                interval = { item -> item.intervalMinutes },
            ) { item, ctx, ruleId -> item.intervalMatches(ctx, ruleId) },
        )
        register(
            lambda(
                type = ConditionType.VOLUME_RANGE,
                keys = setOf(AutomationKeys.VOLUME),
                affectsVolume = true,
            ) { item, ctx, _ ->
                rangeContains(ctx.volumePercent[item.audioStream], item.volumeMin, item.volumeMax)
            },
        )
        register(
            lambda(
                type = ConditionType.RINGER_SILENT,
                keys = setOf(AutomationKeys.VOLUME),
                affectsVolume = true,
            ) { item, ctx, _ ->
                val expected = item.ringerMode ?: RingerMode.NORMAL
                expected == ctx.ringerMode
            },
        )
        register(
            lambda(
                type = ConditionType.VOLUME_CHANGED,
                keys = setOf(AutomationKeys.VOLUME),
                pulse = true,
                affectsVolume = true,
            ) { item, ctx, _ -> item.volumeChangedMatches(ctx) },
        )
        register(
            lambda(type = ConditionType.NETWORK_TYPE) { item, ctx, _ ->
                item.networkType == ctx.networkType
            },
        )
        register(
            lambda(type = ConditionType.WIFI_SSID) { item, ctx, _ -> item.wifiSsidMatches(ctx) },
        )
        register(
            lambda(
                type = ConditionType.HEADPHONES,
                keys = setOf(AutomationKeys.HEADPHONES),
            ) { item, ctx, _ -> (item.plugged ?: false) == ctx.headphonesConnected },
        )
        register(
            lambda(
                type = ConditionType.HEADPHONES_CHANGED,
                keys = setOf(AutomationKeys.HEADPHONES),
                pulse = true,
            ) { item, ctx, _ -> item.connectionMatches(ctx.headphonesChanged) },
        )
        register(
            lambda(
                type = ConditionType.BLUETOOTH,
                keys = setOf(AutomationKeys.BLUETOOTH),
            ) { item, ctx, _ -> item.bluetoothMatches(ctx) },
        )
        register(
            lambda(
                type = ConditionType.BLUETOOTH_CHANGED,
                keys = setOf(AutomationKeys.BLUETOOTH),
                pulse = true,
            ) { item, ctx, _ -> item.connectionMatches(ctx.bluetoothChanged) },
        )
        register(
            lambda(type = ConditionType.AIRPLANE_MODE) { item, ctx, _ ->
                (item.airplaneMode ?: false) == ctx.airplaneMode
            },
        )
        register(
            lambda(
                type = ConditionType.MUSIC_PLAYING,
                keys = setOf(AutomationKeys.MUSIC),
                affectsMusic = true,
            ) { item, ctx, _ -> (item.musicPlaying ?: false) == ctx.isMusicPlaying },
        )
        register(
            lambda(
                type = ConditionType.MUSIC_CHANGED,
                keys = setOf(AutomationKeys.MUSIC),
                pulse = true,
                affectsMusic = true,
            ) { item, ctx, _ ->
                ctx.musicChanged != null &&
                    (item.playbackDirection == null || item.playbackDirection == ctx.musicChanged)
            },
        )
        register(
            lambda(
                type = ConditionType.TORCH_ON,
                keys = setOf(AutomationKeys.TORCH),
                affectsTorch = true,
            ) { item, ctx, _ -> (item.torchOn ?: false) == ctx.isTorchOn },
        )
        register(
            lambda(
                type = ConditionType.TORCH_CHANGED,
                keys = setOf(AutomationKeys.TORCH),
                pulse = true,
                affectsTorch = true,
            ) { item, ctx, _ ->
                ctx.torchChangedTo != null && item.torchChangedTo == ctx.torchChangedTo
            },
        )
        register(
            lambda(
                type = ConditionType.DND_ON,
                keys = setOf(AutomationKeys.DND),
            ) { item, ctx, _ -> (item.dndOn ?: false) == ctx.isDndOn },
        )
        register(
            lambda(
                type = ConditionType.DND_CHANGED,
                keys = setOf(AutomationKeys.DND),
                pulse = true,
            ) { item, ctx, _ ->
                ctx.dndChangedTo != null && item.dndChangedTo == ctx.dndChangedTo
            },
        )
        register(
            lambda(type = ConditionType.VAR_COMPARE, keys = setOf(AutomationKeys.VARIABLE)) { item, ctx, _ ->
                item.variableMatches(ctx.variables)
            },
        )
    }

    private fun registerPulseConditions() {
        register(
            lambda(
                type = ConditionType.SCREEN_EVENT,
                keys = setOf(AutomationKeys.SCREEN),
                pulse = true,
            ) { item, ctx, _ -> ctx.screenEvent == item.screenEvent },
        )
        register(
            lambda(
                type = ConditionType.NOTIFICATION_POSTED,
                keys = setOf(AutomationKeys.NOTIFICATION),
                pulse = true,
            ) { item, ctx, _ -> item.notificationMatches(ctx.notificationPosted) },
        )
        register(
            lambda(
                type = ConditionType.NOTIFICATION_REMOVED,
                keys = setOf(AutomationKeys.NOTIFICATION),
                pulse = true,
            ) { item, ctx, _ -> item.notificationMatches(ctx.notificationRemoved) },
        )
        register(
            lambda(
                type = ConditionType.NOTIFICATION_EXISTS,
                keys = setOf(AutomationKeys.NOTIFICATION),
            ) { item, ctx, _ -> item.notificationExistsMatches(ctx) },
        )
        register(
            lambda(
                type = ConditionType.SHARE_RECEIVED,
                keys = setOf(AutomationKeys.SHARE),
                pulse = true,
            ) { _, ctx, _ -> ctx.shareReceived != null },
        )
        register(
            lambda(
                type = ConditionType.CLIPBOARD_CHANGED,
                keys = setOf(AutomationKeys.CLIPBOARD),
                pulse = true,
            ) { item, ctx, _ ->
                ctx.clipboardChanged?.let {
                    item.clipboardKeyword.isBlank() ||
                        it.text.contains(item.clipboardKeyword, ignoreCase = true)
                } == true
            },
        )
    }

    private fun lambda(
        type: String,
        keys: Set<String> = emptySet(),
        pulse: Boolean = false,
        timeWindow: ((ConditionItem) -> TimeWindow?)? = null,
        scheduled: Boolean = false,
        interval: ((ConditionItem) -> Int?)? = null,
        affectsVolume: Boolean = false,
        affectsMusic: Boolean = false,
        affectsTorch: Boolean = false,
        evaluate: (ConditionItem, ConditionContext, String?) -> Boolean,
    ): Condition = object : Condition {
        override val type: String = type
        override val isPulse: Boolean = pulse
        override val affectsVolume: Boolean = affectsVolume
        override val affectsMusic: Boolean = affectsMusic
        override val affectsTorch: Boolean = affectsTorch

        override fun subscriptionKeys(item: ConditionItem): Set<String> = keys

        override fun evaluate(item: ConditionItem, ctx: ConditionContext, ruleId: String?): Boolean =
            evaluate.invoke(item, ctx, ruleId)

        override fun timeWindow(item: ConditionItem): TimeWindow? = timeWindow?.invoke(item)

        override fun isScheduled(item: ConditionItem): Boolean = scheduled

        override fun intervalMinutes(item: ConditionItem): Int? = interval?.invoke(item)
    }

    private fun ConditionItem.screenStateMatches(ctx: ConditionContext): Boolean = when (screenType) {
        ScreenType.LOCK_SCREEN -> ctx.isLockScreen
        ScreenType.LAUNCHER -> ctx.isLauncher
        ScreenType.LANDSCAPE -> ctx.isLandscape
        ScreenType.PORTRAIT -> !ctx.isLandscape
        ScreenType.KEYBOARD_INPUT -> ctx.isKeyboardInput
        null -> false
    }

    private fun ConditionItem.variableMatches(variables: Map<String, String>): Boolean {
        val name = compareVarName.trim()
        if (name.isEmpty()) return false
        val key = if (name.startsWith("var.")) name else "var.$name"
        val left = variables[key] ?: return false
        return compareValues(left, compareOp, compareValue)
    }

    private fun ConditionItem.connectionMatches(pulse: ConnectionDirection?): Boolean {
        if (pulse == null) return false
        return connectionDirection == null || connectionDirection == pulse
    }

    private fun ConditionItem.notificationMatches(pulse: NotificationPulse?): Boolean {
        if (pulse == null) return false
        if (notifyPackages.isNotEmpty() && pulse.packageName !in notifyPackages) return false
        if (notifyExcludePackages.isNotEmpty() && pulse.packageName in notifyExcludePackages) return false
        if (notifyOngoing != null && pulse.ongoing != notifyOngoing) return false
        if (notifyClearable != null && pulse.clearable != notifyClearable) return false
        val importanceAtLeast = notifyImportanceAtLeast
        if (importanceAtLeast != null && pulse.importance < importanceAtLeast) return false

        val mode = notifyMatchMode.takeIf { it in NotificationMatchMode.all }
            ?: NotificationMatchMode.CONTAINS
        // 旧版单关键字继续兼容：标题或正文任意命中即可。
        if (notifyKeyword.isNotBlank() &&
            !textMatches(pulse.title, notifyKeyword, mode) &&
            !textMatches(pulse.text, notifyKeyword, mode)
        ) return false
        if (notifyTitleKeyword.isNotBlank() && !textMatches(pulse.title, notifyTitleKeyword, mode)) return false
        if (notifyTextKeyword.isNotBlank() && !textMatches(pulse.text, notifyTextKeyword, mode)) return false
        if (notifyExcludeKeyword.isNotBlank() &&
            (textMatches(pulse.title, notifyExcludeKeyword, mode) ||
                textMatches(pulse.text, notifyExcludeKeyword, mode))
        ) return false
        return true
    }

    private fun ConditionItem.notificationExistsMatches(ctx: ConditionContext): Boolean {
        val exists = ctx.activeNotifications.any { notificationMatches(it) }
        return exists == (notifyExists ?: true)
    }

    private fun textMatches(source: String, pattern: String, mode: String): Boolean = when (mode) {
        NotificationMatchMode.REGEX -> runCatching {
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(source)
        }.getOrDefault(false)

        NotificationMatchMode.GLOB -> {
            val regex = buildString {
                append(".*")
                for (ch in pattern) {
                    when (ch) {
                        '*' -> append(".*")
                        '?' -> append('.')
                        else -> append(Regex.escape(ch.toString()))
                    }
                }
                append(".*")
            }
            runCatching { Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(source) }
                .getOrDefault(false)
        }

        else -> source.contains(pattern, ignoreCase = true)
    }

    private fun ConditionItem.packageChangedMatches(ctx: ConditionContext): Boolean {
        val pulse = ctx.packageChanged ?: return false
        if (packageNames.isNotEmpty() && pulse.packageName !in packageNames) return false
        return packageChangeAction == null || packageChangeAction == pulse.action
    }

    private fun ConditionItem.intervalMatches(ctx: ConditionContext, ruleId: String?): Boolean {
        val key = ruleId?.let { intervalDueKey(it, this) } ?: return false
        return key in ctx.intervalDueKeys
    }

    private fun ConditionItem.wifiSsidMatches(ctx: ConditionContext): Boolean {
        if (ctx.networkType != NetworkType.WIFI) return false
        if (wifiSsid.isBlank()) return true
        val actual = ctx.wifiSsid ?: return false
        return actual.equals(wifiSsid.trim(), ignoreCase = true)
    }

    private fun ConditionItem.bluetoothMatches(ctx: ConditionContext): Boolean {
        val hasDeviceFilter = bluetoothDeviceName.isNotBlank() || bluetoothDeviceAddress.isNotBlank()
        if (expectedBluetoothAdapterOn == null &&
            expectedBluetoothAudioConnected == null &&
            !hasDeviceFilter
        ) return false
        val adapterOk = expectedBluetoothAdapterOn == null ||
            expectedBluetoothAdapterOn == ctx.bluetoothAdapterOn
        val audioOk = expectedBluetoothAudioConnected == null ||
            expectedBluetoothAudioConnected == ctx.bluetoothAudioConnected
        if (!adapterOk) return false
        // 指定设备意味着必须已连接；读取失败（null）按不匹配处理，避免误触。
        if (hasDeviceFilter) {
            if (ctx.bluetoothAudioConnected != true) return false
            if (bluetoothDeviceName.isNotBlank() &&
                !ctx.bluetoothDeviceName.equals(bluetoothDeviceName.trim(), ignoreCase = true)
            ) return false
            if (bluetoothDeviceAddress.isNotBlank() &&
                !ctx.bluetoothDeviceAddress.equals(bluetoothDeviceAddress.trim(), ignoreCase = true)
            ) return false
        }
        return audioOk
    }

    private fun ConditionItem.volumeChangedMatches(ctx: ConditionContext): Boolean {
        val pulse = ctx.volumeChanged ?: return false
        val streamOk = audioStream == null || pulse.stream == audioStream
        val dirOk = volumeDirection == null || pulse.direction == volumeDirection
        return streamOk && dirOk
    }
}

/** 事件键常量与 `AutomationEventKey` 同值；保持字符串契约在两侧可独立演进。 */
private object AutomationKeys {
    const val APP = "app"
    const val SCREEN = "screen"
    const val KEYBOARD = "keyboard"
    const val CHARGING = "charging"
    const val VOLUME = "volume"
    const val NOTIFICATION = "notification"
    const val PACKAGE = "package"
    const val INTERVAL = "interval"
    const val SHARE = "share"
    const val CLIPBOARD = "clipboard"
    const val BLUETOOTH = "bluetooth"
    const val HEADPHONES = "headphones"
    const val TIME = "time"
    const val GESTURE = "gesture"
    const val MUSIC = "music"
    const val TORCH = "torch"
    const val DND = "dnd"
    const val VARIABLE = "variable"
}
