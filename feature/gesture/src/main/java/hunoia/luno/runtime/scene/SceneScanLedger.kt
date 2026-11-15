package hunoia.luno.runtime.scene

/**
 * 情景识别的发布记账（纯逻辑，便于 JVM 单测）。
 *
 * 只回答三件事：
 * - 这一屏是不是已经推送过（同屏去重，省掉重复的规则匹配与队列替换）；
 * - 空快照还要不要补扫（WebView / XWeb 刚打开时文本树常常是空的）；
 * - 用户离开界面后怎么复位（回到同一屏要能重新弹出）。
 *
 * **记账必须发生在推送之后**：扫描被新事件取消、快照为空、用户已经切走时都不能记成「已推送」，
 * 否则这一屏会被去重永久吃掉——表现就是「识别经常不弹」。
 */
internal class SceneScanLedger(
    private val maxEmptyRetries: Int = MAX_EMPTY_RETRIES,
) {

    var publishedPackage: String? = null
        private set
    var publishedKey: String? = null
        private set

    private var emptyRetryPackage: String? = null
    private var emptyRetries = 0

    fun isSameAsPublished(packageName: String, screenKey: String): Boolean =
        publishedPackage == packageName && publishedKey == screenKey

    /** 一次结果真的交出去了：更新同屏账，并结束这一屏的空快照补扫。 */
    fun onPublished(packageName: String, screenKey: String) {
        publishedPackage = packageName
        publishedKey = screenKey
        emptyRetryPackage = null
        emptyRetries = 0
    }

    /**
     * 快照为空 / 无文本：同一应用最多补扫 [maxEmptyRetries] 次。
     * 换应用重新计数，避免在 A 应用耗尽的次数把 B 应用的首扫也挡掉。
     */
    fun shouldRetryEmpty(packageName: String): Boolean {
        if (emptyRetryPackage != packageName) {
            emptyRetryPackage = packageName
            emptyRetries = 0
        }
        if (emptyRetries >= maxEmptyRetries) return false
        emptyRetries++
        return true
    }

    /** 用户离开这一屏（回桌面 / 锁屏 / 息屏 / 打开自家界面）：同屏账清零。 */
    fun forgetScreen() {
        publishedPackage = null
        publishedKey = null
        emptyRetryPackage = null
        emptyRetries = 0
    }

    companion object {
        /** 空快照补扫次数：页面加载完成通常在一次跟随窗口内。 */
        const val MAX_EMPTY_RETRIES = 2
    }
}
