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
        miniWindowVerticalOffsetFraction: Float = 0f,
        miniWindowWidthFraction: Float = 0.46f,
        miniWindowHeightFraction: Float = 0.74f,
        miniWindowOverrideBounds: Boolean = false,
        unfreezePackage: suspend (context: Context, packageName: String) -> Boolean = { _, _ -> true }
    ): Boolean {
        if (DisabledAppQuery.isDisabled(context, packageName)) {
            val undisabled = unfreezePackage(context, packageName)
            if (!undisabled) {
                showToast(R.string.enable_disabled_app_failed)
                return false
            }
            DisabledAppQuery.markEnabled(packageName)
            QuickLaunchFacade.invalidateLauncherCache()
        }
        return QuickLaunchFacade.launchAppDirect(
            context, packageName, className, miniWindow,
            miniWindowHorizontalBias, miniWindowVerticalBias,
            miniWindowVerticalOffsetFraction,
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
        if (DisabledAppQuery.isDisabled(context, packageName)) {
            val undisabled = unfreezePackage(context, packageName)
            if (!undisabled) {
                showToast(R.string.enable_disabled_app_failed)
                return false
            }
            DisabledAppQuery.markEnabled(packageName)
            QuickLaunchFacade.invalidateLauncherCache()
        }
        return QuickLaunchFacade.launchAppActivityDirect(context, packageName, className)
    }
}
