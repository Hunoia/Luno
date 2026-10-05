package hunoia.luno.ui.component

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
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
import hunoia.luno.action.model.StoredAction
import hunoia.luno.action.model.launchPackageName
import hunoia.luno.action.model.NewActionLibraryEntry
import hunoia.luno.action.definitions.ActionDefinitions
import hunoia.luno.config.model.DirectionActions
import hunoia.luno.quicklaunch.model.icon
import hunoia.luno.ui.actionlibrary.IconPalette
import hunoia.luno.ui.navigation.LocalActionLibraryEntries
import kotlin.math.min
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

fun Context.actionText(
    action: StoredAction,
    entryId: String? = null,
    emptyIfNone: Boolean = true,
    actionLibraryEntries: List<NewActionLibraryEntry> = emptyList()
): String {
    if (entryId != null) {
        actionLibraryEntries.firstOrNull { it.id == entryId }?.name?.takeIf { it.isNotBlank() }?.let { return it }
        if (actionLibraryEntries.isNotEmpty()) {
            return getString(R.string.action_library_missing)
        }
    }
    if (action.typeId == "app.launch") {
        return appLabel(action) ?: ""
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
    entryId: String? = null,
    emptyIfNone: Boolean = true,
    actionLibraryEntries: List<NewActionLibraryEntry> = emptyList()
): String {
    if (entryId != null) {
        actionLibraryEntries.firstOrNull { it.id == entryId }?.name?.takeIf { it.isNotBlank() }?.let { return it }
        if (actionLibraryEntries.isNotEmpty()) {
            return stringResource(R.string.action_library_missing)
        }
    }
    if (action.typeId == "app.launch") {
        val context = androidx.compose.ui.platform.LocalContext.current
        val packageName = action.launchPackageName()
        return remember(action, packageName) { context.appLabel(action) ?: "" }
    }
    return ActionDefinitions.byTypeId(action.typeId)?.name
        ?: if (emptyIfNone) "" else stringResource(R.string.action_none)
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
fun actionIcon(action: StoredAction): Any? {
    if (action.typeId == "app.launch") {
        val context = androidx.compose.ui.platform.LocalContext.current
        val packageName = action.launchPackageName()
        val className = (action.params["className"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
        return remember(action) {
            runCatching {
                if (className.isNotEmpty()) {
                    context.packageManager.getActivityIcon(
                        android.content.ComponentName.createRelative(packageName!!, className)
                    )
                } else {
                    context.packageManager.getApplicationIcon(packageName!!)
                }
            }.getOrNull()
        }
    }
    return ActionDefinitions.byTypeId(action.typeId)?.icon
}

@Composable
fun DirectionActions.actionTextCompose(): String =
    actions.values
        .map { it.actionTextCompose(true) }
        .filter { it.isNotEmpty() }
        .joinToString(separator = ",")

@Composable
fun List<StoredAction>.actionTextCompose(emptyIfNone: Boolean = false): String {
    val entries = LocalActionLibraryEntries.current
    if (size <= 1) {
        val value = firstOrNull() ?: StoredAction.of("internal.none")
        return actionText(value, emptyIfNone = emptyIfNone, actionLibraryEntries = entries)
    }
    return remember(this, emptyIfNone, entries) {
        this
            .filter {
                it.typeId.isNotEmpty() && it.typeId != "internal.none"
            }
            .joinToString(separator = ",") {
                AppContext.get().actionText(it, emptyIfNone = emptyIfNone, actionLibraryEntries = entries)
            }
    }
}
