package hunoia.luno.gesture.touch

/**
 * 把 Linux input 事件聚合成「每 SYN_REPORT 一帧」的触摸点集合。
 *
 * 同时支持协议 B（ABS_MT_SLOT / TRACKING_ID）和旧式单点（BTN_TOUCH + ABS_X/Y）；
 * 只保留坐标与按下/抬起语义，不做手势识别。
 */
class TouchTracker {

    private class SlotPoint(
        var id: Int = -1,
        var x: Int = 0,
        var y: Int = 0,
    )

    private val slots = sortedMapOf<Int, SlotPoint>()
    private var currentSlot = 0

    private var singleX = 0
    private var singleY = 0
    private var singleDown = false
    private var singleSeen = false

    private var lastCount = 0
    private var frameTimestamp = 0.0

    /** 断流/切设备后重新开始：丢弃上一段会话残留的槽位。 */
    fun reset() {
        slots.clear()
        currentSlot = 0
        singleX = 0
        singleY = 0
        singleDown = false
        singleSeen = false
        lastCount = 0
        frameTimestamp = 0.0
    }

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
                if (event.code == "BTN_TOUCH" || event.code == "BTN_TOOL_FINGER") {
                    singleDown = event.value != 0
                }
            }
            "EV_SYN" -> {
                if (event.code == "SYN_REPORT") return emit(event.timestamp)
            }
        }
        return null
    }

    private fun slot(): SlotPoint = slots.getOrPut(currentSlot) { SlotPoint() }

    private fun emit(timestamp: Double): TouchFrame? {
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
        return TouchFrame(
            timestamp = if (timestamp > 0) timestamp else frameTimestamp,
            action = action,
            points = points,
        )
    }
}
