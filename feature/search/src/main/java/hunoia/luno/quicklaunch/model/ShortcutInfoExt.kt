package hunoia.luno.quicklaunch.model

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.drawable.updateBounds
import androidx.core.graphics.scale
import hunoia.luno.bridge.ui.DensityProvider
import hunoia.luno.quicklaunch.icon.rememberAppDrawable
import hunoia.luno.quicklaunch.model.LauncherInfo
import kotlin.math.min

val LauncherInfo.componentName: ComponentName get() = ComponentName.createRelative(packageName, className)

val LauncherInfo.qualifiedName: String get() = "$packageName/$className"

val LauncherInfo.icon: Drawable? @Composable get() {
    val context = LocalContext.current
    // 跨进程 PM 查询：缓存 + IO 异步，组合期不再同步阻塞（见 rememberAppDrawable）。
    return rememberAppDrawable("info/$qualifiedName") { getIcon(context) }
}

fun LauncherInfo.getIcon(context: Context): Drawable? {
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

val LauncherInfo.ShortcutInfo.componentName: ComponentName get() = ComponentName.createRelative(packageName, className)

val LauncherInfo.ShortcutInfo.qualifiedName: String get() = "$packageName/$className"

val LauncherInfo.ShortcutInfo.qualifiedNameWithIntents: String get() = "$packageName/$className(${intents.joinToString()})"

val LauncherInfo.ShortcutInfo.icon: Drawable? @Composable get() {
    val context = LocalContext.current
    // 快捷方式图标含磁盘解码/位图栅格化，更不该在组合期同步做；key 区分图标来源。
    return rememberAppDrawable("shortcut/$packageName/${iconPath ?: iconRes}") { getIcon(context) }
}

fun LauncherInfo.ShortcutInfo.getIcon(context: Context): Drawable? {
    return try {
        var resources = context.resources
        val model = iconBitmap
            ?: if (iconPath != null) {
                BitmapFactory.decodeFile(iconPath)
            } else {
                resources = context.packageManager.getResourcesForApplication(packageName)
                BitmapFactory.decodeResource(resources, iconRes)
                    ?: ResourcesCompat.getDrawable(resources, iconRes, context.theme)
            }

        val stdSize = DensityProvider.dp2px(48f)
        if (model is Bitmap) {
            val minSize = min(model.width, model.height)
            if (minSize < stdSize) {
                val ratio = model.width.toFloat() / model.height.toFloat()
                model.scale(stdSize, (stdSize / ratio).toInt()).toDrawable(resources)
            } else {
                model.toDrawable(resources)
            }
        } else {
            model as Drawable
            model.updateBounds(0, 0, stdSize, stdSize)
            val bitmap = createBitmap(stdSize, stdSize, Bitmap.Config.ARGB_8888)
            model.draw(Canvas(bitmap))
            bitmap.toDrawable(resources)
        }
    } catch (ignored: Exception) {
        null
    }
}
