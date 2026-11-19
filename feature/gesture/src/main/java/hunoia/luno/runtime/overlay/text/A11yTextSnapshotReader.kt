package hunoia.luno.runtime.overlay.text

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import hunoia.luno.core.LunoLog as Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import hunoia.luno.bridge.ui.DensityProvider
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

        /** 地址栏定向读取的预算：几次 id 查询，正常 <20ms。 */
        private const val ADDRESS_BAR_BUDGET_MS = 150L

        /** 地址栏可能不在最顶层窗口里，最多试几个应用窗口。 */
        private const val MAX_ADDRESS_WINDOWS = 3

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

        /**
         * 可能显示「当前页域名」的控件 id。
         *
         * 内置浏览器（微信 / QQ 的 XWeb）用框架布局的 `text2` 显示域名，浏览器类用自己的
         * 地址栏。这是定向读取，不做遍历，所以只能枚举常见 id；认不出来就当没有。
         */
        private val ADDRESS_BAR_IDS = listOf(
            "android:id/text2",
            "com.android.chrome:id/url_bar",
            "com.android.browser:id/url",
            "com.tencent.mtt:id/url",
            "com.UCMobile:id/url",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx:id/url_bar",
        )
    }

    /**
     * 抓一次快照。
     *
     * [textOnly] = 只收文本叶子（情景识别用）：不记图片 / 布局 / 逐字坐标，也不暂存节点引用。
     * 这些产物只有长按取词消费，但每个叶子的 `AccessibilityNodeInfo` 与 rect 都是实打实的分配，
     * 而本类跑在 `:service` 进程里，和胶囊 UI 共享堆 —— 省下来的分配就是少一次 GC 打在动画上。
     */
    fun capture(
        downX: Int,
        downY: Int,
        startedAtMs: Long = SystemClock.uptimeMillis(),
        timeBudgetMs: Long = TIME_BUDGET_MS,
        textOnly: Boolean = false,
        /**
         * 情景识别的事件包名（`ScreenSceneScanner` 传入）。非 null 时把目标限定为「事件对应
         * 应用」的窗口：全屏优先，浮在其上的小窗不进快照；该应用无全屏（分屏/纯小窗）时回退
         * 该应用窗口。长按取词 / 识屏传 null（按下点决定目标），不受影响。
         */
        sceneExpectedPackage: String? = null,
    ): TextSnapshot {
        val deadline = startedAtMs + timeBudgetMs
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        // window.root 是最贵的单次 IPC 之一：取一次就复用，别在过滤和遍历里各取一次。
        val appWindows = windows
            .asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }
            .mapNotNull { window ->
                val root = runCatching { window.root }.getOrNull() ?: return@mapNotNull null
                val pkg = runCatching { root.packageName?.toString() }.getOrNull().orEmpty()
                if (isExcludedPackage(pkg)) null else TargetWindow(window, root, pkg, window.layer)
            }
            .toList()

        // 情景识别（sceneExpectedPackage != null，由 ScreenSceneScanner 传入事件包名）的目标
        // 窗口集合：只识别「事件对应应用」的窗口，全屏优先。
        // - 该应用有全屏窗口：只遍历它的**全屏窗口集合**（可能有多个：闪屏/占位/全屏对话框 +
        //   主窗口。全部遍历，避免选中无文本的单个窗口而漏掉有文本的主窗口；这也正是“只识别
        //   全屏、排除小窗”的正确实现）。浮在其上的小窗（非全屏）不进快照。
        // - 该应用无全屏窗口（分屏双开 / 纯小窗）：只遍历该应用的窗口（回退——不再混入其他
        //   应用文本，targetPackage 恒 = expectedPackage，不触发 scan 的包名比对 drop）。
        // - 该应用已无窗口（用户已切走）：目标集合为空 → 放弃，与 scan 的包名比对一致。
        // sceneExpectedPackage == null（长按取词 / 识屏）时保持原逻辑：按下点所在窗口。
        val sceneTargets: List<TargetWindow>? = if (sceneExpectedPackage != null) {
            val pkgWindows = appWindows.filter { it.packageName == sceneExpectedPackage }
            if (pkgWindows.isEmpty()) {
                null
            } else {
                val fullscreen = pkgWindows.filter { entry ->
                    val r = Rect()
                    val ok = runCatching { entry.window.getBoundsInScreen(r) }.isSuccess
                    ok && isSceneFullscreen(
                        left = r.left,
                        top = r.top,
                        right = r.right,
                        bottom = r.bottom,
                        screenWidth = DensityProvider.screenWidthPx,
                        screenHeight = DensityProvider.screenHeightPx,
                    )
                }
                // 有全屏窗口只认全屏（排除小窗）；无全屏回退该应用全部窗口。
                fullscreen.ifEmpty { pkgWindows }
            }
        } else null

        // 目标窗口（单个）：情景识别取目标集合里 layer 最高的（语义同 targetPackage 兜底），
        // 长按取词取按下点所在、layer 最高的应用窗口。屏幕浮着其他应用小窗（画中画 / 悬浮窗）
        // 时，最顶层应用窗口是那层小窗而不是用户长按的背景应用：盲目取顶层会把 targetPackage
        // 定为小窗，且小窗节点树还先吃预算。按下点决定目标，避免「识别不到」。按下点不在任何
        // 应用窗口（状态栏/导航栏/空隙）时 target 为 null，回退遍历全部窗口。
        val target = sceneTargets?.firstOrNull()
            ?: if (downX < 0 && downY < 0) null
            else appWindows.firstOrNull { it.window.containsPoint(downX, downY) }

        val targetPackage = sceneTargets?.first()?.packageName
            ?: target?.packageName
            ?: appWindows.firstOrNull()?.packageName.orEmpty()
        val targetIds = if (sceneTargets != null) {
            sceneTargets.mapTo(HashSet()) { it.window.id }
        } else {
            appWindows
                .filter { it.packageName == targetPackage }
                .mapTo(HashSet()) { it.window.id }
        }

        val leaves = ArrayList<TextLeaf>()
        if (textOnly) leafNodes.clear()
        val images = if (textOnly) null else ArrayList<ImageLeaf>()
        val layoutRects = if (textOnly) null else ArrayList<SnapshotRect>()
        val visited = IntRef()
        var complete = true
        // 遮挡区预填全部应用窗口（含 layer），只对「layer 高于当前窗口」的窗口生效：
        // 小窗盖住目标应用的那块要排除，但目标窗口的叶子不能被更底层的全屏窗口误判为被盖住。
        // 预填而非遍历中累积，保证「目标窗口先遍历」时遮挡判定依然正确。
        val occluders = appWindows.mapNotNull { w ->
            val r = Rect()
            runCatching { w.window.getBoundsInScreen(r) }.getOrNull() ?: return@mapNotNull null
            if (r.width() <= 0 || r.height() <= 0) null
            else WindowOccluder(
                SnapshotRect(r.left, r.top, r.right, r.bottom),
                w.window.layer,
            )
        }

        // 目标窗口独占遍历预算：目标层先被完整读入，避免无关小窗（浮在上层、节点又多）把
        // 预算吃光，导致「目标区域根本没遍历到、识别不到内容」。非目标窗口不进快照（不在
        // 目标上），只以窗口 bounds 参与遮挡。
        val ordered = when {
            sceneTargets != null -> appWindows
            target != null -> listOf(target) + appWindows.filter { it !== target }
            else -> appWindows
        }

        for (entry in ordered) {
            if (sceneTargets != null) {
                // 情景识别：只遍历目标集合（全屏窗口 / 事件应用窗口），小窗等非目标不进快照。
                if (entry !in sceneTargets) continue
            } else if (target != null && entry !== target) {
                continue
            }
            val window = entry.window
            val root = entry.root
            if (visited.value >= MAX_NODES || SystemClock.uptimeMillis() > deadline) {
                complete = false
                break
            }
            val windowPackage = entry.packageName

            val windowRect = Rect()
            window.getBoundsInScreen(windowRect)
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
                windowLayer = window.layer,
                windowArea = windowRect.width() * windowRect.height(),
                collectLeafNodes = !textOnly,
            )
            if (timedOut) {
                complete = false
                break
            }
        }

        // 逐字坐标 pass：只为落点所在叶子补（长按取词最精确的路径），
        // 框选裁字在无字符坐标时走相交比例回退，仍然可用。
        if (!textOnly) {
            fillCharBounds(leaves, downX, downY, deadline + CHAR_BUDGET_MS)
        }

        // 布局兜底：按下点所在的可视节点 bounds（含容器），按面积升序去重。
        // 文字/图片都没命中时，取最小的那个裁截图出图片卡，空白处不再是“无内容”。
        val layouts = layoutRects
            ?.filter { it.contains(downX, downY) }
            ?.distinct()
            ?.sortedBy { it.area }
            ?.take(32)
            .orEmpty()

        val deduped = TextSnapshotOps.dedupeAndOrder(leaves)
        val snapshot = TextSnapshot(
            leaves = deduped,
            images = images
                ?.sortedBy { it.bounds.top / 16 }
                ?.distinctBy {
                    "${it.bounds.left},${it.bounds.top},${it.bounds.right},${it.bounds.bottom}"
                }
                .orEmpty(),
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
     * 只读「这一屏顶部那条地址 / 域名」的候选文本，给情景识别的链接对账用。
     *
     * 为什么不用 [capture]：整棵树是深度优先的，内置浏览器把标题栏排在整个 WebView 之后，
     * 而网页内容又慢又多（实测 347 个节点要 >500ms），带预算的遍历会在到达标题栏之前就
     * 超时——本方法用定向 id 查询绕开这个顺序问题，只花几次 IPC。
     *
     * 返回原样文本，形状（能不能当 host）由调用方判定，这里不猜。
     */
    fun readAddressBarTexts(
        startedAtMs: Long = SystemClock.uptimeMillis(),
        timeBudgetMs: Long = ADDRESS_BAR_BUDGET_MS,
    ): List<String> {
        if (ADDRESS_BAR_IDS.isEmpty()) return emptyList()
        val deadline = startedAtMs + timeBudgetMs
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        // 顶层窗口不一定是带地址栏那个（内置浏览器可能把网页与标题栏分在不同窗口），
        // 按层级从高到低试几个，凑够候选就停。
        val targets = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .filterNot {
                isExcludedPackage(runCatching { it.root?.packageName?.toString() }.getOrNull())
            }
            .sortedByDescending { it.layer }
            .take(MAX_ADDRESS_WINDOWS)
        val out = ArrayList<String>(ADDRESS_BAR_IDS.size)
        for (window in targets) {
            if (SystemClock.uptimeMillis() > deadline) break
            val root = runCatching { window.root }.getOrNull() ?: continue
            for (id in ADDRESS_BAR_IDS) {
                if (SystemClock.uptimeMillis() > deadline) break
                val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(id) }
                    .getOrNull()
                    .orEmpty()
                for (node in nodes) {
                    val text = runCatching { node.text?.toString() }.getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: continue
                    out += text
                }
            }
            if (out.isNotEmpty()) break
        }
        return out
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

    /** 一个待遍历的应用窗口：root 与包名一次取好（`window.root` 是贵调用）。 */
    private class TargetWindow(
        val window: AccessibilityWindowInfo,
        val root: AccessibilityNodeInfo,
        val packageName: String,
        val layer: Int,
    )

    /** 一个可能遮挡低层窗口的应用窗口：只在 layer 高于当前窗口时才遮挡。 */
    private class WindowOccluder(
        val bounds: SnapshotRect,
        val layer: Int,
    )

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
        images: MutableList<ImageLeaf>?,
        layoutRects: MutableList<SnapshotRect>?,
        occluders: List<WindowOccluder>,
        windowLayer: Int,
        windowArea: Int,
        collectLeafNodes: Boolean,
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
                // 只让 layer 高于当前窗口的窗口遮挡：小窗盖住目标应用的那块要排除，
                // 但目标窗口的叶子不能被更底层的全屏窗口误判为被盖住。
                cover.layer > windowLayer &&
                    bounds.left >= cover.bounds.left && bounds.top >= cover.bounds.top &&
                    bounds.right <= cover.bounds.right && bounds.bottom <= cover.bounds.bottom
            }
            if (!fullyCovered) {
                // 所有可视节点都记录布局 bounds（按下点命中的候选，供空白处截屏兜底）。
                layoutRects?.add(bounds)
                // 图片类节点一律按图片处理（长按取词只出图片卡，不把 alt/标签当文字）；
                // 普通节点 text 为空时回退 contentDescription。
                if (classLikeImage) {
                    images?.add(ImageLeaf(bounds, packageName, windowId))
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
                    if (collectLeafNodes) leafNodes += node
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
                windowLayer = windowLayer,
                windowArea = windowArea,
                collectLeafNodes = collectLeafNodes,
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

    /** 按下点是否落在该窗口的屏幕 bounds 内（选目标窗口的依据）。 */
    private fun AccessibilityWindowInfo.containsPoint(x: Int, y: Int): Boolean {
        val r = Rect()
        runCatching { getBoundsInScreen(r) }.getOrNull() ?: return false
        return r.width() > 0 && r.height() > 0 &&
            x >= r.left && x <= r.right && y >= r.top && y <= r.bottom
    }

    private fun isImageLike(className: String): Boolean =
        IMAGE_CLASS_HINTS.any { className.contains(it, ignoreCase = true) }

    private class IntRef(var value: Int = 0)
}

/**
 * 情景识别的「全屏窗口」判定（纯函数）：窗口 bounds 覆盖屏幕的大部分即视为全屏。
 *
 * 浮在全屏主窗口上的小窗（画中画 / 悬浮窗 / 分屏小块）面积明显小于屏幕，不满足；分屏双开
 * （各约 0.5 面积）也不满足。阈值 0.85 用整数比例（×100 vs ×85）避免浮点误差，也容忍全屏
 * 窗口因系统栏 / 沉浸式略小于屏幕的偏移。
 */
internal fun isSceneFullscreen(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    screenWidth: Int,
    screenHeight: Int,
): Boolean {
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) return false
    val screenArea = screenWidth.toLong() * screenHeight
    if (screenArea <= 0) return false
    val windowArea = width.toLong() * height
    return windowArea * 100 >= screenArea * 85
}
