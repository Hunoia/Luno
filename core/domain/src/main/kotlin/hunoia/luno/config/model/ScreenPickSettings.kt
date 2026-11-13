package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 识屏自身的参数（与触发方式无关）。
 *
 * 触发方式（长按 / 双指下滑 / …）已经迁到「识别区」的触发条目里
 * （[GlobalGestureTrigger]）；本模型只留“识别出来之后怎么跑 OCR”这类参数。
 *
 * - [ocrScripts]：框选 OCR 要尝试的文字体系；多选时并行识别后合并。中文模型已覆盖拉丁字母。
 * - 框选后的动作菜单固定显示 OCR；长按取词不再触发 OCR。
 */
@Serializable
@Keep
data class ScreenPickSettings(
    // 中文模型同时覆盖拉丁字母与数字（中英混排），默认只跑它：多跑一份就多一份耗时，
    // 而日文映射到同一个 zh 模型（纯重复）、韩文模型对中文只会产出垃圾块再被去重丢掉。
    val ocrScripts: List<OcrScript> = listOf(OcrScript.Chinese),
    /**
     * 识别屏幕区域内的二维码/条形码：长按自动检测 + 框选动作条「二维码」按钮。
     * 关闭后两者都不跑，屏幕识别只保留 OCR/文字路径。
     */
    val enableQrRecognition: Boolean = true,
)
