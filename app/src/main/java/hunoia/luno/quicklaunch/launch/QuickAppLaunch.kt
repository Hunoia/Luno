package hunoia.luno.quicklaunch.launch

import android.content.Context
import hunoia.luno.config.model.MiniWindowSettings
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.config.model.OpenUrlQueryParameter
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.AppQuery
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
