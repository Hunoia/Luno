package hunoia.luno.extract.backend

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import hunoia.luno.extract.ExtractBackend
import hunoia.luno.extract.ExtractBackendKind
import hunoia.luno.extract.ExtractRequest
import hunoia.luno.extract.ExtractResult

/**
 * 零提权取词后端：复用无障碍服务的节点树，在坐标处取「最深、带文字」的节点。
 *
 * 不引入新的权限 / 系统能力：无障碍服务本来就在跑（动作面板、子手势、指针都依赖它），
 * 这里只是多问一句「这个点下面有什么文字」。来源包名直接来自根窗口，天然能过
 * 一墨那种「白/黑名单」过滤（见 ADR-0003 §5 后续）。
 *
 * 现状：返回该坐标命中的最深文字节点整段文字，不是严格分词后的单词；
 * 长按选区展开留待后续（命中节点 → `ACTION_PREPARE_FOR_SELECTION` 等）。
 */
class A11yExtractBackend(
    private val service: AccessibilityService,
) : ExtractBackend {

    override val kind: ExtractBackendKind = ExtractBackendKind.A11Y

    override fun isAvailable(): Boolean = true

    override suspend fun extract(request: ExtractRequest): ExtractResult {
        val root = service.rootInActiveWindow ?: return ExtractResult.Failed("no_root_window")
        try {
            val best = BestText()
            collectTextAt(root, request.x, request.y, depth = 0, best = best)
            val text = best.text
            if (text.isBlank()) return ExtractResult.Unsupported("no_text_at_point")
            return ExtractResult.Text(text = text.trim(), sourcePackage = root.packageName?.toString())
        } finally {
            root.recycle()
        }
    }

    /** DFS：只访问含坐标的节点子树，记录最深的非空文字（text 优先于 contentDescription）。 */
    private fun collectTextAt(
        node: AccessibilityNodeInfo,
        x: Int,
        y: Int,
        depth: Int,
        best: BestText,
    ) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) return
        val text = node.text?.toString().orEmpty().trim()
        val description = node.contentDescription?.toString().orEmpty().trim()
        val candidate = text.ifBlank { description }
        if (candidate.isNotBlank() && depth > best.depth) {
            best.depth = depth
            best.text = candidate
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextAt(child, x, y, depth + 1, best)
            child.recycle()
        }
    }

    private class BestText {
        var depth: Int = -1
        var text: String = ""
    }
}
