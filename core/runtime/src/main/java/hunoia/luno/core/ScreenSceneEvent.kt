package hunoia.luno.core

/**
 * 情景识别的一次结果：当前前台应用窗口的**无障碍文本**快照。
 *
 * 只由无障碍事件驱动（不轮询、不扫后台窗口、不做 OCR），生产者（手势侧）已做空闲防抖与
 * 同屏去重；消费方（建议胶囊）拿它跑规则与链接分发，产不出建议就什么都不做。
 *
 * [screenKey] 是「包名 + 文本」的指纹，只表示同一屏，不含原文；同屏再次扫描不会重复推送。
 * [text] 可能很长，日志与 [toString] 一律不得输出正文。
 */
data class ScreenSceneEvent(
    val text: String,
    val packageName: String,
    val screenKey: String,
    /** true = 抓取预算用尽，只保证已抓到的部分可用。 */
    val truncated: Boolean = false,
) {
    /** 防泄露：界面正文不进日志。 */
    override fun toString(): String =
        "ScreenSceneEvent(package=$packageName, key=$screenKey, chars=${text.length}, truncated=$truncated)"
}
