package hunoia.luno.gesture.touch

/**
 * 把 Linux input 事件聚合成「每 SYN_REPORT 一帧」的触摸点集合。
 *
 * 同时支持协议 B（ABS_MT_SLOT / TRACKING_ID）和旧式单点（BTN_TOUCH + ABS_X/Y）；
 * 只保留坐标与按下/抬起语义，不做手势识别。
 *
 * 两条健壮性规则（都是「帧不可信就清空状态」，不尝试猜）：
 * - 收到 `SYN_DROPPED`：内核输入队列溢出，之后读到的槽位增量不可信；
 * - 帧时间戳超出 [clockMs] 给的出现窗口：读取侧积压，坐标属于过去。
 */
class TouchTracker(
    /**
     * 可选的帧新鲜度时钟（毫秒，须与内核事件时间戳同源，即 `CLOCK_MONOTONIC`）。
     * 传 null（单测默认、或不需要校时）表示不校验。
     */
    private val clockMs: (() -> Long)? = null,
) {

    private class SlotPoint(
        var id: Int = -1,
        var x: Int = 0,
        var y: Int = 0,
    )

    private val slots = sortedMapOf<Int, SlotPoint>()
    private var currentSlot = 0

    private var singleX = 0
    private var singleY = 0
    private var singleSeen = false

    /**
     * 老式单点路径的两个按下信号分开记。
     *
     * 部分设备的抬指只清 [BTN_TOOL_FINGER]、不清 [BTN_TOUCH]（或反之）；合并成一个字段时
     * 后到的信号会把前一个覆盖掉，表现为「手指已抬起却一直按着」或「按下丢了」。
     * 只要设备报过 [BTN_TOUCH] 就以它为准，否则退回 [BTN_TOOL_FINGER]——只有后者的设备
     * 才走兜底，优先级不随事件顺序抖动。
     */
    private var buttonTouchSeen = false
    private var buttonTouchDown = false
    private var toolFingerDown = false

    private val singleDown: Boolean
        get() = if (buttonTouchSeen) buttonTouchDown else toolFingerDown

    private var lastCount = 0
    private var frameTimestamp = 0.0

    /** 断流/切设备后重新开始：丢弃上一段会话残留的槽位。 */
    fun reset() = clearState()

    fun feed(event: RawInputEvent): TouchFrame? {
        when (event.type) {
            "EV_ABS" -> {
                frameTimestamp = event.timestamp
                when (event.code) {
                    "ABS_MT_SLOT" -> currentSlot = event.value
                    "ABS_MT_TRACKING_ID" -> {
                        if (event.value < 0) {
                            slots.remove(currentSlot)
                        } else {
                            slot().id = event.value
                        }
                    }
                    "ABS_MT_POSITION_X" -> slot().x = event.value
                    "ABS_MT_POSITION_Y" -> slot().y = event.value
                    "ABS_X" -> {
                        singleX = event.value
                        singleSeen = true
                    }
                    "ABS_Y" -> {
                        singleY = event.value
                        singleSeen = true
                    }
                }
            }
            "EV_KEY" -> {
                frameTimestamp = event.timestamp
                when (event.code) {
                    "BTN_TOUCH" -> {
                        buttonTouchSeen = true
                        buttonTouchDown = event.value != 0
                    }
                    "BTN_TOOL_FINGER" -> toolFingerDown = event.value != 0
                }
            }
            "EV_SYN" -> when (event.code) {
                "SYN_REPORT" -> return emit(event.timestamp)
                "SYN_DROPPED" -> return discardFrame(event.timestamp)
            }
        }
        return null
    }

    private fun slot(): SlotPoint = slots.getOrPut(currentSlot) { SlotPoint() }

    private fun emit(timestamp: Double): TouchFrame? {
        val frameTime = if (timestamp > 0) timestamp else frameTimestamp
        if (isStale(frameTime)) return discardFrame(frameTime)

        val points = slots.values
            .filter { it.id >= 0 }
            .map { TouchPoint(id = it.id, x = it.x, y = it.y) }
            .ifEmpty {
                if (singleDown && singleSeen) {
                    listOf(TouchPoint(id = 0, x = singleX, y = singleY))
                } else {
                    emptyList()
                }
            }

        val action = when {
            points.size > lastCount -> TouchAction.Down
            points.isEmpty() && lastCount > 0 -> TouchAction.Up
            points.isEmpty() -> return null
            else -> TouchAction.Move
        }
        lastCount = points.size
        return TouchFrame(timestamp = frameTime, action = action, points = points)
    }

    /**
     * 丢弃当前帧并清空槽位状态。
     *
     * 丢的可能是 `ABS_MT_TRACKING_ID = -1` 那一帧；不清空的话槽位会永远留着，
     * 之后每一帧都带一个已经不存在的触点（「幽灵手指卡住」/「明明抬起却一直按着」），
     * 靠后续正常帧永远无法自愈。清空后新触点会重新走 Down，不会吞掉下一次按下。
     *
     * 若这一段正在跟踪触点，必须补一帧 Up，否则上层手势状态机永远停在「按住中」。
     */
    private fun discardFrame(timestamp: Double): TouchFrame? {
        val wasTracking = lastCount > 0
        clearState()
        if (!wasTracking) return null
        return TouchFrame(
            timestamp = if (timestamp > 0) timestamp else frameTimestamp,
            action = TouchAction.Up,
            points = emptyList(),
        )
    }

    /**
     * 帧是否已偏离 [clockMs] 的当前时间太远。
     *
     * 内核事件时间戳是 `CLOCK_MONOTONIC` 秒，读取侧若积压（flow 背压 / 主线程忙 /
     * 跨进程读管道跟不上），恢复时会一次性回放一堆积压帧：坐标是过去的，判定用的是当下，
     * 位移与长按时序都会算错。宁可整段作废也不要拿过期坐标去点火。
     *
     * 时间戳缺失（<= 0，设备或 getevent 没给）时不判断：宁可放过也不误杀。
     */
    private fun isStale(frameTime: Double): Boolean {
        val clock = clockMs ?: return false
        if (frameTime <= 0) return false
        val age = clock() - (frameTime * 1000.0).toLong()
        return age > StaleBehindMs || age < -StaleAheadMs
    }

    private fun clearState() {
        slots.clear()
        currentSlot = 0
        singleX = 0
        singleY = 0
        singleSeen = false
        buttonTouchSeen = false
        buttonTouchDown = false
        toolFingerDown = false
        lastCount = 0
        frameTimestamp = 0.0
    }

    companion object {
        /** 允许的落后量：超过即视为读取侧积压。 */
        internal const val StaleBehindMs = 1_000L

        /** 允许的领先量：事件时间戳不该来自未来（跨源时钟错位时兜底）。 */
        internal const val StaleAheadMs = 100L
    }
}
