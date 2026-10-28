package hunoia.luno.launch

import hunoia.luno.core.domain.AppLaunchSource
import hunoia.luno.core.domain.DisabledAppState
import hunoia.luno.core.domain.LaunchTarget
import hunoia.luno.core.domain.LaunchTargetResolver
import hunoia.luno.core.domain.LaunchTelemetry

/**
 * 启动能力库的装配点：由 `:app` 在 `App.onCreate` 注入一次。
 *
 * 两个协作者都是「配置流 / 查询」侧的实现，启动库本身不持有业务状态：
 * - [telemetry]：启动成功后的用量统计（`:core:data` 实现，配置流写入）；
 * - [targetResolver]：包名 → launcher Activity（`:app` 用 `AppQuery` 实现）。
 *
 * 未注入时退化为 no-op：能力库可以独立编译与单测，不需要宿主。
 */
object LaunchCapability {
    var telemetry: LaunchTelemetry = LaunchTelemetry { _, _ -> }
    var targetResolver: LaunchTargetResolver = LaunchTargetResolver { null }
    var disabledApps: DisabledAppState = object : DisabledAppState {
        override fun isDisabled(packageName: String) = false
        override fun markEnabled(packageName: String) = Unit
        override fun invalidateLauncherCache() = Unit
    }

    suspend fun resolveLauncherActivity(packageName: String): LaunchTarget? =
        targetResolver.launcherActivity(packageName)

    fun recordLaunch(packageName: String, source: AppLaunchSource) {
        telemetry.recordLaunch(packageName, source)
    }

    fun resolveDisabledState(): DisabledAppState = disabledApps
}
