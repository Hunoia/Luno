package hunoia.luno.ui.component

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlin.math.min

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
 * 文字/表情图标的统一渲染：在 [size] 见方容器内按需缩小字号，保证不被裁剪且横竖居中。
 * 直接用 titleLarge 的 Text 会因行高超过容器而被切掉一部分，emoji 还会偏上。
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
