package hunoia.luno.action.executor

import android.content.Intent
import android.content.pm.PackageManager
import hunoia.luno.action.controller.IntentController
import hunoia.luno.action.execution.ActionExecutor
import hunoia.luno.action.execution.ExecutorContext
import hunoia.luno.action.model.Action
import hunoia.luno.action.model.ActionFailure
import hunoia.luno.action.model.ActionResult
import hunoia.luno.bridge.queryIntentActivitiesCompat
import hunoia.luno.config.model.AdvancedSettings
import hunoia.luno.config.model.effectiveForAction
import hunoia.luno.core.JsonSerializer
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.launch.AppLaunchBypass
import hunoia.luno.quicklaunch.model.LauncherInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AppExecutors(private val intentController: IntentController) : ActionExecutor {

    override val supportedTypes: Set<String> = setOf(
        "app.launch", "app.openActivity", "app.details", "app.popup", "app.launchShortcut"
    )

    override suspend fun execute(action: Action, context: ExecutorContext): ActionResult {
        return when (action) {
            is Action.LaunchApp -> {
                val adv = context.advancedSettings.effectiveForAction(action)
                val ok = AppLaunchBypass.launchWithAutoUnfreeze(
                    context = context.appContext,
                    packageName = action.packageName,
                    className = action.className,
                    miniWindow = action.miniWindow,
                    miniWindowHorizontalBias = adv.miniWindowHorizontalBias,
                    miniWindowVerticalBias = adv.miniWindowVerticalBias,
                    miniWindowWidthFraction = adv.miniWindowWidthFraction,
                    miniWindowHeightFraction = adv.miniWindowHeightFraction,
                    miniWindowOverrideBounds = adv.miniWindowOverrideBounds,
                    unfreezePackage = { _, pkg -> enablePackage(context, pkg) },
                )
                if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.OpenActivity -> {
                val adv = context.advancedSettings.effectiveForAction(action)
                val ok = AppLaunchBypass.launchWithAutoUnfreeze(
                    context = context.appContext,
                    packageName = action.packageName,
                    className = action.activityClassName,
                    miniWindow = action.miniWindow,
                    miniWindowHorizontalBias = adv.miniWindowHorizontalBias,
                    miniWindowVerticalBias = adv.miniWindowVerticalBias,
                    miniWindowWidthFraction = adv.miniWindowWidthFraction,
                    miniWindowHeightFraction = adv.miniWindowHeightFraction,
                    miniWindowOverrideBounds = adv.miniWindowOverrideBounds,
                    unfreezePackage = { _, pkg -> enablePackage(context, pkg) },
                )
                if (ok) ActionResult.Success() else ActionResult.Failed(ActionFailure.ExecutionFailed)
            }
            is Action.AppDetails -> intentController.openAppDetails(action.packageName)
            is Action.Popup -> {
                popupScreen(context, context.advancedSettings.effectiveForAction(action))
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

    private fun popupScreen(context: ExecutorContext, adv: AdvancedSettings) {
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
        val resolveInfo = context.appContext.packageManager
            .queryIntentActivitiesCompat(intent, PackageManager.MATCH_ALL)
            .firstOrNull()
        val className = resolveInfo?.activityInfo?.name
        if (!className.isNullOrEmpty()) {
            if (adv.miniWindowOverrideBounds) {
                QuickLaunchFacade.launchAppInPopup(
                    context.appContext, pkgName, className,
                    adv.miniWindowHorizontalBias,
                    adv.miniWindowVerticalBias,
                    adv.miniWindowWidthFraction,
                    adv.miniWindowHeightFraction,
                    overrideBounds = true,
                )
            } else {
                QuickLaunchFacade.launchAppInPopup(context.appContext, pkgName, className)
            }
        }
    }

    private suspend fun enablePackage(context: ExecutorContext, pkg: String): Boolean =
        suspendCancellableCoroutine { cont ->
            context.requestEnableDisabledPackage(pkg) { success ->
                if (cont.isActive) cont.resume(success)
            }
        }
}
