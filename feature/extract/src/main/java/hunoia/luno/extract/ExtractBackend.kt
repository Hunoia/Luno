package hunoia.luno.extract

/**
 * 取词后端：把「坐标」变成「文字」的一种实现。
 *
 * 后端是扩展点：零提权的 a11y 节点命中是一等实现，OCR（图片/视频文字）是另一等实现。
 * 两种后端产出同一份 [ExtractResult]，调用方（[ExtractStrategy]）只按可用性选择，
 * 不感知算法差异（架构 §9.1：`SearchSource` 之外的取词后端也算独立扩展点）。
 */
enum class ExtractBackendKind { A11Y, OCR }

interface ExtractBackend {
    val kind: ExtractBackendKind

    /** 当前环境是否可用（如 OCR 引擎未接入时恒 false）。 */
    fun isAvailable(): Boolean

    suspend fun extract(request: ExtractRequest): ExtractResult
}
