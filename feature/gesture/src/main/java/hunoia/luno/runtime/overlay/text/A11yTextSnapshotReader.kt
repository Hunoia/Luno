package hunoia.luno.runtime.overlay.text

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import hunoia.luno.core.LOG_TAG
import hunoia.luno.gesture.BuildConfig

/**
 * 把当前窗口树抓成 [TextSnapshot]。
 *
 * 对齐一墨的取词路径：
 * - 只读节点真实 text（contentDescription 仅作回退）；
 * - `refreshWithExtraData(DATA_TEXT_CHARACTER_LOCATION_KEY)` 拿逐字 Rect；
 * - 过滤 own package / 输入法 / 系统 UI；
 * - 只保留按下那一刻已存在的窗口，之后弹出的应用菜单不进入快照。
 */
class A11yTextSnapshotReader(
    private val service: AccessibilityService,
    private val ownPackage: String,
) {

    companion object {
        private const val TAG = LOG_TAG
        private const val MAX_DEPTH = 45
        private const val MAX_NODES = 1_500
        private const val MAX_TEXT_CHARS = 20_000
        private const val TIME_BUDGET_MS = 250L

        /**
         * 逐字坐标 pass 的独立预算。
         *
         * 不能和遍历共用：[refreshWithExtraData] 是每叶一次的同步 IPC，
         * 叶子多的界面（聊天流/信息流）会把遍历预算吃光，后面的节点整片丢失，
         * 表现为“界面越复杂越识别不到”。遍历只收叶子，字符坐标只给命中候选。
         */
        private const val CHAR_BUDGET_MS = 300L

        /** 命中候选（落点在叶内）最多补逐字坐标的叶子数，按面积从小到大。 */
        private const val MAX_CHAR_CANDIDATES = 8

        private val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "com.miui.systemui",
        )

        private val IMAGE_CLASS_HINTS = listOf(
            "ImageView",
            "ImageButton",
            "TextureView",
            "SurfaceView",
            "VideoView",
        )
    }

    fun capture(
        downX: Int,
        downY: Int,
        startedAtMs: Long = SystemClock.uptimeMillis(),
        timeBudgetMs: Long = TIME_BUDGET_MS,
    ): TextSnapshot {
        val deadline = startedAtMs + timeBudgetMs
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val appWindows = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .filterNot { isExcludedPackage(runCatching { it.root?.packageName?.toString() }.getOrNull()) }
            .sortedByDescending { it.layer }

        val target = appWindows.firstOrNull()
        val targetPackage = runCatching { target?.root?.packageName?.toString() }.getOrNull().orEmpty()
        val targetIds = appWindows.mapNotNull { window ->
            val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull()
            if (pkg == targetPackage) window.id else null
        }.toSet()

        val leaves = ArrayList<TextLeaf>()
        val images = ArrayList<ImageLeaf>()
        val layoutRects = ArrayList<SnapshotRect>()
        val visited = IntRef()
        var complete = true
        val occluders = ArrayList<SnapshotRect>()

        for (window in appWindows) {
            if (visited.value >= MAX_NODES || SystemClock.uptimeMillis() > deadline) {
                complete = false
                break
            }
            val root = runCatching { window.root }.getOrNull() ?: continue
            val windowPackage = runCatching { root.packageName?.toString() }.getOrNull().orEmpty()
            if (isExcludedPackage(windowPackage)) continue

            val windowRect = Rect()
            window.getBoundsInScreen(windowRect)
            val windowBounds = SnapshotRect(
                windowRect.left,
                windowRect.top,
                windowRect.right,
                windowRect.bottom,
            )
            val timedOut = walk(
                node = root,
                depth = 0,
                packageName = windowPackage,
                windowId = window.id,
                deadline = deadline,
                visited = visited,
                leaves = leaves,
                images = images,
                layoutRects = layoutRects,
                occluders = occluders,
                windowArea = windowRect.width() * windowRect.height(),
            )
            if (timedOut) {
                complete = false
                break
            }
            if (windowBounds.width > 0 && windowBounds.height > 0) {
                // 更高层的应用窗口作为遮挡区；完全落在里面的叶子不再产出。
                occluders += windowBounds
            }
        }

        // 逐字坐标 pass：只为落点所在叶子补（长按取词最精确的路径），
        // 框选裁字在无字符坐标时走相交比例回退，仍然可用。
        fillCharBounds(leaves, downX, downY, deadline + CHAR_BUDGET_MS)

        // 布局兜底：按下点所在的可视节点 bounds（含容器），按面积升序去重。
        // 文字/图片都没命中时，取最小的那个裁截图出图片卡，空白处不再是“无内容”。
        val layouts = layoutRects
            .filter { it.contains(downX, downY) }
            .distinct()
            .sortedBy { it.area }
            .take(32)

        val deduped = TextSnapshotOps.dedupeAndOrder(leaves)
        val snapshot = TextSnapshot(
            leaves = deduped,
            images = images.sortedBy { it.bounds.top / 16 }.distinctBy {
                "${it.bounds.left},${it.bounds.top},${it.bounds.right},${it.bounds.bottom}"
            },
            layouts = layouts,
            capturedAtMs = startedAtMs,
            targetPackage = targetPackage,
            targetWindowIds = targetIds,
            complete = complete,
        )
        if (BuildConfig.DEBUG) {
            Log.i(
                TAG,
                "a11y snapshot: leaves=${snapshot.leaves.size} " +
                    "charLeaves=${deduped.count { it.hasCharGeometry }} " +
                    "images=${snapshot.images.size} nodes=${visited.value} " +
                    "complete=$complete pkg=$targetPackage down=($downX,$downY)",
            )
        }
        return snapshot
    }

    /**
     * 给命中候选补逐字坐标：落点压在叶 bounds 内的叶子（容差同命中判定），
     * 按面积从小到大（更具体的叶子优先）最多 [MAX_CHAR_CANDIDATES] 个，
     * 超出 [deadline] 即停。
     */
    private fun fillCharBounds(leaves: MutableList<TextLeaf>, x: Int, y: Int, deadline: Long) {
        if (leafNodes.size != leaves.size) return
        val candidates = leaves.indices
            .filter { leaves[it].bounds.contains(x, y, tolerance = 8) }
            .sortedBy { leaves[it].bounds.area }
            .take(MAX_CHAR_CANDIDATES)
        for (index in candidates) {
            if (SystemClock.uptimeMillis() > deadline) return
            val node = leafNodes[index] ?: continue
            val chars = readCharBounds(node, leaves[index].text.length)
            if (chars.isNotEmpty()) leaves[index] = leaves[index].copy(chars = chars)
        }
        leafNodes.clear()
    }

    /** 与遍历同步填充：每个已收叶子对应的节点（供逐字坐标 pass 复用）。 */
    private val leafNodes = ArrayList<AccessibilityNodeInfo?>()

    /** @return true = 超时/预算用尽，快照不完整。 */
    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        packageName: String,
        windowId: Int,
        deadline: Long,
        visited: IntRef,
        leaves: MutableList<TextLeaf>,
        images: MutableList<ImageLeaf>,
        layoutRects: MutableList<SnapshotRect>,
        occluders: List<SnapshotRect>,
        windowArea: Int,
    ): Boolean {
        if (depth > MAX_DEPTH) return false
        if (visited.value >= MAX_NODES || SystemClock.uptimeMillis() > deadline) return true
        visited.value++

        val nodeBounds = Rect()
        runCatching { node.getBoundsInScreen(nodeBounds) }
        val bounds = SnapshotRect(
            nodeBounds.left,
            nodeBounds.top,
            nodeBounds.right,
            nodeBounds.bottom,
        )
        val visible = runCatching { node.isVisibleToUser }.getOrDefault(true)
        val nodeText = runCatching { node.text?.toString() }.getOrNull()
        val classLikeImage = isImageLike(runCatching { node.className?.toString() }.getOrNull().orEmpty())

        if (visible && !bounds.isEmpty) {
            val fullyCovered = occluders.any { cover ->
                bounds.left >= cover.left && bounds.top >= cover.top &&
                    bounds.right <= cover.right && bounds.bottom <= cover.bottom
            }
            if (!fullyCovered) {
                // 所有可视节点都记录布局 bounds（按下点命中的候选，供空白处截屏兜底）。
                layoutRects += bounds
                // 图片类节点一律按图片处理（长按取词只出图片卡，不把 alt/标签当文字）；
                // 普通节点 text 为空时回退 contentDescription。
                if (classLikeImage) {
                    images += ImageLeaf(bounds, packageName, windowId)
                }
                // 图片节点不回退 contentDescription（alt/标签不是屏上文字）；
                // 普通节点 text 为空时回退 CD，但仅限小块控件：全屏容器的 CD 是
                // 控件描述（如抖音播放器的“视频”），当文字会把整卡变成无意义标签。
                val content = nodeText?.takeIf { it.isNotEmpty() }
                    ?: if (!classLikeImage && bounds.area * 4 <= windowArea) {
                        runCatching { node.contentDescription?.toString() }.getOrNull()
                            ?.takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                if (!content.isNullOrEmpty() && content.length <= MAX_TEXT_CHARS) {
                    // 遍历阶段不取逐字坐标（每叶一次同步 IPC，会把预算吃光）；
                    // 节点先暂存，命中候选在 fillCharBounds 里按需补。
                    leafNodes += node
                    leaves += TextLeaf(
                        text = content,
                        bounds = bounds,
                        chars = emptyList(),
                        packageName = packageName,
                        windowId = windowId,
                        fromContentDescription = nodeText.isNullOrEmpty(),
                        isPassword = runCatching { node.isPassword }.getOrDefault(false),
                    )
                }
                // 图片节点若自身无 text，不深入子节点（与原行为一致：图片叶不再细扫）。
                if (classLikeImage && nodeText.isNullOrEmpty()) return false
            }
        }

        val childCount = runCatching { node.childCount }.getOrDefault(0)
        for (index in 0 until childCount) {
            if (visited.value >= MAX_NODES || SystemClock.uptimeMillis() > deadline) return true
            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
            val timedOut = walk(
                node = child,
                depth = depth + 1,
                packageName = packageName,
                windowId = windowId,
                deadline = deadline,
                visited = visited,
                leaves = leaves,
                images = images,
                layoutRects = layoutRects,
                occluders = occluders,
                windowArea = windowArea,
            )
            if (timedOut) return true
        }
        return false
    }

    private fun readCharBounds(node: AccessibilityNodeInfo, length: Int): List<TextChar> {
        if (length <= 0) return emptyList()
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, length)
        }
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        var refreshed = runCatching {
            node.refreshWithExtraData(key, args)
        }.getOrDefault(false)

        if (!refreshed) {
            // 部分应用要先给节点无障碍焦点，才会补齐逐字坐标。
            runCatching { node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) }
            refreshed = runCatching { node.refreshWithExtraData(key, args) }.getOrDefault(false)
        }
        if (!refreshed) return emptyList()

        val raw = runCatching { node.extras?.get(key) }.getOrNull() ?: return emptyList()
        val rects: List<Any?> = when (raw) {
            is Array<*> -> raw.toList()
            is List<*> -> raw
            else -> return emptyList()
        }
        if (rects.size != length) return emptyList()

        val out = ArrayList<TextChar>(length)
        for (item in rects) {
            val r = when (item) {
                is Rect -> SnapshotRect(item.left, item.top, item.right, item.bottom)
                is RectF -> SnapshotRect(
                    item.left.toInt(),
                    item.top.toInt(),
                    item.right.toInt(),
                    item.bottom.toInt(),
                )
                else -> SnapshotRect(0, 0, 0, 0)
            }
            out += TextChar("", r)
        }
        return out
    }

    private fun isExcludedPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == ownPackage) return true
        if (packageName in SYSTEM_UI_PACKAGES) return true
        // 输入法窗口即使被标成 application，也按包名兜底排除常见输入法。
        return packageName.contains("inputmethod", ignoreCase = true)
    }

    private fun isImageLike(className: String): Boolean =
        IMAGE_CLASS_HINTS.any { className.contains(it, ignoreCase = true) }

    private class IntRef(var value: Int = 0)
}
