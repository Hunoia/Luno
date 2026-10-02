package hunoia.luno.quicklaunch.launch

import android.content.Context
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
