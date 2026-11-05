package hunoia.luno.runtime

import android.content.Context
import hunoia.luno.core.domain.ActivityOptionItem
import hunoia.luno.core.domain.ActivityOptionsSource
import hunoia.luno.quicklaunch.QuickLaunchFacade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 活动列表查询的实现：把 `:feature:search` 的平台查询收在这一层，
 * 让 `:feature:actions` 只依赖 `:core:domain` 的契约。
 */
class QuickLaunchActivityOptionsSource(
    private val context: Context,
) : ActivityOptionsSource {

    override fun launcherPackageActivity(packageName: String): String? =
        QuickLaunchFacade.queryLauncherAppOptions(context)
            .firstOrNull { it.packageName == packageName }
            ?.launcherClassName

    override suspend fun activities(
        packageName: String,
        launcherActivity: String?,
    ): List<ActivityOptionItem> = withContext(Dispatchers.IO) {
        QuickLaunchFacade.queryActivityOptions(
            context = context,
            packageName = packageName,
            selectedActivityClassName = "",
            launcherClassName = launcherActivity.orEmpty(),
        ).map { option ->
            ActivityOptionItem(
                className = option.className,
                label = QuickLaunchFacade.formatActivityOptionText(option, packageName),
            )
        }
    }
}
