package hunoia.luno.capability.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 引擎 round-trip 单测：ZXing 编码一张二维码 → 像素数组 → detectPixels → 解码。
 * 只用纯 JVM 类型（BitMatrix/IntArray），不依赖 android.graphics.Bitmap。
 */
class ZxingQrEngineTest {

    /** 生成二维码的 ARGB 像素数组（纯白背景黑模块）。 */
    private fun qrPixels(text: String, size: Int = 320): IntArray {
        val matrix: BitMatrix = MultiFormatWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val pixels = IntArray(size * size)
        for (x in 0 until size) {
            for (y in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return pixels
    }

    @Test
    fun `detects qr code content round trip`() {
        val text = "https://example.com/path?q=1"
        val pixels = qrPixels(text)
        val result = ZxingQrEngine().detectPixels(320, 320, pixels)
        assertNotNull(result)
        assertEquals(text, result?.text)
        assertEquals(QrKind.QrCode, result?.kind)
    }

    @Test
    fun `returns null on blank image`() {
        val pixels = IntArray(200 * 200) { 0xFFFFFFFF.toInt() }
        assertNull(ZxingQrEngine().detectPixels(200, 200, pixels))
    }
}
