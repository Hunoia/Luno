package hunoia.luno.capability.ocr

import android.graphics.Bitmap
import hunoia.luno.config.model.OcrScript

/**
 * 屏幕文字识别能力（默认实现见 [MlKitOcrEngine]）。
 *
 * 契约只暴露纯数据，不泄漏 ML Kit 类型；将来换 PaddleOCR 时 feature 侧不改。
 * 输入 bitmap 的包围盒坐标系即 bitmap 像素坐标，调用方负责换算成屏幕坐标
 *（`:feature:gesture` 统一走 `PickCapture.origin + crop`）。
 */
interface OcrEngine {

    /**
     * 识别 [bitmap] 中的文字。
     *
     * [scripts] 为待尝试的文字体系，空集合按 [OcrScript.Chinese] 处理；多选时实现可并行，
     * 返回前应完成去重合并。结果按阅读顺序排列。
     *
     * 该方法是 suspend，实现内部自行切后台线程；调用方不得在主线程执行识别。
     */
    suspend fun recognize(bitmap: Bitmap, scripts: List<OcrScript>): OcrResult

    /** 释放识别器。服务销毁时调用一次；调用后 [recognize] 仍会按需重新初始化。 */
    fun close()
}

/** 一块识别出的文字，坐标为 bitmap 像素坐标。 */
data class OcrBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val script: OcrScript,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val isEmpty: Boolean get() = text.isBlank() || width <= 0 || height <= 0
}

/** 一次识别的全部文字块（已去重、已按阅读顺序排序）。 */
data class OcrResult(val blocks: List<OcrBlock>) {

    val isEmpty: Boolean get() = blocks.isEmpty()

    companion object {
        val Empty = OcrResult(emptyList())
    }
}
