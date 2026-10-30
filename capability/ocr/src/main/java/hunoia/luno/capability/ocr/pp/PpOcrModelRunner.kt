package hunoia.luno.capability.ocr.pp

import android.content.Context
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
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
 * 模型来自用户导入的 host AOT 包（见 `OcrModelStore`）：AOT 图整图就是一个 NPU dispatch，
 * 只走 Qualcomm NPU（QNN / HTP）；加载失败直接抛异常，由上层按「无结果」处理。
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
         * 从 [file] 加载模型。AOT 包里已是针对 SM8750 预编译的 HTP context；
         * 非 AOT 的原始 tflite 会触发设备端编译，耗时且可能失败，由调用方保证包类型。
         */
        fun fromFile(context: Context, file: File): PpOcrModelRunner {
            require(file.isFile && file.length() > 0) { "模型文件不存在或为空：${file.name}" }
            // 只有 NPU 一条路：导入的是 host AOT 图（整图就是一个 NPU dispatch），
            // CPU 解释器跑不了它，降级过去只会更慢地失败。
            return runCatching { create(context, file) }
                .onSuccess { Log.i(LOG_TAG, "ppocr: ${file.name} 已加载（NPU）") }
                .onFailure { Log.w(LOG_TAG, "ppocr: ${file.name} NPU 加载失败：${it.message}") }
                .getOrThrow()
        }

        private fun create(context: Context, file: File): PpOcrModelRunner {
            var environment: Environment? = null
            try {
                environment = Environment.create(
                    context.applicationContext,
                    BuiltinNpuAcceleratorProvider(context.applicationContext),
                )
                val options = CompiledModel.Options(setOf(Accelerator.NPU, Accelerator.CPU)).apply {
                    qualcommOptions = CompiledModel.QualcommOptions(
                        logLevel = CompiledModel.QualcommOptions.LogLevel.WARN,
                        htpPerformanceMode =
                            CompiledModel.QualcommOptions.HtpPerformanceMode.HIGH_PERFORMANCE,
                        optimizationLevel =
                            CompiledModel.QualcommOptions.OptimizationLevel.HTP_OPTIMIZE_FOR_INFERENCE,
                    )
                }
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

    /** 读回输出张量；NPU 上这一步才真正等待计算完成。 */
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
