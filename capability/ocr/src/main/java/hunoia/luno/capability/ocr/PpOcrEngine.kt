package hunoia.luno.capability.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import hunoia.luno.capability.ocr.pp.PpOcrDetector
import hunoia.luno.capability.ocr.pp.PpOcrRecognizer
import hunoia.luno.config.model.OcrScript
import hunoia.luno.core.LOG_TAG
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * PP-OCRv5（LiteRT / Google AI Edge）本地离线识别实现。
 *
 * 管线移植自 google-ai-edge/litert-samples 的 `PpocrImageEngine`：检测（DBNet）+ 逐行
 * 识别（PPLCNetV3+SVTR+CTC）。模型为**用户导入的 host AOT 包**，运行时优先 Qualcomm NPU、
 * 失败降级 CPU（见 `PpOcrModelRunner`），完全离线。
 *
 * 模型目录与校验见 [OcrModelStore]；未导入 / 不完整时直接返回空结果，不做任何隐式下载。
 * 加载或推理失败时抛异常，由调用方（识屏层）按「无结果」处理。
 *
 * 多脚本：中文模型（zh rec）覆盖拉丁 / 西里尔 / CJK，韩文模型（ko rec）覆盖谚文；
 * 选中的脚本各自对同一检测框识别一次，再交给 [OcrText.merge] 按重合与脚本匹配度
 * 去重、定阅读顺序。
 */
class PpOcrEngine(private val context: Context) : OcrEngine {

    private companion object {
        /** rec 裁剪时上下留的行高比例（det 框贴得紧，必须留余量）。 */
        const val LINE_PAD_Y = 0.15f
        /** 左右留白（防首尾字被切）。 */
        const val LINE_PAD_X = 0.02f
        /** 源图里矮于该值的框直接丢（图标碎笔划）。 */
        const val MIN_LINE_HEIGHT_PX = 10
    }

    private val mutex = Mutex()
    private var detector: PpOcrDetector? = null
    private val recognizers = HashMap<OcrScript, PpOcrRecognizer>()

    override suspend fun recognize(bitmap: Bitmap, scripts: List<OcrScript>): OcrResult =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                if (bitmap.isRecycled) return@withContext OcrResult.Empty
                if (!OcrModelStore.isReady(context)) {
                    Log.w(LOG_TAG, "ppocr: 模型未就绪（未导入 AOT 包），返回空结果")
                    return@withContext OcrResult.Empty
                }
                val effective = scripts.ifEmpty { listOf(OcrScript.Chinese) }.distinct()
                val det = detector ?: openDetector().also { detector = it }
                val recs = effective.map { script ->
                    script to (recognizers[script] ?: openRecognizer(script).also {
                        recognizers[script] = it
                    })
                }

                val t0 = System.nanoTime()
                val lb = letterbox(bitmap)
                val rgb = bitmapToRgb(lb.image)
                // 正 / 反两遍检测取并集：暗色模式、彩底白字（微信绿气泡、蓝色气泡）只有反色
                // 那一遍才检得出来。det 单次 NPU ~1ms，这一遍几乎不增加端到端耗时。
                val boxes = unionBoxes(
                    PpOcrDetector.boxes(det.probMap(rgb)),
                    PpOcrDetector.boxes(det.probMap(invertRgb(rgb))),
                )
                val blocks = ArrayList<OcrBlock>()
                for (b in boxes) {
                    val src = lb.toSource(b)
                    // 太矮的框多半是图标里的碎笔划，识别出来也是「`」「*」这种噪声。
                    if (src.y1 - src.y0 < MIN_LINE_HEIGHT_PX) continue
                    // 识别取图用**原图**：640 图上那一条文字只有十几像素高，放大到 48 高后
                    // 细节早没了（实测 CER 0.28 vs 从原图裁 0.07）。
                    val line = lineInput(bitmap, src)
                    for ((script, rec) in recs) {
                        val text = rec.recognize(line).trim()
                        if (text.isEmpty()) continue
                        blocks.add(
                            OcrBlock(
                                text = text,
                                left = src.x0,
                                top = src.y0,
                                right = src.x1,
                                bottom = src.y1,
                                script = script,
                            ),
                        )
                    }
                }
                // letterbox 的 640 位图每调用分配一张，用完即回收（lineInputs 内部各自回收）。
                lb.image.recycle()
                val merged = OcrText.merge(blocks)
                val ms = (System.nanoTime() - t0) / 1_000_000.0
                val totalChars = merged.blocks.sumOf { it.text.length }
                Log.i(
                    LOG_TAG,
                    "ppocr: 识别完成 boxes=${merged.blocks.size} " +
                        "字符=$totalChars 耗时=${ms}ms",
                )
                // release 会裁掉 Log.i，这里留一条 w：整段一个字都没认出来时能直接从 logcat 定位到
                //“没检出框”还是“框检出了但识别空”，不用再猜。
                if (merged.blocks.isEmpty()) {
                    Log.w(
                        LOG_TAG,
                        "ppocr: 本次无文字 检出框=${boxes.size} " +
                            "裁剪=${bitmap.width}x${bitmap.height}",
                    )
                }
                merged
            }
        }

    /** 预热：导入模型后由 `:service` 触发，确认 AOT 包可加载 / 可执行。失败只记日志。 */
    suspend fun warmUp() {
        val probe = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(probe).drawColor(Color.WHITE)
        try {
            val result = recognize(probe, listOf(OcrScript.Chinese))
            Log.i(LOG_TAG, "ppocr: 预热完成 blocks=${result.blocks.size}")
        } catch (failure: Throwable) {
            Log.w(LOG_TAG, "ppocr: 预热失败：${failure.message}")
        } finally {
            if (!probe.isRecycled) probe.recycle()
        }
    }

    /**
     * 模型包变了（导入 / 删除）：释放旧 native 句柄并重新预热。
     *
     * 拿 [mutex] 做，避免和正在跑的识别并发释放 buffer。
     */
    suspend fun reload() {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val d = detector
                detector = null
                recognizers.values.forEach { runCatching { it.close() } }
                recognizers.clear()
                runCatching { d?.close() }
            }
        }
        if (OcrModelStore.isReady(context)) warmUp()
    }

    override fun close() {
        val d = detector
        detector = null
        recognizers.values.forEach { runCatching { it.close() } }
        recognizers.clear()
        runCatching { d?.close() }
    }

    private fun openDetector(): PpOcrDetector {
        val file = File(OcrModelStore.modelDir(context), OcrModelStore.DET_MODEL)
        return PpOcrDetector(context, file)
    }

    private fun openRecognizer(script: OcrScript): PpOcrRecognizer {
        val dir = OcrModelStore.modelDir(context)
        val korean = script == OcrScript.Korean
        return PpOcrRecognizer(
            context = context,
            modelFile = File(dir, if (korean) OcrModelStore.KO_REC_MODEL else OcrModelStore.ZH_REC_MODEL),
            dictFile = File(dir, if (korean) OcrModelStore.KO_DICT else OcrModelStore.ZH_DICT),
        )
    }

    /** 把输入位图等比铺进 [PpOcrDetector.SIZE]×[PpOcrDetector.SIZE]，白底居中（letterbox）。 */
    private fun letterbox(src: Bitmap): Letterbox {
        val size = PpOcrDetector.SIZE
        val s = min(size.toFloat() / src.width, size.toFloat() / src.height)
        val nw = (src.width * s).toInt().coerceAtLeast(1)
        val nh = (src.height * s).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            val scaled = Bitmap.createScaledBitmap(src, nw, nh, true)
            drawBitmap(scaled, (size - nw) / 2f, (size - nh) / 2f, null)
            scaled.recycle()
        }
        return Letterbox(out, s, (size - nw) / 2f, (size - nh) / 2f, src.width, src.height)
    }

    private fun bitmapToRgb(bm: Bitmap): FloatArray {
        val n = bm.width * bm.height
        val px = IntArray(n)
        bm.getPixels(px, 0, bm.width, 0, 0, bm.width, bm.height)
        val out = FloatArray(n * 3)
        for (i in 0 until n) {
            val p = px[i]
            out[i * 3] = ((p shr 16) and 0xFF).toFloat()
            out[i * 3 + 1] = ((p shr 8) and 0xFF).toFloat()
            out[i * 3 + 2] = (p and 0xFF).toFloat()
        }
        return out
    }

    /**
     * 把框内的文字行转成 rec 的输入张量：按 **原图** 裁 + 上下留 15% 行高余量。
     *
     * 不做长行切段：实测 zh 模型对 18:1 的宽行整行识别是准的，切段反而在切口处
     * 多字/错字（“整整体”）。余量必须留：det 的框贴得紧，切掉字形上下沿后
     * 识别会在「蓝牙/医牙」之间抖。
     */
    private fun lineInput(source: Bitmap, box: PpOcrDetector.Box): FloatArray {
        val h = (box.y1 - box.y0).coerceAtLeast(1)
        val padY = (h * LINE_PAD_Y).toInt().coerceAtLeast(2)
        val padX = ((box.x1 - box.x0) * LINE_PAD_X).toInt().coerceAtLeast(2)
        val x0 = (box.x0 - padX).coerceIn(0, source.width - 1)
        val y0 = (box.y0 - padY).coerceIn(0, source.height - 1)
        val x1 = (box.x1 + padX).coerceIn(x0 + 1, source.width)
        val y1 = (box.y1 + padY).coerceIn(y0 + 1, source.height)
        return lineInput(source, x0, y0, x1 - x0, y1 - y0)
    }

    /** 裁 [x, y, w, h] → resize 到 H 高、宽按比例（≤W）→ 白底右补 → CHW 归一化。 */
    private fun lineInput(source: Bitmap, x: Int, y: Int, w: Int, h: Int): FloatArray {
        val crop = Bitmap.createBitmap(source, x, y, w.coerceAtLeast(1), h.coerceAtLeast(1))
        val nw = minOf((PpOcrRecognizer.H.toFloat() * w / h).toInt(), PpOcrRecognizer.W)
            .coerceAtLeast(1)
        val rz = Bitmap.createScaledBitmap(crop, nw, PpOcrRecognizer.H, true)
        val px = IntArray(nw * PpOcrRecognizer.H)
        rz.getPixels(px, 0, nw, 0, 0, nw, PpOcrRecognizer.H)
        val out = FloatArray(PpOcrRecognizer.H * PpOcrRecognizer.W * 3)
        for (yy in 0 until PpOcrRecognizer.H) {
            for (xx in 0 until nw) {
                val p = px[yy * nw + xx]
                val o = (yy * PpOcrRecognizer.W + xx) * 3
                out[o] = ((p shr 16) and 0xFF).toFloat()
                out[o + 1] = ((p shr 8) and 0xFF).toFloat()
                out[o + 2] = (p and 0xFF).toFloat()
            }
        }
        crop.recycle()
        rz.recycle()
        return out
    }

    private fun invertRgb(rgb: FloatArray): FloatArray {
        val out = FloatArray(rgb.size)
        for (i in rgb.indices) out[i] = 255f - rgb[i]
        return out
    }

    /** 两遍检测的框取并集（同一条文字在两次里位置差几像素，按 IoU 去重）。 */
    private fun unionBoxes(
        first: List<PpOcrDetector.Box>,
        second: List<PpOcrDetector.Box>,
    ): List<PpOcrDetector.Box> {
        if (second.isEmpty()) return first
        val merged = ArrayList(first)
        for (candidate in second) {
            val duplicate = merged.any { iou(it, candidate) >= 0.5f }
            if (!duplicate) merged.add(candidate)
        }
        return merged.sortedWith(compareBy({ it.y0 / 16 }, { it.x0 }))
    }

    private fun iou(a: PpOcrDetector.Box, b: PpOcrDetector.Box): Float {
        val interW = minOf(a.x1, b.x1) - maxOf(a.x0, b.x0)
        val interH = minOf(a.y1, b.y1) - maxOf(a.y0, b.y0)
        if (interW <= 0 || interH <= 0) return 0f
        val inter = interW.toFloat() * interH
        val areaA = (a.x1 - a.x0).toFloat() * (a.y1 - a.y0)
        val areaB = (b.x1 - b.x0).toFloat() * (b.y1 - b.y0)
        return inter / (areaA + areaB - inter)
    }

    /** letterbox 参数 + 640 空间框 → 原图坐标的逆映射。 */
    private class Letterbox(
        val image: Bitmap,
        private val scale: Float,
        private val offsetX: Float,
        private val offsetY: Float,
        private val srcW: Int,
        private val srcH: Int,
    ) {
        fun toSource(b: PpOcrDetector.Box): PpOcrDetector.Box {
            val x0 = ((b.x0 - offsetX) / scale).toInt().coerceIn(0, srcW)
            val y0 = ((b.y0 - offsetY) / scale).toInt().coerceIn(0, srcH)
            val x1 = ((b.x1 - offsetX) / scale).toInt().coerceIn(0, srcW)
            val y1 = ((b.y1 - offsetY) / scale).toInt().coerceIn(0, srcH)
            return PpOcrDetector.Box(x0, y0, x1, y1)
        }
    }
}
