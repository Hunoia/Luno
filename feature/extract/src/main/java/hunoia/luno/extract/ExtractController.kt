package hunoia.luno.extract

import hunoia.luno.runtime.RuntimeHost
import hunoia.luno.extract.backend.A11yExtractBackend
import hunoia.luno.extract.backend.OcrExtractBackend
import hunoia.luno.extract.overlay.ExtractResultOverlay

/**
 * 取词编排：策略选后端 → 取文字 → 弹结果浮层。
 *
 * 不放任何 `when(typeId)`、不碰窗口与启动细节（那是 [ExtractActions] / [ExtractResultOverlay] 的事）。
 * 触发侧（手势长按）只调 [extractAt]，结果走轻量浮层，不静默改剪贴板（架构 §7 / ADR-0003 §5）。
 */
class ExtractController(
    private val host: RuntimeHost,
    private val actions: ExtractActions,
    private val strategy: ExtractStrategy = DefaultExtractStrategy(),
    private val backends: List<ExtractBackend> = listOf(
        // 零提权优先；OCR 未接入（不可用），仅作兜底占位。
        A11yExtractBackend(host.accessibilityService),
        OcrExtractBackend(),
    ),
) {
    private var overlay: ExtractResultOverlay? = null

    suspend fun extractAt(
        x: Int,
        y: Int,
        sourcePackage: String? = null,
        preferOcr: Boolean = false,
    ): ExtractResult {
        val result = strategy.extract(ExtractRequest(x, y, sourcePackage, preferOcr), backends)
        when (result) {
            is ExtractResult.Text -> showOverlay(result)
            else -> closeOverlay()
        }
        return result
    }

    private fun showOverlay(result: ExtractResult.Text) {
        closeOverlay()
        overlay = ExtractResultOverlay(host, actions).apply { show(result) }
    }

    fun closeOverlay() {
        overlay?.close()
        overlay = null
    }
}
