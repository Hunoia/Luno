package hunoia.luno.config

import hunoia.luno.core.AppContext
import hunoia.luno.core.domain.AppLaunchSource
import hunoia.luno.core.domain.LaunchTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 启动用量统计的实现（配置流写入）。
 *
 * 依据 architecture_future §二「配置写入不构造 ActionRequest，不走 Dispatcher」与
 * ADR-0001 D3：能力库只持有 `LaunchTelemetry` 契约，落库归 `:core:data`。
 * 只做异步落库，不阻塞启动链路；失败静默（统计不影响功能）。
 */
object DataLaunchTelemetry : LaunchTelemetry {
    override fun recordLaunch(packageName: String, source: AppLaunchSource) {
        if (packageName.isBlank()) return
        val scope = AppContext.applicationScope ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { ConfigProvider.recordAppLaunch(packageName, source) }
        }
    }
}
