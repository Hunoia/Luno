package hunoia.luno.service

import android.app.Application
import android.content.Context
import hunoia.luno.bridge.system.AccessibilitySettings
import hunoia.luno.bridge.system.ProcessPresence
import hunoia.luno.bridge.system.hasWriteSecureSettingsPermission
import hunoia.luno.core.AppContext
import hunoia.luno.core.LunoLog as Log
import hunoia.luno.core.Paths
import hunoia.luno.domain.system.EnableAccessibilityUseCase
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 无障碍服务掉线后的自动恢复（写回 token 强制系统重绑）。
 *
 * 两条触发路径，动作与节流共用：
 *
 * - **最近崩溃**：`:service` 进程崩溃 → 系统重启进程（PID 变）但常不重连无障碍连接
 *   （MIUI 尤其如此），crashlog 是信号源（[hunoia.luno.core.CrashHandler] 落盘，最新一条在
 *   文件开头，带 `Time:`）。
 * - **实例掉线**：应用被划掉（force-stop）后，`:service` 进程连同无障碍实例一起被杀，
 *   实测 ROM 还会把本服务的 token 从 `ENABLED_ACCESSIBILITY_SERVICES` 里摘掉——所以
 *   「守护补 token」那时也已随 `:service` 一起被杀，没人写回，系统不会自己重新绑定。
 *   另一形态是 token 残留但实例没了，那必须摘除再写回才有效（单纯「补一遍」不产生 token
 *   变化，系统不重绑）。这两条正是「只有重开总开关才恢复」的原因：总开关链路写的就是
 *   这两步。
 *
 * 其余进程（非主进程、非 `:service`）直接跳过：只在与无障碍连接有关的进程里自愈。
 * `Application.onCreate` 天然每进程一次，即本类的节流。
 */
object AccessibilityRecovery {

    private const val TAG = "AccessibilityRecovery"

    /** 无障碍服务宿主进程后缀（见 AndroidManifest `android:process=":service"`）。 */
    private const val SERVICE_PROCESS_SUFFIX = ":service"

    /** 崩溃恢复窗口：超过则视为「旧崩溃」，不强制重绑（避免开机很久后误触发）。 */
    private const val RECENT_WINDOW_MS = 5 * 60 * 1000L

    private const val TIME_PREFIX = "Time: "
    private const val TIME_FORMAT = "yyyy-MM-dd HH:mm:ss"

    /** 恢复动作：不动 / 把 token 补回 / 摘除再写回强制重绑。 */
    internal enum class RecoveryAction { None, Reattach, ForceRebind }

    /**
     * 在主进程 / `:service` 进程的 [Application.onCreate] 调用。
     */
    fun tryRecover(context: Context) {
        val app = context.applicationContext
        val processName = runCatching { Application.getProcessName() }
            .getOrNull() ?: return
        val isServiceProcess = processName.endsWith(SERVICE_PROCESS_SUFFIX)
        val isMainProcess = processName == app.packageName
        if (!isServiceProcess && !isMainProcess) return

        val scope = AppContext.applicationScope ?: return
        scope.launch(Dispatchers.IO) {
            val masterSwitchOn = ServiceAutoStart.readMasterSwitchEnabled(app)
            val tokenPresent = isTokenPresent(app)
            val instanceOnline = instanceOnline(app, isServiceProcess)
            val action = decideRecovery(
                masterSwitchOn = masterSwitchOn,
                tokenPresent = tokenPresent,
                instanceOnline = instanceOnline,
                recentCrash = recentCrashTimestampMs() != null,
                hasWriteSecureSettings = app.hasWriteSecureSettingsPermission(),
            )
            if (action == RecoveryAction.None) {
                Log.i(
                    TAG,
                    "skip: process=$processName token=$tokenPresent instance=$instanceOnline",
                )
                return@launch
            }
            // 用 Shizuku 授予的 WRITE_SECURE_SETTINGS 把 token 补回（Reattach），或摘除再写回
            // （ForceRebind）让系统走一遍 unbind → bind，免去用户手动重开开关。
            val ok = EnableAccessibilityUseCase
                .enable(app, forceRebind = action == RecoveryAction.ForceRebind)
            Log.w(
                TAG,
                "recovery: process=$processName action=$action token=$tokenPresent " +
                    "instance=$instanceOnline ok=$ok",
            )
        }
    }

    /**
     * 该做哪种恢复。抽成纯函数便于单测。
     *
     * - 总闸关 / 无写权限 → 不动（用户不想开，或 `enable` 必失败）。
     * - 最近崩溃 → 强制重绑（崩溃后系统可能已摘 token）。
     * - **token 不在** → 把 token 补回。这是应用被划掉的最常见形态：force-stop 时 ROM 会把本
     *   服务的 token 从 `ENABLED_ACCESSIBILITY_SERVICES` 里摘掉，而「守护补 token」那时已随
     *   `:service` 一起被杀，没人写回。
     * - token 在位：实例明确不在 → 摘除再写回强制重绑；在线或判不准 → 不动（宁可不动，
     *   也不打断正常使用）。
     */
    internal fun decideRecovery(
        masterSwitchOn: Boolean,
        tokenPresent: Boolean,
        instanceOnline: Boolean?,
        recentCrash: Boolean,
        hasWriteSecureSettings: Boolean,
    ): RecoveryAction {
        if (!masterSwitchOn || !hasWriteSecureSettings) return RecoveryAction.None
        if (recentCrash) return RecoveryAction.ForceRebind
        if (!tokenPresent) return RecoveryAction.Reattach
        return if (instanceOnline == false) RecoveryAction.ForceRebind else RecoveryAction.None
    }

    /** 本服务的 token 是否还在 `ENABLED_ACCESSIBILITY_SERVICES` 里（含全局总闸关闭的残留）。 */
    private fun isTokenPresent(app: Context): Boolean {
        val current = runCatching {
            android.provider.Settings.Secure.getString(
                app.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull() ?: return false
        return AccessibilitySettings.tokenContains(
            current,
            android.content.ComponentName(app, SideGestureService::class.java),
        )
    }

    /**
     * 无障碍实例是否在线。`:service` 进程内读自己的服务引用（权威）；主进程只能看进程在不在
     * （进程在但服务未 onCreate 的窗口极短，且那时不重绑是无副作用的）。
     */
    private fun instanceOnline(app: Context, isServiceProcess: Boolean): Boolean? =
        if (isServiceProcess) {
            SideGestureService.current != null
        } else {
            ProcessPresence.isRunning(app, SERVICE_PROCESS_SUFFIX)
        }

    /** 最近 [RECENT_WINDOW_MS] 内有崩溃记录则返回其时间戳，否则 null。 */
    private fun recentCrashTimestampMs(): Long? {
        val lastCrashMs = readLastCrashTimestampMs() ?: return null
        return if (System.currentTimeMillis() - lastCrashMs <= RECENT_WINDOW_MS) lastCrashMs else null
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