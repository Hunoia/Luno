package hunoia.luno.quicklaunch.panel
import hunoia.luno.quicklaunch.QuickLaunchFacade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import hunoia.luno.quicklaunch.icon.SearchIconCache
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hunoia.luno.R
import hunoia.luno.config.model.MAX_QUERY_CHARS
import hunoia.luno.quicklaunch.query.SearchBadge
import hunoia.luno.quicklaunch.query.SearchIcon
import hunoia.luno.quicklaunch.query.SearchResult
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.feedback.LunoIconButton
import hunoia.luno.ui.component.row.LunoSelectableRow
/**
 * 列表上下羽化：顶部 28dp / 底部 24dp，溢出截断 melts 进背景。底部罩的是钉住的首行——
 * 贴紧输入框后它 melts 进框，顺滑但首行底部是虚的，这是内容羽化的税；离屏层多一笔绘制。
 * Brush 只在尺寸变化时重建：每帧 new 一个 LinearGradient（含 FloatArray + ColorStop 列表）
 * 会在滚动时持续分配。
 */
internal fun Modifier.listFade(
    top: androidx.compose.ui.unit.Dp = 28.dp,
    bottom: androidx.compose.ui.unit.Dp = 12.dp,
): Modifier = this
    .graphicsLayer {
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }
    .then(FadeNodeElement(top, bottom))

/** listFade 的节点化实现：绘制节点持有 brush 缓存，跨帧复用。 */
private data class FadeNodeElement(
    val top: androidx.compose.ui.unit.Dp,
    val bottom: androidx.compose.ui.unit.Dp,
) : androidx.compose.ui.node.ModifierNodeElement<FadeNode>() {
    override fun create() = FadeNode(top, bottom)
    override fun update(node: FadeNode) {
        node.top = top
        node.bottom = bottom
    }
}

private class FadeNode(
    var top: androidx.compose.ui.unit.Dp,
    var bottom: androidx.compose.ui.unit.Dp,
) : androidx.compose.ui.node.DrawModifierNode, androidx.compose.ui.Modifier.Node() {
    private var cachedHeight = Float.NaN
    private var cachedTop = Float.NaN
    private var cachedBottom = Float.NaN
    private var cachedBrush: androidx.compose.ui.graphics.Brush? = null

    override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.draw() {
        drawContent()
        val h = size.height
        if (h <= 0f) return
        val topPx = top.toPx()
        val bottomPx = bottom.toPx()
        val brush = cachedBrush?.takeIf {
            cachedHeight == h && cachedTop == topPx && cachedBottom == bottomPx
        } ?: run {
            val topF = (topPx / h).coerceIn(0f, 0.5f)
            val bottomF = (bottomPx / h).coerceIn(0f, (1f - topF).coerceAtLeast(0f))
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to ComposeColor.Transparent,
                topF to ComposeColor.Black,
                (1f - bottomF) to ComposeColor.Black,
                1f to ComposeColor.Transparent,
            ).also {
                cachedBrush = it
                cachedHeight = h
                cachedTop = topPx
                cachedBottom = bottomPx
            }
        }
        drawRect(
            brush = brush,
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
}
/** 搜索行半透明底色：透出窗口背后的系统模糊，不支持模糊的设备直接呈现半透明。 */
@Composable
private fun searchRowContainer(): ComposeColor =
    MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.65f)
@Composable
private fun searchRowSelectedContainer(): ComposeColor =
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
/**
 * 行标题预览：取首行 + 上限截断。全文进 StaticLayout 会在主线程量数秒
 * （剪贴板大段文字直接 ANR 被杀），展示只取头部；长按复制仍用全文。
 */
internal fun rowPreview(text: String, limit: Int = 120): String =
    text.lines().firstOrNull()?.trim()?.take(limit).orEmpty()
/**
 * 回填搜索框上限：MB 级全文进 TextField 同样会量死主线程；[MAX_QUERY_CHARS]
 * 足够用（剪贴匹配只看前 64 字，历史是 contains 匹配，前缀不丢命中）。
 */
internal fun backfillText(text: String, limit: Int = MAX_QUERY_CHARS): String = text.take(limit)
/** 剪贴板行：标题=剪贴内容（单行省略），无副标题；点回填、长按写回系统剪贴板；
 *  行尾图钉按钮点固定/取消（固定态=primary 色，非固定=淡色）；
 *  [menu] 锚定在行尾图钉按钮处（长按弹菜单，见调用方）。
 */
@Composable
internal fun ClipboardRow(
    text: String,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    pinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    menu: (@Composable () -> Unit)? = null,
) {
    LunoSelectableRow(
        title = rowPreview(text),
        subtitle = "",
        selected = false,
        onSelectedChange = { onClick() },
        shape = shape,
        icon = Icons.Default.ContentPaste,
        containerColor = searchRowContainer(),
        selectedContainerColor = searchRowSelectedContainer(),
        onLongClick = onLongClick,
        trailing = {
            Box {
                LunoIconButton(
                    icon = Icons.Default.PushPin,
                    contentDescription = stringResource(
                        if (pinned) R.string.quick_search_unpin else R.string.quick_search_pin
                    ),
                    onClick = onTogglePin,
                    modifier = Modifier.size(32.dp),
                    tint = if (pinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                menu?.invoke()
            }
        },
    )
}
@Composable
internal fun HistoryRow(
    text: String,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    icon: ImageVector = Icons.Default.Search,
    textIcon: TextIcon? = null,
) {
    LunoSelectableRow(
        title = rowPreview(text),
        subtitle = "",
        selected = false,
        onSelectedChange = { onClick() },
        shape = shape,
        icon = icon,
        textIcon = textIcon,
        containerColor = searchRowContainer(),
        selectedContainerColor = searchRowSelectedContainer(),
        onLongClick = onLongClick,
    )
}
@Composable
internal fun ResultRow(
    result: SearchResult,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 24.dp.toPx().toInt() }
    val icon = result.icon
    val iconBitmap = when (icon) {
        is SearchIcon.Bitmap -> icon.image
        is SearchIcon.AppIcon -> icon.bitmap ?: SearchIconCache.get(icon.packageName, sizePx)
        else -> null
    }
    val iconModel = when {
        iconBitmap != null -> null
        icon is SearchIcon.AppIcon -> QuickLaunchFacade.appIconRequest(context, icon.packageName, sizePx)
        icon is SearchIcon.Request -> icon.model
        else -> null
    }
    val iconVector = (icon as? SearchIcon.Vector)?.image
    val textIcon = (icon as? SearchIcon.Text)?.text?.let(::TextIcon)
    LunoSelectableRow(
        title = result.title,
        subtitle = result.subtitle,
        headline = if (result.badge == SearchBadge.DisabledApp) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = result.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = " " + stringResource(R.string.quick_search_app_disabled),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        selected = false,
        onSelectedChange = { onClick() },
        shape = shape,
        iconModel = iconModel,
        icon = iconVector,
        iconBitmap = iconBitmap,
        textIcon = textIcon,
        containerColor = searchRowContainer(),
        selectedContainerColor = searchRowSelectedContainer(),
        onLongClick = onLongClick,
        trailing = menu?.let { anchor ->
            {
                Box {
                    LunoIconButton(
                        icon = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more),
                        onClick = onLongClick ?: {},
                    )
                    anchor()
                }
            }
        },
    )
}
