package hunoia.luno.launch

import hunoia.luno.bridge.system.PackageChangeReceiver
import hunoia.luno.config.ConfigProvider
import hunoia.luno.core.AppContext
import hunoia.luno.quickaccess.query.AppQuery
import hunoia.luno.quickaccess.query.DisabledAppQuery
import hunoia.luno.quickaccess.query.LauncherEnvironment
import hunoia.luno.shizuku.PackageResult
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 冻结 / 解冻的唯一写入点。
 *
 * 页面（主进程）与一键动作（`:service`）都调这里；写入成功后统一刷新
 * [DisabledAppQuery] 与启动器缓存，再通知上层重扫清单，避免出现两份状态。
 *
 * 语义（architecture「冻结」口径）：
 * - 冻结 = `setApplicationEnabledSetting(DISABLED_USER)`；解冻 = `ENABLED`；
 * - suspend 是厂商冻结，`enablePackage` 无效：不进入解冻目标，失败原因明确回传；
 * - 解冻目标只允许「一键名单 ∩ 当前 disabled」，不误伤系统里手动禁用的应用；
 * - 批量 binder 失败不逐包 shell 兜底（批量 shell 太慢），单包允许兜底。
 */
object PackageControl {

    /** 同屏批量冻结最多同时打 6 个 binder，避免一次性把 200+ 请求打给 Shizuku。 */
    private const val BatchConcurrency = 6

    data class Failure(val packageName: String, val reason: String)

    data class BatchResult(
        val successes: List<String> = emptyList(),
        val failures: List<Failure> = emptyList(),
    ) {
        val total: Int get() = successes.size + failures.size
    }

    /** 单包解冻：页面开关与启动前自动解冻共用。 */
    suspend fun unfreeze(packageName: String): PackageResult =
        change(packageName, enable = true, shellFallback = true)

    /** 单包冻结：页面开关。 */
    suspend fun freeze(packageName: String): PackageResult =
        change(packageName, enable = false, shellFallback = true)

    suspend fun freezeBatch(packageNames: Collection<String>): BatchResult =
        batch(packageNames, enable = false)

    suspend fun unfreezeBatch(packageNames: Collection<String>): BatchResult =
        batch(packageNames, enable = true)

    /** 一键冻结：名单中仍安装的包（卸载后残留在名单里的包直接跳过）。 */
    suspend fun freezeOneKey(): BatchResult {
        val context = AppContext.get()
        val oneKey = oneKeyPackages()
        val installed = withContext(Dispatchers.IO) {
            oneKey.filterTo(mutableSetOf()) { isInstalled(context, it) }
        }
        return freezeBatch(AppControlTargets.freezeTargets(oneKey, installed))
    }

    /** 一键解冻：名单 ∩ 当前 disabled；suspend 包会被 [batch] 明确拒绝。 */
    suspend fun unfreezeOneKey(): BatchResult {
        val context = AppContext.get()
        val oneKey = oneKeyPackages()
        val disabled = oneKey.filterTo(mutableSetOf()) { DisabledAppQuery.isDisabled(context, it) }
        return unfreezeBatch(AppControlTargets.unfreezeTargets(oneKey, disabled))
    }

    /**
     * 清理一键名单里已卸载的包：页面进入与开机各跑一次。
     *
     * 只删「确认已不在安装列表」的包名；删除按当前配置做减法，避免与用户同时编辑
     * 名单时互相覆盖。返回被清掉的包名，便于日志/测试。
     */
    suspend fun pruneUninstalledFromOneKey(): Set<String> {
        val context = AppContext.get()
        val oneKey = oneKeyPackages()
        if (oneKey.isEmpty()) return emptySet()
        val installed = withContext(Dispatchers.IO) {
            oneKey.filterTo(mutableSetOf()) { isInstalled(context, it) }
        }
        val pruned = oneKey - installed
        if (pruned.isNotEmpty()) {
            ConfigProvider.updateAppControlSettings { settings ->
                settings.copy(
                    oneKeyFreezePackageNames =
                    settings.oneKeyFreezePackageNames - pruned,
                )
            }
        }
        return pruned
    }

    suspend fun oneKeyPackages(): Set<String> =
        withContext(Dispatchers.IO) {
            ConfigProvider.getAppControlSettings().oneKeyFreezePackageNames
        }

    private suspend fun batch(
        packageNames: Collection<String>,
        enable: Boolean,
    ): BatchResult {
        val names = packageNames.filter { it.isNotBlank() }.distinct()
        if (names.isEmpty()) return BatchResult()
        val semaphore = Semaphore(BatchConcurrency)
        val results = coroutineScope {
            names.map { packageName ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        change(packageName, enable = enable, shellFallback = false)
                    }
                }
            }.awaitAll()
        }
        val successes = results.filter { it.success }.map { it.packageName }
        val failures = results.filterNot { it.success }
            .map { Failure(it.packageName, it.errorMessage) }
        if (successes.isNotEmpty()) notifyPackageStateChanged()
        return BatchResult(successes, failures)
    }

    private suspend fun change(
        packageName: String,
        enable: Boolean,
        shellFallback: Boolean,
    ): PackageResult = withContext(Dispatchers.IO) {
        val context = AppContext.get()
        if (enable) {
            if (DisabledAppQuery.isSuspended(context, packageName)) {
                return@withContext PackageResult(
                    false,
                    packageName,
                    SUSPENDED_REASON,
                )
            }
            if (!DisabledAppQuery.isDisabled(context, packageName)) {
                return@withContext PackageResult(true, packageName)
            }
        } else {
            if (DisabledAppQuery.isSuspended(context, packageName)) {
                return@withContext PackageResult(false, packageName, SUSPENDED_REASON)
            }
            if (packageName == context.packageName) {
                return@withContext PackageResult(false, packageName, PROTECTED_SELF)
            }
            if (LauncherEnvironment.isLauncherPackage(context, packageName) ||
                LauncherEnvironment.currentHomePackage(context) == packageName
            ) {
                return@withContext PackageResult(false, packageName, PROTECTED_LAUNCHER)
            }
            if (DisabledAppQuery.isDisabled(context, packageName)) {
                return@withContext PackageResult(true, packageName)
            }
        }

        val result = if (enable) {
            ShizukuManager.enablePackage(packageName, shellFallback)
        } else {
            ShizukuManager.disablePackage(packageName, shellFallback)
        }
        if (result.success) {
            if (enable) {
                DisabledAppQuery.markEnabled(packageName)
            } else {
                DisabledAppQuery.markDisabled(packageName)
            }
            AppQuery.invalidateLauncherCache()
            // 本进程立即回调 + 跨进程广播：页面在另一进程时也能立刻刷新。
            notifyPackageStateChanged()
            PackageChangeReceiver.notifyLocalPackageChanged(context)
        }
        result
    }

    private fun isInstalled(context: android.content.Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.isSuccess

    private fun notifyPackageStateChanged() {
        runCatching { LaunchCapability.onPackageStateChanged() }
    }

    const val SUSPENDED_REASON = "suspended"
    const val PROTECTED_SELF = "protected:self"
    const val PROTECTED_LAUNCHER = "protected:launcher"
}
