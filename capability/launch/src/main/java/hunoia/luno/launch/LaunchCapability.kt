package hunoia.luno.launch

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
    var telemetry: LaunchTelemetry = LaunchTelemetry { _ -> }
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

    suspend fun resolveLauncherActivity(packageName: String): LaunchTarget? =
        targetResolver.launcherActivity(packageName)

    fun recordLaunch(packageName: String) {
        telemetry.recordLaunch(packageName)
    }
}

/**
 * 被禁用/冻结应用的状态查询与标记，启动能力库解冻前后要用。
 *
 * 实现收口在 `DisabledAppQuery` + 启动器缓存失效，全应用只有这一份缓存；
 * UI 与启动链路读同一事实源。
 */
object DisabledApps {
    /** enabled 位被禁用（Luno 可解冻）。 */
    fun isDisabled(packageName: String): Boolean =
        DisabledAppQuery.isDisabled(AppContext.get(), packageName)

    /** 厂商/系统 suspend：Luno 无法通过 enablePackage 解除。 */
    fun isSuspended(packageName: String): Boolean =
        DisabledAppQuery.isSuspended(AppContext.get(), packageName)

    /** 解冻成功后标记为可用（含缓存失效）。 */
    fun markEnabled(packageName: String) {
        DisabledAppQuery.markEnabled(packageName)
        AppQuery.invalidateLauncherCache()
    }

    /** 解冻成功后让启动器查询缓存失效。 */
    fun invalidateLauncherCache() {
        AppQuery.invalidateLauncherCache()
    }
}
