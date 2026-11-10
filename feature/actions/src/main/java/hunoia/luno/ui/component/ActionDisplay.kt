package hunoia.luno.ui.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import hunoia.luno.actions.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.SwapHoriz
import hunoia.luno.actions.resolvedIcon
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.actionPanelId
import hunoia.luno.core.domain.gridPanelId
import hunoia.luno.core.domain.libraryEntryId
import hunoia.luno.core.domain.launchPackageName
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.ui.icon.icon
import hunoia.luno.ui.icon.rememberAppDrawable
import hunoia.luno.ui.icon.rememberAppLabel
import hunoia.luno.action.model.shortcutInfo
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import hunoia.luno.ui.navigation.LocalActionPanels
import hunoia.luno.ui.navigation.LocalGridPanels
import hunoia.luno.ui.navigation.LocalSubGestures
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import hunoia.luno.action.model.Action

fun Context.actionText(
    action: StoredAction,
    emptyIfNone: Boolean = true,
    actionLibraryEntries: List<ActionLibraryEntry> = emptyList()
): String {
    action.libraryEntryId()?.let { refId ->
        actionLibraryEntries.firstOrNull { it.id == refId }?.name?.takeIf { it.isNotBlank() }?.let { return it }
        if (actionLibraryEntries.isNotEmpty()) {
            return getString(R.string.action_library_missing)
        }
    }
    if (action.typeId == hunoia.luno.core.domain.AppLaunchAction.TYPE_ID) {
        return appLabel(action) ?: ""
    }
    if (action.typeId == "app.launchShortcut") {
        return action.shortcutInfo()?.label ?: ""
    }
    val definition = ActionDefinitions.byTypeId(action.typeId)
    // 合并式动作（快捷开关/打开设置页）按变体展示：与 Composable 版同一个口径。
    definition?.resolvedVariant(action.params)?.let { return it.label }
    return definition?.name
        ?: if (emptyIfNone) "" else getString(R.string.action_none)
}

/** app.launch 显示名：读已安装应用 label，读不到落包名。 */
private fun Context.appLabel(action: StoredAction): String? {
    val packageName = action.launchPackageName() ?: return null
    return runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull() ?: packageName
}

@Composable
fun actionText(
    action: StoredAction,
    emptyIfNone: Boolean = true,
    actionLibraryEntries: List<ActionLibraryEntry> = emptyList()
): String {
    action.libraryEntryId()?.let { refId ->
        actionLibraryEntries.firstOrNull { it.id == refId }?.name?.takeIf { it.isNotBlank() }?.let { return it }
        if (actionLibraryEntries.isNotEmpty()) {
            return stringResource(R.string.action_library_missing)
        }
    }
    // 动作面板显示具名面板而不是类型名；面板列表与动作库同走 Local* 通路
    // （主进程与 :service 运行时都提供了）。
    action.actionPanelId()?.let { panelId ->
        LocalActionPanels.current.firstOrNull { it.id == panelId }
            ?.name?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    // 网格面板同上；有面板但没起名给「未命名」，不在列表里则落空（已删）。
    action.gridPanelId()?.let { panelId ->
        val panels = LocalGridPanels.current
        panels.firstOrNull { it.id == panelId }?.let { panel ->
            return panel.name.ifBlank { stringResource(hunoia.luno.actions.R.string.grid_panel_unnamed) }
        }
    }
    if (action.typeId == Action.SubGesture.typeId) {
        val id = (action.params["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val name = LocalSubGestures.current.firstOrNull { it.id == id }?.name
        if (!name.isNullOrBlank()) return name
    }
    if (action.typeId == hunoia.luno.core.domain.AppLaunchAction.TYPE_ID) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val packageName = action.launchPackageName()
        // 与 actionIcon 的 rememberAppDrawable 同策略：首帧吃缓存，未命中 IO 异步补载，组合期零 Binder。
        if (packageName == null) return ""
        return rememberAppLabel(
            key = "launchLabel/$packageName",
            fallback = "",
        ) {
            context.appLabel(action) ?: ""
        }
    }
    if (action.typeId == "app.launchShortcut") {
        return action.shortcutInfo()?.label ?: ""
    }
    val definition = ActionDefinitions.byTypeId(action.typeId)
    // 合并式动作（快捷开关等）按变体展示：按钮上直接写「勿扰」而不是「快捷开关」。
    definition?.resolvedVariant(action.params)?.let { return it.label }
    return definition?.name
        ?: if (emptyIfNone) "" else stringResource(R.string.action_none)
}


@Composable
fun actionIcon(action: StoredAction): Any? {
    // internal.libraryRef 在定义表里没有条目，不能落到 byTypeId，只能查动作库。
    action.libraryEntryId()?.let { refId ->
        return LocalActionLibraryEntries.current.firstOrNull { it.id == refId }?.resolvedIcon()
    }
    if (action.typeId == hunoia.luno.core.domain.AppLaunchAction.TYPE_ID) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val packageName = action.launchPackageName()
        val className = (action.params["className"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
        // 取图标是跨进程 PM 查询：走缓存 + IO 异步，组合期不再同步阻塞（面板首帧 N 行 × Binder）。
        return rememberAppDrawable("launch/$packageName/$className") {
            if (className.isNotEmpty()) {
                context.packageManager.getActivityIcon(
                    android.content.ComponentName.createRelative(packageName!!, className)
                )
            } else {
                context.packageManager.getApplicationIcon(packageName!!)
            }
        } ?: Icons.Default.Android
    }
    if (action.typeId == "app.launchShortcut") {
        return action.shortcutInfo()?.icon
    }
    if (action.typeId == "internal.previousApp") {
        // 图标跟随当前「上一个应用」，一眼看出会切到哪。必须 collect 而不是读 .value：
        // RecentAppsHub 更新时要触发重组，否则图标停在首次组合那一刻的应用。
        val context = androidx.compose.ui.platform.LocalContext.current
        val history by hunoia.luno.runtime.environment.RecentAppsHub.history.collectAsState()
        val foreground by hunoia.luno.runtime.environment.RecentAppsHub.foreground.collectAsState()
        // 与 previousApp 同一口径：去掉当前前台那条，末尾就是会切到的那一项。
        // 分身与主应用同包名，图标按 userId 取，避免总是画主应用。
        val app = history.lastOrNull { it != foreground }
        return rememberAppDrawable("previous/${app?.blockKey}") {
            app?.let {
                hunoia.luno.bridge.system.AppIcons.drawable(
                    context,
                    it.packageName,
                    className = "",
                    userId = it.userId,
                )
            }
        } ?: Icons.Default.SwapHoriz
    }
    val definition = ActionDefinitions.byTypeId(action.typeId)
    return definition?.resolvedVariant(action.params)?.icon ?: definition?.icon
}

@Composable
fun DirectionActions.actionTextCompose(): String =
    actions.values
        .map { it.actionTextCompose(true) }
        .filter { it.isNotEmpty() }
        .joinToString(separator = ",")

/** 单值展示：空槽落 `internal.none` 的显示名（「无」）。 */
@Composable
fun StoredAction?.actionTextCompose(emptyIfNone: Boolean = false): String {
    val entries = LocalActionLibraryEntries.current
    val value = this ?: StoredAction.of(Action.NONE_TYPE_ID)
    return actionText(value, emptyIfNone = emptyIfNone, actionLibraryEntries = entries)
}
