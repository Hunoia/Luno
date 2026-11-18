package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 识屏 OCR 可用的文字体系。
 *
 * PP-OCRv6 用**单一模型**（统一 50 语言字符表）覆盖中文 / 日文 / 韩文 / 拉丁，
 * 脚本只作为结果块的标签（用于去重打分），不再选不同模型。
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
