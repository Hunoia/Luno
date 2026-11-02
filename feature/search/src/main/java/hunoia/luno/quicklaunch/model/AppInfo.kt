package hunoia.luno.quicklaunch.model

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import hunoia.luno.bridge.system.AppIcons
import hunoia.luno.ui.icon.rememberAppDrawable

/**
 * AppInfo 的平台与 Compose 扩展。
 *
 * 数据类本身已下沉 `hunoia.luno.model`，这里用 typealias 保持既有引用（
 * `import hunoia.luno.quicklaunch.model.AppInfo`）不漂移；配置层直接依赖 model 包。
 */
typealias AppInfo = hunoia.luno.core.domain.AppInfo

val AppInfo.componentName: ComponentName get() = ComponentName.createRelative(packageName, className)

val AppInfo.qualifiedName: String get() = "$packageName/$className"

/**
 * 跨用户唯一标识：分身与主应用包名/Activity 相同，只有 userId 不同。
 * 列表 key、选中比对、图标缓存用它；匹配类配置仍按包名（见架构：分身共享主应用规则）。
 */
val AppInfo.identity: String
    get() = if (userId == 0) qualifiedName else "$qualifiedName#$userId"

val AppInfo.icon: Drawable? @Composable get() {
    val context = LocalContext.current
    // 跨进程 PM 查询：缓存 + IO 异步，组合期不再同步阻塞（见 rememberAppDrawable）。
    return rememberAppDrawable("info/$identity") { getIcon(context) }
}

fun AppInfo.getIcon(context: Context): Drawable? {
    // 取不到图标是常态（应用被卸载/跨用户/权限限制），降级为 null 而非视为异常。
    return AppIcons.drawable(context, packageName, className, userId)
}
