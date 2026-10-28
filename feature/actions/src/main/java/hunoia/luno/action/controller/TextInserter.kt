package hunoia.luno.action.controller

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import hunoia.luno.actions.R
import hunoia.luno.bridge.system.copyText
import hunoia.luno.core.LOG_TAG

/**
 * 把文本「拖到」屏幕某点，直接写进落点下（或当前焦点）的输入框。
 *
 * 默认**不碰剪贴板**：用 `ACTION_SET_TEXT` 在光标处插入（必要时用 `ACTION_SET_SELECTION`
 * 把光标移到插入末尾）。只有目标输入框不支持 SET_TEXT（WebView / 自定义编辑器、
 * 密码框）时，才退回「写剪贴板 + ACTION_PASTE」，并用 [Result.InsertedViaClipboard]
 * 告知调用方（UI 可以提示这次动了剪贴板）。
 *
 * 为什么不用系统拖放：跨应用拖放要求系统把 drop 事件派给悬浮窗**下面**的窗口，
 * 而网格面板是一整块全屏可触摸浮窗（不这么做就接不到卡片外的触摸），drop 会被
 * 它自己吞掉。所以输入只能走无障碍节点。
 *
 * 两个容易踩的点：
 * - **不能只信 `rootInActiveWindow`**：面板自己可获焦，active window 就是面板窗口，
 *   它下面的焦点查询永远落到本应用（结果是「明明有输入框却报告找不到」）。
 *   必须在应用窗口列表里逐窗查 `findFocus(FOCUS_INPUT)`。
 * - **焦点切换是异步的**：`ACTION_FOCUS` 发出后立刻写文本，目标往往还没拿到焦点。
 *   先等一拍再写。
 */
object TextInserter {

    private const val TAG = LOG_TAG

    enum class Result {
        /** 已直接写进输入框（没碰剪贴板）。 */
        Inserted,

        /** 目标只支持粘贴，已用剪贴板写入。 */
        InsertedViaClipboard,

        /** 落点下没有可编辑节点。 */
        NoTarget,

        /** 找到了输入框但写入失败。 */
        Failed,
    }

    /** 焦点切换后的等待：太短目标还没拿到焦点，太长用户能感受到延迟。 */
    private const val FocusSettleMs = 60L
    private const val PasteRetryMs = 40L

    /** 面板刚关掉时应用窗口要过一拍才重新变成可交互窗口：查找失败时重试几次。 */
    private const val FindAttempts = 4
    private const val FindRetryMs = 120L

    fun insertAt(service: AccessibilityService, text: String, x: Int, y: Int): Result {
        if (text.isEmpty()) return Result.Failed
        repeat(FindAttempts) { attempt ->
            val node = editableNodeAt(service, x, y)
                ?: focusedInput(service)
                ?: activeRootInput(service, x, y)
                ?: singleInputInTopWindow(service)
            if (node != null) {
                val bounds = Rect().also { runCatching { node.getBoundsInScreen(it) } }
                Log.i(
                    TAG,
                    "drag drop at ($x,$y): target pkg=${node.packageName} cls=${node.className} " +
                        "bounds=$bounds editable=${node.isEditable} focused=${node.isFocused} attempt=$attempt",
                )
                return writeText(service, node, text)
            }
            if (attempt < FindAttempts - 1) SystemClock.sleep(FindRetryMs)
        }
        Log.i(TAG, "drag drop at ($x,$y): no editable node, windows=${describeWindows(service)}")
        return Result.NoTarget
    }

    /** 先拿到焦点，然后优先直接写文本；不行才退回剪贴板粘贴。 */
    private fun writeText(service: AccessibilityService, node: AccessibilityNodeInfo, text: String): Result {
        if (!node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (!node.isFocused) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            // 焦点切换异步生效，等一拍再写。
            SystemClock.sleep(FocusSettleMs)
        }
        if (setTextInserting(node, text)) return Result.Inserted
        // 仅剩「目标只认粘贴」的编辑器（WebView/自定义）才动剪贴板。
        if (!copyText(service, service.getString(R.string.app_name), text)) return Result.Failed
        repeat(2) { attempt ->
            if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return Result.InsertedViaClipboard
            if (attempt == 0) SystemClock.sleep(PasteRetryMs)
        }
        Log.i(TAG, "drag drop: paste rejected after setText unsupported")
        return Result.Failed
    }

    /**
     * 在光标处插入文本（有选区则替换选区），再把光标移到插入内容之后。
     * 密码框读不到明文（`node.text` 是掩码），不走这条避免抹掉已输字符。
     */
    private fun setTextInserting(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isPassword) return false
        if (node.actionList.none {
                it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.id
            }
        ) {
            return false
        }
        // 占位提示词（hint）在部分 ROM / 编辑器里会直接出现在 `text` 里。
        // 直接拼进去会把「发送消息」「输入内容」这类提示一起写进正文，所以先认作空框。
        val rawText = node.text?.toString().orEmpty()
        val hint = node.hintText?.toString()
        val hintOnly = !hint.isNullOrEmpty() && rawText.trim() == hint.trim()
        val current = if (hintOnly) "" else rawText
        val selStart = node.textSelectionStart
        val selEnd = node.textSelectionEnd
        val hasSelection = !hintOnly && selStart >= 0 && selEnd >= selStart && selEnd <= current.length
        val insertAt = if (hasSelection) selStart else current.length
        val replaceEnd = if (hasSelection) selEnd else current.length
        val merged = current.substring(0, insertAt) + text + current.substring(replaceEnd)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok) {
            val caret = insertAt + text.length
            val selection = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        }
        Log.i(
            TAG,
            "drag drop: setText ok=$ok len=${merged.length} at=$insertAt " +
                "hintOnly=$hintOnly hint=${hint?.take(20)}",
        )
        return ok
    }

    /**
     * 落点下最深的输入节点。只看**应用窗口**（悬浮窗是 TYPE_APPLICATION_OVERLAY，
     * 不属于应用窗口），并跳过本应用自己的窗口（面板里的 Compose 也会报节点）。
     */
    private fun editableNodeAt(service: AccessibilityService, x: Int, y: Int): AccessibilityNodeInfo? {
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val ownPackage = service.packageName
        for (index in windows.indices.reversed()) {
            val window = windows[index] ?: continue
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = runCatching { window.root }.getOrNull() ?: continue
            if (root.packageName?.toString() == ownPackage) continue
            deepestInputContaining(root, x, y)?.let { return it }
        }
        return null
    }

    /**
     * 最后一个兵底：落点没命中、也没有焦点输入框时，若**最上层应用窗口只有一个**输入节点，
     * 就用它（常见于只有一个搜索框的应用）。多于一个就不猜，避免往错的地方写。
     */    private fun singleInputInTopWindow(service: AccessibilityService): AccessibilityNodeInfo? {
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val ownPackage = service.packageName
        val root = windows.asReversed().firstNotNullOfOrNull { window ->
            if (window?.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@firstNotNullOfOrNull null
            val r = runCatching { window.root }.getOrNull() ?: return@firstNotNullOfOrNull null
            if (r.packageName?.toString() == ownPackage) null else r
        } ?: return null
        val found = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty() && found.size < 2) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && isInputNode(node)) found.add(node)
            for (i in 0 until node.childCount) {
                runCatching { node.getChild(i) }.getOrNull()?.let(queue::add)
            }
        }
        return found.singleOrNull()
    }

    private fun describeWindows(service: AccessibilityService): String {
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        return windows.joinToString(prefix = "[", postfix = "]") { window ->
            val pkg = window?.root?.packageName?.toString()
            "${window?.type}:$pkg"
        }
    }

    /**
     * 当前激活窗口（`rootInActiveWindow`）里的输入框：面板关掉后它通常就是目标应用。
     * 仍然跳过本应用窗口（面板残留/下次打开时）。
     */
    private fun activeRootInput(service: AccessibilityService, x: Int, y: Int): AccessibilityNodeInfo? {
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return null
        if (root.packageName?.toString() == service.packageName) return null
        return deepestInputContaining(root, x, y)
            ?: runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }
                .getOrNull()
                ?.takeIf { it.isVisibleToUser && isInputNode(it) }
    }

    /**
     * 当前焦点输入框：**逐个应用窗口**查 `findFocus(FOCUS_INPUT)`。
     * `service.rootInActiveWindow` 会被本面板自己的窗口顶掉，不能直接用。
     */
    private fun focusedInput(service: AccessibilityService): AccessibilityNodeInfo? {
        val windows = runCatching { service.windows }.getOrNull().orEmpty()
        val ownPackage = service.packageName
        for (index in windows.indices.reversed()) {
            val window = windows[index] ?: continue
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = runCatching { window.root }.getOrNull() ?: continue
            if (root.packageName?.toString() == ownPackage) continue
            val focused = runCatching {
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            }.getOrNull() ?: continue
            if (focused.isVisibleToUser && isInputNode(focused)) return focused
        }
        return null
    }

    /**
     * 输入节点判定不只靠 [AccessibilityNodeInfo.isEditable]：Compose/WebView 的输入框
     * 未必置这个标志，但都会声明 `ACTION_SET_TEXT`；原生的写 `EditText` 类名。
     */
    private fun isInputNode(node: AccessibilityNodeInfo): Boolean =
        node.isEditable ||
            node.actionList.any {
                it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.id
            } ||
            node.className?.toString()?.contains("EditText") == true

    /** 迭代遍历：取包含落点、可见且是输入节点的最深节点。 */
    private fun deepestInputContaining(
        root: AccessibilityNodeInfo,
        x: Int,
        y: Int,
    ): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestDepth = -1
        val bounds = Rect()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>().apply { add(root to 0) }
        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            bounds.setEmpty()
            runCatching { node.getBoundsInScreen(bounds) }
            val contains = bounds.contains(x, y)
            if (contains && depth >= bestDepth && node.isVisibleToUser && isInputNode(node)) {
                best = node
                bestDepth = depth
            }
            // 落点不在这个子树的范围里就不必往下找（bounds 是子树并集，粗筛即可）。
            if (!contains) continue
            for (i in 0 until node.childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                queue.add(child to (depth + 1))
            }
        }
        return best
    }
}
