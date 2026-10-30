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
                occluders = occluders,
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

        val snapshot = TextSnapshot(
            leaves = TextSnapshotOps.dedupeAndOrder(leaves),
            images = images.sortedBy { it.bounds.top / 16 }.distinctBy {
                "${it.bounds.left},${it.bounds.top},${it.bounds.right},${it.bounds.bottom}"
            },
            capturedAtMs = startedAtMs,
            targetPackage = targetPackage,
            targetWindowIds = targetIds,
            complete = complete,
        )
        if (BuildConfig.DEBUG) {
            Log.i(
                TAG,
                "a11y snapshot: leaves=${snapshot.leaves.size} images=${snapshot.images.size} " +
                    "nodes=${visited.value} complete=$complete pkg=$targetPackage down=($downX,$downY)",
            )
        }
        return snapshot
    }

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
        occluders: List<SnapshotRect>,
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
                // 图片类节点没有真实 text 时只按图片处理；带 text 的仍按文字
                //（部分聊天/容器会把文本挂在 ImageView 类节点上）。
                if (classLikeImage && nodeText.isNullOrEmpty()) {
                    images += ImageLeaf(bounds, packageName, windowId)
                    return false
                }
                val description = if (nodeText.isNullOrEmpty()) {
                    runCatching { node.contentDescription?.toString() }.getOrNull()
                } else {
                    null
                }
                val content = nodeText?.takeIf { it.isNotEmpty() }
                    ?: description?.takeIf { it.isNotEmpty() }
                if (!content.isNullOrEmpty() && content.length <= MAX_TEXT_CHARS) {
                    leaves += TextLeaf(
                        text = content,
                        bounds = bounds,
                        chars = readCharBounds(node, content.length),
                        packageName = packageName,
                        windowId = windowId,
                        fromContentDescription = nodeText.isNullOrEmpty(),
                        isPassword = runCatching { node.isPassword }.getOrDefault(false),
                    )
                }
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
                occluders = occluders,
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
