package hunoia.luno.quicklaunch.launch

import android.content.Context
import hunoia.luno.R
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.query.DisabledAppQuery
import hunoia.luno.bridge.feedback.showToast

object AppLaunchBypass {

    suspend fun launchWithAutoUnfreeze(
        context: Context,
        packageName: String,
        className: String,
        miniWindow: Boolean = false,
        miniWindowHorizontalBias: Float = 0f,
        miniWindowVerticalBias: Float = 0f,
        miniWindowWidthFraction: Float = 0.46f,
        miniWindowHeightFraction: Float = 0.74f,
        miniWindowOverrideBounds: Boolean = false,
        unfreezePackage: suspend (context: Context, packageName: String) -> Boolean = { _, _ -> true }
    ): Boolean {
        if (!ensureEnabled(context, packageName, unfreezePackage)) return false
        return QuickLaunchFacade.launchAppDirect(
            context, packageName, className, miniWindow,
            miniWindowHorizontalBias, miniWindowVerticalBias,
            miniWindowWidthFraction, miniWindowHeightFraction,
            miniWindowOverrideBounds,
        )
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
