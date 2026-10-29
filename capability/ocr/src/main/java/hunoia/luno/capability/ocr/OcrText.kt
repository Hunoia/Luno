package hunoia.luno.capability.ocr

import hunoia.luno.config.model.OcrScript

/**
 * OCR 结果的纯几何 / 文本处理：多脚本合并、阅读顺序、落点命中、区域命中。
 *
 * 不依赖 Android 类型，JVM 单测直接覆盖（见 `OcrTextTest`）。
 */
object OcrText {

    /** 同一块文字的判定阈值：交集至少占较小框的一半。 */
    private const val DuplicateOverlap = 0.5f

    /** 行内竖直重叠达到该比例视为同一行。 */
    private const val SameLineOverlap = 0.5f

    /** 去重 + 按阅读顺序排序。 */
    fun merge(blocks: List<OcrBlock>): OcrResult {
        val cleaned = blocks
            .filterNot { it.isEmpty }
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() }
        if (cleaned.size <= 1) return OcrResult(sorted(cleaned))

        val ranked = cleaned.sortedByDescending { score(it) }
        val accepted = ArrayList<OcrBlock>(ranked.size)
        for (block in ranked) {
            if (accepted.none { overlapsAsDuplicate(it, block) }) accepted += block
        }
        return OcrResult(sorted(accepted))
    }

    /** 阅读顺序：先上后下，同一行先左后右。 */
    fun sorted(blocks: List<OcrBlock>): List<OcrBlock> =
        blocks.sortedWith(compareBy({ it.top }, { it.left }))

    /**
     * 落点命中的文字块。
     *
     * 优先取包含触点（含 [tolerancePx] 容差）的块，多个时取面积最小者（最内层）；
     * 都不包含时取距触点最近、且距离不超过 [maxDistancePx] 的块。
     */
    fun blockAt(
        blocks: List<OcrBlock>,
        x: Int,
        y: Int,
        tolerancePx: Int,
        maxDistancePx: Int,
    ): OcrBlock? {
        if (blocks.isEmpty()) return null
        val containing = blocks.filter { contains(it, x, y, tolerancePx) }
        if (containing.isNotEmpty()) {
            return containing.minByOrNull { it.width.toLong() * it.height }
        }
        var best: OcrBlock? = null
        var bestDistance = Double.MAX_VALUE
        for (block in blocks) {
            val distance = distanceTo(block, x, y)
            if (distance <= maxDistancePx && distance < bestDistance) {
                bestDistance = distance
                best = block
            }
        }
        return best
    }

    /** 与给定矩形相交的文字块（按阅读顺序）。 */
    fun blocksIn(
        blocks: List<OcrBlock>,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): List<OcrBlock> = sorted(
        blocks.filter { it.left < right && it.right > left && it.top < bottom && it.bottom > top },
    )

    /** 把若干文字块按行合并成可复制文本；同行内用空格连接，行间用换行。 */
    fun join(blocks: List<OcrBlock>): String {
        if (blocks.isEmpty()) return ""
        val lines = ArrayList<MutableList<OcrBlock>>()
        for (block in sorted(blocks)) {
            val current = lines.lastOrNull()
            if (current != null && sameLine(current, block)) {
                current += block
            } else {
                lines += mutableListOf(block)
            }
        }
        return lines.joinToString("\n") { line ->
            line.sortedBy { it.left }.joinToString(" ") { it.text.trim() }
        }.trim()
    }

    // ---- 内部纯函数 ----

    /** 准确度粗排：文本越长、脚本字符占比越高，越可能是该语言模型的正解。 */
    private fun score(block: OcrBlock): Double {
        val length = block.text.count { !it.isWhitespace() }
        if (length == 0) return 0.0
        return length * (0.5 + scriptMatch(block.text, block.script))
    }

    private fun scriptMatch(text: String, script: OcrScript): Double {
        val letters = text.count { !it.isWhitespace() }
        if (letters == 0) return 0.0
        val matched = text.count { matches(it, script) }
        return matched.toDouble() / letters
    }

    private fun matches(char: Char, script: OcrScript): Boolean = when (script) {
        // 中文模型同时识别拉丁字母与数字，所以这里也计入，中英混排不会被日/韩模型抢走。
        OcrScript.Chinese -> char.isCjk() || char.isLetterOrDigit()
        OcrScript.Japanese ->
            char in '\u3040'..'\u309F' || // 平假名
                char in '\u30A0'..'\u30FF' || // 片假名
                char == '\u30FC' || // 长音
                char.isCjk()
        OcrScript.Korean ->
            char in '\uAC00'..'\uD7AF' || // 谚文音节
                char in '\u1100'..'\u11FF' || // 谚文字母
                char in '\u3130'..'\u318F' // 兼容字母
    }

    private fun Char.isCjk(): Boolean =
        this in '\u4E00'..'\u9FFF' ||
            this in '\u3400'..'\u4DBF' ||
            this in '\uF900'..'\uFAFF'

    private fun contains(block: OcrBlock, x: Int, y: Int, tolerance: Int): Boolean =
        x >= block.left - tolerance && x <= block.right + tolerance &&
            y >= block.top - tolerance && y <= block.bottom + tolerance

    private fun distanceTo(block: OcrBlock, x: Int, y: Int): Double {
        val dx = when {
            x < block.left -> (block.left - x).toDouble()
            x > block.right -> (x - block.right).toDouble()
            else -> 0.0
        }
        val dy = when {
            y < block.top -> (block.top - y).toDouble()
            y > block.bottom -> (y - block.bottom).toDouble()
            else -> 0.0
        }
        return kotlin.math.hypot(dx, dy)
    }

    private fun sameLine(line: List<OcrBlock>, block: OcrBlock): Boolean {
        val top = line.minOf { it.top }
        val bottom = line.maxOf { it.bottom }
        val overlap = minOf(bottom, block.bottom) - maxOf(top, block.top)
        val minHeight = minOf(bottom - top, block.height)
        return minHeight > 0 && overlap.toFloat() / minHeight >= SameLineOverlap
    }

    /**
     * 不同模型对同一行文字的分段方式不同（一个整体、一个拆成几块），
     * 2D IoU 会偏低。改按「横向 + 纵向重叠都超过较小边的一半」判定：
     * 同一行不同模型的块互相覆盖会归并，左右并排的不同文字不会误删。
     */
    private fun overlapsAsDuplicate(a: OcrBlock, b: OcrBlock): Boolean {
        val interW = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val interH = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (interW <= 0 || interH <= 0) return false
        val minW = minOf(a.width, b.width)
        val minH = minOf(a.height, b.height)
        if (minW <= 0 || minH <= 0) return false
        return interW.toFloat() / minW >= DuplicateOverlap &&
            interH.toFloat() / minH >= DuplicateOverlap
    }
}
