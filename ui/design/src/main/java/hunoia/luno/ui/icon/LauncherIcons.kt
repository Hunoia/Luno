package hunoia.luno.ui.icon

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.drawable.updateBounds
import androidx.core.graphics.scale
import hunoia.luno.bridge.launcher.LauncherInfo
import hunoia.luno.bridge.ui.DensityProvider
import kotlin.math.min

/**
 * 启动器条目/快捷方式的图标：跨进程 PM 查询与磁盘解码都走 [rememberAppDrawable]，
 * 组合期不做同步 IO。归 `:ui:design`：这是图标原子组件，查询侧与动作展示共用。
 */

val LauncherInfo.icon: Drawable? @Composable get() {
    val context = LocalContext.current
    // 跨进程 PM 查询：缓存 + IO 异步，组合期不再同步阻塞（见 rememberAppDrawable）。
    return rememberAppDrawable("info/$packageName/$className") { getIcon(context) }
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

/**
 * 按包名取应用图标：无障碍服务列表这类「只有包名」的 PM 枚举场景，
 * 与 [LauncherInfo.icon] 共用 [rememberAppDrawable] 的缓存与异步加载。
 */
@Composable
fun rememberPackageIcon(packageName: String): Drawable? {
    val context = LocalContext.current
    return rememberAppDrawable("pkg/$packageName") {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
}

/**
 * [rememberPackageIcon] 的行图标位图版：先等比缩到 [sizePx] 再栅格化，
 * 取不到图返回 null（调用方按「该行无图标」降级）。
 */
@Composable
fun rememberPackageIconBitmap(packageName: String, sizePx: Int): ImageBitmap? {
    val drawable = rememberPackageIcon(packageName) ?: return null
    return remember(drawable, sizePx) { drawable.toIconBitmap(sizePx) }
}

/**
 * Drawable → 行首图标位图，只缩不放（同 SearchIconCache 口径）；取不到返回 null。
 * 位图分配/绘制失败只损失一个行图标，不该带崩整页。
 */
fun Drawable.toIconBitmap(sizePx: Int): ImageBitmap? {
    val intrinsic = maxOf(intrinsicWidth, intrinsicHeight)
    if (intrinsic <= 0 || sizePx <= 0) return null
    val side = minOf(sizePx, intrinsic)
    // 源图不大于目标尺寸：直接共用原位图，不放大重画。
    if (this is BitmapDrawable && side >= intrinsic) return bitmap?.asImageBitmap()
    return runCatching {
        setBounds(0, 0, side, side)
        Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            .also { draw(Canvas(it)) }
            .asImageBitmap()
    }.getOrNull()
}

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
