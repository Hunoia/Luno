package hunoia.luno.launch

import hunoia.luno.core.domain.AppLaunchSource
import hunoia.luno.core.domain.DisabledAppState
import hunoia.luno.core.domain.LaunchTarget
import hunoia.luno.core.domain.LaunchTargetResolver
import hunoia.luno.core.domain.LaunchTelemetry
import hunoia.luno.core.AppContext
import hunoia.luno.quickaccess.query.AppQuery
import hunoia.luno.quickaccess.query.DisabledAppQuery

/**
 * 启动能力库的装配点：由 `:app` 在 `App.onCreate` 注入一次。
 *
 * 两个协作者都是「配置流 / 查询」侧的实现，启动库本身不持有业务状态：
 * - [telemetry]：启动成功后的用量统计（`:core:data` 实现，配置流写入）；
 * - [targetResolver]：包名 → launcher Activity（默认实现用 `AppQuery`）。
 *
 * 未注入时退化为 no-op：能力库可以独立编译与单测，不需要宿主。
 */
object LaunchCapability {
    var telemetry: LaunchTelemetry = LaunchTelemetry { _, _ -> }
    var targetResolver: LaunchTargetResolver = LaunchTargetResolver { packageName ->
        AppQuery.findLauncherActivity(AppContext.get(), packageName)
            ?.let { LaunchTarget(it.packageName, it.className) }
    }
    /**
     * 写入成功后通知上层「应用清单/搜索索引该重扫了」。
     *
     * 包变更广播是兜底，不是事实源：部分 ROM 对 `pm disable-user` 不发
     * `ACTION_PACKAGE_CHANGED`，只靠广播会让面板/搜索继续展示旧状态。
     * `:app` 在组装时注入（`AppListRepository.refresh()`）。
     */
    var onPackageStateChanged: () -> Unit = {}

    var disabledApps: DisabledAppState = object : DisabledAppState {
        override fun isDisabled(packageName: String): Boolean =
            DisabledAppQuery.isDisabled(AppContext.get(), packageName)

        override fun isSuspended(packageName: String): Boolean =
            DisabledAppQuery.isSuspended(AppContext.get(), packageName)

        override fun markEnabled(packageName: String) {
            DisabledAppQuery.markEnabled(packageName)
            AppQuery.invalidateLauncherCache()
        }

        override fun invalidateLauncherCache() {
            AppQuery.invalidateLauncherCache()
        }
    }

    suspend fun resolveLauncherActivity(packageName: String): LaunchTarget? =
        targetResolver.launcherActivity(packageName)

    fun recordLaunch(packageName: String, source: AppLaunchSource) {
        telemetry.recordLaunch(packageName, source)
    }

    fun resolveDisabledState(): DisabledAppState = disabledApps
}
