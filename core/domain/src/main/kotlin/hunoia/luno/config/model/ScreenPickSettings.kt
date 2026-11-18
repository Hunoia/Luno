package hunoia.luno.config.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 识屏自身的参数（与触发方式无关）。
 *
 * 触发方式（长按 / 双指下滑 / …）已经迁到「识别区」的触发条目里
 * （[GlobalGestureTrigger]）；本模型只留“识别出来之后怎么跑 OCR”这类参数。
 *
 * - [ocrScripts]：框选 OCR 的结果标签。v6 单一模型覆盖中 / 日 / 韩 / 拉丁，
 *   脚本不再选模型，只作结果块标签；页面已不暴露选择，恒为默认中文。
 * - 框选后的动作菜单固定显示 OCR；长按取词不再触发 OCR。
 * - [portalReleaseMode]：幽灵卡松手未落在工具条动作上时的去向。
 */
@Serializable
@Keep
data class ScreenPickSettings(
    // 中文模型同时覆盖拉丁字母与数字（中英混排）；v6 单一模型下脚本仅作结果标签，
    // 页面不暴露选择，恒为默认中文。
    val ocrScripts: List<OcrScript> = listOf(OcrScript.Chinese),
    /**
     * 识别屏幕区域内的二维码/条形码：长按自动检测 + 框选动作条「二维码」按钮。
     * 关闭后两者都不跑，屏幕识别只保留 OCR/文字路径。
     */
    val enableQrRecognition: Boolean = true,
    /**
     * 幽灵卡松手没落在工具条动作上时的去向。
     *
     * 只影响「取消」这一条结局：命中动作一律执行并关层，与本节参数无关。
     */
    val portalReleaseMode: PortalReleaseMode = PortalReleaseMode.Cancel,
)

/**
 * 幽灵卡松手未命中工具条动作时的去向。
 *
 *「留在识屏页」保留半透明识屏层：可继续长按重新取词、拖动框选，点空白关层（与旧行为一致）。
 */
@Serializable
@Keep
enum class PortalReleaseMode {
    /** 撤卡并关层：一次手势一次结果（默认）。 */
    Cancel,

    /** 撤卡但保留识屏层，等用户继续取词 / 框选或点空白关层。 */
    StayInPicker,
}
