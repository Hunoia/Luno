package hunoia.luno.quicklaunch.launch

import android.content.Context
import hunoia.luno.R
import hunoia.luno.bridge.ui.showToast
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.OpenUrlQueryParameter
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.AppQuery
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
        requestEnableDisabledPackage: (String, (Boolean) -> Unit) -> Unit,
        log: (String) -> Unit,
        onLaunch: (AppInfo, Boolean) -> Boolean,
        onLaunched: () -> Unit
    ) {
        if (!isDisabled) {
            if (onLaunch(app, miniWindow)) onLaunched()
            return
        }

        requestEnableDisabledPackage(app.packageName) { success ->
            if (!success) return@requestEnableDisabledPackage

            log("enable_package: request launch pkg=${app.packageName} miniWindow=$miniWindow")
            coroutineScope.launch launchBlock@{
                val found = withContext(Dispatchers.IO) {
                    AppQuery.findLauncherActivity(context, app.packageName)
                }
                if (found == null) {
                    log("enable_package: launcher activity not found pkg=${app.packageName}")
                    return@launchBlock
                }

                log("enable_package: launcher found pkg=${found.packageName} cls=${found.className}")
                val result = onLaunch(found, miniWindow)
                log("enable_package: launch after enable result=$result pkg=${app.packageName}")
                if (result) onLaunched()
            }
        }
    }
}

object AppLaunchBypass {

    suspend fun launchWithAutoUnfreeze(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean = false,
        miniWindowSettings: MiniWindowSettings = MiniWindowSettings(),
        unfreezePackage: suspend (context: Context, packageName: String) -> Boolean = { _, _ -> true }
    ): Boolean {
        if (!ensureEnabled(context, packageName, unfreezePackage)) return false
        return QuickLaunchFacade.launchAppDirect(context, packageName, className, miniWindow, miniWindowSettings)
    }

    suspend fun launchActivityWithAutoUnfreeze(
        context: Context,
        packageName: String,
        className: String,
        unfreezePackage: suspend (context: Context, packageName: String) -> Boolean = { _, _ -> true }
    ): Boolean {
        if (!ensureEnabled(context, packageName, unfreezePackage)) return false
        return QuickLaunchFacade.launchAppActivityDirect(context, packageName, className)
    }

    /** 应用被禁用时先解冻再启动；解冻失败弹提示并返回 false */
    private suspend fun ensureEnabled(
        context: Context,
        packageName: String,
        unfreezePackage: suspend (context: Context, packageName: String) -> Boolean,
    ): Boolean {
        if (!DisabledAppQuery.isDisabled(context, packageName)) return true
        if (!unfreezePackage(context, packageName)) {
            showToast(R.string.enable_disabled_app_failed)
            return false
        }
        DisabledAppQuery.markEnabled(packageName)
        QuickLaunchFacade.invalidateLauncherCache()
        return true
    }
}

internal object OpenUrlBuilder {
    fun build(rawUrl: String, data: OpenAppOrUrlData): String {
        if (rawUrl.startsWith("intent:") || rawUrl.startsWith("android-app:")) {
            return rawUrl
        }
        val additions = buildList {
            if (data.miniWindow) {
                add(OpenUrlQueryParameter(name = "miniWindow", value = "true"))
            }
            data.queryParameters
                .filter { it.enabled && it.name.isNotBlank() }
                .forEach { add(it) }
        }
        if (additions.isEmpty()) return rawUrl

        val fragmentIndex = rawUrl.indexOf('#')
        val base = if (fragmentIndex >= 0) rawUrl.substring(0, fragmentIndex) else rawUrl
        val suffix = if (fragmentIndex >= 0) rawUrl.substring(fragmentIndex) else ""
        val separator = when {
            base.endsWith("?") || base.endsWith("&") -> ""
            base.contains("?") -> "&"
            else -> "?"
        }
        return base + separator + additions.joinToString("&") { parameter ->
            "${parameter.name.encodeQueryComponent()}=${parameter.value.encodeQueryComponent()}"
        } + suffix
    }

    private fun String.encodeQueryComponent(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
