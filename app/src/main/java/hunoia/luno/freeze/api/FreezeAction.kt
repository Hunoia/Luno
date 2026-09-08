package hunoia.luno.freeze.api

import android.content.Context
import hunoia.luno.config.ConfigProvider
import hunoia.luno.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FreezeResult(
    val success: Boolean,
    val packageName: String,
    val wasFrozen: Boolean,
    val nowFrozen: Boolean
)

data class OneKeyFreezeResult(
    val oneKeyCount: Int,
    val targetCount: Int,
    val candidateCount: Int,
    val successCount: Int
)

data class BatchFreezeResult(
    val requestedCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val attemptedCount: Int
)

object FreezeAction {

    fun isShizukuReady(): Boolean = ShizukuManager.currentStatus().isReady

    suspend fun checkAndFreeze(context: Context, packageName: String): FreezeResult {
        val wasFrozen = FreezeState.isFrozen(context, packageName)
        if (wasFrozen) {
            return FreezeResult(true, packageName, wasFrozen = true, nowFrozen = true)
        }
        val result = withContext(Dispatchers.IO) {
            ShizukuManager.disablePackage(packageName)
        }
        if (result.success) FreezeState.markFrozen(packageName)
        return FreezeResult(result.success, packageName, wasFrozen = false, nowFrozen = result.success)
    }

    suspend fun checkAndUnfreeze(context: Context, packageName: String): FreezeResult {
        val wasFrozen = FreezeState.isFrozen(context, packageName)
        if (!wasFrozen) {
            return FreezeResult(true, packageName, wasFrozen = false, nowFrozen = false)
        }
        val result = withContext(Dispatchers.IO) {
            ShizukuManager.enablePackage(packageName)
        }
        if (result.success) FreezeState.markUnfrozen(packageName)
        return FreezeResult(result.success, packageName, wasFrozen = true, nowFrozen = !result.success)
    }

    suspend fun batchFreeze(context: Context, packageNames: List<String>): BatchFreezeResult {
        val candidates = if (FreezeState.isCacheReady()) {
            val frozenState = FreezeState.queryFrozenStateByPackage(context, packageNames)
            packageNames.filter { frozenState[it] != true }
        } else {
            packageNames
        }
        if (candidates.isEmpty()) {
            return BatchFreezeResult(packageNames.size, 0, 0, 0)
        }
        val batchResult = withContext(Dispatchers.IO) {
            ShizukuManager.executeBatch(candidates, disable = true)
        }
        val successCount = if (batchResult.fallbackTriggered) {
            batchResult.fallbackSuccessCount
        } else {
            batchResult.successCount
        }
        FreezeState.markBatchFrozen(candidates)
        return BatchFreezeResult(
            requestedCount = packageNames.size,
            successCount = successCount,
            failedCount = candidates.size - successCount,
            attemptedCount = candidates.size
        )
    }

    suspend fun batchUnfreeze(context: Context, packageNames: List<String>): BatchFreezeResult {
        val candidates = if (FreezeState.isCacheReady()) {
            val frozenState = FreezeState.queryFrozenStateByPackage(context, packageNames)
            packageNames.filter { frozenState[it] == true }
        } else {
            packageNames
        }
        if (candidates.isEmpty()) {
            return BatchFreezeResult(packageNames.size, 0, 0, 0)
        }
        val batchResult = withContext(Dispatchers.IO) {
            ShizukuManager.executeBatch(candidates, disable = false)
        }
        val successCount = if (batchResult.fallbackTriggered) {
            batchResult.fallbackSuccessCount
        } else {
            batchResult.successCount
        }
        return BatchFreezeResult(
            requestedCount = packageNames.size,
            successCount = successCount,
            failedCount = candidates.size - successCount,
            attemptedCount = candidates.size
        )
    }

    suspend fun oneKeyFreeze(context: Context): OneKeyFreezeResult = withContext(Dispatchers.IO) {
        val settings = ConfigProvider.getFrozenAppSettings()
        val oneKeySet = settings.oneKeyPackageNames

        val beforeCount = FreezeState.queryFrozenApplications(context).size
        val batchResult = batchFreeze(context, oneKeySet.toList())
        val afterCount = FreezeState.queryFrozenApplications(context).size

        OneKeyFreezeResult(
            oneKeyCount = oneKeySet.size,
            targetCount = oneKeySet.size,
            candidateCount = batchResult.attemptedCount,
            successCount = maxOf(0, afterCount - beforeCount)
        )
    }

    suspend fun oneKeyFreezeForService(context: Context): OneKeyFreezeResult {
        return oneKeyFreeze(context)
    }

    suspend fun oneKeyUnfreeze(context: Context, targets: List<String>): OneKeyFreezeResult = withContext(Dispatchers.IO) {
        val candidates = if (FreezeState.isCacheReady()) {
            val frozenState = FreezeState.queryFrozenStateByPackage(context, targets)
            targets.filter { frozenState[it] == true }
        } else {
            targets
        }

        val successCount = if (candidates.isNotEmpty()) {
            val batch = ShizukuManager.executeBatch(candidates, disable = false)
            FreezeState.markBatchUnfrozen(candidates)
            batch.successCount
        } else 0

        OneKeyFreezeResult(
            oneKeyCount = targets.size,
            targetCount = targets.size,
            candidateCount = candidates.size,
            successCount = successCount
        )
    }

    fun computeOneKeyTargetsInRange(
        apps: List<hunoia.luno.quicklaunch.model.AppInfo>,
        oneKeyPackageNames: Set<String>
    ): List<String> {
        return apps
            .asSequence()
            .map { it.packageName }
            .distinct()
            .filter { it in oneKeyPackageNames }
            .toList()
    }
}
