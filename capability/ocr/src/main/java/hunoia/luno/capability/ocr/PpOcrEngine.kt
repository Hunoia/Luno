package hunoia.luno.capability.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import hunoia.luno.capability.ocr.pp.PpOcrDetector
import hunoia.luno.capability.ocr.pp.PpOcrRecognizer
import hunoia.luno.capability.ocr.pp.RecModelChoice
import hunoia.luno.config.model.OcrScript
import hunoia.luno.core.LOG_TAG
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * PP-OCRv6（LiteRT / Google AI Edge）本地离线识别实现。
 *
 * 管线移植自 google-ai-edge/litert-samples 的 `PpocrImageEngine`：检测（DBNet）+ 逐行
 * 识别（CTC）。模型为**用户导入的 GPU 包**（原始 fp32 tflite），运行时经 GPU delegate
 * （OpenCL，Adreno）执行、失败降级 CPU（见 `PpOcrModelRunner`），完全离线。
 *
 * 模型目录与校验见 [OcrModelStore]；未导入 / 不完整时直接返回空结果，不做任何隐式下载。
 * 加载或推理失败时抛异常，由调用方（识屏层）按「无结果」处理。
 *
 * v6 用单一模型 + 统一 50 语言字符表，覆盖中文 / 日文 / 韩文 / 拉丁等；请求的脚本
 * 只作为结果块的标签，不再选不同模型。
 */
class PpOcrEngine(private val context: Context) : OcrEngine {

    private companion object {
        /** 源图里矮于该值的框直接丢（图标碎笔划）。 */
        const val MIN_LINE_HEIGHT_PX = 10

        /** 同时常驻的 rec 模型上限：320/640 两档足够覆盖常见屏幕，960 用时再换入。 */
        const val MAX_RESIDENT_RECOGNIZERS = 2
    }

    private val mutex = Mutex()
    private var detector: PpOcrDetector? = null

    /** 已加载的 rec 模型，按输入宽度索引；访问顺序即 LRU（最多常驻两档，防显存堆积）。 */
    private val recognizers = LinkedHashMap<Int, PpOcrRecognizer>(4, 0.75f, true)
    private var cachedRecFiles: List<Pair<Int, File>>? = null

    override suspend fun recognize(bitmap: Bitmap, scripts: List<OcrScript>): OcrResult =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                if (bitmap.isRecycled) return@withContext OcrResult.Empty
                if (!OcrModelStore.isReady(context)) {
                    Log.w(LOG_TAG, "ppocr: 模型未就绪（未导入 GPU 包），返回空结果")
                    return@withContext OcrResult.Empty
                }
                val script = scripts.firstOrNull() ?: OcrScript.Chinese
                val det = detector ?: openDetector().also { detector = it }
                if (recModelFiles().isEmpty()) {
                    Log.w(LOG_TAG, "ppocr: 没有可用的 rec 模型，返回空结果")
                    return@withContext OcrResult.Empty
                }

                val t0 = System.nanoTime()
                val lb = letterbox(bitmap)
                val rgb = bitmapToRgb(lb.image)
                // 正 / 反两遍检测取并集：暗色模式、彩底白字（微信绿气泡、蓝色气泡）只有反色
                // 那一遍才检得出来。det 单次几 ms，这一遍几乎不增加端到端耗时。
                val boxes = unionBoxes(
                    PpOcrDetector.boxes(det.probMap(rgb)),
                    PpOcrDetector.boxes(det.probMap(invertRgb(rgb))),
                )
                val blocks = ArrayList<OcrBlock>()
                // 诊断：每档模型实际用了几行、总共切了几段，方便真机确认动态选档生效。
                val recUse = LinkedHashMap<Int, Int>()
                var segmentCount = 0
                for (b in boxes) {
                    val src = lb.toSource(b)
                    // 太矮的框多半是图标里的碎笔划，识别出来也是「`」「*」这种噪声。
                    if (src.y1 - src.y0 < MIN_LINE_HEIGHT_PX) continue
                    // 识别取图用**原图**：640 图上那一条文字只有十几像素高，放大到 48 高后
                    // 细节早没了（实测 CER 0.28 vs 从原图裁 0.07）。宽高比超过模型输入
                    // （recW/recH）的宽行按原图切段（2 个字高重叠），不能整体压扁。
                    val height = src.y1 - src.y0
                    val width = src.x1 - src.x0
                    if (width < 1) continue
                    // 按行宽选模型：短行 320、宽行自动升到 640/960，放不下才切段。
                    val rec = recognizerForLine(width, height) ?: continue
                    val recW = rec.inputWidth
                    val recH = rec.inputHeight
                    recUse[recW] = (recUse[recW] ?: 0) + 1
                    val segments = PpOcrRecognizer.lineSegments(
                        width = width,
                        height = height,
                        modelWidth = recW,
                        modelHeight = recH,
                    )
                    segmentCount += segments.size
                    val inputs = segments.map { seg ->
                        lineInput(bitmap, src.x0 + seg.x0, src.y0, seg.x1 - seg.x0, height, recW, recH)
                    }
                    run {
                        // 拼接去重：本段开头 [dropSrc] 由上一段完整看到；本段结尾 [dropTailSrc]
                        // 只有半截字，交给下一段完整识别（末段不切尾）。两条合起来保证边界处
                        // 每个字恰好由一侧完整出字，既不多字也不吃字。
                        // CTC 时间步线性映射回输入宽度：x ≈ (step+0.5) × recW / T。
                        val last = inputs.lastIndex
                        val text = buildString {
                            inputs.forEachIndexed { j, input ->
                                val seg = segments[j]
                                val headX = seg.dropSrc * recH.toFloat() / height
                                val tailX = seg.dropTailSrc * recH.toFloat() / height
                                val segWidthX = (seg.x1 - seg.x0) * recH.toFloat() / height
                                rec.recognizeDetailed(input).forEach { ch ->
                                    val afterHead = ch.x >= headX
                                    val beforeTail = j == last || ch.x <= segWidthX - tailX
                                    if (afterHead && beforeTail) append(ch.text)
                                }
                            }
                        }.trim()
                        if (text.isNotEmpty()) {
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
                }
                // letterbox 的 640 位图每调用分配一张，用完即回收（lineInputs 内部各自回收）。
                lb.image.recycle()
                val merged = OcrText.merge(blocks)
                val ms = (System.nanoTime() - t0) / 1_000_000.0
                val totalChars = merged.blocks.sumOf { it.text.length }
                val recDesc = recUse.entries
                    .joinToString("/") { (w, n) -> "${w}×$n" }
                    .ifEmpty { "无" }
                Log.i(
                    LOG_TAG,
                    "ppocr: 识别完成 boxes=${merged.blocks.size} " +
                        "字符=$totalChars 耗时=${ms}ms rec=$recDesc 切段=$segmentCount",
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

    /** 预热：导入模型后由 `:service` 触发，确认 GPU 包可加载 / 可执行。失败只记日志。 */
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
                releaseRecognizers()
                runCatching { d?.close() }
            }
        }
        if (OcrModelStore.isReady(context)) warmUp()
    }

    override fun close() {
        val d = detector
        detector = null
        releaseRecognizers()
        runCatching { d?.close() }
    }

    private fun releaseRecognizers() {
        recognizers.values.forEach { runCatching { it.close() } }
        recognizers.clear()
        cachedRecFiles = null
    }

    private fun openDetector(): PpOcrDetector {
        val file = File(OcrModelStore.modelDir(context), OcrModelStore.DET_MODEL)
        return PpOcrDetector(context, file)
    }

    /** 目录里已安装的 rec 档位（宽度 → 文件），按宽度升序；缓存到下次 reload。 */
    private fun recModelFiles(): List<Pair<Int, File>> {
        cachedRecFiles?.let { return it }
        val dir = OcrModelStore.modelDir(context)
        return OcrModelStore.recFiles(dir)
            .mapNotNull { file -> OcrModelStore.recWidth(file.name)?.let { it to file } }
            .sortedBy { it.first }
            .also { cachedRecFiles = it }
    }

    /**
     * 按行宽挑一个可用模型：优先“刚好放得下”的最小档；加载失败就换下一档，
     * 全部失败返回 null（这一行跳过，不拖垮整屏识别）。
     */
    private fun recognizerForLine(lineWidth: Int, lineHeight: Int): PpOcrRecognizer? {
        val files = recModelFiles()
        var candidates = files.map { it.first }
        while (candidates.isNotEmpty()) {
            val width = RecModelChoice.pick(
                lineWidth = lineWidth,
                lineHeight = lineHeight,
                modelHeight = PpOcrRecognizer.H,
                widths = candidates,
            ) ?: return null
            val file = files.firstOrNull { it.first == width }?.second ?: return null
            try {
                return recognizerFor(width, file)
            } catch (failure: Throwable) {
                Log.w(LOG_TAG, "ppocr: rec($width) 加载失败，换下一档：${failure.message}")
                candidates = candidates.filter { it != width }
            }
        }
        return null
    }

    private fun recognizerFor(width: Int, file: File): PpOcrRecognizer {
        recognizers[width]?.let { return it }
        if (recognizers.size >= MAX_RESIDENT_RECOGNIZERS) {
            // 访问顺序即 LRU：换出最久未用的那一档，避免三档模型同时占显存。
            val iterator = recognizers.entries.iterator()
            if (iterator.hasNext()) {
                val evicted = iterator.next()
                iterator.remove()
                runCatching { evicted.value.close() }
            }
        }
        return openRecognizer(file).also { recognizers[width] = it }
    }

    private fun openRecognizer(file: File): PpOcrRecognizer = PpOcrRecognizer(
        context = context,
        modelFile = file,
        dictFile = File(OcrModelStore.modelDir(context), OcrModelStore.DICT),
    )

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
     * 裁 [x, y, w, h] → resize 到 [targetH] 高、宽按比例（≤[targetW]）→ 白底右补 → CHW 归一化。
     *
     * 尺寸取自模型自身（320 / 640 / 960 三档），不写死。
     */
    private fun lineInput(
        source: Bitmap,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        targetW: Int,
        targetH: Int,
    ): FloatArray {
        val crop = Bitmap.createBitmap(source, x, y, w.coerceAtLeast(1), h.coerceAtLeast(1))
        val nw = minOf((targetH.toFloat() * w / h).toInt(), targetW).coerceAtLeast(1)
        val rz = Bitmap.createScaledBitmap(crop, nw, targetH, true)
        val px = IntArray(nw * targetH)
        rz.getPixels(px, 0, nw, 0, 0, nw, targetH)
        val out = FloatArray(targetH * targetW * 3)
        for (yy in 0 until targetH) {
            for (xx in 0 until nw) {
                val p = px[yy * nw + xx]
                val o = (yy * targetW + xx) * 3
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
