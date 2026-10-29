package hunoia.luno.extract.backend

import hunoia.luno.extract.ExtractBackend
import hunoia.luno.extract.ExtractBackendKind
import hunoia.luno.extract.ExtractRequest
import hunoia.luno.extract.ExtractResult

/**
 * OCR 取词后端（占位）：覆盖图片 / 视频里没有 a11y 节点的文字。
 *
 * 现状：未接入 OCR 引擎（如 ML Kit Text Recognition），[isAvailable] 恒为 false，
 * 走 [ExtractResult.Unsupported]。接入时在此处拿当前屏幕截图（[android.view.Surface]/`PixelCopy`）
 * 喂给识别器，把结果回填到 [ExtractResult.Text.screenshotPath]。
 *
 * 不引入 SelectToSpeak 复用：其底层与本项目自动化 / 读屏争抢同一套 a11y 会话，
 * MIUI 上同 TIC 踩雷（见 ADR-0003 替代方案）。
 */
class OcrExtractBackend : ExtractBackend {

    override val kind: ExtractBackendKind = ExtractBackendKind.OCR

    override fun isAvailable(): Boolean = false

    override suspend fun extract(request: ExtractRequest): ExtractResult =
        ExtractResult.Unsupported("ocr_backend_not_available")
}
