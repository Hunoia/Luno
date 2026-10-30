package hunoia.luno.runtime.overlay.text

/**
 * 识屏取词的纯数据模型与几何算法。
 *
 * 这里不出现 Android `Rect` / `AccessibilityNodeInfo`，方便 JVM 单测；
 * Android 读取层（`A11yTextSnapshotReader`）负责把节点转成这些模型。
 */

/** 屏幕像素矩形；右/下为开区间，和 Android `Rect` 的 `contains` 口径一致。 */
data class SnapshotRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Int get() = width * height
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun contains(x: Int, y: Int, tolerance: Int = 0): Boolean =
        x >= left - tolerance && x <= right + tolerance &&
            y >= top - tolerance && y <= bottom + tolerance

    fun intersects(other: SnapshotRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun intersection(other: SnapshotRect): SnapshotRect? {
        val l = maxOf(left, other.left)
        val t = maxOf(top, other.top)
        val r = minOf(right, other.right)
        val b = minOf(bottom, other.bottom)
        return if (l < r && t < b) SnapshotRect(l, t, r, b) else null
    }

    fun union(other: SnapshotRect): SnapshotRect = SnapshotRect(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )

    companion object {
        fun of(l: Int, t: Int, r: Int, b: Int): SnapshotRect = SnapshotRect(
            minOf(l, r),
            minOf(t, b),
            maxOf(l, r),
            maxOf(t, b),
        )
    }
}

/** 单个字符的屏幕包围盒；[text] 只用于调试/测试，真正取子串按索引走 [TextLeaf.text]。 */
data class TextChar(
    val text: String,
    val bounds: SnapshotRect,
)

/** 一个文本叶子：节点真实 text + 可选逐字坐标。 */
data class TextLeaf(
    val text: String,
    val bounds: SnapshotRect,
    val chars: List<TextChar> = emptyList(),
    val packageName: String = "",
    val windowId: Int = -1,
    val fromContentDescription: Boolean = false,
    val isPassword: Boolean = false,
) {
    val hasCharGeometry: Boolean get() = chars.isNotEmpty()
}

/** 图片类节点：长按只出图片卡，不参与文本取词。 */
data class ImageLeaf(
    val bounds: SnapshotRect,
    val packageName: String = "",
    val windowId: Int = -1,
)

/** 一次按下时抓到的界面文本快照。 */
data class TextSnapshot(
    val leaves: List<TextLeaf> = emptyList(),
    val images: List<ImageLeaf> = emptyList(),
    val capturedAtMs: Long = 0L,
    val targetPackage: String = "",
    val targetWindowIds: Set<Int> = emptySet(),
    /** false = 抓取超时/预算用尽，只保证已抓到的部分可用。 */
    val complete: Boolean = true,
) {
    val isEmpty: Boolean get() = leaves.isEmpty() && images.isEmpty()

    fun imageAt(x: Int, y: Int, tolerance: Int = 0): ImageLeaf? =
        images
            .filter { it.bounds.contains(x, y, tolerance) }
            .minByOrNull { it.bounds.area }

    companion object {
        val Empty = TextSnapshot()
    }
}

/** 长按默认全选上限：超过则只取落点所在行。 */
const val SELECT_ALL_MAX_CHARS = 2_000

object TextSnapshotOps {

    /**
     * 触点命中的文本叶子。
     *
     * 优先用逐字坐标（最精确）；同一位置多个叶子时取面积最小的（子节点通常比父容器小）。
     * 没有字符坐标时退回叶子 bounds。
     */
    fun hitText(snapshot: TextSnapshot, x: Int, y: Int, tolerancePx: Int = 0): TextLeaf? {
        val charHits = snapshot.leaves.filter { leaf ->
            leaf.chars.any { it.bounds.contains(x, y, tolerancePx) }
        }
        if (charHits.isNotEmpty()) return charHits.minByOrNull { it.bounds.area }

        val boundsHits = snapshot.leaves.filter { it.bounds.contains(x, y, tolerancePx) }
        return boundsHits.minByOrNull { it.bounds.area }
    }

    /**
     * 长按默认行为：全选命中叶子；超过 [maxChars] 时只取落点所在行。
     *
     * 没有逐字坐标且文本超长时，优先取第一行；仍超长才硬截断，避免把整篇文章塞进幽灵卡。
     */
    fun selectAll(
        leaf: TextLeaf,
        x: Int,
        y: Int,
        maxChars: Int = SELECT_ALL_MAX_CHARS,
    ): String? {
        val text = leaf.text
        if (text.isBlank()) return null
        if (text.length <= maxChars) return text.trim().takeIf { it.isNotEmpty() }

        if (leaf.chars.size == text.length) {
            lineAt(leaf, x, y)?.let { line ->
                return line.trim().takeIf { it.isNotEmpty() }?.take(maxChars)
            }
        }

        // 没有可用逐字坐标：按换行取第一行；单行超长再硬截断。
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        return (if (firstLine.isNotBlank()) firstLine else text)
            .trim()
            .take(maxChars)
            .takeIf { it.isNotEmpty() }
    }

    /** 选区裁字：有逐字坐标时按字符 Rect 求交，否则按叶子相交比例回退。 */
    fun clipRegion(
        snapshot: TextSnapshot,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): List<TextLeaf> {
        val selection = SnapshotRect.of(left, top, right, bottom)
        if (selection.isEmpty) return emptyList()
        val out = ArrayList<TextLeaf>()
        for (leaf in snapshot.leaves) {
            if (!leaf.bounds.intersects(selection)) continue
            if (leaf.chars.size == leaf.text.length && leaf.chars.isNotEmpty()) {
                out += clipLeafByChars(leaf, selection)
            } else if (intersectionRatio(leaf.bounds, selection) >= 0.2f) {
                out += leaf.copy(bounds = leaf.bounds.intersection(selection) ?: leaf.bounds)
            }
        }
        return dedupeAndOrder(out)
    }

    /** 选区对应的纯文本；多叶子按阅读顺序换行拼接。 */
    fun regionText(
        snapshot: TextSnapshot,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): String =
        clipRegion(snapshot, left, top, right, bottom)
            .mapNotNull { it.text.trim().takeIf(String::isNotEmpty) }
            .joinToString("\n")

    /** 阅读顺序：先按行（y/16）再按 x；同文本同位置去重。 */
    fun dedupeAndOrder(leaves: List<TextLeaf>): List<TextLeaf> {
        val seen = HashSet<String>()
        val out = ArrayList<TextLeaf>(leaves.size)
        for (leaf in leaves) {
            val key = "${leaf.text}|${leaf.bounds.left},${leaf.bounds.top}," +
                "${leaf.bounds.right},${leaf.bounds.bottom}"
            if (seen.add(key)) out += leaf
        }
        return out.sortedWith(
            compareBy({ it.bounds.top / 16 }, { it.bounds.left }, { it.bounds.top }),
        )
    }

    private fun clipLeafByChars(leaf: TextLeaf, selection: SnapshotRect): List<TextLeaf> {
        val hits = leaf.chars.indices.filter { leaf.chars[it].bounds.intersects(selection) }
        if (hits.isEmpty()) return emptyList()

        val out = ArrayList<TextLeaf>()
        var start = hits.first()
        var end = start
        for (index in hits.drop(1)) {
            if (index == end + 1) {
                end = index
            } else {
                addCharRun(leaf, start, end, out)
                start = index
                end = index
            }
        }
        addCharRun(leaf, start, end, out)
        return out
    }

    private fun addCharRun(leaf: TextLeaf, start: Int, end: Int, out: MutableList<TextLeaf>) {
        if (end < start) return
        val text = leaf.text.substring(start, end + 1)
        if (text.isBlank()) return
        var bounds = leaf.chars[start].bounds
        for (index in start + 1..end) bounds = bounds.union(leaf.chars[index].bounds)
        out += leaf.copy(
            text = text,
            bounds = bounds,
            chars = leaf.chars.subList(start, end + 1).toList(),
        )
    }

    private fun lineAt(leaf: TextLeaf, x: Int, y: Int): String? {
        val hit = leaf.chars.indexOfFirst { it.bounds.contains(x, y) }
            .takeIf { it >= 0 }
            ?: leaf.chars.indices.minByOrNull { index ->
                val b = leaf.chars[index].bounds
                val dx = when {
                    x < b.left -> b.left - x
                    x > b.right -> x - b.right
                    else -> 0
                }
                val dy = when {
                    y < b.top -> b.top - y
                    y > b.bottom -> y - b.bottom
                    else -> 0
                }
                dx + dy
            }
            ?: return null
        val target = leaf.chars[hit].bounds
        var start = hit
        var end = hit
        while (start > 0 && verticalOverlap(target, leaf.chars[start - 1].bounds) >= 0.5f) start--
        while (end < leaf.chars.lastIndex &&
            verticalOverlap(target, leaf.chars[end + 1].bounds) >= 0.5f
        ) {
            end++
        }
        return leaf.text.substring(start, end + 1)
    }

    private fun verticalOverlap(a: SnapshotRect, b: SnapshotRect): Float {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (overlap <= 0) return 0f
        val minHeight = minOf(a.height, b.height).coerceAtLeast(1)
        return overlap.toFloat() / minHeight
    }

    private fun intersectionRatio(leaf: SnapshotRect, selection: SnapshotRect): Float {
        val inter = leaf.intersection(selection) ?: return 0f
        if (leaf.area <= 0) return 0f
        return inter.area.toFloat() / leaf.area
    }
}
