package hunoia.luno.capability.ocr.pp

import android.content.Context
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import hunoia.luno.core.LOG_TAG
import java.io.File

/**
 * LiteRT [CompiledModel] 的轻量生命周期包装（移植自 google-ai-edge/litert-samples）。
 *
 * 预分配一组输入 / 输出 buffer，close 时一并释放（[TensorBuffer] 也是 AutoCloseable，
 * 忘关会泄漏 native 内存）。构造是“全有或全无”：某 buffer 分配失败会回滚已建部分。
 *
 * 模型来自用户导入的 GPU 包（见 `OcrModelStore`）：原始 fp32 tflite，运行时经 GPU
 * delegate（OpenCL，Adreno）执行，delegate 不可用时回落 CPU（Accelerator.CPU 兜底）。
 */
class PpOcrModelRunner private constructor(
    private val model: CompiledModel,
    private val environment: Environment?,
) : AutoCloseable {

    val inputBuffers: List<TensorBuffer>
    val outputBuffers: List<TensorBuffer>

    init {
        var inputs: List<TensorBuffer>? = null
        try {
            inputs = model.createInputBuffers()
            inputBuffers = inputs
            outputBuffers = model.createOutputBuffers()
        } catch (failure: Throwable) {
            inputs?.forEach { runCatching { it.close() } }
            runCatching { model.close() }
            runCatching { environment?.close() }
            throw failure
        }
    }

    companion object {
        /**
         * 从 [file] 加载模型（原始 fp32 tflite，GPU 优先、CPU 兜底）。GPU 首次加载有
         * 设备端编译（warmup），delegate 初始化失败会自动回落 CPU。
         */
        fun fromFile(context: Context, file: File): PpOcrModelRunner {
            require(file.isFile && file.length() > 0) { "模型文件不存在或为空：${file.name}" }
            return runCatching { create(context, file) }
                .onSuccess { Log.i(LOG_TAG, "ppocr: ${file.name} 已加载（GPU/CPU）") }
                .onFailure { Log.w(LOG_TAG, "ppocr: ${file.name} 加载失败：${it.message}") }
                .getOrThrow()
        }

        private fun create(context: Context, file: File): PpOcrModelRunner {
            var environment: Environment? = null
            try {
                environment = Environment.create(context.applicationContext)
                val options = CompiledModel.Options(setOf(Accelerator.GPU, Accelerator.CPU))
                val model = CompiledModel.create(file.absolutePath, options, environment)
                return PpOcrModelRunner(model, environment)
            } catch (failure: Throwable) {
                runCatching { environment?.close() }
                throw failure
            }
        }
    }

    fun writeInput(index: Int, data: FloatArray) {
        inputBuffers[index].writeFloat(data)
    }

    fun run() {
        model.run(inputBuffers, outputBuffers)
    }

    /** 读回输出张量；这一步同步等待计算完成。 */
    fun readOutput(index: Int): FloatArray = outputBuffers[index].readFloat()

    @Volatile private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        outputBuffers.forEach { runCatching { it.close() } }
        inputBuffers.forEach { runCatching { it.close() } }
        runCatching { model.close() }
        runCatching { environment?.close() }
    }
}
