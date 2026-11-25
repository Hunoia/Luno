package hunoia.luno.capability.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledOcrModelTest {

    @Test
    fun bundledRecWidthsAreSingleTier640() {
        assertEquals(listOf(640), BundledOcrModel.REC_WIDTHS)
    }

    @Test
    fun assetPathsPointIntoBundledDirWithKnownFilenames() {
        assertEquals(
            "ppocrv6_gpu/ppocrv6_small_det_640_fp32.tflite",
            BundledOcrModel.detAssetPath(),
        )
        assertEquals(
            "ppocrv6_gpu/ppocrv6_small_rec_640_fp32.tflite",
            BundledOcrModel.recAssetPath(640),
        )
        assertEquals("ppocrv6_gpu/characters.json", BundledOcrModel.dictAssetPath())
    }

    @Test
    fun recFilenameParsingMatchesBundledSet() {
        assertTrue(OcrModelStore.isRecFile("ppocrv6_small_rec_640_fp32.tflite"))
        assertEquals(640, OcrModelStore.recWidth("ppocrv6_small_rec_640_fp32.tflite"))
        assertFalse(OcrModelStore.isRecFile("ppocrv6_small_det_640_fp32.tflite"))
        assertEquals("ppocrv6_small_rec_640_fp32.tflite", OcrModelStore.recFileName(640))
    }
}
