package hunoia.luno.capability.qr

import android.graphics.Bitmap

/** 命中的条码种类（UI 文案/图标区分）。 */
enum class QrKind { QrCode, Barcode }

/** 一次识别的结果：原始解码文本 + 种类。 */
data class QrResult(
    val text: String,
    val kind: QrKind,
)

/**
 * 屏幕条码识别能力（实现见 [ZxingQrEngine]）。
 *
 * 契约只暴露纯数据，不泄漏 ZXing 类型；换后端时 feature 侧不改。
 * 输入 bitmap 为待识别区域像素，调用方负责从截图裁剪。
 */
interface QrEngine {

    /**
     * 识别 [bitmap] 中的二维码/条形码；未命中返回 null。
     *
     * 该方法是 suspend，实现内部自行切后台线程；调用方不得在主线程执行识别。
     */
    suspend fun detect(bitmap: Bitmap): QrResult?
}
