package hunoia.luno.gesture.touch

/** `getevent -p` 里单个 ABS 轴的范围。 */
data class AbsRange(
    val min: Int,
    val max: Int,
) {
    val span: Int get() = (max - min).coerceAtLeast(1)
}

/** `getevent -lp` 解析出的一个输入设备。 */
data class GetEventDevice(
    val path: String,
    val name: String,
    val absRanges: Map<String, AbsRange> = emptyMap(),
    val keyCodes: Set<String> = emptySet(),
    val inputProps: Set<String> = emptySet(),
) {
    val xRange: AbsRange? get() = absRanges["ABS_MT_POSITION_X"] ?: absRanges["ABS_X"]
    val yRange: AbsRange? get() = absRanges["ABS_MT_POSITION_Y"] ?: absRanges["ABS_Y"]

    val hasMultiTouch: Boolean
        get() = absRanges.containsKey("ABS_MT_POSITION_X") &&
            absRanges.containsKey("ABS_MT_POSITION_Y")

    val hasTouchKey: Boolean get() = "BTN_TOUCH" in keyCodes
    val isDirect: Boolean get() = "INPUT_PROP_DIRECT" in inputProps

    val isTouchscreen: Boolean
        get() = xRange != null && yRange != null && (hasMultiTouch || hasTouchKey || isDirect)

    /**
     * 多屏输入设备（C 屏/副屏）和触摸板也会报 ABS，但只有触摸屏带
     * INPUT_PROP_DIRECT 或多点坐标 + BTN_TOUCH。分数只用于排序，不参与语义判断。
     */
    val score: Int
        get() {
            var value = 0
            if (isDirect) value += 100
            if (hasMultiTouch) value += 50
            if (hasTouchKey) value += 20
            if (absRanges.containsKey("ABS_MT_TRACKING_ID")) value += 10
            val range = xRange
            val yRange = yRange
            if (range != null && yRange != null) {
                // 面积只作为同分时的决胜项，避免把 4x4 的触摸板排在主屏前面。
                value += (range.span.toLong() * yRange.span / 100_000L).toInt().coerceAtMost(9)
            }
            return value
        }

    fun normalizedX(raw: Int): Float? = xRange?.let {
        ((raw - it.min).toFloat() / it.span).coerceIn(0f, 1f)
    }

    fun normalizedY(raw: Int): Float? = yRange?.let {
        ((raw - it.min).toFloat() / it.span).coerceIn(0f, 1f)
    }
}

/** `getevent -lt` 的一行原始事件。 */
data class RawInputEvent(
    val timestamp: Double,
    val type: String,
    val code: String,
    val value: Int,
)

data class TouchPoint(
    val id: Int,
    val x: Int,
    val y: Int,
)

enum class TouchAction {
    Down,
    Move,
    Up,
}

/** 一个 SYN_REPORT 边界上的触摸帧。 */
data class TouchFrame(
    val timestamp: Double,
    val action: TouchAction,
    val points: List<TouchPoint>,
)

/** 一次输入事件及其（可选的）触摸帧结果。调试页据此同时展示原始事件与解析结果。 */
data class TouchProbeSample(
    val raw: RawInputEvent,
    val frame: TouchFrame?,
)
