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
        isDisabled: Boolean,
        miniWindow: Boolean,
        debugPrefix: String?,
        requestEnableDisabledPackage: (String, (Boolean) -> Unit) -> Unit,
        log: (String) -> Unit,
        onLaunch: (AppInfo, Boolean) -> Boolean,
        onLaunched: () -> Unit
    ) {
        if (!isDisabled) {
            if (onLaunch(app, miniWindow)) onLaunched()
            return
        }

        val disabledStart = System.currentTimeMillis()
        logDisabledStart(context, app, debugPrefix)
        requestEnableDisabledPackage(app.packageName) { success ->
            logEnableEnd(app, debugPrefix, success, disabledStart)
            if (!success) {
                logEnableFailed(app, debugPrefix, disabledStart)
                return@requestEnableDisabledPackage
            }

            log("enable_package: request launch pkg=${app.packageName} miniWindow=$miniWindow")
            logResolveStart(app, debugPrefix)
            val resolveStart = System.currentTimeMillis()
            coroutineScope.launch launchBlock@{
                val found = withContext(Dispatchers.IO) {
                    AppQuery.findLauncherActivity(context, app.packageName)
                }
                if (found == null) {
                    logResolveNotFound(app, debugPrefix, resolveStart, disabledStart)
                    log("enable_package: launcher activity not found pkg=${app.packageName}")
                    return@launchBlock
                }

                logResolveFound(app, debugPrefix, resolveStart)
                log("enable_package: launcher found pkg=${found.packageName} cls=${found.className}")
                val launchStart = System.currentTimeMillis()
                val result = onLaunch(found, miniWindow)
                logLaunchResult(app, debugPrefix, result, launchStart, disabledStart)
                log("enable_package: launch after enable result=$result pkg=${app.packageName}")
                if (result) onLaunched()
            }
        }
    }

    private fun logDisabledStart(context: Context, app: AppInfo, debugPrefix: String?) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        val beforeState = runCatching {
            context.packageManager.getApplicationEnabledSetting(app.packageName)
        }.getOrDefault(-1)
        android.util.Log.d("LauncherPerf", "$debugPrefix: disabled start pkg=${app.packageName} beforeEnable=$beforeState")
    }

    private fun logEnableEnd(app: AppInfo, debugPrefix: String?, success: Boolean, disabledStart: Long) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        android.util.Log.d(
            "LauncherPerf",
            "$debugPrefix: enable_end pkg=${app.packageName} success=$success elapsed=${System.currentTimeMillis() - disabledStart}ms"
        )
    }

    private fun logEnableFailed(app: AppInfo, debugPrefix: String?, disabledStart: Long) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        android.util.Log.d(
            "LauncherPerf",
            "$debugPrefix: enable_failed pkg=${app.packageName} total=${System.currentTimeMillis() - disabledStart}ms"
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

    private fun logResolveNotFound(app: AppInfo, debugPrefix: String?, resolveStart: Long, disabledStart: Long) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        android.util.Log.d(
            "LauncherPerf",
            "$debugPrefix: resolve_intent not_found pkg=${app.packageName} elapsed=${System.currentTimeMillis() - resolveStart}ms total=${System.currentTimeMillis() - disabledStart}ms"
        )
    }

    private fun logLaunchResult(app: AppInfo, debugPrefix: String?, result: Boolean, launchStart: Long, disabledStart: Long) {
        if (debugPrefix == null || !BuildConfig.DEBUG) return
        android.util.Log.d(
            "LauncherPerf",
            "$debugPrefix: startActivity pkg=${app.packageName} result=$result elapsed=${System.currentTimeMillis() - launchStart}ms total=${System.currentTimeMillis() - disabledStart}ms"
        )
    }
}
