package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 识屏 OCR 可用的文字体系。
 *
 * ML Kit 的中文模型同时覆盖拉丁字母（中英混排），因此没有单独的 Latin 项；
 * 日文/韩文各用各自模型，命中后再按脚本匹配度去重合并（见 `:capability:ocr`）。
 */
@Serializable
@Keep
enum class OcrScript {
    /** 中文 + 拉丁字母。 */
    Chinese,

    /** 日文（假名 + 汉字）。 */
    Japanese,

    /** 韩文（谚文）。 */
    Korean,
}
