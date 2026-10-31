package hunoia.luno.capability.ocr.pp

/**
 * rec 模型宽度档位的自动选择：同一行文字用“刚好整行放得下”的最小模型，避免无谓切段。
 *
 * 例（模型高 48）：320 宽的行 → 320；500 宽的行 → 640；1600 宽的行在三档都放不下 →
 * 用最宽的 960 切段。纯函数，JVM 单测锁定选择口径。
 */
internal object RecModelChoice {

    /**
     * @param lineWidth 文字行在源图里的宽度（px）
     * @param lineHeight 文字行在源图里的高度（px）
     * @param modelHeight 候选 rec 模型的输入高度（v6 三档都是 48）
     * @param widths 设备上已安装的 rec 输入宽度（320/640/960，可只装其中几档）
     * @return 选中的输入宽度；[widths] 为空时返回 null
     */
    fun pick(
        lineWidth: Int,
        lineHeight: Int,
        modelHeight: Int,
        widths: List<Int>,
    ): Int? {
        if (lineWidth <= 0 || lineHeight <= 0 || modelHeight <= 0) return null
        val sorted = widths.filter { it > 0 }.distinct().sorted()
        if (sorted.isEmpty()) return null
        // 整行按比例缩到模型高度后不超过模型宽度即“放得下”（用 Long 防极端行宽溢出）。
        return sorted.firstOrNull { width ->
            lineWidth.toLong() * modelHeight <= width.toLong() * lineHeight
        } ?: sorted.last()
    }
}
