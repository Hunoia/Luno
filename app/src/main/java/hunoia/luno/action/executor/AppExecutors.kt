package hunoia.luno.action.executor

import android.content.Intent
import android.content.pm.PackageManager
import hunoia.luno.action.controller.IntentController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.bridge.system.queryIntentActivitiesCompat
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.core.JsonSerializer
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.launch.AppLaunchBypass
import hunoia.luno.quicklaunch.model.LauncherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import hunoia.luno.config.model.miniWindowSettings

class AppExecutors(private val intentController: IntentController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "app.launch", "app.details", "app.popup", "app.launchShortcut"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.LaunchApp -> {
                val adv = context.advancedSettings
                val ok = AppLaunchBypass.launchWithAutoUnfreeze(
                    context = context.appContext,
                    packageName = action.packageName,
                    className = action.className,
                    miniWindow = action.miniWindow,
                    miniWindowSettings = adv.miniWindowSettings(),
                    intentAction = action.intentAction,
                    data = action.data,
                    extras = action.extras,
                    unfreezePackage = { _, pkg -> enablePackage(context, pkg) },
                )
                if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.AppDetails -> intentController.openAppDetails(action.packageName)
            is Action.Popup -> {
                popupScreen(context, context.advancedSettings)
                ActionResult.Success()
            }
            is Action.LaunchShortcut -> {
                val info = runCatching {
                    JsonSerializer.decodeFromString<LauncherInfo.ShortcutInfo>(action.data)
                }.getOrNull()
                if (info == null) {
                    ActionResult.Failed(ActionFailure.InvalidParameter)
                } else {
                    QuickLaunchFacade.launchShortcutInfo(context.appContext, info)
                    ActionResult.Success()
                }
            }
            else -> ActionResult.Failed(ActionFailure.Unsupported)
        }
    }

    private suspend fun popupScreen(context: ExecutorContext, adv: AdvancedSettings) {
        val pkgName = context.accessibilityService
            .rootInActiveWindow?.packageName?.toString()
            ?: context.currentPackageName()
        if (context.nowInLauncher() || pkgName.isNullOrEmpty()) {
            return
        }
        val intent = Intent().apply {
            setPackage(pkgName)
            setAction(Intent.ACTION_MAIN)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        // MATCH_ALL 是 PMS 重查询（几十 ms 级），别占手势收尾的主线程。
        val resolveInfo = withContext(Dispatchers.IO) {
            context.appContext.packageManager
                .queryIntentActivitiesCompat(intent, PackageManager.MATCH_ALL)
                .firstOrNull()
        }
        val className = resolveInfo?.activityInfo?.name
        if (!className.isNullOrEmpty()) {
            QuickLaunchFacade.launchAppInPopup(
                context.appContext, pkgName, className,
                adv.miniWindowSettings(),
            )
        }
    }

    private suspend fun enablePackage(context: ExecutorContext, pkg: String): Boolean =
        suspendCancellableCoroutine { cont ->
            context.requestEnableDisabledPackage(pkg) { success ->
                if (cont.isActive) cont.resume(success)
            }
        }
}
