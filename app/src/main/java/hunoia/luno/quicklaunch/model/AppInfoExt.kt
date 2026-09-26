package hunoia.luno.quicklaunch.model

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import hunoia.luno.quicklaunch.model.AppInfo

val AppInfo.componentName: ComponentName get() = ComponentName.createRelative(packageName, className)

val AppInfo.qualifiedName: String get() = "$packageName/$className"

val AppInfo.icon: Drawable? @Composable get() {
    val context = LocalContext.current
    return remember(this, context) { getIcon(context) }
}

fun AppInfo.getIcon(context: Context): Drawable? {
    return try {
        val pkgManager = context.packageManager
        if (className.isNotEmpty()) {
            pkgManager.getActivityIcon(ComponentName.createRelative(packageName, className))
        } else {
            pkgManager.getApplicationIcon(packageName)
        }
    } catch (ignored: Exception) {
        null
    }
}
