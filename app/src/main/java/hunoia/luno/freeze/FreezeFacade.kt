package hunoia.luno.freeze

import android.content.Context
import hunoia.luno.freeze.api.BatchFreezeResult
import hunoia.luno.freeze.api.FreezeAction
import hunoia.luno.freeze.api.FreezeLaunch
import hunoia.luno.freeze.api.FreezeResult
import hunoia.luno.freeze.api.FreezeState
import hunoia.luno.freeze.api.OneKeyFreezeResult
import hunoia.luno.quicklaunch.QuickLaunchFacade
import hunoia.luno.quicklaunch.model.AppInfo
import hunoia.luno.quicklaunch.query.QuickAppLauncherAppList

object FreezeFacade {

    fun isShizukuReady(): Boolean = FreezeAction.isShizukuReady()

    fun isFrozen(context: Context, packageName: String): Boolean =
        FreezeState.isFrozen(context, packageName)

    suspend fun freeze(context: Context, packageName: String): FreezeResult =
        FreezeAction.checkAndFreeze(context, packageName)

    suspend fun unfreeze(context: Context, packageName: String): FreezeResult =
        FreezeAction.checkAndUnfreeze(context, packageName)

    suspend fun batchFreeze(context: Context, packageNames: List<String>): BatchFreezeResult =
        FreezeAction.batchFreeze(context, packageNames)

    suspend fun batchUnfreeze(context: Context, packageNames: List<String>): BatchFreezeResult =
        FreezeAction.batchUnfreeze(context, packageNames)

    suspend fun oneKeyFreeze(context: Context): OneKeyFreezeResult =
        FreezeAction.oneKeyFreeze(context)

    suspend fun oneKeyUnfreeze(context: Context, targets: List<String>): OneKeyFreezeResult =
        FreezeAction.oneKeyUnfreeze(context, targets)

    suspend fun oneKeyFreezeForService(context: Context): OneKeyFreezeResult =
        FreezeAction.oneKeyFreezeForService(context)

    fun queryFrozenApps(context: Context): List<AppInfo> =
        FreezeState.queryFrozenApplications(context)

    suspend fun queryFrozenAppsOnIo(context: Context): List<AppInfo> =
        FreezeState.queryFrozenApplicationsOnIo(context)

    fun queryFrozenStateByPackage(context: Context, packageNames: Collection<String>): Map<String, Boolean> =
        FreezeState.queryFrozenStateByPackage(context, packageNames)

    fun computeOneKeyTargets(apps: List<AppInfo>, oneKeyPackageNames: Set<String>): List<String> =
        FreezeAction.computeOneKeyTargetsInRange(apps, oneKeyPackageNames)

    fun invalidateFrozenCache() {
        FreezeState.invalidateFrozenCache()
    }

    fun queryQuickAppLauncherApps(context: Context): QuickAppLauncherAppList {
        val frozenApps = FreezeState.queryFrozenApplications(context)
        return QuickLaunchFacade.queryCombinedQuickAppList(context, frozenApps)
    }

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
        unfreezePackage: suspend (Context, String) -> Boolean = { _, _ -> true },
    ): Boolean {
        return FreezeLaunch.launchWithAutoUnfreeze(
            context, packageName, className,
            miniWindow, miniWindowHorizontalBias,
            miniWindowVerticalBias, miniWindowVerticalOffsetFraction,
            miniWindowWidthFraction, miniWindowHeightFraction,
            miniWindowOverrideBounds, unfreezePackage,
        )
    }

    suspend fun launchActivityWithAutoUnfreeze(
        context: Context,
        packageName: String,
        className: String,
        unfreezePackage: suspend (Context, String) -> Boolean = { _, _ -> true },
    ): Boolean {
        return FreezeLaunch.launchActivityWithAutoUnfreeze(
            context, packageName, className, unfreezePackage,
        )
    }
}
