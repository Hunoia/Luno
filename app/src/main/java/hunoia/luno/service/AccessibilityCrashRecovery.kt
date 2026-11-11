package hunoia.luno.service

import android.content.Context
import hunoia.luno.core.AppContext
import hunoia.luno.core.Paths
import hunoia.luno.domain.system.EnableAccessibilityUseCase
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 无障碍服务进程崩溃后的自动恢复。
 *
 * 崩溃链路：`:service` 进程崩溃 → 系统重启该进程 → `App.onCreate`（:service 进程）执行。
 * 系统重启进程不等于自动重连无障碍连接（MIUI 尤其如此，连接断了要手动重开）。
 * 这里在进程重启（或主进程被拉起）时检测「最近有崩溃」→ 用 Shizuku 授予的
 * WRITE_SECURE_SETTINGS 写回 token 强制系统重绑，免去手动重开。
 *
 * 崩溃信号源是 [hunoia.luno.core.CrashHandler] 落盘的 crashlog（最新一条在文件开头，
 * 带 `Time: yyyy-MM-dd HH:mm:ss`）；本类不新增标记文件，避免多进程写冲突。
 */
object AccessibilityCrashRecovery {

    private const val TAG = "AccessibilityCrashRecovery"

    /** 崩溃恢复窗口：超过则视为「旧崩溃」，不强制重绑（避免开机很久后误触发）。 */
    private const val RECENT_WINDOW_MS = 5 * 60 * 1000L

    private const val TIME_PREFIX = "Time: "
    private const val TIME_FORMAT = "yyyy-MM-dd HH:mm:ss"

    /**
     * 在主进程 / `:service` 进程的 [android.app.Application.onCreate] 调用。
     * 只在可能承载无障碍连接（主进程拉起或 :service 崩溃后重启）的进程自愈，
     * 其余进程（如 debug:service 之外的分进程）直接跳过。
     */
    fun tryRecover(context: Context) {
        val app = context.applicationContext
        val processName = runCatching { android.app.Application.getProcessName() }
            .getOrNull() ?: return
        val isServiceProcess = processName.endsWith(":service")
        val isMainProcess = processName == app.packageName
        if (!isServiceProcess && !isMainProcess) return

        val scope = AppContext.applicationScope ?: return
        scope.launch(Dispatchers.IO) {
            if (!ServiceAutoStart.readMasterSwitchEnabled(app)) return@launch
            val lastCrashMs = readLastCrashTimestampMs() ?: return@launch
            if (System.currentTimeMillis() - lastCrashMs > RECENT_WINDOW_MS) return@launch
            // 总闸仍开且最近崩过：Shizuku 权限写回 token 强制重绑无障碍服务。
            val ok = EnableAccessibilityUseCase.enable(app, forceRebind = true)
            hunoia.luno.core.LunoLog.i(
                TAG,
                "crash recovery: process=$processName rebind=$ok",
            )
        }
    }

    /** 读 crashlog 最新一条的 `Time:` 时间戳（最新 entry 在文件开头）。 */
    private fun readLastCrashTimestampMs(): Long? {
        val file = File("${Paths.AppCache}/crash/crashlog")
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        val timeLine = text.lineSequence().firstOrNull { it.startsWith(TIME_PREFIX) } ?: return null
        val raw = timeLine.removePrefix(TIME_PREFIX).trim()
        return runCatching {
            LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(TIME_FORMAT))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
