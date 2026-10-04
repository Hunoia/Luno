package hunoia.luno.quicklaunch.model

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import hunoia.luno.quicklaunch.model.AppInfo
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class AppInfo(
    val packageName: String,
    val className: String,
    val label: String,
    val miniWindow: Boolean = false,
)

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
    } catch (_: Exception) {
        // 取不到图标是常态（应用被卸载/跨用户/权限限制），降级为 null 而非视为异常
        null
    }
}
