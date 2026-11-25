package hunoia.luno.bridge.system

import android.app.ActivityManager
import android.content.Context

/**
 * 本应用的分进程是否在跑（跨进程视角）。
 *
 * 「服务掉线自愈」需要知道 `:service` 进程存不存在：应用被划掉（force-stop）后该进程
 * 会被杀、无障碍实例随之消失，而主进程读不到 `:service` 内的服务引用。
 *
 * 平台 API 收口在这里（架构 §8.2）：`getRunningAppProcesses` 已弃用但仍是本场景唯一
 * 能拿到的进程在不在的信号；拿不到就返回 null，调用方按「不确定」处理——宁可不动，
 * 也不在判不准时乱恢复。
 */
object ProcessPresence {

    /**
     * 进程名后缀（如 `:service`）对应的进程是否在跑。
     * @return true / false = 明确在 / 明确不在；null = 无法判定。
     */
    @Suppress("DEPRECATION")
    fun isRunning(context: Context, processSuffix: String): Boolean? {
        val manager = runCatching {
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        }.getOrNull() ?: return null
        val processes = runCatching { manager.getRunningAppProcesses() }.getOrNull() ?: return null
        val target = "${context.packageName}$processSuffix"
        return processes.any { it.processName == target }
    }
}