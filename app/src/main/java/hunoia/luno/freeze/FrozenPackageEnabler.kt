package hunoia.luno.freeze

import android.content.Context
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FrozenPackageEnabler(
    private val context: Context,
    private val scopeProvider: () -> CoroutineScope,
    private val log: (String) -> Unit
) {
    private val lock = Any()
    private var inFlightPackageName: String? = null

    fun request(packageName: String, onResult: (Boolean) -> Unit) {
        synchronized(lock) {
            if (inFlightPackageName != null) {
                log("enable_package: in-flight $inFlightPackageName, ignoring $packageName")
                onResult(false)
                return
            }
            inFlightPackageName = packageName
        }

        log("enable_package: requesting $packageName")

        scopeProvider().launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val result = ShizukuManager.enablePackage(packageName)
            if (result.success) {
                log("enable_package: success pkg=$packageName elapsed=${System.currentTimeMillis() - start}ms")
            } else {
                log("enable_package: failed pkg=$packageName error=${result.errorMessage}")
            }
            clearRequest(packageName)
            withContext(Dispatchers.Main) {
                onResult(result.success)
            }
        }
    }

    fun release() {
        synchronized(lock) { inFlightPackageName = null }
    }

    private fun clearRequest(packageName: String) {
        synchronized(lock) {
            if (inFlightPackageName == packageName) inFlightPackageName = null
        }
    }
}
