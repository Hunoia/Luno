package hunoia.luno.quicklaunch.query

import android.content.Context
import android.content.Intent.ShortcutIconResource

object LauncherIconQuery {

    fun resolveShortcutIconResourceId(context: Context, iconResource: ShortcutIconResource): Int {
        return runCatching {
            val resources = context.packageManager.getResourcesForApplication(iconResource.packageName)
            @Suppress("DiscouragedApi")
            resources.getIdentifier(iconResource.resourceName, null, null)
        }.getOrDefault(0)
    }
}
