package hunoia.luno.freeze.api

import android.content.Context
import hunoia.luno.R
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.bridge.feedback.showToast

object FreezeLaunch {

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
        if (FreezeState.isFrozen(context, packageName)) {
            val unfrozen = unfreezePackage(context, packageName)
            if (!unfrozen) {
                showToast(R.string.enable_frozen_app_failed)
                return false
            }
            FreezeState.markUnfrozen(packageName)
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
        if (FreezeState.isFrozen(context, packageName)) {
            val unfrozen = unfreezePackage(context, packageName)
            if (!unfrozen) {
                showToast(R.string.enable_frozen_app_failed)
                return false
            }
            FreezeState.markUnfrozen(packageName)
            QuickLaunchFacade.invalidateLauncherCache()
        }
        return QuickLaunchFacade.launchAppActivityDirect(context, packageName, className)
    }
}
