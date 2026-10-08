package hunoia.luno.quicklaunch.panel
import hunoia.luno.quicklaunch.QuickLaunchFacade
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.ui.graphics.asImageBitmap
import hunoia.luno.quicklaunch.icon.SearchIconCache
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawWithContent
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
import hunoia.luno.quicklaunch.query.QuickSearchResult
import hunoia.luno.ui.component.TextIcon
import hunoia.luno.ui.component.row.LunoSelectableRow
/**
 * 列表上下羽化：顶部 28dp / 底部 24dp，溢出截断 melts 进背景。
 * 底部罩的是钉住的首行——贴紧输入框后它 melts 进框，顺滑但首行底部是虚的，
 * 这是内容羽化的税。离屏层多一笔绘制，行多时装机看帧率。
 */
internal fun Modifier.listFade(
    top: androidx.compose.ui.unit.Dp = 28.dp,
    bottom: androidx.compose.ui.unit.Dp = 12.dp,
): Modifier = this
    .graphicsLayer {
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val topF = (top.toPx() / h).coerceIn(0f, 0.5f)
        val bottomF = (bottom.toPx() / h).coerceIn(0f, (1f - topF).coerceAtLeast(0f))
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to ComposeColor.Transparent,
                topF to ComposeColor.Black,
                (1f - bottomF) to ComposeColor.Black,
                1f to ComposeColor.Transparent,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
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
 *  行尾图钉按钮点固定/取消（固定态=primary 色，非固定=淡色）。 */@Composable
internal fun ClipboardRow(
    text: String,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    pinned: Boolean = false,
    onTogglePin: () -> Unit = {},
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
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = if (pinned) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
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
    result: QuickSearchResult,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    // 与 flow 预热同源的边长：读缓存必须同 key（包名@边长），否则命中不了。
    val sizePx = with(LocalDensity.current) { 24.dp.toPx().toInt() }
    // 已停用标记：从包名行挪到标题行。标题占权重 1f 承担省略号，后缀固定不压缩；
    // 后缀字号比标题小（bodySmall，同原副标题），颜色沿用原副标题色（onSurfaceVariant）。
    val disabledApp = result is QuickSearchResult.App && result.isDisabled
    LunoSelectableRow(
        title = result.title,
        subtitle = result.subtitle,
        headline = if (disabledApp) {
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
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        selected = false,
        onSelectedChange = { onClick() },
        shape = shape,        iconModel = iconModel(result, context),
        icon = iconVector(result),
        // 同步图标优先（flow 发射前已 resolve，首帧即有图，无 AsyncImage 空隙）；
        // 缺席（新装应用）才走 AsyncImage 兜底。
        iconBitmap = when (result) {
            is QuickSearchResult.App -> SearchIconCache.get(result.appInfo.packageName, sizePx)
            is QuickSearchResult.Shortcut -> result.shortcut.iconBitmap?.asImageBitmap()
                ?: SearchIconCache.get(result.shortcut.packageName, sizePx)
            else -> null
        },
        textIcon = textIcon(result),
        containerColor = searchRowContainer(),
        selectedContainerColor = searchRowSelectedContainer(),
        onLongClick = onLongClick,
    )
}
@Composable
internal fun iconVector(result: QuickSearchResult): ImageVector? = when (result) {
    is QuickSearchResult.Url -> Icons.AutoMirrored.Filled.OpenInNew
    is QuickSearchResult.EngineSearch -> null
    is QuickSearchResult.Calc -> null
    is QuickSearchResult.Settings -> Icons.Default.Search
    is QuickSearchResult.ActionDef -> result.icon as? ImageVector
    is QuickSearchResult.LibraryEntry -> result.icon as? ImageVector
    is QuickSearchResult.App -> null
    is QuickSearchResult.Shortcut -> null
    is QuickSearchResult.Contact -> null
    is QuickSearchResult.AudioTrack -> Icons.Default.MusicNote
    is QuickSearchResult.ImageVideo ->
        if (result.mime.startsWith("video")) Icons.Default.Movie else Icons.Default.Image
    is QuickSearchResult.DocFile -> Icons.Default.Description
    is QuickSearchResult.CalendarEvent -> Icons.Default.DateRange
    is QuickSearchResult.SmsMsg -> Icons.AutoMirrored.Filled.Message
    is QuickSearchResult.CallLogEntry -> Icons.Default.Call
    is QuickSearchResult.ClipEntry -> Icons.Default.ContentPaste
    is QuickSearchResult.PhoneToggle -> when (result.kind) {
        QuickSearchResult.ToggleKind.WIFI -> Icons.Default.Wifi
        QuickSearchResult.ToggleKind.BLUETOOTH -> Icons.Default.Bluetooth
        QuickSearchResult.ToggleKind.TORCH -> Icons.Default.FlashlightOn
        QuickSearchResult.ToggleKind.DND -> Icons.Default.DoNotDisturbOn
        QuickSearchResult.ToggleKind.LOCATION -> Icons.Default.LocationOn
        QuickSearchResult.ToggleKind.HOTSPOT -> Icons.Default.WifiTethering
    }
}
@Composable
internal fun iconModel(result: QuickSearchResult, context: android.content.Context): Any? {
    val sizePx = with(LocalDensity.current) { 24.dp.toPx().toInt() }
    return when (result) {
        is QuickSearchResult.App ->
            QuickLaunchFacade.appIconRequest(context, result.appInfo.packageName, sizePx)
        // 快捷方式自带图标优先（行为不变），取不到才回落来源应用图标（之前行首空着）。
        is QuickSearchResult.Shortcut -> result.shortcut.iconBitmap
            ?: QuickLaunchFacade.appIconRequest(context, result.shortcut.packageName, sizePx)
        else -> null
    }
}
@Composable
internal fun textIcon(result: QuickSearchResult): TextIcon? = when (result) {
    is QuickSearchResult.LibraryEntry -> result.icon as? TextIcon
    is QuickSearchResult.ActionDef -> result.icon as? TextIcon
    // 引擎行用首字图标（B/G/D…），计算器行用 =，联系人用姓名首字，替代通用图标。
    is QuickSearchResult.EngineSearch -> result.engineName.firstOrNull()?.toString()?.let { TextIcon(it) }
    is QuickSearchResult.Calc -> TextIcon("=")
    is QuickSearchResult.Contact -> result.name.firstOrNull()?.toString()?.let { TextIcon(it) }
    else -> null
}