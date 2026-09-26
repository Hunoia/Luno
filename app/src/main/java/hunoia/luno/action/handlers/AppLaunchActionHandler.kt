package hunoia.luno.action.handlers

import android.content.Intent
import android.content.pm.PackageManager
import hunoia.luno.R
import hunoia.luno.action.api.ActionExecutionResult
import hunoia.luno.action.api.ActionHandler
import hunoia.luno.action.api.ActionHandlerContext
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.config.model.Action
import hunoia.luno.config.model.OpenAppOrUrlData
import hunoia.luno.freeze.FreezeFacade
import hunoia.luno.action.api.appInfo
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.bridge.queryIntentActivitiesCompat
import hunoia.luno.core.JsonSerializer
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object AppLaunchActionHandler : ActionHandler {

    override val supportedActions = setOf(
        ActionFacade.EXTRA_LAUNCH_APP,
        ActionFacade.OPEN_APP_ACTIVITY,
        ActionFacade.OPEN_URL,
        ActionFacade.QUICK_APP_LAUNCHER,
        ActionFacade.POPUP_SCREEN,
    )

    override suspend fun handle(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        when (action.value) {
            ActionFacade.EXTRA_LAUNCH_APP -> handleExtraLaunchApp(action, context)
            ActionFacade.OPEN_APP_ACTIVITY -> handleOpenAppActivity(action, context)
            ActionFacade.OPEN_URL -> return handleOpenUrl(action, context)
            ActionFacade.QUICK_APP_LAUNCHER -> context.toggleQuickAppLauncher()
            ActionFacade.POPUP_SCREEN -> handlePopupScreen(context)
            else -> return ActionExecutionResult.Ignored
        }
        return ActionExecutionResult.Success
    }

    private fun handlePopupScreen(context: ActionHandlerContext) {
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
            if (context.advancedSettings.miniWindowOverrideBounds) {
                QuickLaunchFacade.launchAppInPopup(
                    context.appContext, pkgName, className,
                    context.advancedSettings.miniWindowHorizontalBias,
                    context.advancedSettings.miniWindowVerticalBias,
                    context.advancedSettings.miniWindowVerticalOffsetFraction,
                    context.advancedSettings.miniWindowWidthFraction,
                    context.advancedSettings.miniWindowHeightFraction,
                    overrideBounds = true,
                )
            } else {
                QuickLaunchFacade.launchAppInPopup(context.appContext, pkgName, className)
            }
        }
    }

    private fun handleExtraLaunchApp(action: Action, context: ActionHandlerContext) {
        val appInfo = action.appInfo ?: return
        launchAppWithFrozenSupport(context, appInfo, appInfo.miniWindow)
    }

    private fun handleOpenAppActivity(action: Action, context: ActionHandlerContext) {
        val data = try {
            JsonSerializer.decodeFromString<OpenAppOrUrlData>(action.data)
        } catch (e: Exception) {
            null
        }
        if (data != null && data.packageName.isNotBlank() && data.activityClassName.isNotBlank()) {
            context.scope.launch {
                FreezeFacade.launchActivityWithAutoUnfreeze(
                    context = context.appContext,
                    packageName = data.packageName,
                    className = data.activityClassName
                ) { _, pkg ->
                    suspendEnablePackageViaBridge(context.requestEnableFrozenPackage, pkg)
                }
            }
        }
    }

    private fun handleOpenUrl(action: Action, context: ActionHandlerContext): ActionExecutionResult {
        val data = try {
            JsonSerializer.decodeFromString<OpenAppOrUrlData>(action.data)
        } catch (e: Exception) {
            null
        }
        if (data?.url?.isBlank() != false) return ActionExecutionResult.Ignored
        val success = QuickLaunchFacade.launchUrlDirect(
            context = context.appContext,
            data = data,
            miniWindowHorizontalBias = context.advancedSettings.miniWindowHorizontalBias,
            miniWindowVerticalBias = context.advancedSettings.miniWindowVerticalBias,
            miniWindowVerticalOffsetFraction = context.advancedSettings.miniWindowVerticalOffsetFraction,
            miniWindowWidthFraction = context.advancedSettings.miniWindowWidthFraction,
            miniWindowHeightFraction = context.advancedSettings.miniWindowHeightFraction,
            miniWindowOverrideBounds = context.advancedSettings.miniWindowOverrideBounds,
        )
        return if (success) ActionExecutionResult.Success else ActionExecutionResult.Failed("Launch URL failed")
    }

    private fun launchAppWithFrozenSupport(
        context: ActionHandlerContext,
        appInfo: hunoia.luno.quicklaunch.model.AppInfo,
        miniWindow: Boolean
    ) {
        context.scope.launch {
            if (context.advancedSettings.miniWindowOverrideBounds) {
                FreezeFacade.launchWithAutoUnfreeze(
                    context = context.appContext,
                    packageName = appInfo.packageName,
                    className = appInfo.className,
                    miniWindow = miniWindow,
                    miniWindowHorizontalBias = context.advancedSettings.miniWindowHorizontalBias,
                    miniWindowVerticalBias = context.advancedSettings.miniWindowVerticalBias,
                    miniWindowVerticalOffsetFraction = context.advancedSettings.miniWindowVerticalOffsetFraction,
                    miniWindowWidthFraction = context.advancedSettings.miniWindowWidthFraction,
                    miniWindowHeightFraction = context.advancedSettings.miniWindowHeightFraction,
                    miniWindowOverrideBounds = true,
                ) { _, pkg ->
                    suspendEnablePackageViaBridge(context.requestEnableFrozenPackage, pkg)
                }
            } else {
                FreezeFacade.launchWithAutoUnfreeze(
                    context = context.appContext,
                    packageName = appInfo.packageName,
                    className = appInfo.className,
                    miniWindow = miniWindow,
                ) { _, pkg ->
                    suspendEnablePackageViaBridge(context.requestEnableFrozenPackage, pkg)
                }
            }
        }
    }

    private suspend fun suspendEnablePackageViaBridge(
        requestEnableFrozenPackage: (String, (Boolean) -> Unit) -> Unit,
        packageName: String
    ): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        requestEnableFrozenPackage(packageName) { success ->
            cont.resume(success)
        }
    }
}
