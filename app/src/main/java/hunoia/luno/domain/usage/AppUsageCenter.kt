package hunoia.luno.domain.usage

import hunoia.luno.config.ConfigProvider
import hunoia.luno.config.model.AppLaunchSource
import hunoia.luno.core.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用启动统计唯一写入口：所有 Luno 触发的应用启动成功后都走这里。
 * 只做异步落库，不阻塞启动/点击链路；失败静默（统计不影响功能）。
 */
object AppUsageCenter {

    fun recordLaunch(packageName: String, source: AppLaunchSource = AppLaunchSource.UNKNOWN) {
        if (packageName.isBlank()) return
        val scope = AppContext.applicationScope ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { ConfigProvider.recordAppLaunch(packageName, source) }
        }
    }
}
