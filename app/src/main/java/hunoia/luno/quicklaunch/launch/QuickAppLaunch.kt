package hunoia.luno.quicklaunch.launch

import android.content.Context
import hunoia.luno.BuildConfig
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.AppQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object QuickAppLaunch {
    fun launch(
        context: Context,
        coroutineScope: CoroutineScope,
        app: AppInfo,
        isFrozen: Boolean,
        miniWindow: Boolean,
        debugPrefix: String?,
        requestEnableFrozenPackage: (String, (Boolean) -> Unit) -> Unit,
        log: (String) -> Unit,
        onLaunch: (AppInfo, Boolean) -> Boolean,
        onLaunched: () -> Unit
    ) {
        if (!isFrozen) {
            if (onLaunch(app, miniWindow)) onLaunched()
            return
        }

        val frozenStart = System.currentTimeMillis()
        logFrozenStart(context, app, debugPrefix)
        requestEnableFrozenPackage(app.packageName) { success ->
            logEnableEnd(app, debugPrefix, success, frozenStart)
            if (!success) {
                logEnableFailed(app, debugPrefix, frozenStart)
                return@requestEnableFrozenPackage
            }

            log("enable_package: request launch pkg=${app.packageName} miniWindow=$miniWindow")
            logResolveStart(app, debugPrefix)
            val resolveStart = System.currentTimeMillis()
            coroutineScope.launch launchBlock@{
                val found = withContext(Dispatchers.IO) {
                    AppQuery.findLauncherActivity(context, app.packageName)
                }
                if (found == null) {
                    logResolveNotFound(app, debugPrefix, resolveStart, frozenStart)
                    log("enable_package: launcher activity not found pkg=${app.packageName}")
                    return@launchBlock
                }

                logResolveFound(app, debugPrefix, resolveStart)
                log("enable_package: launcher found pkg=${found.packageName} cls=${found.className}")
                val launchStart = System.currentTimeMillis()
                val result = onLaunch(found, miniWindow)
                logLaunchResult(app, debugPrefix, result, launchStart, frozenStart)
                log("enable_package: launch after enable result=$result pkg=${app.packageName}")
                if (result) onLaunched()
            }
        }
    }

    private fun logFrozenStart(context: Context, app: AppInfo, debugPrefix: String?) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        val beforeState = runCatching {
            context.packageManager.getApplicationEnabledSetting(app.packageName)
        }.getOrDefault(-1)
        android.util.Log.d("LauncherPerf", "$debugPrefix: frozen start pkg=${app.packageName} beforeEnable=$beforeState")
    }

    private fun logEnableEnd(app: AppInfo, debugPrefix: String?, success: Boolean, frozenStart: Long) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        android.util.Log.d(
            "LauncherPerf",
            "$debugPrefix: enable_end pkg=${app.packageName} success=$success elapsed=${System.currentTimeMillis() - frozenStart}ms"
        )
    }

    private fun logEnableFailed(app: AppInfo, debugPrefix: String?, frozenStart: Long) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        android.util.Log.d(
            "LauncherPerf",
            "$debugPrefix: enable_failed pkg=${app.packageName} total=${System.currentTimeMillis() - frozenStart}ms"
        )
    }

    private fun logResolveStart(app: AppInfo, debugPrefix: String?) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        android.util.Log.d("LauncherPerf", "$debugPrefix: resolve_intent start pkg=${app.packageName}")
    }

    private fun logResolveFound(app: AppInfo, debugPrefix: String?, resolveStart: Long) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        android.util.Log.d(
            "LauncherPerf",
            "$debugPrefix: resolve_intent found pkg=${app.packageName} elapsed=${System.currentTimeMillis() - resolveStart}ms"
        )
    }

    private fun logResolveNotFound(app: AppInfo, debugPrefix: String?, resolveStart: Long, frozenStart: Long) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        android.util.Log.d(
            "LauncherPerf",
            "$debugPrefix: resolve_intent not_found pkg=${app.packageName} elapsed=${System.currentTimeMillis() - resolveStart}ms total=${System.currentTimeMillis() - frozenStart}ms"
        )
    }

    private fun logLaunchResult(app: AppInfo, debugPrefix: String?, result: Boolean, launchStart: Long, frozenStart: Long) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        android.util.Log.d(
            "LauncherPerf",
            "$debugPrefix: startActivity pkg=${app.packageName} result=$result elapsed=${System.currentTimeMillis() - launchStart}ms total=${System.currentTimeMillis() - frozenStart}ms"
        )
    }
}
