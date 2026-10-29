package hunoia.luno.extract

/**
 * 取词结果三态（与动作执行三态同口径：成功 / 不支持 / 失败）。
 *
 * - [Text]：取到文字，[screenshotPath] 仅在 OCR 后端回填（a11y 后端不截图）。
 * - [Unsupported]：该坐标 / 该来源取不到（如 a11y 无文字节点、OCR 后端不可用）。
 * - [Failed]：取词链路本身出错（如无障碍根窗口拿不到）。
 */
sealed interface ExtractResult {
    data class Text(
        val text: String,
        val sourcePackage: String?,
        val screenshotPath: String? = null,
    ) : ExtractResult

    data class Unsupported(val reason: String) : ExtractResult
    data class Failed(val reason: String) : ExtractResult
}
