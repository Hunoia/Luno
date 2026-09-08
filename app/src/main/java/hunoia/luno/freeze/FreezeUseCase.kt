package hunoia.luno.freeze

import hunoia.luno.core.AppContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FreezeUseCase {

    suspend fun oneKeyFreeze(): FrozenChangeResult = withContext(Dispatchers.IO) {
        val context = AppContext.get()
        val beforeCount = FreezeFacade.queryFrozenAppsOnIo(context).size
        FreezeFacade.oneKeyFreeze(context)
        val afterCount = FreezeFacade.queryFrozenAppsOnIo(context).size
        val frozenCount = afterCount - beforeCount
        FrozenChangeResult(afterCount, frozenCount)
    }

    suspend fun oneKeyUnfreeze(): FrozenChangeResult = withContext(Dispatchers.IO) {
        val context = AppContext.get()
        val frozenApps = FreezeFacade.queryFrozenAppsOnIo(context)
        val targets = frozenApps.map { it.packageName }
        val beforeCount = frozenApps.size
        FreezeFacade.oneKeyUnfreeze(context, targets)
        val afterCount = FreezeFacade.queryFrozenAppsOnIo(context).size
        val unfrozenCount = beforeCount - afterCount
        FrozenChangeResult(afterCount, unfrozenCount)
    }

    suspend fun queryFrozenCount(): Int = withContext(Dispatchers.IO) {
        FreezeFacade.queryFrozenAppsOnIo(AppContext.get()).size
    }
}

data class FrozenChangeResult(
    val totalAfter: Int,
    val changed: Int,
)
