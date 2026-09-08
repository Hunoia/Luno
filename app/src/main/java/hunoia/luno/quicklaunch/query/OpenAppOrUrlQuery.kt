package hunoia.luno.quicklaunch.query

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import hunoia.luno.bridge.queryIntentActivitiesCompat

data class LauncherAppOption(
    val packageName: String,
    val launcherClassName: String,
    val label: String
)

data class ActivityOption(
    val className: String,
    val label: String
)

object OpenAppOrUrlQuery {

    fun queryLauncherApps(context: Context): List<LauncherAppOption> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val result = mutableListOf<LauncherAppOption>()
        val seenPackages = mutableSetOf<String>()
        for (resolveInfo in packageManager.queryIntentActivitiesCompat(intent, PackageManager.MATCH_ALL)) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val packageName = activityInfo.packageName ?: continue
            if (!seenPackages.add(packageName)) continue
            result += LauncherAppOption(
                packageName = packageName,
                launcherClassName = activityInfo.name,
                label = activityInfo.loadLabel(packageManager).toString()
            )
        }
        return result.sortedWith(compareBy<LauncherAppOption> { it.label }.thenBy { it.packageName })
    }

    fun loadApplicationIcon(context: Context, packageName: String) = LauncherIconQuery.loadApplicationIcon(context, packageName)

    fun queryActivityOptions(
        context: Context,
        packageName: String,
        selectedActivityClassName: String,
        launcherClassName: String?
    ): List<ActivityOption> {
        if (packageName.isBlank()) return emptyList()
        val packageManager = context.packageManager
        val exportedActivities = try {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
            )
        } catch (_: Exception) {
            null
        }?.activities
            ?.filter { it.exported }
            ?.map {
                ActivityOption(
                    className = it.name,
                    label = it.loadLabel(packageManager).toString()
                )
            }
            .orEmpty()

        return if (exportedActivities.isNotEmpty()) {
            exportedActivities
                .distinctBy { it.className }
                .sortedWith(compareBy<ActivityOption> { it.label }.thenBy { it.className })
        } else {
            listOfNotNull(
                selectedActivityClassName.takeIf { it.isNotBlank() }?.let { ActivityOption(it, it) },
                launcherClassName?.takeIf { it.isNotBlank() }?.let { ActivityOption(it, it) }
            ).distinctBy { it.className }
        }
    }

    fun formatActivityOptionText(option: ActivityOption, packageName: String): String {
        return if (packageName.isNotBlank() && option.className.startsWith("$packageName.")) {
            option.className.removePrefix("$packageName.")
        } else {
            option.className
        }
    }
}
