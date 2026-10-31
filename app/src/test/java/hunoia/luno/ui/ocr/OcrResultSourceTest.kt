package hunoia.luno.ui.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 结果页默认来源：文本命中时 a11y 是现成的，OCR 才懒加载，不能卡在 OCR 上等模型。
 * 已经拿到 OCR 文本的路径（框选等）保持原来的默认 OCR 行为。
 */
class OcrResultSourceTest {

    @Test
    fun `text hit with lazy ocr defaults to a11y`() {
        assertEquals(
            ResultSource.A11y,
            defaultResultSource(ocrText = "", hasLazyOcr = true, a11yText = "你好"),
        )
    }

    @Test
    fun `eager ocr with both texts keeps ocr default`() {
        assertEquals(
            ResultSource.Ocr,
            defaultResultSource(ocrText = "OCR", hasLazyOcr = false, a11yText = "无障碍"),
        )
    }

    @Test
    fun `only a11y defaults to a11y`() {
        assertEquals(
            ResultSource.A11y,
            defaultResultSource(ocrText = "", hasLazyOcr = false, a11yText = "你好"),
        )
    }

    @Test
    fun `lazy ocr without a11y still starts on ocr`() {
        assertEquals(
            ResultSource.Ocr,
            defaultResultSource(ocrText = "", hasLazyOcr = true, a11yText = null),
        )
    }
}
