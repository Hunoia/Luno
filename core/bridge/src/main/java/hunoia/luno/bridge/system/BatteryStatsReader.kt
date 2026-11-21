package hunoia.luno.bridge.system

import android.content.Context
import hunoia.luno.core.LunoLog
import hunoia.luno.shizuku.ShizukuManager

/**
 * `dumpsys batterystats` 的耗电段抓取器。
 *
 * 只负责「拿到原始文本」，不做解析：解析是纯函数，在 `:feature:gridpanel` 的
 * `PowerStatsModel` 里，方便对着真机 fixture 单测。
 *
 * 为什么要 shell 侧过滤：
 *  - 完整 `dumpsys batterystats` 在这台机器上是 5541 行 / 约 300KB，而耗电段只有 501 行。
 *  - [ShizukuManager.executeShell] 的 `drain()` 会**读完全部输出**（必须读到 EOF，否则子进程
 *    会阻塞在满管道上），只保留前 `maxChars` 字符。不过滤的话每次刷新都白读几百 KB。
 *  - 因此命令里先 `grep -A` 出耗电段，再交给 maxChars 兜底。
 *
 * 统计窗口固定为 `--charged`（上次充满以来）。**不做 `--reset`**：那会清掉全系统统计，
 * 属于破坏性操作，不该由面板部件触发。
 */
class BatteryStatsReader(context: Context) {

    @Suppress("unused")
    private val appContext = context.applicationContext

    /**
     * 拉一次耗电段原文。失败（无 Shizuku / 命令失败 / 输出为空）返回 null，
     * 由调用方走 usage stats 降级路径。
     *
     * 内部走 `executeShell`（自带 IO 线程切换），但本方法本身是阻塞式设计，
     * 调用方仍应放在 IO 线程。
     */
    suspend fun readPowerSection(): String? {
        if (!ShizukuManager.currentStatus().isReady) return null
        val result = runCatching {
            ShizukuManager.executeShell(
                command = DUMP_POWER_COMMAND,
                maxChars = MAX_CHARS,
            )
        }.onFailure {
            LunoLog.w(LOG_TAG, "batterystats dump failed: ${it.message}")
        }.getOrNull() ?: return null

        val text = result.stdout
        if (!result.isSuccess && text.isBlank()) {
            LunoLog.w(LOG_TAG, "batterystats dump unsuccessful: ${result.errorMessage}")
            return null
        }
        if (text.isBlank() || !text.contains(POWER_SECTION_MARKER)) {
            LunoLog.w(LOG_TAG, "batterystats power section missing (len=${text.length})")
            return null
        }
        return text
    }

    private companion object {
        /**
         * `-A 520` 覆盖真机耗电段（501 行）并留余量：段长随装机应用数增长，
         * 520 行足够覆盖一两百个 uid，超出的尾部条目丢掉不影响排行。
         */
        const val DUMP_POWER_COMMAND =
            "dumpsys batterystats --charged | grep -A 520 'Estimated power use'"

        /** 段文本约 43KB；给 128KB 余量，避免 uid 多时被截断。 */
        const val MAX_CHARS = 128 * 1024

        const val POWER_SECTION_MARKER = "Estimated power use"

        const val LOG_TAG = "BatteryStats"
    }
}
