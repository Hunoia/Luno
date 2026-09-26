package hunoia.luno.quicklaunch.query

import android.content.Context
import android.content.Intent.ShortcutIconResource
import android.graphics.drawable.Drawable

object LauncherIconQuery {
    fun loadApplicationIcon(context: Context, packageName: String): Drawable? {
        return runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    fun resolveShortcutIconResourceId(context: Context, iconResource: ShortcutIconResource): Int {
        return runCatching {
            val resources = context.packageManager.getResourcesForApplication(iconResource.packageName)
            @Suppress("DiscouragedApi")
            resources.getIdentifier(iconResource.resourceName, null, null)
        }.getOrDefault(0)
    }
}
