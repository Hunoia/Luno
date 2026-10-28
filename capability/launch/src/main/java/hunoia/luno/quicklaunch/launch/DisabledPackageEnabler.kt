package hunoia.luno.quicklaunch.launch

import android.content.Context
import hunoia.luno.launch.LaunchCapability
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DisabledPackageEnabler(
    private val context: Context,
    private val scopeProvider: () -> CoroutineScope,
    private val log: (String) -> Unit
) {
    private val lock = Any()
    private var inFlightPackageName: String? = null
    private var generation = 0

    fun request(packageName: String, onResult: (Boolean) -> Unit) {
        val startGeneration: Int
        synchronized(lock) {
            if (inFlightPackageName != null) {
                log("enable_package: in-flight $inFlightPackageName, ignoring $packageName")
                onResult(false)
                return
            }
            inFlightPackageName = packageName
            startGeneration = generation
        }

        log("enable_package: requesting $packageName")

        scopeProvider().launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val result = ShizukuManager.enablePackage(packageName)
            if (result.success) {
                // 解冻成功后同步能力层的禁用缓存：否则同一轮 app.launch 还会再判一次
                // disabled，且下次启动仍可能命中旧的 false/true。
                LaunchCapability.resolveDisabledState().markEnabled(packageName)
                LaunchCapability.resolveDisabledState().invalidateLauncherCache()
                log("enable_package: success pkg=$packageName elapsed=${System.currentTimeMillis() - start}ms")
            } else {
                log("enable_package: failed pkg=$packageName error=${result.errorMessage}")
            }
            val shouldNotify = synchronized(lock) {
                val current = generation == startGeneration && inFlightPackageName == packageName
                if (inFlightPackageName == packageName) inFlightPackageName = null
                current
            }
            // 面板已关闭/重开：不要回调已废弃的 composition。
            if (shouldNotify) {
                withContext(Dispatchers.Main) { onResult(result.success) }
            }
        }
    }

    fun release() {
        synchronized(lock) {
            generation++
            inFlightPackageName = null
        }
    }
}
