package hunoia.luno.capability.notify

import hunoia.luno.core.LunoLog
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.runBlocking

/**
 * XMSF 鉴权绕过门：发焦点通知前经 OEM_DENY_3 防火墙链切断 `com.xiaomi.xmsf` 网络，
 * 使 SystemUI 的焦点通知鉴权失败放行；投递 + 校验窗口结束后恢复。
 *
 * 机制依据（2026-12 真机实测，HyperOS 4 / Android 17，设备 192.168.3.69）：
 * - `ConnectivityManager.updateAurogonUidRule`（aurogon 规则）**无效**：挡不住 XMSF 已建立的
 *   MiPush 长连接，鉴权恢复网络后 ~250ms 完成并失败 → 岛被撤回（220ms/5s/30s 三档一致）。
 * - OEM_DENY_3 链（bpf）**有效**：切断含已建立连接在内的全部流量。与 InstallerX-Revived
 *   `DefaultPrivilegedService.setPackageNetworkingEnabled` 同机制；Shell 等价命令
 *   `cmd connectivity set-package-networking-enabled` 由 shell uid 执行，Shizuku shell
 *   （adb 同权）即可，无需 bind hook。实测 `onAuthSuccess` + 岛持续存活。
 *
 * 链状态礼貌恢复：arm 时记录 `get-chain3-enabled` 原值；若链原本关闭则由本次临时开启、
 * restore 时关回；原本开启则保持开启（避免意外放行链内其他被禁包，与 InstallerX 口径一致）。
 *
 * 调用约定：调用方保证串行（发布器进程锁）；本类自身加锁兜底。
 * 主线程禁止调用（内含阻塞 shell 调用）。恢复失败视为本次发布失败，并保持降级态：
 * 后续 `block()` 会先尝试恢复，恢复不成功前不开启新事务（不留全局断网）。
 */
internal class XmsfGate(
    private val probe: IslandProbe,
) {

    private var chainWasEnabled: Boolean? = null
    private var degraded = false

    /**
     * 切断 XMSF 网络。返回 false = 切断失败（Shizuku 不可用 / 命令失败），
     * 调用方应放弃绕过直接走回退链。
     */
    @Synchronized
    fun block(): Boolean {
        if (degraded) {
            // 上次恢复失败：先补恢复，成功前不允许开新事务。
            if (!restore()) return false
            degraded = false
        }
        val pkg = probe.xmsfPackage ?: return false
        if (!ShizukuManager.currentStatus().isReady) {
            LunoLog.w(LOG_TAG, "xmsf gate block failed: shizuku not ready")
            return false
        }
        val wasEnabled = shell("cmd connectivity get-chain3-enabled")
            ?.contains("true") ?: return false
        if (!wasEnabled) {
            val enabled = shell("cmd connectivity set-chain3-enabled true")
            if (enabled == null) {
                LunoLog.w(LOG_TAG, "xmsf gate block failed: cannot enable chain3")
                return false
            }
        }
        val denied = shell("cmd connectivity set-package-networking-enabled false $pkg")
        val ok = denied?.contains("Disabled") == true
        if (!ok) {
            // 本次临时开的链必须关回去，否则链状态被永久改动。
            if (!wasEnabled) shell("cmd connectivity set-chain3-enabled false")
            LunoLog.w(LOG_TAG, "xmsf gate block failed: deny output=$denied")
            return false
        }
        chainWasEnabled = wasEnabled
        LunoLog.i(LOG_TAG, "xmsf gate blocked pkg=$pkg (chain pre-enabled=$wasEnabled)")
        return true
    }

    /**
     * 恢复 XMSF 网络。返回 false = 恢复失败（发布方必须把本次发布判为失败，
     * 并停止后续发布直到恢复成功——不留全局断网状态）。
     */
    @Synchronized
    fun restore(): Boolean {
        val pkg = probe.xmsfPackage ?: return false
        val allow = shell("cmd connectivity set-package-networking-enabled true $pkg")
            ?.contains("Enabled") == true
        val chainOk = if (chainWasEnabled == false) {
            shell("cmd connectivity set-chain3-enabled false") != null
        } else {
            true
        }
        val ok = allow && chainOk
        if (ok) {
            chainWasEnabled = null
            degraded = false
            LunoLog.i(LOG_TAG, "xmsf gate restored pkg=$pkg")
        } else {
            degraded = true
            LunoLog.w(LOG_TAG, "xmsf gate restore FAILED (allow=$allow chain=$chainOk); publishing degraded")
        }
        return ok
    }

    /** shell 执行；null = Shizuku 不可用或命令失败。 */
    private fun shell(command: String): String? = runCatching {
        runBlocking {
            ShizukuManager.executeShell(command).takeIf { it.isSuccess }?.stdout?.trim()
        }
    }.getOrNull()

    private companion object {
        const val LOG_TAG = "XmsfGate"
    }
}
