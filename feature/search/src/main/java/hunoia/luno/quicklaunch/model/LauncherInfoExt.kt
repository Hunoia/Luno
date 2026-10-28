package hunoia.luno.quicklaunch.model

import android.content.ComponentName
import hunoia.luno.bridge.launcher.LauncherInfo

/**
 * 启动器条目的标识扩展。
 *
 * 模型已下沉 `:core:bridge`；这些扩展留在查询侧包名（`hunoia.luno.quicklaunch.model`）里，
 * 因为搜索去重、选择页、图标缓存都以 `qualifiedName` 为 key，改动面大而收益为零。
 */

val LauncherInfo.componentName: ComponentName get() = ComponentName.createRelative(packageName, className)

val LauncherInfo.qualifiedName: String get() = "$packageName/$className"

val LauncherInfo.ShortcutInfo.componentName: ComponentName
    get() = ComponentName.createRelative(packageName, className)

val LauncherInfo.ShortcutInfo.qualifiedName: String get() = "$packageName/$className"

val LauncherInfo.ShortcutInfo.qualifiedNameWithIntents: String
    get() = "$packageName/$className(${intents.joinToString()})"
