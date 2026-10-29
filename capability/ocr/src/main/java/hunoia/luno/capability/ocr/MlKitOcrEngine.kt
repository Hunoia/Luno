package hunoia.luno.capability.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import hunoia.luno.config.model.OcrScript
import hunoia.luno.core.LOG_TAG
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ML Kit 文本识别实现（bundled 模型，离线）。
 *
 * - 识别器按脚本懒加载，首次使用多付一次初始化；之后常驻直到 [close]。
 * - 多脚本并行识别，再由 [OcrText.merge] 去重合并。
 * - 同一时刻只跑一组识别（[mutex]），避免连按把 CPU 放大。
 *
 * 注意：Luno 的识屏跑在 `:service` 独立进程，ML Kit 的自动初始化 ContentProvider
 * 不一定在该进程生效，因此首次识别前显式 [MlKit.initialize]。
 */
class MlKitOcrEngine(private val context: Context) : OcrEngine {

    /** 只在 [mutex] 内读写。 */
    private var initialized = false

    private val mutex = Mutex()

    /** 只在 [mutex] 内访问。 */
    private val recognizers = HashMap<OcrScript, TextRecognizer>()

    override suspend fun recognize(bitmap: Bitmap, scripts: List<OcrScript>): OcrResult =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                if (bitmap.isRecycled) return@withContext OcrResult.Empty
                // 注意：构造可能发生在 Service.attachBaseContext 之前，这里才取 applicationContext。
                if (!initialized) {
                    MlKit.initialize(context.applicationContext)
                    initialized = true
                }
                val effective = scripts.ifEmpty { listOf(OcrScript.Chinese) }.distinct()
                val image = InputImage.fromBitmap(bitmap, 0)
                val perScript = coroutineScope {
                    effective.map { script ->
                        async(Dispatchers.Default) {
                            runCatching { recognizeWith(script, image) }
                                .onFailure { Log.w(LOG_TAG, "ocr: $script failed: ${it.message}") }
                                .getOrDefault(emptyList())
                        }
                    }.awaitAll()
                }
                OcrText.merge(perScript.flatten())
            }
        }

    override fun close() {
        recognizers.values.forEach { runCatching { it.close() } }
        recognizers.clear()
    }

    private suspend fun recognizeWith(script: OcrScript, image: InputImage): List<OcrBlock> {
        val recognizer = recognizers.getOrPut(script) {
            TextRecognition.getClient(
                when (script) {
                    OcrScript.Chinese -> ChineseTextRecognizerOptions.Builder().build()
                    OcrScript.Japanese -> JapaneseTextRecognizerOptions.Builder().build()
                    OcrScript.Korean -> KoreanTextRecognizerOptions.Builder().build()
                },
            )
        }
        val text = recognizer.process(image).awaitResult()
        return text.toBlocks(script)
    }

    private fun Text.toBlocks(script: OcrScript): List<OcrBlock> = buildList {
        for (block in textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val content = line.text.trim()
                if (content.isEmpty()) continue
                add(
                    OcrBlock(
                        text = content,
                        left = box.left,
                        top = box.top,
                        right = box.right,
                        bottom = box.bottom,
                        script = script,
                    ),
                )
            }
        }
    }
}

/** ML Kit 的 `Task` 转协程；不引入 play-services-coroutines 依赖。 */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
}
