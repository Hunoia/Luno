package hunoia.luno.extract

/**
 * 取词判定策略：输入请求 + 候选后端，输出一个 [ExtractResult]。
 *
 * 与手势判定同属「Resolve 策略族」：共用输出契约（[ExtractResult]），不共用算法。
 * 默认实现 [DefaultExtractStrategy] 按可用性顺序选后端，a11y 优先、OCR 兜底。
 */
interface ExtractStrategy {
    suspend fun extract(request: ExtractRequest, backends: List<ExtractBackend>): ExtractResult
}

class DefaultExtractStrategy : ExtractStrategy {
    override suspend fun extract(
        request: ExtractRequest,
        backends: List<ExtractBackend>,
    ): ExtractResult {
        // 优先 OCR 时把 OCR 后端排到前面；否则按注入顺序（a11y 在前）。
        val ordered = if (request.preferOcr) {
            backends.sortedByDescending { it.kind == ExtractBackendKind.OCR }
        } else {
            backends
        }
        for (backend in ordered) {
            if (!backend.isAvailable()) continue
            when (val result = backend.extract(request)) {
                is ExtractResult.Text -> return result
                // 不支持 / 失败不阻断：换下一个可用后端再试。
                else -> continue
            }
        }
        return ExtractResult.Unsupported("no_available_backend")
    }
}
