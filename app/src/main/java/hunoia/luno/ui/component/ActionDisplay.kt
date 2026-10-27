package hunoia.luno.ui.component

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import hunoia.luno.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.SwapHoriz
import hunoia.luno.core.domain.StoredAction
import hunoia.luno.core.domain.actionPanelId
import hunoia.luno.core.domain.gridPanelId
import hunoia.luno.core.domain.libraryEntryId
import hunoia.luno.core.domain.launchPackageName
import hunoia.luno.core.domain.ActionLibraryEntry
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.quicklaunch.icon.rememberAppDrawable
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.ui.actionlibrary.IconPalette
import hunoia.luno.ui.actionselect.shortcutInfo
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import hunoia.luno.ui.navigation.LocalActionPanels
import hunoia.luno.ui.navigation.LocalGridPanels
import kotlin.math.min
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
    if (action.typeId == "app.launch") {
        return appLabel(action) ?: ""
    }
    if (action.typeId == "app.launchShortcut") {
        return action.shortcutInfo()?.label ?: ""
    }
    return ActionDefinitions.byTypeId(action.typeId)?.name
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
            return panel.name.ifBlank { stringResource(R.string.grid_panel_unnamed) }
        }
    }
    if (action.typeId == "app.launch") {
        val context = androidx.compose.ui.platform.LocalContext.current
        val packageName = action.launchPackageName()
        return remember(action, packageName) { context.appLabel(action) ?: "" }
    }
    if (action.typeId == "app.launchShortcut") {
        return action.shortcutInfo()?.label ?: ""
    }
    return ActionDefinitions.byTypeId(action.typeId)?.name
        ?: if (emptyIfNone) "" else stringResource(R.string.action_none)
}

/**
 * 文字/表情图标：动作库条目的 [ActionLibraryEntry.iconKey] 非 palette key 时，
 * 把原始字符串当文字/表情渲染，与 App/快捷方式的 String(Coil 模型) 区分开。
 */
data class TextIcon(val text: String)

/**
 * 文字/表情图标的统一渲染：在 [size] 见方容器内按需缩小字号，避免裁剪且横竖居中。
 * 直接用 titleLarge 的 Text 会因行高超出容器被切掉，emoji 还会偏上。
 */
@Composable
fun TextIconGlyph(
    text: String,
    size: Dp,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = MaterialTheme.typography.titleLarge.copy(
        color = color,
        lineHeight = TextUnit.Unspecified,
    )
    val sidePx = with(density) { size.toPx() }
    val style = remember(text, base, sidePx, density) {
        val measured = measurer.measure(text = AnnotatedString(text), style = base)
        val w = measured.size.width.toFloat()
        val h = measured.size.height.toFloat()
        if (w <= sidePx && h <= sidePx) {
            base
        } else {
            base.copy(fontSize = base.fontSize * min(sidePx / w, sidePx / h))
        }
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.wrapContentSize(Alignment.Center, unbounded = true),
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** 图标优先级：自定义文字/表情 > palette 图标 > 类型默认图标。 */
fun ActionLibraryEntry.resolvedIcon(): Any? {
    iconKey?.let { key ->
        val vector = IconPalette.byKey(key)
        if (vector != null) return vector
        if (key.isNotBlank()) return TextIcon(key)
    }
    return ActionDefinitions.byTypeId(typeId)?.icon
}

@Composable
fun actionIcon(action: StoredAction): Any? {
    // internal.libraryRef 在定义表里没有条目，不能落到 byTypeId，只能查动作库。
    action.libraryEntryId()?.let { refId ->
        return LocalActionLibraryEntries.current.firstOrNull { it.id == refId }?.resolvedIcon()
    }
    if (action.typeId == "app.launch") {
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
        val pkg = history.dropLast(1).lastOrNull()
        return rememberAppDrawable("app/$pkg") {
            pkg?.let { context.packageManager.getApplicationIcon(it) }
        } ?: Icons.Default.SwapHoriz
    }
    return ActionDefinitions.byTypeId(action.typeId)?.icon
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
