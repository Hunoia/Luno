package hunoia.luno.ui.component

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource

import hunoia.luno.core.AppContext
import hunoia.luno.R
import hunoia.luno.config.model.Action
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.api.appInfo
import hunoia.luno.action.api.shortcutInfo
import hunoia.luno.action.api.ActionFacade
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.config.model.actionLibraryRefId
import hunoia.luno.config.model.newActionData
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.ui.actionlibrary.IconPalette
import hunoia.luno.ui.navigation.LocalActionLibraryEntries

fun Context.actionText(
    action: Action,
    emptyIfNone: Boolean = true,
    actionLibraryEntries: List<NewActionLibraryEntry> = emptyList()
): String {
    val refId = action.actionLibraryRefId()
    if (refId != null) {
        actionLibraryEntries.firstOrNull { it.id == refId }?.name?.takeIf { it.isNotBlank() }?.let { return it }
        if (actionLibraryEntries.isNotEmpty()) {
            return getString(R.string.action_library_missing)
        }
    }
    action.newActionData()?.let { stored ->
        return ActionDefinitions.byTypeId(stored.typeId)?.name ?: ""
    }
    return when (action.value) {
        ActionFacade.EXTRA_LAUNCH_APP -> action.appInfo?.label ?: ""
        ActionFacade.EXTRA_LAUNCH_SHORTCUT -> action.shortcutInfo?.label ?: ""
        else -> {
            val def = ActionFacade.byId(action.value)
            if (def != null) getString(def.titleResId) else if (emptyIfNone) "" else getString(R.string.action_none)
        }
    }
}

@Composable
fun actionText(
    action: Action,
    emptyIfNone: Boolean = true,
    actionLibraryEntries: List<NewActionLibraryEntry> = emptyList()
): String {
    val refId = action.actionLibraryRefId()
    if (refId != null) {
        actionLibraryEntries.firstOrNull { it.id == refId }?.name?.takeIf { it.isNotBlank() }?.let { return it }
        if (actionLibraryEntries.isNotEmpty()) {
            return stringResource(R.string.action_library_missing)
        }
    }
    action.newActionData()?.let { stored ->
        return ActionDefinitions.byTypeId(stored.typeId)?.name ?: ""
    }
    return when (action.value) {
        ActionFacade.EXTRA_LAUNCH_APP -> action.appInfo?.label ?: ""
        ActionFacade.EXTRA_LAUNCH_SHORTCUT -> action.shortcutInfo?.label ?: ""
        else -> {
            val def = ActionFacade.byId(action.value)
            if (def != null) stringResource(def.titleResId) else if (emptyIfNone) "" else stringResource(R.string.action_none)
        }
    }
}

/**
 * 文字/表情图标：动作库条目 [NewActionLibraryEntry.iconKey] 非 palette key 时，
 * 把原始字符串当作文字/表情渲染，与 App/快捷方式的 String(Coil 模型) 区分开。
 */
data class TextIcon(val text: String)

/**
 * 解析动作库条目的展示图标：自定义文字/表情 > palette 图标 > 类型默认图标。
 */
fun NewActionLibraryEntry.resolvedIcon(): Any? {
    iconKey?.let { key ->
        val vector = IconPalette.byKey(key)
        if (vector != null) return vector
        if (key.isNotBlank()) return TextIcon(key)
    }
    return ActionDefinitions.byTypeId(typeId)?.icon
}

@Composable
fun actionIcon(action: Action): Any? {
    action.newActionData()?.let { stored ->
        return ActionDefinitions.byTypeId(stored.typeId)?.icon
    }
    action.actionLibraryRefId()?.let { refId ->
        val entries = LocalActionLibraryEntries.current
        return entries.firstOrNull { it.id == refId }?.resolvedIcon()
    }
    return when (action.value) {
        ActionFacade.EXTRA_LAUNCH_APP -> action.appInfo?.icon ?: Icons.Default.Android
        ActionFacade.EXTRA_LAUNCH_SHORTCUT -> action.shortcutInfo?.icon ?: Icons.Default.Android
        else -> ActionFacade.byId(action.value)?.icon
    }
}

@Composable
fun DirectionActions.actionTextCompose(): String =
    actions.values
        .map { it.actionTextCompose(true) }
        .filter { it.isNotEmpty() }
        .joinToString(separator = ",")

@Composable
fun List<Action>.actionTextCompose(emptyIfNone: Boolean = false): String {
    val entries = LocalActionLibraryEntries.current
    if (size <= 1) {
        val value = firstOrNull() ?: Action.NONE
        return actionText(value, emptyIfNone, entries)
    }
    return remember(this, emptyIfNone, entries) {
        this
            .filter {
                it.value.isNotEmpty() && it.value != ActionFacade.NONE
            }
            .joinToString(separator = ",") {
                AppContext.get().actionText(it, emptyIfNone, entries)
            }
    }
}
