package hunoia.luno.extract

/**
 * 一次取词请求：屏幕坐标（px）+ 来源包 + 是否优先 OCR。
 *
 * 坐标是触发侧的物理触点（长按落点）；取词后端据此在坐标处取文字。
 */
data class ExtractRequest(
    val x: Int,
    val y: Int,
    val sourcePackage: String? = null,
    val preferOcr: Boolean = false,
)
