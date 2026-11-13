package hunoia.luno.capability.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ZXing 实现：纯本地解码，无 GMS/Google 依赖。
 *
 * 识别二维码（QR_CODE）+ 常用一维码（EAN-13/8、UPC-A/E、Code128/39/93、ITF）。
 * 一维码需要 [DecodeHintType.TRY_HARDER]；[DecodeHintType.POSSIBLE_FORMATS] 限定
 * 候选格式，避免把无关内容误判成条码。`detect` 只在后台线程执行。
 */
class ZxingQrEngine : QrEngine {

    private val formats = listOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
        BarcodeFormat.CODE_93,
        BarcodeFormat.ITF,
    )

    override suspend fun detect(bitmap: Bitmap): QrResult? = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        detectPixels(bitmap.width, bitmap.height, pixels)
    }

    /**
     * 纯 JVM 可测的核心逻辑：ARGB 像素数组 → 条码结果。
     * 未命中（[NotFoundException]）或尺寸非法返回 null。
     */
    internal fun detectPixels(width: Int, height: Int, pixels: IntArray): QrResult? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        return runCatching {
            val source = RGBLuminanceSource(width, height, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            reader.setHints(
                mapOf<DecodeHintType, Any>(
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.POSSIBLE_FORMATS to formats,
                ),
            )
            val result = reader.decode(binary)
            QrResult(
                text = result.text,
                kind = if (result.barcodeFormat == BarcodeFormat.QR_CODE) QrKind.QrCode else QrKind.Barcode,
            )
        }.getOrNull()
    }
}
