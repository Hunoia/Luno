package hunoia.luno.gesture.touch

/**
 * 解析 toybox `getevent` 的 `-lp` / `-lt` 输出。
 *
 * 这里只做纯文本解析，不碰 Android / Shizuku，便于 JVM 单测覆盖真实设备输出。
 */
object GetEventParser {

    private val DEVICE_HEADER = Regex("""^add device\s+\d+:\s*(\S+)\s*$""")
    private val DEVICE_NAME = Regex("""^\s*name:\s*"?(.*?)"?\s*$""")
    private val ABS_ENTRY = Regex(
        """(ABS_[A-Z0-9_]+)\s*:\s*value\s+(-?\d+),\s*min\s+(-?\d+),\s*max\s+(-?\d+)"""
    )
    private val INPUT_PROP = Regex("""INPUT_PROP_[A-Z0-9_]+""")
    private val KEY_CODE = Regex("""\b[A-Z][A-Z0-9_]{2,}\b""")
    /**
     * `getevent -lt` 在多设备或部分 ROM 上会给每行加 `/dev/input/eventN:` 前缀，
     * 单设备时又没有。两种都要认，否则整条流会被当成无效行静默丢弃。
     */
    private val EVENT_LINE = Regex(
        """^(?:/dev/input/event\d+:\s*)?\[\s*(\d+(?:\.\d+)?)\]\s+(\S+)\s+(\S+)\s+([0-9a-fA-F]+)\s*$"""
    )

    /**
     * 设备段之间没有显式结束标记，以 `add device` 作为分隔符。
     * 同一段里的属性行可能缩进，故按行累积后统一抽取。
     */
    fun parseDevices(output: String): List<GetEventDevice> {
        val result = mutableListOf<GetEventDevice>()
        var current: DeviceBlock? = null

        fun flush() {
            current?.let { result += it.build() }
            current = null
        }

        output.lineSequence().forEach { line ->
            val header = DEVICE_HEADER.find(line)
            if (header != null) {
                flush()
                current = DeviceBlock(header.groupValues[1])
                return@forEach
            }
            val block = current ?: return@forEach
            block.lines += line
            DEVICE_NAME.find(line)?.let { match ->
                val name = match.groupValues[1].trim()
                if (name.isNotEmpty()) block.name = name
            }
        }
        flush()
        return result
    }

    /** 解析一行 `[  1234.567890] EV_ABS ABS_MT_POSITION_X 00000123`。 */
    fun parseEvent(line: String): RawInputEvent? {
        val match = EVENT_LINE.find(line.trim()) ?: return null
        val hex = match.groupValues[4]
        val raw = hex.toLong(16)
        val value = if (raw > Int.MAX_VALUE) (raw - 0x1_0000_0000L).toInt() else raw.toInt()
        return RawInputEvent(
            timestamp = match.groupValues[1].toDoubleOrNull() ?: return null,
            type = match.groupValues[2],
            code = match.groupValues[3],
            value = value,
        )
    }

    private class DeviceBlock(val path: String) {
        var name: String = ""
        val lines = mutableListOf<String>()

        fun build(): GetEventDevice {
            val absRanges = linkedMapOf<String, AbsRange>()
            val inputProps = linkedSetOf<String>()
            val keyCodes = linkedSetOf<String>()
            lines.forEach { line ->
                ABS_ENTRY.findAll(line).forEach { match ->
                    absRanges[match.groupValues[1]] = AbsRange(
                        min = match.groupValues[3].toInt(),
                        max = match.groupValues[4].toInt(),
                    )
                }
                INPUT_PROP.findAll(line).forEach { inputProps += it.value }
                if (line.contains("(0001):")) {
                    keyCodes += KEY_CODE.findAll(line.substringAfter("(0001):"))
                        .map { it.value }
                        .filter { it.startsWith("KEY_") || it.startsWith("BTN_") }
                        .toList()
                }
            }
            return GetEventDevice(
                path = path,
                name = name,
                absRanges = absRanges,
                keyCodes = keyCodes,
                inputProps = inputProps,
            )
        }
    }
}
